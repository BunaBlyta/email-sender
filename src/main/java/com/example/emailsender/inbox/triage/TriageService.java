package com.example.emailsender.inbox.triage;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.MailThreadRepository;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.mail.provider.FetchedMessage;
import com.example.emailsender.mail.provider.FetchedThread;
import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.screener.ScreenerRepository;
import com.example.emailsender.security.PhishingAnalysisRequest;
import com.example.emailsender.security.PhishingAnalysisResponse;
import com.example.emailsender.security.PhishingDetector;
import com.example.emailsender.security.PhishingRiskLevel;
import com.example.emailsender.security.PhishingTrustContext;
import com.example.emailsender.security.SenderTrustService;
import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.IDN;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TriageService {

    private static final int MIN_RESULTS = 1;
    private static final int MAX_RESULTS = 50;
    private static final Pattern REQUEST_PATTERN = Pattern.compile(
            "\\?|\\b(please|could you|can you|would you|let me know|review|approve"
                    + "|confirm|send me|reply|respond|need your|thoughts)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IMPORTANCE_PATTERN = Pattern.compile(
            "\\b(deadline|due today|due tomorrow|meeting|thesis|supervisor"
                    + "|important|priority)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AUTOMATED_PATTERN = Pattern.compile(
            "\\b(no-?reply|do-?not-?reply|notification|newsletter|digest"
                    + "|promotion|receipt|unsubscribe)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OPERATIONAL_PATTERN = Pattern.compile(
            "\\b(invoice|billing|payment|order|shipment|booking|ticket|receipt"
                    + "|account|verification|security alert|statement)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final UserRepository userRepository;
    private final MailThreadRepository mailThreadRepository;
    private final GmailProvider gmailProvider;
    private final SenderTrustService senderTrustService;
    private final PhishingDetector phishingDetector;
    private final ScreenerRepository screenerRepository;

    public TriageService(
            UserRepository userRepository,
            MailThreadRepository mailThreadRepository,
            GmailProvider gmailProvider,
            SenderTrustService senderTrustService,
            PhishingDetector phishingDetector,
            ScreenerRepository screenerRepository) {
        this.userRepository = userRepository;
        this.mailThreadRepository = mailThreadRepository;
        this.gmailProvider = gmailProvider;
        this.senderTrustService = senderTrustService;
        this.phishingDetector = phishingDetector;
        this.screenerRepository = screenerRepository;
    }

    @Transactional(readOnly = true)
    public TriageInboxResponse triageInbox(String email, int maxResults) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        if (maxResults < MIN_RESULTS || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException("maxResults must be between 1 and 50");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<MailThread> threadSummaries = gmailProvider.fetchThreads(user, maxResults);
        List<TriageThreadResponse> threads = threadSummaries.stream()
                .map(summary -> gmailProvider.fetchThread(user, summary.getExternalThreadId()))
                .map(thread -> triageThread(email, user, thread))
                .sorted(Comparator
                        .comparingInt(TriageThreadResponse::attentionScore)
                        .reversed()
                        .thenComparing(
                                TriageThreadResponse::lastMessageAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                .toList();

        return new TriageInboxResponse(
                threads.size(),
                count(threads, TriageLabel.SECURITY_REVIEW),
                count(threads, TriageLabel.NEEDS_REPLY),
                count(threads, TriageLabel.IMPORTANT),
                count(threads, TriageLabel.WAITING),
                count(threads, TriageLabel.LOW_PRIORITY),
                count(threads, TriageLabel.FYI),
                threads
        );
    }

    private TriageThreadResponse triageThread(String email, User user, FetchedThread thread) {
        MailThread localThread = localThreadState(user, thread);
        FetchedMessage latest = latestMessage(thread);
        if (latest == null) {
            ThreadSignals signals = new ThreadSignals(
                    PhishingTrustContext.none(),
                    null,
                    null,
                    MailThread.Category.THINGS
            );
            return response(
                    thread,
                    localThread,
                    signals,
                    TriageLabel.FYI,
                    10,
                    "Review when convenient.",
                    List.of("No message content was available for this thread.")
            );
        }

        String combinedText = text(thread.subject(), latest.body(), latest.snippet());
        boolean inbound = latest.direction() == Message.Direction.INBOUND;
        boolean outbound = latest.direction() == Message.Direction.OUTBOUND;
        boolean request = REQUEST_PATTERN.matcher(combinedText).find();
        boolean importantLanguage = IMPORTANCE_PATTERN.matcher(combinedText).find();
        boolean automated = AUTOMATED_PATTERN.matcher(text(latest.sender(), combinedText)).find();

        SenderIdentity sender = senderIdentity(latest);
        PhishingTrustContext trust = sender.raw().isBlank()
                ? PhishingTrustContext.none()
                : senderTrustService.trustContext(email, sender.raw());
        PhishingAnalysisResponse phishing = phishingDetector.analyze(
                new PhishingAnalysisRequest(sender.raw(), thread.subject(), combinedText),
                trust
        );
        MailThread.ScreenerStatus screenerStatus = screenerStatus(user, sender);
        ThreadSignals signals = new ThreadSignals(
                trust,
                phishing,
                screenerStatus,
                suggestedCategory(latest, combinedText, trust, phishing, automated)
        );

        if (phishing.riskLevel() == PhishingRiskLevel.HIGH) {
            return response(
                    thread,
                    localThread,
                    signals,
                    TriageLabel.SECURITY_REVIEW,
                    95,
                    "Review security signals before replying or clicking links.",
                    List.of("High-risk phishing signals were found.")
            );
        }
        if (inbound && request && !automated) {
            return response(
                    thread,
                    localThread,
                    signals,
                    TriageLabel.NEEDS_REPLY,
                    trust.hasTrust() ? 90 : 80,
                    "Decide whether this needs a reply.",
                    reasons(
                            "The latest message is from someone else.",
                            "The message appears to ask for a response.",
                            trust.hasTrust() ? "The sender or domain is trusted." : null
                    )
            );
        }
        if (inbound && (trust.hasTrust() || importantLanguage || thread.hasUnread()) && !automated) {
            return response(
                    thread,
                    localThread,
                    signals,
                    TriageLabel.IMPORTANT,
                    trust.hasTrust() ? 75 : 65,
                    "Read this before lower-priority mail.",
                    reasons(
                            "The latest message is from someone else.",
                            trust.hasTrust() ? "The sender or domain is trusted." : null,
                            importantLanguage ? "The message contains priority-related language." : null,
                            thread.hasUnread() ? "The thread has unread mail." : null
                    )
            );
        }
        if (outbound) {
            return response(
                    thread,
                    localThread,
                    signals,
                    TriageLabel.WAITING,
                    45,
                    "No action needed unless you want to follow up manually.",
                    List.of("Your message appears to be the latest reply.")
            );
        }
        if (automated) {
            return response(
                    thread,
                    localThread,
                    signals,
                    TriageLabel.LOW_PRIORITY,
                    25,
                    "Review later or clean up when convenient.",
                    List.of("The sender or content looks automated.")
            );
        }
        return response(
                thread,
                localThread,
                signals,
                TriageLabel.FYI,
                35,
                "Review when convenient.",
                List.of("No urgent attention signal was found.")
        );
    }

    private TriageThreadResponse response(
            FetchedThread thread,
            MailThread localThread,
            ThreadSignals signals,
            TriageLabel label,
            int attentionScore,
            String suggestedAction,
            List<String> reasons) {
        MailThread.Category suggestedCategory = signals.suggestedCategory();
        MailThread.Category category = localThread.isCategoryOverride()
                && localThread.getCategory() != null
                ? localThread.getCategory()
                : suggestedCategory;
        return new TriageThreadResponse(
                thread.externalThreadId(),
                thread.subject(),
                thread.participants(),
                thread.lastMessageAt(),
                thread.hasUnread(),
                label,
                attentionScore,
                suggestedAction,
                reasons,
                category,
                localThread.isCategoryOverride(),
                suggestedCategory,
                workflowState(localThread),
                signals.screenerStatus(),
                signals.trust().senderTrusted(),
                signals.trust().domainTrusted(),
                signals.riskLevel(),
                signals.phishingScore()
        );
    }

    private MailThread localThreadState(User user, FetchedThread fetchedThread) {
        String threadId = requireThreadId(fetchedThread.externalThreadId());
        MailThread localThread = mailThreadRepository
                .findFirstByUserAndExternalThreadIdOrderByIdAsc(user, threadId)
                .orElseGet(() -> {
                    MailThread created = new MailThread();
                    created.setUser(user);
                    created.setExternalThreadId(threadId);
                    created.setWorkflowState(MailThread.WorkflowState.ACTIVE);
                    return created;
                });
        return localThread;
    }

    private String requireThreadId(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("Thread id is required");
        }
        return threadId.trim();
    }

    private MailThread.WorkflowState workflowState(MailThread localThread) {
        return localThread.getWorkflowState() == null
                ? MailThread.WorkflowState.ACTIVE
                : localThread.getWorkflowState();
    }

    private MailThread.ScreenerStatus screenerStatus(User user, SenderIdentity sender) {
        if (sender.email().isBlank()) {
            return null;
        }
        return screenerRepository
                .findByUserAndSenderEmailIgnoreCase(user, sender.email())
                .map(entry -> switch (entry.getStatus()) {
                    case APPROVED -> MailThread.ScreenerStatus.APPROVED;
                    case REJECTED -> MailThread.ScreenerStatus.REJECTED;
                    case PENDING -> MailThread.ScreenerStatus.PENDING;
                })
                .orElse(null);
    }

    private MailThread.Category suggestedCategory(
            FetchedMessage latest,
            String combinedText,
            PhishingTrustContext trust,
            PhishingAnalysisResponse phishing,
            boolean automated) {
        if (automated) {
            return MailThread.Category.NOISE;
        }
        if (phishing.riskLevel() == PhishingRiskLevel.HIGH
                || OPERATIONAL_PATTERN.matcher(combinedText).find()) {
            return MailThread.Category.THINGS;
        }
        if (trust.hasTrust()) {
            return MailThread.Category.PEOPLE;
        }
        if (latest.direction() == Message.Direction.INBOUND
                || latest.direction() == Message.Direction.OUTBOUND) {
            return MailThread.Category.PEOPLE;
        }
        return MailThread.Category.THINGS;
    }

    private SenderIdentity senderIdentity(FetchedMessage latestMessage) {
        if (latestMessage == null) {
            return new SenderIdentity("", "", "");
        }
        String raw = normalize(latestMessage.sender());
        String email = extractEmail(raw);
        return new SenderIdentity(raw, email, domainFromEmail(email));
    }

    private String extractEmail(String value) {
        if (value.isBlank()) {
            return "";
        }
        try {
            InternetAddress[] addresses = InternetAddress.parse(value, false);
            if (addresses.length != 1 || addresses[0].getAddress() == null) {
                return "";
            }
            String address = addresses[0].getAddress().trim().toLowerCase(Locale.ROOT);
            InternetAddress strictAddress = new InternetAddress(address, true);
            strictAddress.validate();
            return address.contains("@") ? address : "";
        } catch (AddressException exception) {
            return "";
        }
    }

    private String domainFromEmail(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return "";
        }
        try {
            return IDN.toASCII(email.substring(atIndex + 1).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        }
    }

    private int count(List<TriageThreadResponse> threads, TriageLabel label) {
        return (int) threads.stream()
                .filter(thread -> thread.label() == label)
                .count();
    }

    private FetchedMessage latestMessage(FetchedThread thread) {
        if (thread.messages() == null || thread.messages().isEmpty()) {
            return null;
        }
        return thread.messages().stream()
                .max(Comparator.comparing(
                        FetchedMessage::sentAt,
                        Comparator.nullsFirst(LocalDateTime::compareTo)
                ))
                .orElse(null);
    }

    private List<String> reasons(String... values) {
        return java.util.Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String text(String... values) {
        if (values == null) {
            return "";
        }
        return java.util.Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record SenderIdentity(String raw, String email, String domain) {
    }

    private record ThreadSignals(
            PhishingTrustContext trust,
            PhishingAnalysisResponse phishing,
            MailThread.ScreenerStatus screenerStatus,
            MailThread.Category suggestedCategory) {

        private PhishingRiskLevel riskLevel() {
            return phishing == null ? PhishingRiskLevel.LOW : phishing.riskLevel();
        }

        private int phishingScore() {
            return phishing == null ? 0 : phishing.score();
        }
    }
}

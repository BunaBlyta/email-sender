package com.example.emailsender.inbox.triage;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.mail.provider.FetchedMessage;
import com.example.emailsender.mail.provider.FetchedThread;
import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.security.PhishingAnalysisRequest;
import com.example.emailsender.security.PhishingAnalysisResponse;
import com.example.emailsender.security.PhishingDetector;
import com.example.emailsender.security.PhishingRiskLevel;
import com.example.emailsender.security.PhishingTrustContext;
import com.example.emailsender.security.SenderTrustService;
import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.springframework.stereotype.Service;

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

    private final UserRepository userRepository;
    private final GmailProvider gmailProvider;
    private final SenderTrustService senderTrustService;
    private final PhishingDetector phishingDetector;

    public TriageService(
            UserRepository userRepository,
            GmailProvider gmailProvider,
            SenderTrustService senderTrustService,
            PhishingDetector phishingDetector) {
        this.userRepository = userRepository;
        this.gmailProvider = gmailProvider;
        this.senderTrustService = senderTrustService;
        this.phishingDetector = phishingDetector;
    }

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
                .map(thread -> triageThread(email, thread))
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

    private TriageThreadResponse triageThread(String email, FetchedThread thread) {
        FetchedMessage latest = latestMessage(thread);
        if (latest == null) {
            return response(
                    thread,
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

        PhishingTrustContext trust = senderTrustService.trustContext(email, latest.sender());
        PhishingAnalysisResponse phishing = phishingDetector.analyze(
                new PhishingAnalysisRequest(latest.sender(), thread.subject(), combinedText),
                trust
        );

        if (phishing.riskLevel() == PhishingRiskLevel.HIGH) {
            return response(
                    thread,
                    TriageLabel.SECURITY_REVIEW,
                    95,
                    "Review security signals before replying or clicking links.",
                    List.of("High-risk phishing signals were found.")
            );
        }
        if (inbound && request && !automated) {
            return response(
                    thread,
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
                    TriageLabel.WAITING,
                    45,
                    "No action needed unless you want to follow up manually.",
                    List.of("Your message appears to be the latest reply.")
            );
        }
        if (automated) {
            return response(
                    thread,
                    TriageLabel.LOW_PRIORITY,
                    25,
                    "Review later or clean up when convenient.",
                    List.of("The sender or content looks automated.")
            );
        }
        return response(
                thread,
                TriageLabel.FYI,
                35,
                "Review when convenient.",
                List.of("No urgent attention signal was found.")
        );
    }

    private TriageThreadResponse response(
            FetchedThread thread,
            TriageLabel label,
            int attentionScore,
            String suggestedAction,
            List<String> reasons) {
        return new TriageThreadResponse(
                thread.externalThreadId(),
                thread.subject(),
                thread.participants(),
                thread.lastMessageAt(),
                thread.hasUnread(),
                label,
                attentionScore,
                suggestedAction,
                reasons
        );
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
}

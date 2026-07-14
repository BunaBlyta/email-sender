package com.example.emailsender.inbox.context;

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
import com.example.emailsender.security.PhishingSignalResponse;
import com.example.emailsender.security.PhishingTrustContext;
import com.example.emailsender.security.SenderTrustService;
import com.example.emailsender.security.TrustRequest;
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
public class ThreadContextService {

    private static final Pattern AUTOMATED_PATTERN = Pattern.compile(
            "\\b(no-?reply|do-?not-?reply|notification|newsletter|digest"
                    + "|promotion|receipt|unsubscribe|automated message)\\b",
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

    public ThreadContextService(
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
    public ThreadContextResponse getContext(String email, String threadId) {
        User user = findUser(email);
        FetchedThread fetchedThread = fetchThread(user, threadId);
        MailThread localThread = localThreadState(user, fetchedThread);
        return buildResponse(email, user, localThread, fetchedThread);
    }

    @Transactional
    public ThreadContextResponse updateCategory(
            String email,
            String threadId,
            ThreadCategoryRequest request) {
        if (request == null || request.category() == null) {
            throw new IllegalArgumentException("Category is required");
        }

        User user = findUser(email);
        FetchedThread fetchedThread = fetchThread(user, threadId);
        MailThread localThread = localThreadState(user, fetchedThread);
        localThread.setCategory(request.category());
        localThread.setCategoryOverride(true);
        if (localThread.getWorkflowState() == null) {
            localThread.setWorkflowState(MailThread.WorkflowState.ACTIVE);
        }
        localThread = mailThreadRepository.save(localThread);
        return buildResponse(email, user, localThread, fetchedThread);
    }

    @Transactional
    public ThreadContextResponse updateWorkflowState(
            String email,
            String threadId,
            ThreadWorkflowStateRequest request) {
        if (request == null || request.workflowState() == null) {
            throw new IllegalArgumentException("Workflow state is required");
        }

        User user = findUser(email);
        FetchedThread fetchedThread = fetchThread(user, threadId);
        MailThread localThread = localThreadState(user, fetchedThread);
        localThread.setWorkflowState(request.workflowState());
        localThread = mailThreadRepository.save(localThread);
        return buildResponse(email, user, localThread, fetchedThread);
    }

    @Transactional
    public ThreadContextResponse trustSender(String email, String threadId) {
        User user = findUser(email);
        FetchedThread fetchedThread = fetchThread(user, threadId);
        SenderIdentity sender = senderIdentity(latestMessage(fetchedThread));
        if (sender.email().isBlank()) {
            throw new IllegalArgumentException("Valid sender email is required");
        }

        senderTrustService.trustSender(email, new TrustRequest(sender.raw()));
        MailThread localThread = localThreadState(user, fetchedThread);
        return buildResponse(email, user, localThread, fetchedThread);
    }

    @Transactional
    public ThreadContextResponse trustDomain(String email, String threadId) {
        User user = findUser(email);
        FetchedThread fetchedThread = fetchThread(user, threadId);
        SenderIdentity sender = senderIdentity(latestMessage(fetchedThread));
        if (sender.domain().isBlank()) {
            throw new IllegalArgumentException("Valid sender domain is required");
        }

        senderTrustService.trustDomain(email, new TrustRequest(sender.domain()));
        MailThread localThread = localThreadState(user, fetchedThread);
        return buildResponse(email, user, localThread, fetchedThread);
    }

    private ThreadContextResponse buildResponse(
            String email,
            User user,
            MailThread localThread,
            FetchedThread fetchedThread) {
        FetchedMessage latestMessage = latestMessage(fetchedThread);
        SenderIdentity sender = senderIdentity(latestMessage);
        PhishingTrustContext trust = trustContext(email, sender);
        PhishingAnalysisResponse phishing = analyzePhishing(
                fetchedThread,
                latestMessage,
                trust
        );
        MailThread.ScreenerStatus screenerStatus = screenerStatus(user, sender);

        CategoryDecision categoryDecision = categoryDecision(
                fetchedThread,
                latestMessage,
                sender,
                trust,
                phishing
        );
        MailThread.Category category = localThread.isCategoryOverride()
                && localThread.getCategory() != null
                ? localThread.getCategory()
                : categoryDecision.category();

        return new ThreadContextResponse(
                fetchedThread.externalThreadId(),
                fetchedThread.subject(),
                emptyIfNull(fetchedThread.participants()),
                fetchedThread.lastMessageAt(),
                fetchedThread.hasUnread(),
                category,
                localThread.isCategoryOverride(),
                categoryDecision.category(),
                workflowState(localThread),
                screenerStatus,
                sender.raw(),
                sender.email(),
                sender.domain(),
                trust.senderTrusted(),
                trust.domainTrusted(),
                riskLevel(phishing),
                phishingScore(phishing),
                phishingSignals(phishing),
                categoryDecision.reasons()
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

    private FetchedThread fetchThread(User user, String threadId) {
        String normalizedThreadId = requireThreadId(threadId);
        return gmailProvider.fetchThread(user, normalizedThreadId);
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
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

    private PhishingTrustContext trustContext(String email, SenderIdentity sender) {
        if (sender.raw().isBlank()) {
            return PhishingTrustContext.none();
        }
        return senderTrustService.trustContext(email, sender.raw());
    }

    private PhishingAnalysisResponse analyzePhishing(
            FetchedThread thread,
            FetchedMessage latestMessage,
            PhishingTrustContext trust) {
        if (latestMessage == null) {
            return phishingDetector.analyze(
                    new PhishingAnalysisRequest("", thread.subject(), ""),
                    trust
            );
        }
        return phishingDetector.analyze(
                new PhishingAnalysisRequest(
                        latestMessage.sender(),
                        thread.subject(),
                        text(latestMessage.body(), latestMessage.snippet())
                ),
                trust
        );
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

    private CategoryDecision categoryDecision(
            FetchedThread thread,
            FetchedMessage latestMessage,
            SenderIdentity sender,
            PhishingTrustContext trust,
            PhishingAnalysisResponse phishing) {
        String combinedText = text(
                sender.raw(),
                thread.subject(),
                latestMessage == null ? "" : latestMessage.body(),
                latestMessage == null ? "" : latestMessage.snippet()
        );
        boolean automated = AUTOMATED_PATTERN.matcher(combinedText).find();
        boolean operational = OPERATIONAL_PATTERN.matcher(combinedText).find();

        if (automated) {
            return new CategoryDecision(
                    MailThread.Category.NOISE,
                    List.of("The sender or content looks automated.")
            );
        }
        if (riskLevel(phishing) == PhishingRiskLevel.HIGH) {
            return new CategoryDecision(
                    MailThread.Category.THINGS,
                    List.of("High-risk security signals were found.")
            );
        }
        if (operational) {
            return new CategoryDecision(
                    MailThread.Category.THINGS,
                    List.of("The message looks transactional or account-related.")
            );
        }
        if (trust.hasTrust()) {
            return new CategoryDecision(
                    MailThread.Category.PEOPLE,
                    List.of("The sender or domain is trusted.")
            );
        }
        if (latestMessage != null && latestMessage.direction() == Message.Direction.INBOUND) {
            return new CategoryDecision(
                    MailThread.Category.PEOPLE,
                    List.of("The latest message is from someone else.")
            );
        }
        if (latestMessage != null && latestMessage.direction() == Message.Direction.OUTBOUND) {
            return new CategoryDecision(
                    MailThread.Category.PEOPLE,
                    List.of("You are already participating in this conversation.")
            );
        }
        return new CategoryDecision(
                MailThread.Category.THINGS,
                List.of("No strong people or noise signal was found.")
        );
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

    private PhishingRiskLevel riskLevel(PhishingAnalysisResponse phishing) {
        return phishing == null ? PhishingRiskLevel.LOW : phishing.riskLevel();
    }

    private int phishingScore(PhishingAnalysisResponse phishing) {
        return phishing == null ? 0 : phishing.score();
    }

    private List<PhishingSignalResponse> phishingSignals(PhishingAnalysisResponse phishing) {
        return phishing == null || phishing.signals() == null ? List.of() : phishing.signals();
    }

    private List<String> emptyIfNull(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
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

    private record CategoryDecision(MailThread.Category category, List<String> reasons) {
    }
}

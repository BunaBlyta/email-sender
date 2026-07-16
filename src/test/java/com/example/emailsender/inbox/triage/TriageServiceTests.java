package com.example.emailsender.inbox.triage;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.MailThreadRepository;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.mail.provider.FetchedMessage;
import com.example.emailsender.mail.provider.FetchedThread;
import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.screener.ScreenerRepository;
import com.example.emailsender.security.PhishingAnalysisResponse;
import com.example.emailsender.security.PhishingDetector;
import com.example.emailsender.security.PhishingRiskLevel;
import com.example.emailsender.security.PhishingTrustContext;
import com.example.emailsender.security.PhishingTrustResponse;
import com.example.emailsender.security.SenderTrustService;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TriageServiceTests {

    private UserRepository userRepository;
    private MailThreadRepository mailThreadRepository;
    private GmailProvider gmailProvider;
    private SenderTrustService senderTrustService;
    private PhishingDetector phishingDetector;
    private ScreenerRepository screenerRepository;
    private TriageService triageService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mailThreadRepository = mock(MailThreadRepository.class);
        gmailProvider = mock(GmailProvider.class);
        senderTrustService = mock(SenderTrustService.class);
        phishingDetector = mock(PhishingDetector.class);
        screenerRepository = mock(ScreenerRepository.class);
        triageService = new TriageService(
                userRepository,
                mailThreadRepository,
                gmailProvider,
                senderTrustService,
                phishingDetector,
                screenerRepository
        );

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(any(), any()))
                .thenReturn(Optional.empty());
        when(mailThreadRepository.save(any(MailThread.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(screenerRepository.findByUserAndSenderEmailIgnoreCase(any(), any()))
                .thenReturn(Optional.empty());
        when(senderTrustService.trustContext(eq("user@example.com"), any()))
                .thenReturn(PhishingTrustContext.none());
        when(phishingDetector.analyze(any(), any()))
                .thenReturn(phishing(PhishingRiskLevel.LOW));
    }

    @Test
    void labelsInboundRequestAsNeedsReply() {
        FetchedThread thread = thread(
                "thread-1",
                "Thesis review",
                true,
                message(
                        "Professor Ercan <ercan@university.edu>",
                        "Could you review the latest chapter?",
                        Message.Direction.INBOUND
                )
        );
        when(senderTrustService.trustContext(
                "user@example.com",
                "Professor Ercan <ercan@university.edu>"
        )).thenReturn(new PhishingTrustContext(true, false));
        mockInbox(thread);

        TriageInboxResponse response =
                triageService.triageInbox("user@example.com", 20);

        assertEquals(1, response.needsReplyCount());
        assertEquals(TriageLabel.NEEDS_REPLY, response.threads().getFirst().label());
        assertEquals(90, response.threads().getFirst().attentionScore());
        assertEquals("Decide whether this needs a reply.",
                response.threads().getFirst().suggestedAction());
        assertEquals(MailThread.Category.PEOPLE, response.threads().getFirst().category());
        assertEquals(MailThread.Category.PEOPLE,
                response.threads().getFirst().suggestedCategory());
        assertEquals(MailThread.WorkflowState.ACTIVE,
                response.threads().getFirst().workflowState());
        assertEquals(PhishingRiskLevel.LOW, response.threads().getFirst().phishingRiskLevel());
        assertEquals(0, response.threads().getFirst().phishingScore());
        assertEquals(true, response.threads().getFirst().senderTrusted());
    }

    @Test
    void labelsHighRiskMessageAsSecurityReview() {
        FetchedThread thread = thread(
                "thread-2",
                "Urgent verification",
                true,
                message(
                        "Support <support@example-alerts.com>",
                        "Login at http://192.168.1.10 now.",
                        Message.Direction.INBOUND
                )
        );
        when(phishingDetector.analyze(any(), any()))
                .thenReturn(phishing(PhishingRiskLevel.HIGH));
        mockInbox(thread);

        TriageInboxResponse response =
                triageService.triageInbox("user@example.com", 20);

        assertEquals(1, response.securityReviewCount());
        assertEquals(TriageLabel.SECURITY_REVIEW, response.threads().getFirst().label());
        assertEquals("Review security signals before replying or clicking links.",
                response.threads().getFirst().suggestedAction());
        assertEquals(MailThread.Category.THINGS, response.threads().getFirst().category());
        assertEquals(PhishingRiskLevel.HIGH, response.threads().getFirst().phishingRiskLevel());
    }

    @Test
    void labelsOutboundLatestMessageAsWaiting() {
        FetchedThread thread = thread(
                "thread-3",
                "Proposal",
                false,
                message(
                        "user@example.com",
                        "I sent the proposal for review.",
                        Message.Direction.OUTBOUND
                )
        );
        mockInbox(thread);

        TriageInboxResponse response =
                triageService.triageInbox("user@example.com", 20);

        assertEquals(1, response.waitingCount());
        assertEquals(TriageLabel.WAITING, response.threads().getFirst().label());
        assertEquals(MailThread.Category.PEOPLE, response.threads().getFirst().category());
    }

    @Test
    void labelsAutomatedMailAsLowPriority() {
        FetchedThread thread = thread(
                "thread-4",
                "Weekly newsletter",
                false,
                message(
                        "Newsletter <no-reply@news.example.com>",
                        "This week in your digest. Unsubscribe here.",
                        Message.Direction.INBOUND
                )
        );
        mockInbox(thread);

        TriageInboxResponse response =
                triageService.triageInbox("user@example.com", 20);

        assertEquals(1, response.lowPriorityCount());
        assertEquals(TriageLabel.LOW_PRIORITY, response.threads().getFirst().label());
        assertEquals(MailThread.Category.NOISE, response.threads().getFirst().category());
        assertEquals(MailThread.Category.NOISE,
                response.threads().getFirst().suggestedCategory());
    }

    @Test
    void keepsManualCategoryOverrideInTriageResponse() {
        FetchedThread thread = thread(
                "thread-5",
                "Weekly newsletter",
                false,
                message(
                        "Newsletter <no-reply@news.example.com>",
                        "This week in your digest. Unsubscribe here.",
                        Message.Direction.INBOUND
                )
        );
        MailThread localThread = new MailThread();
        localThread.setUser(user);
        localThread.setExternalThreadId("thread-5");
        localThread.setCategory(MailThread.Category.PEOPLE);
        localThread.setCategoryOverride(true);
        localThread.setWorkflowState(MailThread.WorkflowState.NEEDS_ACTION);
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(user, "thread-5"))
                .thenReturn(Optional.of(localThread));
        mockInbox(thread);

        TriageInboxResponse response =
                triageService.triageInbox("user@example.com", 20);

        assertEquals(MailThread.Category.PEOPLE, response.threads().getFirst().category());
        assertEquals(MailThread.Category.NOISE,
                response.threads().getFirst().suggestedCategory());
        assertEquals(true, response.threads().getFirst().categoryOverride());
        assertEquals(MailThread.WorkflowState.NEEDS_ACTION,
                response.threads().getFirst().workflowState());
    }

    @Test
    void readDefaultsMissingWorkflowStateWithoutMutatingStoredThread() {
        FetchedThread thread = thread(
                "thread-read-only",
                "Read-only triage",
                false,
                message(
                        "Sender <sender@example.com>",
                        "A message that should not change local state.",
                        Message.Direction.INBOUND
                )
        );
        MailThread localThread = new MailThread();
        localThread.setUser(user);
        localThread.setExternalThreadId("thread-read-only");
        localThread.setWorkflowState(null);
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(
                user,
                "thread-read-only"
        )).thenReturn(Optional.of(localThread));
        mockInbox(thread);

        TriageInboxResponse response =
                triageService.triageInbox("user@example.com", 20);

        assertEquals(MailThread.WorkflowState.ACTIVE,
                response.threads().getFirst().workflowState());
        assertNull(localThread.getWorkflowState());
        verify(mailThreadRepository, never()).save(any(MailThread.class));
    }

    @Test
    void rejectsUnsupportedResultLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> triageService.triageInbox("user@example.com", 51)
        );

        assertEquals("maxResults must be between 1 and 50", exception.getMessage());
        verifyNoInteractions(gmailProvider);
    }

    private void mockInbox(FetchedThread thread) {
        MailThread summary = new MailThread();
        summary.setExternalThreadId(thread.externalThreadId());
        summary.setSubject(thread.subject());
        summary.setParticipants(thread.participants());
        summary.setLastMessageAt(thread.lastMessageAt());
        summary.setHasUnread(thread.hasUnread());
        when(gmailProvider.fetchThreads(user, 20)).thenReturn(List.of(summary));
        when(gmailProvider.fetchThread(user, thread.externalThreadId()))
                .thenReturn(thread);
    }

    private FetchedThread thread(
            String id,
            String subject,
            boolean unread,
            FetchedMessage message) {
        return new FetchedThread(
                id,
                subject,
                List.of(message.sender(), "user@example.com"),
                message.sentAt(),
                unread,
                List.of(message)
        );
    }

    private FetchedMessage message(
            String sender,
            String body,
            Message.Direction direction) {
        return new FetchedMessage(
                "message-" + Math.abs(body.hashCode()),
                sender,
                List.of("user@example.com"),
                body,
                body,
                LocalDateTime.of(2026, 6, 25, 12, 0),
                direction,
                true,
                null,
                List.of()
        );
    }

    private PhishingAnalysisResponse phishing(PhishingRiskLevel riskLevel) {
        return new PhishingAnalysisResponse(
                "sender@example.com",
                "example.com",
                riskLevel,
                riskLevel == PhishingRiskLevel.HIGH ? 90 : 0,
                List.of(),
                List.of(),
                new PhishingTrustResponse(false, false, 0)
        );
    }
}

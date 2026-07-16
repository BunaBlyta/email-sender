package com.example.emailsender.inbox.context;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.MailThreadRepository;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.mail.provider.FetchedMessage;
import com.example.emailsender.mail.provider.FetchedThread;
import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.screener.ScreenerEntry;
import com.example.emailsender.screener.ScreenerRepository;
import com.example.emailsender.security.PhishingAnalysisResponse;
import com.example.emailsender.security.PhishingDetector;
import com.example.emailsender.security.PhishingRiskLevel;
import com.example.emailsender.security.PhishingTrustContext;
import com.example.emailsender.security.PhishingTrustResponse;
import com.example.emailsender.security.SenderTrustService;
import com.example.emailsender.security.TrustEntryResponse;
import com.example.emailsender.security.TrustRequest;
import com.example.emailsender.security.TrustScope;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ThreadContextServiceTests {

    private UserRepository userRepository;
    private MailThreadRepository mailThreadRepository;
    private GmailProvider gmailProvider;
    private SenderTrustService senderTrustService;
    private PhishingDetector phishingDetector;
    private ScreenerRepository screenerRepository;
    private ThreadContextService threadContextService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mailThreadRepository = mock(MailThreadRepository.class);
        gmailProvider = mock(GmailProvider.class);
        senderTrustService = mock(SenderTrustService.class);
        phishingDetector = mock(PhishingDetector.class);
        screenerRepository = mock(ScreenerRepository.class);
        threadContextService = new ThreadContextService(
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
        when(mailThreadRepository.save(any(MailThread.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(senderTrustService.trustContext(eq("user@example.com"), any()))
                .thenReturn(PhishingTrustContext.none());
        when(phishingDetector.analyze(any(), any()))
                .thenReturn(phishing(PhishingRiskLevel.LOW, 0));
        when(screenerRepository.findByUserAndSenderEmailIgnoreCase(any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void returnsThreadContextWithSuggestedPeopleCategoryAndTrustSignals() {
        FetchedThread thread = thread(
                "thread-1",
                "Thesis review",
                message(
                        "Professor Ercan <ercan@university.edu>",
                        "Could you review the latest chapter?",
                        Message.Direction.INBOUND
                )
        );
        ScreenerEntry screenerEntry = new ScreenerEntry();
        screenerEntry.setStatus(ScreenerEntry.Status.APPROVED);
        when(gmailProvider.fetchThread(user, "thread-1")).thenReturn(thread);
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(user, "thread-1"))
                .thenReturn(Optional.empty());
        when(senderTrustService.trustContext(
                "user@example.com",
                "Professor Ercan <ercan@university.edu>"
        )).thenReturn(new PhishingTrustContext(true, false));
        when(screenerRepository.findByUserAndSenderEmailIgnoreCase(
                user,
                "ercan@university.edu"
        )).thenReturn(Optional.of(screenerEntry));

        ThreadContextResponse response =
                threadContextService.getContext("user@example.com", " thread-1 ");

        assertEquals("thread-1", response.threadId());
        assertEquals("Thesis review", response.subject());
        assertEquals(MailThread.Category.PEOPLE, response.category());
        assertEquals(MailThread.Category.PEOPLE, response.suggestedCategory());
        assertFalse(response.categoryOverride());
        assertEquals(MailThread.WorkflowState.ACTIVE, response.workflowState());
        assertEquals(MailThread.ScreenerStatus.APPROVED, response.screenerStatus());
        assertEquals("ercan@university.edu", response.senderEmail());
        assertEquals("university.edu", response.senderDomain());
        assertTrue(response.senderTrusted());
        assertFalse(response.domainTrusted());
        assertEquals(PhishingRiskLevel.LOW, response.phishingRiskLevel());
        verify(mailThreadRepository, never()).save(any(MailThread.class));
    }

    @Test
    void readDefaultsMissingWorkflowStateWithoutMutatingStoredThread() {
        FetchedThread thread = thread(
                "thread-read-only",
                "Read-only context",
                message(
                        "Sender <sender@example.com>",
                        "A message that should not change local state.",
                        Message.Direction.INBOUND
                )
        );
        MailThread localThread = localThread("thread-read-only");
        localThread.setWorkflowState(null);
        when(gmailProvider.fetchThread(user, "thread-read-only")).thenReturn(thread);
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(
                user,
                "thread-read-only"
        )).thenReturn(Optional.of(localThread));

        ThreadContextResponse response =
                threadContextService.getContext("user@example.com", "thread-read-only");

        assertEquals(MailThread.WorkflowState.ACTIVE, response.workflowState());
        assertNull(localThread.getWorkflowState());
        verify(mailThreadRepository, never()).save(any(MailThread.class));
    }

    @Test
    void updatesCategoryAsManualOverride() {
        FetchedThread thread = thread(
                "thread-2",
                "Weekly newsletter",
                message(
                        "Newsletter <no-reply@news.example.com>",
                        "This week in your digest. Unsubscribe here.",
                        Message.Direction.INBOUND
                )
        );
        MailThread localThread = localThread("thread-2");
        when(gmailProvider.fetchThread(user, "thread-2")).thenReturn(thread);
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(user, "thread-2"))
                .thenReturn(Optional.of(localThread));

        ThreadContextResponse response = threadContextService.updateCategory(
                "user@example.com",
                "thread-2",
                new ThreadCategoryRequest(MailThread.Category.PEOPLE)
        );

        assertEquals(MailThread.Category.PEOPLE, response.category());
        assertEquals(MailThread.Category.NOISE, response.suggestedCategory());
        assertTrue(response.categoryOverride());
        assertEquals(MailThread.Category.PEOPLE, localThread.getCategory());
        assertTrue(localThread.isCategoryOverride());
    }

    @Test
    void updatesWorkflowState() {
        FetchedThread thread = thread(
                "thread-3",
                "Proposal",
                message(
                        "user@example.com",
                        "I sent the proposal for review.",
                        Message.Direction.OUTBOUND
                )
        );
        MailThread localThread = localThread("thread-3");
        when(gmailProvider.fetchThread(user, "thread-3")).thenReturn(thread);
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(user, "thread-3"))
                .thenReturn(Optional.of(localThread));

        ThreadContextResponse response = threadContextService.updateWorkflowState(
                "user@example.com",
                "thread-3",
                new ThreadWorkflowStateRequest(MailThread.WorkflowState.DONE)
        );

        assertEquals(MailThread.WorkflowState.DONE, response.workflowState());
        assertEquals(MailThread.WorkflowState.DONE, localThread.getWorkflowState());
    }

    @Test
    void trustsLatestSenderFromThread() {
        FetchedThread thread = thread(
                "thread-4",
                "Office hours",
                message(
                        "Professor Ercan <ercan@university.edu>",
                        "Office hours are available tomorrow.",
                        Message.Direction.INBOUND
                )
        );
        when(gmailProvider.fetchThread(user, "thread-4")).thenReturn(thread);
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(user, "thread-4"))
                .thenReturn(Optional.empty());
        when(senderTrustService.trustSender(eq("user@example.com"), any(TrustRequest.class)))
                .thenReturn(new TrustEntryResponse(
                        1L,
                        TrustScope.SENDER,
                        "ercan@university.edu",
                        LocalDateTime.of(2026, 6, 26, 10, 0)
                ));
        when(senderTrustService.trustContext(
                "user@example.com",
                "Professor Ercan <ercan@university.edu>"
        )).thenReturn(new PhishingTrustContext(true, false));

        ThreadContextResponse response =
                threadContextService.trustSender("user@example.com", "thread-4");

        assertTrue(response.senderTrusted());
        verify(senderTrustService).trustSender(
                eq("user@example.com"),
                argThat(request -> request.value()
                        .equals("Professor Ercan <ercan@university.edu>"))
        );
    }

    @Test
    void trustsLatestSenderDomainFromThread() {
        FetchedThread thread = thread(
                "thread-5",
                "Office hours",
                message(
                        "Professor Ercan <ercan@university.edu>",
                        "Office hours are available tomorrow.",
                        Message.Direction.INBOUND
                )
        );
        when(gmailProvider.fetchThread(user, "thread-5")).thenReturn(thread);
        when(mailThreadRepository.findFirstByUserAndExternalThreadIdOrderByIdAsc(user, "thread-5"))
                .thenReturn(Optional.empty());
        when(senderTrustService.trustDomain(eq("user@example.com"), any(TrustRequest.class)))
                .thenReturn(new TrustEntryResponse(
                        2L,
                        TrustScope.DOMAIN,
                        "university.edu",
                        LocalDateTime.of(2026, 6, 26, 10, 0)
                ));
        when(senderTrustService.trustContext(
                "user@example.com",
                "Professor Ercan <ercan@university.edu>"
        )).thenReturn(new PhishingTrustContext(false, true));

        ThreadContextResponse response =
                threadContextService.trustDomain("user@example.com", "thread-5");

        assertTrue(response.domainTrusted());
        verify(senderTrustService).trustDomain(
                eq("user@example.com"),
                argThat(request -> request.value().equals("university.edu"))
        );
    }

    @Test
    void rejectsMissingThreadId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> threadContextService.getContext("user@example.com", " ")
        );

        verifyNoInteractions(gmailProvider);
    }

    private MailThread localThread(String threadId) {
        MailThread thread = new MailThread();
        thread.setUser(user);
        thread.setExternalThreadId(threadId);
        thread.setWorkflowState(MailThread.WorkflowState.ACTIVE);
        return thread;
    }

    private FetchedThread thread(String id, String subject, FetchedMessage message) {
        return new FetchedThread(
                id,
                subject,
                List.of(message.sender(), "user@example.com"),
                message.sentAt(),
                true,
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

    private PhishingAnalysisResponse phishing(
            PhishingRiskLevel riskLevel,
            int score) {
        return new PhishingAnalysisResponse(
                "sender@example.com",
                "example.com",
                riskLevel,
                score,
                List.of(),
                List.of(),
                new PhishingTrustResponse(false, false, 0)
        );
    }
}

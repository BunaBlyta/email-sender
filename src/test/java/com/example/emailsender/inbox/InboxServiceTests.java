package com.example.emailsender.inbox;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.mail.provider.FetchedAttachment;
import com.example.emailsender.mail.provider.FetchedMessage;
import com.example.emailsender.mail.provider.FetchedThread;
import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InboxServiceTests {

    private UserRepository userRepository;
    private GmailProvider gmailProvider;
    private InboxService inboxService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        gmailProvider = mock(GmailProvider.class);
        inboxService = new InboxService(userRepository, gmailProvider);
    }

    @Test
    void returnsSafeThreadResponsesForAuthenticatedUser() {
        User user = new User();
        user.setEmail("user@example.com");

        LocalDateTime lastMessageAt = LocalDateTime.of(2026, 4, 18, 20, 30);
        MailThread thread = new MailThread();
        thread.setExternalThreadId("gmail-thread-id");
        thread.setSubject("Project update");
        thread.setParticipants(List.of("sender@example.com", "user@example.com"));
        thread.setLastMessageAt(lastMessageAt);
        thread.setHasUnread(true);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(gmailProvider.fetchThreads(user, 20)).thenReturn(List.of(thread));

        List<InboxThreadResponse> responses =
                inboxService.getThreadsForUser("user@example.com", 20);

        assertEquals(1, responses.size());
        assertEquals("gmail-thread-id", responses.getFirst().externalThreadId());
        assertEquals("Project update", responses.getFirst().subject());
        assertEquals(lastMessageAt, responses.getFirst().lastMessageAt());
        assertEquals(true, responses.getFirst().hasUnread());
        verify(gmailProvider).fetchThreads(user, 20);
    }

    @Test
    void returnsThreadDetailWithMessagesAndAttachments() {
        User user = new User();
        user.setEmail("user@example.com");
        LocalDateTime sentAt = LocalDateTime.of(2026, 6, 25, 12, 30);
        FetchedThread thread = new FetchedThread(
                "thread-123",
                "Project update",
                List.of("sender@example.com", "user@example.com"),
                sentAt,
                false,
                List.of(new FetchedMessage(
                        "message-123",
                        "Sender <sender@example.com>",
                        List.of("user@example.com"),
                        "Ready for review.",
                        "Ready for review.",
                        sentAt,
                        Message.Direction.INBOUND,
                        true,
                        List.of(new FetchedAttachment(
                                "report.pdf",
                                "application/pdf",
                                1200L
                        ))
                ))
        );

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(gmailProvider.fetchThread(user, "thread-123")).thenReturn(thread);

        InboxThreadDetailResponse response =
                inboxService.getThreadForUser("user@example.com", "thread-123");

        assertEquals("thread-123", response.externalThreadId());
        assertEquals("Project update", response.subject());
        assertEquals(1, response.messages().size());
        assertEquals("message-123", response.messages().getFirst().externalMessageId());
        assertEquals(Message.Direction.INBOUND, response.messages().getFirst().direction());
        assertEquals("report.pdf",
                response.messages().getFirst().attachments().getFirst().filename());
        verify(gmailProvider).fetchThread(user, "thread-123");
    }

    @Test
    void rejectsMaxResultsOutsideSupportedRange() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inboxService.getThreadsForUser("user@example.com", 101)
        );

        assertEquals("maxResults must be between 1 and 100", exception.getMessage());
        verifyNoInteractions(userRepository, gmailProvider);
    }

    @Test
    void rejectsMissingPrincipalEmail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> inboxService.getThreadsForUser(null, 20)
        );

        verifyNoInteractions(userRepository, gmailProvider);
    }

    @Test
    void rejectsMissingThreadId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> inboxService.getThreadForUser("user@example.com", " ")
        );

        verifyNoInteractions(userRepository, gmailProvider);
    }
}

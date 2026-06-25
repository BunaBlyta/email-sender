package com.example.emailsender.inbox.cleanup;

import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ThreadCleanupServiceTests {

    private UserRepository userRepository;
    private GmailProvider gmailProvider;
    private ThreadCleanupService threadCleanupService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        gmailProvider = mock(GmailProvider.class);
        threadCleanupService = new ThreadCleanupService(userRepository, gmailProvider);

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void marksThreadRead() {
        ThreadCleanupResponse response =
                threadCleanupService.markRead("user@example.com", " thread-123 ");

        assertEquals("thread-123", response.threadId());
        assertEquals(ThreadCleanupAction.MARK_READ, response.action());
        assertEquals("APPLIED", response.status());
        verify(gmailProvider).markThreadRead(user, "thread-123");
    }

    @Test
    void marksThreadUnread() {
        ThreadCleanupResponse response =
                threadCleanupService.markUnread("user@example.com", "thread-456");

        assertEquals("thread-456", response.threadId());
        assertEquals(ThreadCleanupAction.MARK_UNREAD, response.action());
        assertEquals("APPLIED", response.status());
        verify(gmailProvider).markThreadUnread(user, "thread-456");
    }

    @Test
    void archivesThread() {
        ThreadCleanupResponse response =
                threadCleanupService.archive("user@example.com", "thread-789");

        assertEquals("thread-789", response.threadId());
        assertEquals(ThreadCleanupAction.ARCHIVE, response.action());
        assertEquals("APPLIED", response.status());
        verify(gmailProvider).archiveThread(user, "thread-789");
    }

    @Test
    void rejectsMissingThreadId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> threadCleanupService.archive("user@example.com", " ")
        );

        verifyNoInteractions(gmailProvider);
    }

    @Test
    void rejectsMissingUserEmail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> threadCleanupService.markRead(null, "thread-123")
        );

        verifyNoInteractions(gmailProvider);
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> threadCleanupService.markUnread("missing@example.com", "thread-123")
        );

        verifyNoInteractions(gmailProvider);
    }
}

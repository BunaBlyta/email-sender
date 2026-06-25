package com.example.emailsender.search;

import com.example.emailsender.mail.model.MailThread;
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

class SearchServiceTests {

    private UserRepository userRepository;
    private GmailProvider gmailProvider;
    private SearchService searchService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        gmailProvider = mock(GmailProvider.class);
        searchService = new SearchService(userRepository, gmailProvider);

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void searchesGmailThreadsForAuthenticatedUser() {
        LocalDateTime lastMessageAt = LocalDateTime.of(2026, 6, 25, 13, 30);
        MailThread thread = new MailThread();
        thread.setExternalThreadId("thread-123");
        thread.setSubject("Thesis meeting");
        thread.setParticipants(List.of("Professor Ercan <ercan@university.edu>"));
        thread.setLastMessageAt(lastMessageAt);
        thread.setHasUnread(true);

        when(gmailProvider.searchThreads(user, "ercan thesis", 20))
                .thenReturn(List.of(thread));

        SearchResponse response =
                searchService.search("user@example.com", "  ercan thesis  ", 20);

        assertEquals("ercan thesis", response.query());
        assertEquals(1, response.resultCount());
        assertEquals("thread-123", response.threads().getFirst().externalThreadId());
        assertEquals("Thesis meeting", response.threads().getFirst().subject());
        assertEquals(lastMessageAt, response.threads().getFirst().lastMessageAt());
        verify(gmailProvider).searchThreads(user, "ercan thesis", 20);
    }

    @Test
    void rejectsBlankQuery() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> searchService.search("user@example.com", " ", 20)
        );

        assertEquals("Search query is required", exception.getMessage());
        verifyNoInteractions(gmailProvider);
    }

    @Test
    void rejectsUnsupportedLimit() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> searchService.search("user@example.com", "ercan", 51)
        );

        assertEquals("maxResults must be between 1 and 50", exception.getMessage());
        verifyNoInteractions(gmailProvider);
    }
}

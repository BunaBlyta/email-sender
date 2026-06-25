package com.example.emailsender.search;

import com.example.emailsender.inbox.InboxThreadResponse;
import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private static final int MIN_RESULTS = 1;
    private static final int MAX_RESULTS = 50;
    private static final int MAX_QUERY_LENGTH = 200;

    private final UserRepository userRepository;
    private final GmailProvider gmailProvider;

    public SearchService(UserRepository userRepository, GmailProvider gmailProvider) {
        this.userRepository = userRepository;
        this.gmailProvider = gmailProvider;
    }

    public SearchResponse search(String email, String query, int maxResults) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        String normalizedQuery = normalizeQuery(query);
        if (maxResults < MIN_RESULTS || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException("maxResults must be between 1 and 50");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<InboxThreadResponse> threads = gmailProvider
                .searchThreads(user, normalizedQuery, maxResults)
                .stream()
                .map(this::toResponse)
                .toList();
        return new SearchResponse(normalizedQuery, threads.size(), threads);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query is required");
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "Search query must not exceed 200 characters");
        }
        return normalized;
    }

    private InboxThreadResponse toResponse(MailThread thread) {
        return new InboxThreadResponse(
                thread.getExternalThreadId(),
                thread.getSubject(),
                thread.getParticipants(),
                thread.getLastMessageAt(),
                thread.isHasUnread()
        );
    }
}

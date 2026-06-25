package com.example.emailsender.inbox.cleanup;

import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ThreadCleanupService {

    private static final String APPLIED = "APPLIED";

    private final UserRepository userRepository;
    private final GmailProvider gmailProvider;

    public ThreadCleanupService(
            UserRepository userRepository,
            GmailProvider gmailProvider) {
        this.userRepository = userRepository;
        this.gmailProvider = gmailProvider;
    }

    public ThreadCleanupResponse markRead(String email, String threadId) {
        User user = findUser(email);
        String normalizedThreadId = normalizeThreadId(threadId);
        gmailProvider.markThreadRead(user, normalizedThreadId);
        return response(normalizedThreadId, ThreadCleanupAction.MARK_READ);
    }

    public ThreadCleanupResponse markUnread(String email, String threadId) {
        User user = findUser(email);
        String normalizedThreadId = normalizeThreadId(threadId);
        gmailProvider.markThreadUnread(user, normalizedThreadId);
        return response(normalizedThreadId, ThreadCleanupAction.MARK_UNREAD);
    }

    public ThreadCleanupResponse archive(String email, String threadId) {
        User user = findUser(email);
        String normalizedThreadId = normalizeThreadId(threadId);
        gmailProvider.archiveThread(user, normalizedThreadId);
        return response(normalizedThreadId, ThreadCleanupAction.ARCHIVE);
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String normalizeThreadId(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("Thread id is required");
        }
        return threadId.trim();
    }

    private ThreadCleanupResponse response(
            String threadId,
            ThreadCleanupAction action) {
        return new ThreadCleanupResponse(threadId, action, APPLIED);
    }
}

package com.example.emailsender.inbox;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InboxService {

    private static final int MIN_RESULTS = 1;
    private static final int MAX_RESULTS = 100;

    private final UserRepository userRepository;
    private final GmailProvider gmailProvider;

    public InboxService(UserRepository userRepository, GmailProvider gmailProvider) {
        this.userRepository = userRepository;
        this.gmailProvider = gmailProvider;
    }

    public List<InboxThreadResponse> getThreadsForUser(String email, int maxResults) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        if (maxResults < MIN_RESULTS || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException("maxResults must be between 1 and 100");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        List<MailThread> threads = gmailProvider.fetchThreads(user, maxResults);
        return threads.stream()
                .map(thread -> new InboxThreadResponse(
                        thread.getExternalThreadId(),
                        thread.getSubject(),
                        thread.getParticipants(),
                        thread.getLastMessageAt(),
                        thread.isHasUnread()
                ))
                .toList();
    }
}

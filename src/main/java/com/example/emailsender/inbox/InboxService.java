package com.example.emailsender.inbox;

import com.example.emailsender.inbox.cleanup.UnsubscribeOption;
import com.example.emailsender.inbox.cleanup.UnsubscribeService;
import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.provider.FetchedAttachment;
import com.example.emailsender.mail.provider.FetchedMessage;
import com.example.emailsender.mail.provider.FetchedThread;
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
    private final UnsubscribeService unsubscribeService;

    public InboxService(
            UserRepository userRepository,
            GmailProvider gmailProvider,
            UnsubscribeService unsubscribeService) {
        this.userRepository = userRepository;
        this.gmailProvider = gmailProvider;
        this.unsubscribeService = unsubscribeService;
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

    public InboxThreadDetailResponse getThreadForUser(String email, String threadId) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("Thread id is required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        return toDetailResponse(gmailProvider.fetchThread(user, threadId));
    }

    private InboxThreadDetailResponse toDetailResponse(FetchedThread thread) {
        return new InboxThreadDetailResponse(
                thread.externalThreadId(),
                thread.subject(),
                thread.participants(),
                thread.lastMessageAt(),
                thread.hasUnread(),
                thread.messages().stream()
                        .map(this::toMessageResponse)
                        .toList()
        );
    }

    private InboxMessageResponse toMessageResponse(FetchedMessage message) {
        return new InboxMessageResponse(
                message.externalMessageId(),
                message.sender(),
                message.recipients(),
                message.body(),
                message.snippet(),
                message.sentAt(),
                message.direction(),
                message.read(),
                unsubscribeService.findOption(message.listUnsubscribeHeader())
                        .map(this::toUnsubscribeResponse)
                        .orElse(null),
                message.attachments().stream()
                        .map(this::toAttachmentResponse)
                        .toList()
        );
    }

    private InboxUnsubscribeResponse toUnsubscribeResponse(UnsubscribeOption option) {
        return new InboxUnsubscribeResponse(
                option.method(),
                option.url(),
                option.destination()
        );
    }

    private InboxAttachmentResponse toAttachmentResponse(FetchedAttachment attachment) {
        return new InboxAttachmentResponse(
                attachment.filename(),
                attachment.mimeType(),
                attachment.sizeBytes()
        );
    }
}

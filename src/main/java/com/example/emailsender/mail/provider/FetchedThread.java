package com.example.emailsender.mail.provider;

import java.time.LocalDateTime;
import java.util.List;

public record FetchedThread(
        String externalThreadId,
        String subject,
        List<String> participants,
        LocalDateTime lastMessageAt,
        boolean hasUnread,
        List<FetchedMessage> messages
) {
}

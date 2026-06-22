package com.example.emailsender.inbox;

import java.time.LocalDateTime;
import java.util.List;

public record InboxThreadResponse(
        String externalThreadId,
        String subject,
        List<String> participants,
        LocalDateTime lastMessageAt,
        boolean hasUnread
) {
}

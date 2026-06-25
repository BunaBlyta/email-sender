package com.example.emailsender.inbox;

import java.time.LocalDateTime;
import java.util.List;

public record InboxThreadDetailResponse(
        String externalThreadId,
        String subject,
        List<String> participants,
        LocalDateTime lastMessageAt,
        boolean hasUnread,
        List<InboxMessageResponse> messages
) {
}

package com.example.emailsender.scheduling;

import java.time.Instant;
import java.util.List;

public record ScheduledMessageResponse(
        Long id,
        List<String> recipients,
        String subject,
        String body,
        Instant scheduledFor,
        ScheduledMessage.Status status,
        Instant createdAt,
        Instant sentAt,
        String externalMessageId,
        String externalThreadId,
        String failureReason
) {
}

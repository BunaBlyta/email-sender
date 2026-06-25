package com.example.emailsender.compose;

import java.time.Instant;
import java.util.List;

public record DraftResponse(
        Long id,
        List<String> recipients,
        String subject,
        String body,
        Instant scheduledFor,
        Instant createdAt,
        Instant updatedAt
) {
}

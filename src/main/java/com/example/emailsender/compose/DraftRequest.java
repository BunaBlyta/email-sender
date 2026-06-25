package com.example.emailsender.compose;

import java.time.Instant;
import java.util.List;

public record DraftRequest(
        List<String> recipients,
        String subject,
        String body,
        Instant scheduledFor
) {
}

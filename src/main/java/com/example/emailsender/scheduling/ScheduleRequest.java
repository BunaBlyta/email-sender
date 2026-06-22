package com.example.emailsender.scheduling;

import java.time.Instant;
import java.util.List;

public record ScheduleRequest(
        List<String> recipients,
        String subject,
        String body,
        Instant scheduledFor
) {
}

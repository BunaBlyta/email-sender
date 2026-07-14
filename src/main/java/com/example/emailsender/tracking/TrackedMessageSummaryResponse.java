package com.example.emailsender.tracking;

import java.time.LocalDateTime;

public record TrackedMessageSummaryResponse(
        Long sentMessageId,
        String recipient,
        String subject,
        LocalDateTime sentAt,
        boolean scheduled,
        String status,
        int pixelLoadCount,
        LocalDateTime firstPixelLoadedAt,
        LocalDateTime lastPixelLoadedAt
) {
}

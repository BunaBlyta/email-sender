package com.example.emailsender.screener;

import java.time.LocalDateTime;

public record ScreenerEntryResponse(
        Long id,
        String senderEmail,
        String senderDomain,
        LocalDateTime firstContactAt,
        ScreenerEntry.Status status,
        LocalDateTime decidedAt
) {
}

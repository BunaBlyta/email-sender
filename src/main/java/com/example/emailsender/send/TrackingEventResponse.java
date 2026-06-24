package com.example.emailsender.send;

import java.time.LocalDateTime;

public record TrackingEventResponse(
        Long id,
        LocalDateTime loadedAt,
        String source,
        String imageFormat,
        String userAgent
) {
}

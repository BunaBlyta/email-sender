package com.example.emailsender.send;

import java.time.LocalDateTime;
import java.util.List;

public record TrackingResponse(
        boolean enabled,
        String status,
        String trackingId,
        int pixelLoadCount,
        LocalDateTime firstPixelLoadedAt,
        LocalDateTime lastPixelLoadedAt,
        List<TrackingEventResponse> recentEvents
) {
}

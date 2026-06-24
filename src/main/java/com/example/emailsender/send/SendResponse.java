package com.example.emailsender.send;

import java.time.LocalDateTime;
import java.util.List;

public record SendResponse(
        Long id,
        String externalMessageId,
        String externalThreadId,
        List<String> recipients,
        String subject,
        LocalDateTime sentAt,
        boolean scheduled,
        AttachmentResponse attachment,
        TrackingResponse tracking
) {
}

package com.example.emailsender.templates;

import java.time.LocalDateTime;

public record TemplateResponse(
        Long id,
        String name,
        String subject,
        String body,
        String category,
        int usageCount,
        LocalDateTime createdAt
) {
}

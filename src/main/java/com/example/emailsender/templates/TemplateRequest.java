package com.example.emailsender.templates;

public record TemplateRequest(
        String name,
        String subject,
        String body,
        String category
) {
}

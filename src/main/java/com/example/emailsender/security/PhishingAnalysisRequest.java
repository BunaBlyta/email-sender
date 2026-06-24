package com.example.emailsender.security;

public record PhishingAnalysisRequest(
        String sender,
        String subject,
        String body
) {
}

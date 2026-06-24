package com.example.emailsender.security;

public record PhishingSignalResponse(
        String code,
        String description,
        int scoreImpact
) {
}

package com.example.emailsender.security;

public record PhishingTrustResponse(
        boolean senderTrusted,
        boolean domainTrusted,
        int scoreAdjustment
) {
}

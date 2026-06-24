package com.example.emailsender.security;

public record PhishingTrustContext(
        boolean senderTrusted,
        boolean domainTrusted
) {

    public static PhishingTrustContext none() {
        return new PhishingTrustContext(false, false);
    }

    public boolean hasTrust() {
        return senderTrusted || domainTrusted;
    }
}

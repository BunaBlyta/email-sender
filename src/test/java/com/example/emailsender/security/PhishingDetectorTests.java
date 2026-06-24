package com.example.emailsender.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhishingDetectorTests {

    private final PhishingDetector phishingDetector =
            new PhishingDetector(new LinkInspector());

    @Test
    void givesLowRiskForNormalProfessorEmail() {
        PhishingAnalysisResponse response = phishingDetector.analyze(
                new PhishingAnalysisRequest(
                        "Professor Ercan <ercan@university.edu>",
                        "Thesis meeting notes",
                        "Please review the notes at https://university.edu/thesis before Friday."
                )
        );

        assertEquals("university.edu", response.senderDomain());
        assertEquals(PhishingRiskLevel.LOW, response.riskLevel());
        assertEquals(0, response.score());
        assertTrue(response.signals().isEmpty());
        assertEquals(1, response.links().size());
    }

    @Test
    void givesHighRiskForUrgentCredentialEmailWithUnsafeLink() {
        PhishingAnalysisResponse response = phishingDetector.analyze(
                new PhishingAnalysisRequest(
                        "PayPal Support <support@paypal-alerts.example>",
                        "Urgent: verify your account",
                        "Your account is suspended. Login at http://192.168.1.10/login "
                                + "to update payment."
                )
        );

        assertEquals(PhishingRiskLevel.HIGH, response.riskLevel());
        assertEquals(100, response.score());
        assertSignalCodes(
                response,
                "URGENCY_LANGUAGE",
                "CREDENTIAL_REQUEST",
                "PAYMENT_LANGUAGE",
                "NON_HTTPS_LINK",
                "IP_ADDRESS_LINK",
                "SENDER_LINK_DOMAIN_MISMATCH",
                "BRAND_IMPERSONATION_PATTERN"
        );
    }

    @Test
    void givesMediumRiskForSenderAndLinkDomainMismatch() {
        PhishingAnalysisResponse response = phishingDetector.analyze(
                new PhishingAnalysisRequest(
                        "Support <support@example.com>",
                        "Account update",
                        "Please sign in at https://accounts.example-login.com/update"
                )
        );

        assertEquals(PhishingRiskLevel.MEDIUM, response.riskLevel());
        assertEquals(40, response.score());
        assertSignalCodes(
                response,
                "CREDENTIAL_REQUEST",
                "SENDER_LINK_DOMAIN_MISMATCH"
        );
    }

    @Test
    void trustedSenderReducesNonCriticalRisk() {
        PhishingAnalysisResponse response = phishingDetector.analyze(
                new PhishingAnalysisRequest(
                        "Support <support@example.com>",
                        "Account update",
                        "Please sign in at https://accounts.example-login.com/update"
                ),
                new PhishingTrustContext(true, false)
        );

        assertEquals(PhishingRiskLevel.LOW, response.riskLevel());
        assertEquals(25, response.score());
        assertTrue(response.trust().senderTrusted());
        assertEquals(-15, response.trust().scoreAdjustment());
    }

    @Test
    void trustedSenderDoesNotReduceCriticalCredentialLinkRisk() {
        PhishingAnalysisResponse response = phishingDetector.analyze(
                new PhishingAnalysisRequest(
                        "Support <support@example.com>",
                        "Account update",
                        "Login at http://example.com/update"
                ),
                new PhishingTrustContext(true, false)
        );

        assertEquals(PhishingRiskLevel.MEDIUM, response.riskLevel());
        assertEquals(35, response.score());
        assertTrue(response.trust().senderTrusted());
        assertEquals(0, response.trust().scoreAdjustment());
        assertSignalCodes(
                response,
                "CREDENTIAL_REQUEST",
                "NON_HTTPS_LINK"
        );
    }

    private void assertSignalCodes(
            PhishingAnalysisResponse response,
            String... expectedCodes) {
        List<String> codes = response.signals().stream()
                .map(PhishingSignalResponse::code)
                .toList();
        assertEquals(List.of(expectedCodes), codes);
    }
}

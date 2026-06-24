package com.example.emailsender.security;

import java.util.List;

public record PhishingAnalysisResponse(
        String sender,
        String senderDomain,
        PhishingRiskLevel riskLevel,
        int score,
        List<PhishingSignalResponse> signals,
        List<LinkAnalysisResponse> links
) {
}

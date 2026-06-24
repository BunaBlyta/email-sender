package com.example.emailsender.screener;

import com.example.emailsender.security.PhishingAnalysisResponse;

public record ScreenerEvaluationResponse(
        ScreenerEntryResponse entry,
        ScreenerEntry.Status status,
        boolean firstTimeSender,
        boolean requiresDecision,
        boolean trustedSender,
        boolean trustedDomain,
        PhishingAnalysisResponse phishing
) {
}

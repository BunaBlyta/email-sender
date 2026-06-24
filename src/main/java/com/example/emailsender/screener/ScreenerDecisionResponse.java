package com.example.emailsender.screener;

import com.example.emailsender.security.TrustEntryResponse;

public record ScreenerDecisionResponse(
        ScreenerEntryResponse entry,
        TrustEntryResponse trustEntry
) {
}

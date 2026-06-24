package com.example.emailsender.security;

import java.util.List;

public record LinkAnalysisResponse(
        String url,
        String scheme,
        String host,
        boolean secure,
        boolean ipAddressHost,
        boolean punycodeHost,
        boolean shortener,
        List<String> signals
) {
}

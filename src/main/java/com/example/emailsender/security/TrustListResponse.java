package com.example.emailsender.security;

import java.util.List;

public record TrustListResponse(
        List<TrustEntryResponse> senders,
        List<TrustEntryResponse> domains
) {
}

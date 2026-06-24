package com.example.emailsender.security;

import java.time.LocalDateTime;

public record TrustEntryResponse(
        Long id,
        TrustScope scope,
        String value,
        LocalDateTime createdAt
) {
}

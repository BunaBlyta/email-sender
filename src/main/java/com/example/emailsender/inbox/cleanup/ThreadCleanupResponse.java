package com.example.emailsender.inbox.cleanup;

public record ThreadCleanupResponse(
        String threadId,
        ThreadCleanupAction action,
        String status
) {
}

package com.example.emailsender.inbox.cleanup;

public record UnsubscribeOption(
        UnsubscribeMethod method,
        String url,
        String destination
) {
}

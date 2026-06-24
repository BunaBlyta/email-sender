package com.example.emailsender.screener;

public record ScreenerEvaluateRequest(
        String sender,
        String subject,
        String body
) {
}

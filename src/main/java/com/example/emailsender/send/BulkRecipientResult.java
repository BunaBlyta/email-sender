package com.example.emailsender.send;

public record BulkRecipientResult(
        String recipient,
        Status status,
        String externalMessageId,
        String externalThreadId,
        String error
) {
    public enum Status { SENT, FAILED }
}

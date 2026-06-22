package com.example.emailsender.send;

import java.util.List;

public record BulkSendRequest(
        List<Long> recipientGroupIds,
        String subject,
        String body,
        boolean confirmed
) {
}

package com.example.emailsender.send;

import java.util.List;

public record BulkSendResponse(
        int totalRecipients,
        int sentCount,
        int failedCount,
        List<BulkRecipientResult> results
) {
}

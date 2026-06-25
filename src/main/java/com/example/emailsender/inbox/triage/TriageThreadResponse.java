package com.example.emailsender.inbox.triage;

import java.time.LocalDateTime;
import java.util.List;

public record TriageThreadResponse(
        String externalThreadId,
        String subject,
        List<String> participants,
        LocalDateTime lastMessageAt,
        boolean hasUnread,
        TriageLabel label,
        int attentionScore,
        String suggestedAction,
        List<String> reasons
) {
}

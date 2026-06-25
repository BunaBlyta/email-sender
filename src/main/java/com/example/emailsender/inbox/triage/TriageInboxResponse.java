package com.example.emailsender.inbox.triage;

import java.util.List;

public record TriageInboxResponse(
        int totalThreads,
        int securityReviewCount,
        int needsReplyCount,
        int importantCount,
        int waitingCount,
        int lowPriorityCount,
        int fyiCount,
        List<TriageThreadResponse> threads
) {
}

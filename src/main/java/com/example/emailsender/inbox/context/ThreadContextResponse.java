package com.example.emailsender.inbox.context;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.security.PhishingRiskLevel;
import com.example.emailsender.security.PhishingSignalResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ThreadContextResponse(
        String threadId,
        String subject,
        List<String> participants,
        LocalDateTime lastMessageAt,
        boolean hasUnread,
        MailThread.Category category,
        boolean categoryOverride,
        MailThread.Category suggestedCategory,
        MailThread.WorkflowState workflowState,
        MailThread.ScreenerStatus screenerStatus,
        String sender,
        String senderEmail,
        String senderDomain,
        boolean senderTrusted,
        boolean domainTrusted,
        PhishingRiskLevel phishingRiskLevel,
        int phishingScore,
        List<PhishingSignalResponse> phishingSignals,
        List<String> reasons
) {
}

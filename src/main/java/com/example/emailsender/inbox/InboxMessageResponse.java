package com.example.emailsender.inbox;

import com.example.emailsender.mail.model.Message;

import java.time.LocalDateTime;
import java.util.List;

public record InboxMessageResponse(
        String externalMessageId,
        String sender,
        List<String> recipients,
        String body,
        String snippet,
        LocalDateTime sentAt,
        Message.Direction direction,
        boolean read,
        InboxUnsubscribeResponse unsubscribe,
        List<InboxAttachmentResponse> attachments
) {
}

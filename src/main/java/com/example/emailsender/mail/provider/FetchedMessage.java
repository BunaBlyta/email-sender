package com.example.emailsender.mail.provider;

import com.example.emailsender.mail.model.Message;

import java.time.LocalDateTime;
import java.util.List;

public record FetchedMessage(
        String externalMessageId,
        String sender,
        List<String> recipients,
        String body,
        String snippet,
        LocalDateTime sentAt,
        Message.Direction direction,
        boolean read,
        String listUnsubscribeHeader,
        List<FetchedAttachment> attachments
) {
}

package com.example.emailsender.mail.provider;

public record MailSendResult(
        String externalMessageId,
        String externalThreadId
) {
}

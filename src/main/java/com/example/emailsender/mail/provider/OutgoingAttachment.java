package com.example.emailsender.mail.provider;

public record OutgoingAttachment(
        String filename,
        String contentType,
        byte[] content
) {
}

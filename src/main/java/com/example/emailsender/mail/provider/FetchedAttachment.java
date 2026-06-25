package com.example.emailsender.mail.provider;

public record FetchedAttachment(
        String filename,
        String mimeType,
        Long sizeBytes
) {
}

package com.example.emailsender.send;

public record AttachmentResponse(
        String filename,
        String contentType,
        long sizeBytes
) {
}

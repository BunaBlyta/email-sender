package com.example.emailsender.inbox;

public record InboxAttachmentResponse(
        String filename,
        String mimeType,
        Long sizeBytes
) {
}

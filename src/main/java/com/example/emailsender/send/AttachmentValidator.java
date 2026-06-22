package com.example.emailsender.send;

import com.example.emailsender.mail.provider.OutgoingAttachment;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Component
public class AttachmentValidator {

    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/gif",
            "text/plain",
            "text/csv",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "com", "bat", "cmd", "msi", "scr", "js", "jar", "sh", "ps1"
    );

    public OutgoingAttachment validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Attachment file is required");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("Attachment must not exceed 10 MB");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Attachment type is not allowed: " + contentType);
        }
        if (BLOCKED_EXTENSIONS.contains(extension(filename))) {
            throw new IllegalArgumentException(
                    "Attachment file extension is not allowed");
        }

        try {
            return new OutgoingAttachment(filename, contentType, file.getBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Attachment could not be read", exception);
        }
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Attachment filename is required");
        }

        String filename = value.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
        filename = filename.replaceAll("[\\p{Cntrl}]", "");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("Attachment filename is required");
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Attachment filename must not exceed 255 characters");
        }
        return filename;
    }

    private String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return "application/octet-stream";
        }
        int separator = value.indexOf(';');
        String normalized = separator >= 0 ? value.substring(0, separator) : value;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

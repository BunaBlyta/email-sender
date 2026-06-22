package com.example.emailsender.send;

import com.example.emailsender.mail.provider.OutgoingAttachment;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentValidatorTests {

    private final AttachmentValidator validator = new AttachmentValidator();

    @Test
    void acceptsAllowedFileAndRemovesPathFromFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../report.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        OutgoingAttachment attachment = validator.validate(file);

        assertEquals("report.pdf", attachment.filename());
        assertEquals("application/pdf", attachment.contentType());
        assertEquals(3, attachment.content().length);
    }

    @Test
    void rejectsBlockedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.js",
                "application/javascript",
                "alert(1)".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
    }

    @Test
    void rejectsOversizedAttachment() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                new byte[10 * 1024 * 1024 + 1]
        );

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> validator.validate(file));

        assertEquals("Attachment must not exceed 10 MB", exception.getMessage());
    }
}

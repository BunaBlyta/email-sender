package com.example.emailsender.mail.provider;

import com.example.emailsender.auth.TokenStore;
import com.example.emailsender.mail.provider.FetchedAttachment;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GmailProviderTests {

    private final GmailProvider gmailProvider = new GmailProvider((TokenStore) null);

    @Test
    void decodesRfc2047Subject() {
        String subject = gmailProvider.decodeHeader(
                "=?UTF-8?B?8J+luSBQcm9qZWN0IHVwZGF0ZQ==?="
        );

        assertEquals("🥹 Project update", subject);
    }

    @Test
    void repairsUtf8TextDecodedAsLatin1() {
        String subject = gmailProvider.decodeHeader(
                "ðŸ«£ Ends today: 50% off Coursera Plus"
        );

        assertEquals("🫣 Ends today: 50% off Coursera Plus", subject);
    }

    @Test
    void leavesNormalSubjectUnchanged() {
        assertEquals("Weekly project update",
                gmailProvider.decodeHeader("Weekly project update"));
    }

    @Test
    void extractsPlainTextBodyFromNestedMessagePart() {
        MessagePart root = new MessagePart()
                .setMimeType("multipart/alternative")
                .setParts(List.of(new MessagePart()
                        .setMimeType("text/plain")
                        .setBody(new MessagePartBody()
                                .setData(base64Url("Ready for review.")))));

        assertEquals("Ready for review.", gmailProvider.extractBody(root));
    }

    @Test
    void fallsBackToReadableHtmlBody() {
        MessagePart root = new MessagePart()
                .setMimeType("text/html")
                .setBody(new MessagePartBody()
                        .setData(base64Url("<p>Hello&nbsp;team</p><br><strong>Done</strong>")));

        assertEquals("Hello team\nDone", gmailProvider.extractBody(root));
    }

    @Test
    void extractsAttachmentMetadataFromNestedParts() {
        MessagePart root = new MessagePart()
                .setMimeType("multipart/mixed")
                .setParts(List.of(new MessagePart()
                        .setMimeType("application/pdf")
                        .setFilename("report.pdf")
                        .setBody(new MessagePartBody().setSize(1200))));

        List<FetchedAttachment> attachments = gmailProvider.extractAttachments(root);

        assertEquals(1, attachments.size());
        assertEquals("report.pdf", attachments.getFirst().filename());
        assertEquals("application/pdf", attachments.getFirst().mimeType());
        assertEquals(1200L, attachments.getFirst().sizeBytes());
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}

package com.example.emailsender.mail.provider;

import com.example.emailsender.auth.TokenStore;
import org.junit.jupiter.api.Test;

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
}

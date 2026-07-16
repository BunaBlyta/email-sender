package com.example.emailsender.inbox.cleanup;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsubscribeServiceTests {

    private final UnsubscribeService unsubscribeService = new UnsubscribeService();

    @Test
    void prefersHttpsWhenHeaderAlsoContainsMailto() {
        Optional<UnsubscribeOption> option = unsubscribeService.findOption(
                "<mailto:leave@example.com>, <https://lists.example.com/unsubscribe?id=123>"
        );

        assertTrue(option.isPresent());
        assertEquals(UnsubscribeMethod.HTTPS, option.get().method());
        assertEquals("https://lists.example.com/unsubscribe?id=123", option.get().url());
        assertEquals("lists.example.com", option.get().destination());
    }

    @Test
    void acceptsBareHttpsHeader() {
        Optional<UnsubscribeOption> option = unsubscribeService.findOption(
                "https://lists.example.com/unsubscribe"
        );

        assertTrue(option.isPresent());
        assertEquals(UnsubscribeMethod.HTTPS, option.get().method());
    }

    @Test
    void acceptsMailtoButRemovesUntrustedQueryParameters() {
        Optional<UnsubscribeOption> option = unsubscribeService.findOption(
                "<mailto:leave@example.com?subject=Unsubscribe&bcc=other@example.com>"
        );

        assertTrue(option.isPresent());
        assertEquals(UnsubscribeMethod.MAILTO, option.get().method());
        assertEquals("mailto:leave@example.com", option.get().url());
        assertEquals("leave@example.com", option.get().destination());
    }

    @Test
    void rejectsInsecureAndUnsupportedSchemes() {
        assertTrue(unsubscribeService.findOption(
                "<http://example.com/unsubscribe>, <javascript:alert(1)>"
        ).isEmpty());
    }

    @Test
    void rejectsHttpsWithUserInfoOrFragment() {
        assertTrue(unsubscribeService.findOption(
                "<https://trusted.example@evil.example/unsubscribe>"
        ).isEmpty());
        assertTrue(unsubscribeService.findOption(
                "<https://example.com/unsubscribe#confirmation>"
        ).isEmpty());
    }

    @Test
    void rejectsMultipleMailtoRecipients() {
        assertTrue(unsubscribeService.findOption(
                "<mailto:first@example.com,second@example.com>"
        ).isEmpty());
        assertTrue(unsubscribeService.findOption(
                "<mailto:List%20Owner%20%3Cleave@example.com%3E>"
        ).isEmpty());
    }

    @Test
    void rejectsMalformedOrFoldedHeaderValues() {
        assertTrue(unsubscribeService.findOption("not a URI").isEmpty());
        assertTrue(unsubscribeService.findOption(
                "<https://example.com/unsubscribe>\r\nBcc: victim@example.com"
        ).isEmpty());
    }
}

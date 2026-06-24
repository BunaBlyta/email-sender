package com.example.emailsender.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkInspectorTests {

    private final LinkInspector linkInspector = new LinkInspector();

    @Test
    void extractsHttpAndHttpsLinksWithoutFollowingThem() {
        List<LinkInspector.InspectedLink> links = linkInspector.extractLinks(
                "Review https://example.com/report, then avoid http://192.168.1.10/login."
        );

        assertEquals(2, links.size());
        assertEquals("https://example.com/report", links.get(0).url());
        assertEquals("example.com", links.get(0).host());
        assertTrue(links.get(0).secure());

        assertEquals("http://192.168.1.10/login", links.get(1).url());
        assertEquals("192.168.1.10", links.get(1).host());
        assertFalse(links.get(1).secure());
        assertTrue(links.get(1).ipAddressHost());
        assertEquals(List.of("NON_HTTPS_LINK", "IP_ADDRESS_LINK"), links.get(1).signals());
    }

    @Test
    void flagsShortenersAndPunycodeHosts() {
        List<LinkInspector.InspectedLink> links = linkInspector.extractLinks(
                "Open https://bit.ly/abc and https://xn--pple-43d.example/login"
        );

        assertEquals(2, links.size());
        assertTrue(links.get(0).shortener());
        assertEquals(List.of("URL_SHORTENER"), links.get(0).signals());

        assertTrue(links.get(1).punycodeHost());
        assertEquals(List.of("PUNYCODE_LINK"), links.get(1).signals());
    }
}

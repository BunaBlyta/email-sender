package com.example.emailsender.security;

import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LinkInspector {

    private static final Pattern URL_PATTERN =
            Pattern.compile("(?i)\\bhttps?://[^\\s<>()\"'>]+");
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Set<String> URL_SHORTENERS = Set.of(
            "bit.ly",
            "tinyurl.com",
            "t.co",
            "goo.gl",
            "ow.ly",
            "is.gd",
            "buff.ly",
            "rebrand.ly",
            "cutt.ly",
            "shorturl.at"
    );

    public List<InspectedLink> extractLinks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Map<String, InspectedLink> links = new LinkedHashMap<>();
        var matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            inspect(matcher.group()).ifPresent(link -> links.putIfAbsent(link.url(), link));
        }
        return List.copyOf(links.values());
    }

    private java.util.Optional<InspectedLink> inspect(String rawUrl) {
        String url = trimTrailingPunctuation(rawUrl);
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }

        String scheme = normalize(uri.getScheme());
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return java.util.Optional.empty();
        }

        String host = normalizeHost(uri.getHost());
        if (host == null || host.isBlank()) {
            return java.util.Optional.empty();
        }

        boolean secure = "https".equals(scheme);
        boolean ipAddressHost = isIpAddress(host);
        boolean punycodeHost = host.contains("xn--");
        boolean shortener = URL_SHORTENERS.contains(stripWww(host));

        List<String> signals = new ArrayList<>();
        if (!secure) {
            signals.add("NON_HTTPS_LINK");
        }
        if (ipAddressHost) {
            signals.add("IP_ADDRESS_LINK");
        }
        if (punycodeHost) {
            signals.add("PUNYCODE_LINK");
        }
        if (shortener) {
            signals.add("URL_SHORTENER");
        }

        return java.util.Optional.of(new InspectedLink(
                url,
                scheme,
                host,
                secure,
                ipAddressHost,
                punycodeHost,
                shortener,
                List.copyOf(signals)
        ));
    }

    private String trimTrailingPunctuation(String value) {
        String trimmed = value.trim();
        while (!trimmed.isEmpty()) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last != '.' && last != ',' && last != ';' && last != ':'
                    && last != '!' && last != ')' && last != ']' && last != '}') {
                return trimmed;
            }
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        try {
            return IDN.toASCII(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return host.toLowerCase(Locale.ROOT);
        }
    }

    private String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private boolean isIpAddress(String host) {
        if (!IPV4_PATTERN.matcher(host).matches()) {
            return false;
        }

        String[] parts = host.split("\\.");
        for (String part : parts) {
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public record InspectedLink(
            String url,
            String scheme,
            String host,
            boolean secure,
            boolean ipAddressHost,
            boolean punycodeHost,
            boolean shortener,
            List<String> signals
    ) {
    }
}

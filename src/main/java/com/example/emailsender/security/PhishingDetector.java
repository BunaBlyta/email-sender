package com.example.emailsender.security;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PhishingDetector {

    private static final Pattern URGENCY_PATTERN = Pattern.compile(
            "\\b(urgent|immediately|act now|final warning|suspended|locked|expires today"
                    + "|within 24 hours|limited time)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "\\b(password|verify your account|confirm your account|login|sign in"
                    + "|account verification|reset your password)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAYMENT_PATTERN = Pattern.compile(
            "\\b(payment failed|billing|invoice|bank account|credit card|wire transfer"
                    + "|update payment)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Z0-9._%+-]+@([A-Z0-9.-]+\\.[A-Z]{2,})",
                    Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> BRAND_DOMAINS = Map.of(
            "paypal", "paypal.com",
            "google", "google.com",
            "microsoft", "microsoft.com",
            "apple", "apple.com",
            "amazon", "amazon.com"
    );

    private final LinkInspector linkInspector;

    public PhishingDetector(LinkInspector linkInspector) {
        this.linkInspector = linkInspector;
    }

    public PhishingAnalysisResponse analyze(PhishingAnalysisRequest request) {
        return analyze(request, PhishingTrustContext.none());
    }

    public PhishingAnalysisResponse analyze(
            PhishingAnalysisRequest request,
            PhishingTrustContext trustContext) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        PhishingTrustContext effectiveTrust = trustContext == null
                ? PhishingTrustContext.none()
                : trustContext;

        String sender = normalize(request.sender());
        String subject = normalize(request.subject());
        String body = normalize(request.body());
        String combinedText = (subject + "\n" + body).trim();
        String senderDomain = extractSenderDomain(sender);
        List<LinkInspector.InspectedLink> inspectedLinks = linkInspector.extractLinks(combinedText);

        Map<String, PhishingSignalResponse> signals = new LinkedHashMap<>();
        addLanguageSignals(combinedText, signals);
        addLinkSignals(inspectedLinks, signals);
        addSenderMismatchSignal(senderDomain, inspectedLinks, signals);
        addBrandImpersonationSignals(senderDomain, inspectedLinks, signals);

        int score = signals.values().stream()
                .mapToInt(PhishingSignalResponse::scoreImpact)
                .sum();
        int scoreAdjustment = trustScoreAdjustment(score, signals, effectiveTrust);
        int cappedScore = Math.min(100, Math.max(0, score + scoreAdjustment));

        return new PhishingAnalysisResponse(
                sender,
                senderDomain,
                riskLevel(cappedScore),
                cappedScore,
                List.copyOf(signals.values()),
                inspectedLinks.stream()
                        .map(this::toLinkResponse)
                        .toList(),
                new PhishingTrustResponse(
                        effectiveTrust.senderTrusted(),
                        effectiveTrust.domainTrusted(),
                        scoreAdjustment
                )
        );
    }

    private void addLanguageSignals(
            String text,
            Map<String, PhishingSignalResponse> signals) {
        if (URGENCY_PATTERN.matcher(text).find()) {
            addSignal(
                    signals,
                    "URGENCY_LANGUAGE",
                    "Message uses urgent or pressure-based language",
                    15
            );
        }
        if (CREDENTIAL_PATTERN.matcher(text).find()) {
            addSignal(
                    signals,
                    "CREDENTIAL_REQUEST",
                    "Message asks for login, password, or account verification action",
                    20
            );
        }
        if (PAYMENT_PATTERN.matcher(text).find()) {
            addSignal(
                    signals,
                    "PAYMENT_LANGUAGE",
                    "Message mentions payment, billing, banking, or card details",
                    15
            );
        }
    }

    private void addLinkSignals(
            List<LinkInspector.InspectedLink> links,
            Map<String, PhishingSignalResponse> signals) {
        for (LinkInspector.InspectedLink link : links) {
            if (!link.secure()) {
                addSignal(
                        signals,
                        "NON_HTTPS_LINK",
                        "Message contains a link that does not use HTTPS",
                        15
                );
            }
            if (link.ipAddressHost()) {
                addSignal(
                        signals,
                        "IP_ADDRESS_LINK",
                        "Message contains a link that points directly to an IP address",
                        25
                );
            }
            if (link.punycodeHost()) {
                addSignal(
                        signals,
                        "PUNYCODE_LINK",
                        "Message contains an internationalized domain encoded as punycode",
                        25
                );
            }
            if (link.shortener()) {
                addSignal(
                        signals,
                        "URL_SHORTENER",
                        "Message contains a shortened URL that hides the final destination",
                        15
                );
            }
        }
    }

    private void addSenderMismatchSignal(
            String senderDomain,
            List<LinkInspector.InspectedLink> links,
            Map<String, PhishingSignalResponse> signals) {
        if (senderDomain.isBlank() || links.isEmpty()) {
            return;
        }

        String senderBaseDomain = baseDomain(senderDomain);
        boolean mismatch = links.stream()
                .map(LinkInspector.InspectedLink::host)
                .map(this::baseDomain)
                .anyMatch(linkDomain -> !linkDomain.equals(senderBaseDomain));
        if (mismatch) {
            addSignal(
                    signals,
                    "SENDER_LINK_DOMAIN_MISMATCH",
                    "At least one link points to a domain different from the sender domain",
                    20
            );
        }
    }

    private void addBrandImpersonationSignals(
            String senderDomain,
            List<LinkInspector.InspectedLink> links,
            Map<String, PhishingSignalResponse> signals) {
        List<String> domains = new ArrayList<>();
        if (!senderDomain.isBlank()) {
            domains.add(senderDomain);
        }
        links.stream()
                .map(LinkInspector.InspectedLink::host)
                .forEach(domains::add);

        for (String domain : domains) {
            String baseDomain = baseDomain(domain);
            List<String> labels = List.of(baseDomain.split("[.-]"));
            for (Map.Entry<String, String> brand : BRAND_DOMAINS.entrySet()) {
                if (labels.contains(brand.getKey())
                        && !baseDomain.equals(brand.getValue())) {
                    addSignal(
                            signals,
                            "BRAND_IMPERSONATION_PATTERN",
                            "A sender or link domain contains a known brand name outside its main domain",
                            25
                    );
                    return;
                }
            }
        }
    }

    private LinkAnalysisResponse toLinkResponse(LinkInspector.InspectedLink link) {
        return new LinkAnalysisResponse(
                link.url(),
                link.scheme(),
                link.host(),
                link.secure(),
                link.ipAddressHost(),
                link.punycodeHost(),
                link.shortener(),
                link.signals()
        );
    }

    private void addSignal(
            Map<String, PhishingSignalResponse> signals,
            String code,
            String description,
            int scoreImpact) {
        signals.putIfAbsent(code, new PhishingSignalResponse(code, description, scoreImpact));
    }

    private PhishingRiskLevel riskLevel(int score) {
        if (score >= 70) {
            return PhishingRiskLevel.HIGH;
        }
        if (score >= 35) {
            return PhishingRiskLevel.MEDIUM;
        }
        return PhishingRiskLevel.LOW;
    }

    private int trustScoreAdjustment(
            int score,
            Map<String, PhishingSignalResponse> signals,
            PhishingTrustContext trustContext) {
        if (score <= 0 || !trustContext.hasTrust() || hasCriticalSignal(signals)) {
            return 0;
        }
        if (trustContext.senderTrusted()) {
            return -15;
        }
        return -10;
    }

    private boolean hasCriticalSignal(Map<String, PhishingSignalResponse> signals) {
        return signals.containsKey("IP_ADDRESS_LINK")
                || signals.containsKey("PUNYCODE_LINK")
                || signals.containsKey("BRAND_IMPERSONATION_PATTERN")
                || (signals.containsKey("CREDENTIAL_REQUEST")
                        && signals.containsKey("NON_HTTPS_LINK"));
    }

    private String extractSenderDomain(String sender) {
        if (sender.isBlank()) {
            return "";
        }

        try {
            InternetAddress[] addresses = InternetAddress.parse(sender, false);
            if (addresses.length > 0 && addresses[0].getAddress() != null) {
                String domain = domainFromEmail(addresses[0].getAddress());
                if (!domain.isBlank()) {
                    return domain;
                }
            }
        } catch (AddressException ignored) {
        }

        var matcher = EMAIL_PATTERN.matcher(sender);
        if (matcher.find()) {
            return normalizeDomain(matcher.group(1));
        }
        return "";
    }

    private String domainFromEmail(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return "";
        }
        return normalizeDomain(email.substring(atIndex + 1));
    }

    private String normalizeDomain(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String domain = value.trim().toLowerCase(Locale.ROOT);
        try {
            return IDN.toASCII(domain);
        } catch (IllegalArgumentException exception) {
            return domain;
        }
    }

    private String baseDomain(String host) {
        String normalized = normalizeDomain(host);
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        String[] labels = normalized.split("\\.");
        if (labels.length <= 2) {
            return normalized;
        }

        String secondLevel = labels[labels.length - 2];
        String topLevel = labels[labels.length - 1];
        if (topLevel.length() == 2 && secondLevel.length() <= 3 && labels.length >= 3) {
            return labels[labels.length - 3] + "." + secondLevel + "." + topLevel;
        }
        return secondLevel + "." + topLevel;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

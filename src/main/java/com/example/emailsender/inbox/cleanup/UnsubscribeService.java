package com.example.emailsender.inbox.cleanup;

import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UnsubscribeService {

    private static final int MAX_HEADER_LENGTH = 4096;
    private static final int MAX_OPTION_LENGTH = 2048;
    private static final Pattern ANGLE_BRACKET_OPTION = Pattern.compile("<([^<>]+)>");

    public Optional<UnsubscribeOption> findOption(String header) {
        if (header == null
                || header.isBlank()
                || header.length() > MAX_HEADER_LENGTH
                || containsLineBreak(header)) {
            return Optional.empty();
        }

        List<String> candidates = candidates(header);
        Optional<UnsubscribeOption> httpsOption = candidates.stream()
                .map(this::parseHttps)
                .flatMap(Optional::stream)
                .findFirst();
        if (httpsOption.isPresent()) {
            return httpsOption;
        }

        return candidates.stream()
                .map(this::parseMailto)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private List<String> candidates(String header) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = ANGLE_BRACKET_OPTION.matcher(header);
        while (matcher.find()) {
            candidates.add(matcher.group(1).trim());
        }

        if (candidates.isEmpty()
                && !header.contains("<")
                && !header.contains(">")
                && !header.contains(",")) {
            candidates.add(header.trim());
        }
        return candidates;
    }

    private Optional<UnsubscribeOption> parseHttps(String candidate) {
        if (!validCandidateLength(candidate)) {
            return Optional.empty();
        }

        try {
            URI uri = new URI(candidate);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null) {
                return Optional.empty();
            }

            String destination = uri.getHost().toLowerCase(Locale.ROOT);
            if (uri.getPort() != -1) {
                destination += ":" + uri.getPort();
            }
            return Optional.of(new UnsubscribeOption(
                    UnsubscribeMethod.HTTPS,
                    uri.toASCIIString(),
                    destination
            ));
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private Optional<UnsubscribeOption> parseMailto(String candidate) {
        if (!validCandidateLength(candidate)) {
            return Optional.empty();
        }

        try {
            URI uri = new URI(candidate);
            if (!"mailto".equalsIgnoreCase(uri.getScheme()) || !uri.isOpaque()) {
                return Optional.empty();
            }

            String address = uri.getSchemeSpecificPart().split("\\?", 2)[0];
            if (address.isBlank()
                    || address.contains(",")
                    || address.contains(";")
                    || containsLineBreak(address)) {
                return Optional.empty();
            }

            InternetAddress internetAddress = new InternetAddress(address, true);
            internetAddress.validate();
            if (internetAddress.getPersonal() != null
                    || !address.equals(internetAddress.getAddress())) {
                return Optional.empty();
            }
            String safeUrl = new URI("mailto", address, null).toASCIIString();
            return Optional.of(new UnsubscribeOption(
                    UnsubscribeMethod.MAILTO,
                    safeUrl,
                    address
            ));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private boolean validCandidateLength(String candidate) {
        return candidate != null
                && !candidate.isBlank()
                && candidate.length() <= MAX_OPTION_LENGTH
                && !containsLineBreak(candidate);
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}

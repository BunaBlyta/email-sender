package com.example.emailsender.security;

import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.IDN;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SenderTrustService {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
    );

    private final UserRepository userRepository;
    private final SenderTrustRepository senderTrustRepository;
    private final Clock clock;

    public SenderTrustService(
            UserRepository userRepository,
            SenderTrustRepository senderTrustRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.senderTrustRepository = senderTrustRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrustListResponse list(String email) {
        User user = findUser(email);
        List<TrustEntryResponse> entries = senderTrustRepository
                .findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
        return new TrustListResponse(
                entries.stream()
                        .filter(entry -> entry.scope() == TrustScope.SENDER)
                        .toList(),
                entries.stream()
                        .filter(entry -> entry.scope() == TrustScope.DOMAIN)
                        .toList()
        );
    }

    @Transactional
    public TrustEntryResponse trustSender(String email, TrustRequest request) {
        User user = findUser(email);
        String sender = normalizeSender(request);
        return toResponse(findOrCreate(user, TrustScope.SENDER, sender));
    }

    @Transactional
    public TrustEntryResponse trustDomain(String email, TrustRequest request) {
        User user = findUser(email);
        String domain = normalizeDomainRequest(request);
        return toResponse(findOrCreate(user, TrustScope.DOMAIN, domain));
    }

    @Transactional
    public void delete(String email, Long id) {
        User user = findUser(email);
        if (id == null) {
            throw new IllegalArgumentException("Trust entry id is required");
        }
        SenderTrustEntry entry = senderTrustRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trust entry not found"));
        senderTrustRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public PhishingTrustContext trustContext(String email, String senderValue) {
        User user = findUser(email);
        String sender = extractSender(senderValue);
        String domain = domainFromSender(sender);

        boolean senderTrusted = !sender.isBlank()
                && senderTrustRepository.existsByUserAndScopeAndTrustedValueIgnoreCase(
                        user,
                        TrustScope.SENDER,
                        sender
                );
        boolean domainTrusted = !domain.isBlank()
                && senderTrustRepository.existsByUserAndScopeAndTrustedValueIgnoreCase(
                        user,
                        TrustScope.DOMAIN,
                        domain
                );
        return new PhishingTrustContext(senderTrusted, domainTrusted);
    }

    private SenderTrustEntry findOrCreate(
            User user,
            TrustScope scope,
            String trustedValue) {
        return senderTrustRepository
                .findByUserAndScopeAndTrustedValueIgnoreCase(user, scope, trustedValue)
                .orElseGet(() -> {
                    SenderTrustEntry entry = new SenderTrustEntry();
                    entry.setUser(user);
                    entry.setScope(scope);
                    entry.setTrustedValue(trustedValue);
                    entry.setCreatedAt(LocalDateTime.now(clock));
                    return senderTrustRepository.save(entry);
                });
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String normalizeSender(TrustRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String sender = extractSender(request.value());
        if (sender.isBlank()) {
            throw new IllegalArgumentException("Valid sender email is required");
        }
        return sender;
    }

    private String normalizeDomainRequest(TrustRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String value = normalize(request.value());
        if (value == null) {
            throw new IllegalArgumentException("Domain is required");
        }
        if (value.contains("://")) {
            throw new IllegalArgumentException("Domain must not include a URL scheme");
        }
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        if (value.contains("@")) {
            value = domainFromSender(extractSender(value));
        }

        String domain = normalizeDomain(value);
        if (!isValidDomain(domain)) {
            throw new IllegalArgumentException("Invalid domain: " + request.value());
        }
        return domain;
    }

    private String extractSender(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "";
        }

        try {
            InternetAddress[] addresses = InternetAddress.parse(normalized, false);
            if (addresses.length != 1 || addresses[0].getAddress() == null) {
                return "";
            }
            String address = addresses[0].getAddress().trim().toLowerCase(Locale.ROOT);
            InternetAddress strictAddress = new InternetAddress(address, true);
            strictAddress.validate();
            if (!address.equals(strictAddress.getAddress()) || !address.contains("@")) {
                return "";
            }
            return address;
        } catch (AddressException exception) {
            return "";
        }
    }

    private String domainFromSender(String sender) {
        if (sender == null) {
            return "";
        }
        int atIndex = sender.lastIndexOf('@');
        if (atIndex < 0 || atIndex == sender.length() - 1) {
            return "";
        }
        return normalizeDomain(sender.substring(atIndex + 1));
    }

    private String normalizeDomain(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "";
        }
        try {
            return IDN.toASCII(normalized.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return normalized.toLowerCase(Locale.ROOT);
        }
    }

    private boolean isValidDomain(String value) {
        return value != null
                && DOMAIN_PATTERN.matcher(value).matches()
                && value.substring(value.lastIndexOf('.') + 1).length() >= 2;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private TrustEntryResponse toResponse(SenderTrustEntry entry) {
        return new TrustEntryResponse(
                entry.getId(),
                entry.getScope(),
                entry.getTrustedValue(),
                entry.getCreatedAt()
        );
    }
}

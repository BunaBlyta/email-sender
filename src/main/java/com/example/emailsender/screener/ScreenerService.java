package com.example.emailsender.screener;

import com.example.emailsender.security.PhishingAnalysisRequest;
import com.example.emailsender.security.PhishingAnalysisResponse;
import com.example.emailsender.security.PhishingDetector;
import com.example.emailsender.security.PhishingTrustContext;
import com.example.emailsender.security.SenderTrustService;
import com.example.emailsender.security.TrustEntryResponse;
import com.example.emailsender.security.TrustRequest;
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

@Service
public class ScreenerService {

    private final UserRepository userRepository;
    private final ScreenerRepository screenerRepository;
    private final SenderTrustService senderTrustService;
    private final PhishingDetector phishingDetector;
    private final Clock clock;

    public ScreenerService(
            UserRepository userRepository,
            ScreenerRepository screenerRepository,
            SenderTrustService senderTrustService,
            PhishingDetector phishingDetector,
            Clock clock) {
        this.userRepository = userRepository;
        this.screenerRepository = screenerRepository;
        this.senderTrustService = senderTrustService;
        this.phishingDetector = phishingDetector;
        this.clock = clock;
    }

    @Transactional
    public ScreenerEvaluationResponse evaluate(
            String email,
            ScreenerEvaluateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        User user = findUser(email);
        SenderIdentity sender = parseSender(request.sender());
        PhishingTrustContext trustContext =
                senderTrustService.trustContext(email, request.sender());
        PhishingAnalysisResponse phishing = phishingDetector.analyze(
                new PhishingAnalysisRequest(
                        request.sender(),
                        request.subject(),
                        request.body()
                ),
                trustContext
        );

        ScreenerEntry existing = screenerRepository
                .findByUserAndSenderEmailIgnoreCase(user, sender.email())
                .orElse(null);

        if (existing != null && existing.getStatus() == ScreenerEntry.Status.REJECTED) {
            return response(existing, false, false, trustContext, phishing);
        }
        if (trustContext.hasTrust()) {
            if (existing != null && existing.getStatus() == ScreenerEntry.Status.PENDING) {
                markDecided(existing, ScreenerEntry.Status.APPROVED);
                existing = screenerRepository.save(existing);
            }
            return response(existing, existing == null, false, trustContext, phishing);
        }
        if (existing != null) {
            return response(
                    existing,
                    false,
                    existing.getStatus() == ScreenerEntry.Status.PENDING,
                    trustContext,
                    phishing
            );
        }

        ScreenerEntry entry = new ScreenerEntry();
        entry.setUser(user);
        entry.setSenderEmail(sender.email());
        entry.setSenderDomain(sender.domain());
        entry.setFirstContactAt(LocalDateTime.now(clock));
        entry.setStatus(ScreenerEntry.Status.PENDING);
        ScreenerEntry saved = screenerRepository.save(entry);
        return response(saved, true, true, trustContext, phishing);
    }

    @Transactional(readOnly = true)
    public List<ScreenerEntryResponse> pending(String email) {
        User user = findUser(email);
        return screenerRepository
                .findByUserAndStatusOrderByFirstContactAtDesc(
                        user,
                        ScreenerEntry.Status.PENDING
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScreenerDecisionResponse approveSender(String email, Long id) {
        ScreenerEntry entry = findOwnedEntry(email, id);
        markDecided(entry, ScreenerEntry.Status.APPROVED);
        ScreenerEntry saved = screenerRepository.save(entry);
        TrustEntryResponse trustEntry = senderTrustService.trustSender(
                email,
                new TrustRequest(saved.getSenderEmail())
        );
        return new ScreenerDecisionResponse(toResponse(saved), trustEntry);
    }

    @Transactional
    public ScreenerDecisionResponse approveDomain(String email, Long id) {
        ScreenerEntry entry = findOwnedEntry(email, id);
        markDecided(entry, ScreenerEntry.Status.APPROVED);
        ScreenerEntry saved = screenerRepository.save(entry);
        TrustEntryResponse trustEntry = senderTrustService.trustDomain(
                email,
                new TrustRequest(saved.getSenderDomain())
        );
        return new ScreenerDecisionResponse(toResponse(saved), trustEntry);
    }

    @Transactional
    public ScreenerDecisionResponse rejectSender(String email, Long id) {
        ScreenerEntry entry = findOwnedEntry(email, id);
        markDecided(entry, ScreenerEntry.Status.REJECTED);
        ScreenerEntry saved = screenerRepository.save(entry);
        return new ScreenerDecisionResponse(toResponse(saved), null);
    }

    private ScreenerEvaluationResponse response(
            ScreenerEntry entry,
            boolean firstTimeSender,
            boolean requiresDecision,
            PhishingTrustContext trustContext,
            PhishingAnalysisResponse phishing) {
        ScreenerEntry.Status status = entry == null
                ? ScreenerEntry.Status.APPROVED
                : entry.getStatus();
        return new ScreenerEvaluationResponse(
                entry == null ? null : toResponse(entry),
                status,
                firstTimeSender,
                requiresDecision,
                trustContext.senderTrusted(),
                trustContext.domainTrusted(),
                phishing
        );
    }

    private ScreenerEntry findOwnedEntry(String email, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Screener entry id is required");
        }
        User user = findUser(email);
        return screenerRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Screener entry not found"));
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private SenderIdentity parseSender(String value) {
        String sender = extractSender(value);
        if (sender.isBlank()) {
            throw new IllegalArgumentException("Valid sender email is required");
        }
        return new SenderIdentity(sender, domainFromSender(sender));
    }

    private String extractSender(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            InternetAddress[] addresses = InternetAddress.parse(value.trim(), false);
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
        int atIndex = sender.lastIndexOf('@');
        if (atIndex < 0 || atIndex == sender.length() - 1) {
            return "";
        }
        String domain = sender.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        try {
            return IDN.toASCII(domain);
        } catch (IllegalArgumentException exception) {
            return domain;
        }
    }

    private void markDecided(ScreenerEntry entry, ScreenerEntry.Status status) {
        entry.setStatus(status);
        entry.setDecidedAt(LocalDateTime.now(clock));
    }

    private ScreenerEntryResponse toResponse(ScreenerEntry entry) {
        return new ScreenerEntryResponse(
                entry.getId(),
                entry.getSenderEmail(),
                entry.getSenderDomain(),
                entry.getFirstContactAt(),
                entry.getStatus(),
                entry.getDecidedAt()
        );
    }

    private record SenderIdentity(String email, String domain) {
    }
}

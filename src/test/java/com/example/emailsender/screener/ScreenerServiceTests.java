package com.example.emailsender.screener;

import com.example.emailsender.security.PhishingAnalysisResponse;
import com.example.emailsender.security.PhishingDetector;
import com.example.emailsender.security.PhishingRiskLevel;
import com.example.emailsender.security.PhishingTrustContext;
import com.example.emailsender.security.PhishingTrustResponse;
import com.example.emailsender.security.SenderTrustService;
import com.example.emailsender.security.TrustEntryResponse;
import com.example.emailsender.security.TrustRequest;
import com.example.emailsender.security.TrustScope;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenerServiceTests {

    private UserRepository userRepository;
    private ScreenerRepository screenerRepository;
    private SenderTrustService senderTrustService;
    private PhishingDetector phishingDetector;
    private ScreenerService screenerService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        screenerRepository = mock(ScreenerRepository.class);
        senderTrustService = mock(SenderTrustService.class);
        phishingDetector = mock(PhishingDetector.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-24T18:45:00Z"),
                ZoneOffset.UTC
        );
        screenerService = new ScreenerService(
                userRepository,
                screenerRepository,
                senderTrustService,
                phishingDetector,
                clock
        );

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void createsPendingEntryForFirstTimeUntrustedSender() {
        when(senderTrustService.trustContext(
                "user@example.com",
                "New Sender <New@Example.com>"
        )).thenReturn(PhishingTrustContext.none());
        when(phishingDetector.analyze(any(), eq(PhishingTrustContext.none())))
                .thenReturn(phishing(false, false));
        when(screenerRepository.findByUserAndSenderEmailIgnoreCase(
                user,
                "new@example.com"
        )).thenReturn(Optional.empty());
        when(screenerRepository.save(any(ScreenerEntry.class)))
                .thenAnswer(invocation -> {
                    ScreenerEntry entry = invocation.getArgument(0);
                    entry.setId(8L);
                    return entry;
                });

        ScreenerEvaluationResponse response = screenerService.evaluate(
                "user@example.com",
                new ScreenerEvaluateRequest(
                        "New Sender <New@Example.com>",
                        "Hello",
                        "Could we talk?"
                )
        );

        assertEquals(ScreenerEntry.Status.PENDING, response.status());
        assertTrue(response.firstTimeSender());
        assertTrue(response.requiresDecision());
        assertEquals(8L, response.entry().id());
        assertEquals("new@example.com", response.entry().senderEmail());
        assertEquals("example.com", response.entry().senderDomain());
        assertEquals(LocalDateTime.of(2026, 6, 24, 18, 45),
                response.entry().firstContactAt());
    }

    @Test
    void trustedSenderDoesNotCreatePendingEntry() {
        PhishingTrustContext trustContext = new PhishingTrustContext(true, false);
        when(senderTrustService.trustContext(
                "user@example.com",
                "Professor Ercan <ercan@university.edu>"
        )).thenReturn(trustContext);
        when(phishingDetector.analyze(any(), eq(trustContext)))
                .thenReturn(phishing(true, false));
        when(screenerRepository.findByUserAndSenderEmailIgnoreCase(
                user,
                "ercan@university.edu"
        )).thenReturn(Optional.empty());

        ScreenerEvaluationResponse response = screenerService.evaluate(
                "user@example.com",
                new ScreenerEvaluateRequest(
                        "Professor Ercan <ercan@university.edu>",
                        "Thesis",
                        "Please review this."
                )
        );

        assertEquals(ScreenerEntry.Status.APPROVED, response.status());
        assertTrue(response.firstTimeSender());
        assertFalse(response.requiresDecision());
        assertTrue(response.trustedSender());
        assertNull(response.entry());
    }

    @Test
    void rejectedSenderDecisionAppliesToFutureEvaluations() {
        ScreenerEntry rejected = entry(
                12L,
                "sender@example.com",
                ScreenerEntry.Status.REJECTED
        );
        rejected.setDecidedAt(LocalDateTime.of(2026, 6, 24, 18, 50));
        when(senderTrustService.trustContext(
                "user@example.com",
                "sender@example.com"
        )).thenReturn(PhishingTrustContext.none());
        when(phishingDetector.analyze(any(), eq(PhishingTrustContext.none())))
                .thenReturn(phishing(false, false));
        when(screenerRepository.findByUserAndSenderEmailIgnoreCase(
                user,
                "sender@example.com"
        )).thenReturn(Optional.of(rejected));

        ScreenerEvaluationResponse response = screenerService.evaluate(
                "user@example.com",
                new ScreenerEvaluateRequest(
                        "sender@example.com",
                        "Hello",
                        "Trying again"
                )
        );

        assertEquals(ScreenerEntry.Status.REJECTED, response.status());
        assertFalse(response.requiresDecision());
        assertEquals(12L, response.entry().id());
    }

    @Test
    void approveSenderMarksEntryAndCreatesTrustRecord() {
        ScreenerEntry pending = entry(
                15L,
                "ercan@university.edu",
                ScreenerEntry.Status.PENDING
        );
        when(screenerRepository.findByIdAndUser(15L, user))
                .thenReturn(Optional.of(pending));
        when(screenerRepository.save(pending)).thenReturn(pending);
        when(senderTrustService.trustSender(
                eq("user@example.com"),
                any(TrustRequest.class)
        )).thenReturn(new TrustEntryResponse(
                3L,
                TrustScope.SENDER,
                "ercan@university.edu",
                LocalDateTime.of(2026, 6, 24, 18, 45)
        ));

        ScreenerDecisionResponse response =
                screenerService.approveSender("user@example.com", 15L);

        assertEquals(ScreenerEntry.Status.APPROVED, response.entry().status());
        assertEquals(LocalDateTime.of(2026, 6, 24, 18, 45), response.entry().decidedAt());
        assertEquals(TrustScope.SENDER, response.trustEntry().scope());
        verify(senderTrustService).trustSender(
                eq("user@example.com"),
                any(TrustRequest.class)
        );
    }

    @Test
    void approveDomainMarksEntryAndCreatesDomainTrustRecord() {
        ScreenerEntry pending = entry(
                16L,
                "member@university.edu",
                ScreenerEntry.Status.PENDING
        );
        when(screenerRepository.findByIdAndUser(16L, user))
                .thenReturn(Optional.of(pending));
        when(screenerRepository.save(pending)).thenReturn(pending);
        when(senderTrustService.trustDomain(
                eq("user@example.com"),
                any(TrustRequest.class)
        )).thenReturn(new TrustEntryResponse(
                4L,
                TrustScope.DOMAIN,
                "university.edu",
                LocalDateTime.of(2026, 6, 24, 18, 45)
        ));

        ScreenerDecisionResponse response =
                screenerService.approveDomain("user@example.com", 16L);

        assertEquals(ScreenerEntry.Status.APPROVED, response.entry().status());
        assertEquals(TrustScope.DOMAIN, response.trustEntry().scope());
        verify(senderTrustService).trustDomain(
                eq("user@example.com"),
                any(TrustRequest.class)
        );
    }

    @Test
    void rejectSenderMarksEntryRejectedWithoutTrustRecord() {
        ScreenerEntry pending = entry(
                17L,
                "sender@example.com",
                ScreenerEntry.Status.PENDING
        );
        when(screenerRepository.findByIdAndUser(17L, user))
                .thenReturn(Optional.of(pending));
        when(screenerRepository.save(pending)).thenReturn(pending);

        ScreenerDecisionResponse response =
                screenerService.rejectSender("user@example.com", 17L);

        assertEquals(ScreenerEntry.Status.REJECTED, response.entry().status());
        assertEquals(LocalDateTime.of(2026, 6, 24, 18, 45), response.entry().decidedAt());
        assertNull(response.trustEntry());
    }

    @Test
    void listsPendingEntries() {
        when(screenerRepository.findByUserAndStatusOrderByFirstContactAtDesc(
                user,
                ScreenerEntry.Status.PENDING
        )).thenReturn(List.of(
                entry(2L, "first@example.com", ScreenerEntry.Status.PENDING),
                entry(1L, "second@example.com", ScreenerEntry.Status.PENDING)
        ));

        List<ScreenerEntryResponse> response =
                screenerService.pending("user@example.com");

        assertEquals(2, response.size());
        assertEquals("first@example.com", response.getFirst().senderEmail());
    }

    private ScreenerEntry entry(Long id, String sender, ScreenerEntry.Status status) {
        ScreenerEntry entry = new ScreenerEntry();
        entry.setId(id);
        entry.setUser(user);
        entry.setSenderEmail(sender);
        entry.setSenderDomain(sender.substring(sender.indexOf('@') + 1));
        entry.setFirstContactAt(LocalDateTime.of(2026, 6, 24, 18, 30));
        entry.setStatus(status);
        return entry;
    }

    private PhishingAnalysisResponse phishing(boolean senderTrusted, boolean domainTrusted) {
        return new PhishingAnalysisResponse(
                "",
                "",
                PhishingRiskLevel.LOW,
                0,
                List.of(),
                List.of(),
                new PhishingTrustResponse(senderTrusted, domainTrusted, 0)
        );
    }
}

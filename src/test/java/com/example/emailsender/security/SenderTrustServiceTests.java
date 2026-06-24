package com.example.emailsender.security;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SenderTrustServiceTests {

    private UserRepository userRepository;
    private SenderTrustRepository senderTrustRepository;
    private SenderTrustService senderTrustService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        senderTrustRepository = mock(SenderTrustRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-24T18:30:00Z"),
                ZoneOffset.UTC
        );
        senderTrustService = new SenderTrustService(
                userRepository,
                senderTrustRepository,
                clock
        );

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void createsNormalizedTrustedSender() {
        when(senderTrustRepository.findByUserAndScopeAndTrustedValueIgnoreCase(
                user,
                TrustScope.SENDER,
                "ercan@university.edu"
        )).thenReturn(Optional.empty());
        when(senderTrustRepository.save(any(SenderTrustEntry.class)))
                .thenAnswer(invocation -> {
                    SenderTrustEntry entry = invocation.getArgument(0);
                    entry.setId(4L);
                    return entry;
                });

        TrustEntryResponse response = senderTrustService.trustSender(
                "user@example.com",
                new TrustRequest("Professor Ercan <ERCAN@University.edu>")
        );

        assertEquals(4L, response.id());
        assertEquals(TrustScope.SENDER, response.scope());
        assertEquals("ercan@university.edu", response.value());
        assertEquals(LocalDateTime.of(2026, 6, 24, 18, 30), response.createdAt());
    }

    @Test
    void returnsExistingTrustEntryWithoutSavingDuplicate() {
        SenderTrustEntry existing = entry(
                7L,
                TrustScope.SENDER,
                "ercan@university.edu"
        );
        when(senderTrustRepository.findByUserAndScopeAndTrustedValueIgnoreCase(
                user,
                TrustScope.SENDER,
                "ercan@university.edu"
        )).thenReturn(Optional.of(existing));

        TrustEntryResponse response = senderTrustService.trustSender(
                "user@example.com",
                new TrustRequest("ercan@university.edu")
        );

        assertEquals(7L, response.id());
        verify(senderTrustRepository, never()).save(any());
    }

    @Test
    void createsNormalizedTrustedDomain() {
        when(senderTrustRepository.findByUserAndScopeAndTrustedValueIgnoreCase(
                user,
                TrustScope.DOMAIN,
                "university.edu"
        )).thenReturn(Optional.empty());
        when(senderTrustRepository.save(any(SenderTrustEntry.class)))
                .thenAnswer(invocation -> {
                    SenderTrustEntry entry = invocation.getArgument(0);
                    entry.setId(5L);
                    return entry;
                });

        TrustEntryResponse response = senderTrustService.trustDomain(
                "user@example.com",
                new TrustRequest("@University.edu")
        );

        assertEquals(5L, response.id());
        assertEquals(TrustScope.DOMAIN, response.scope());
        assertEquals("university.edu", response.value());
    }

    @Test
    void listsTrustedSendersAndDomainsSeparately() {
        when(senderTrustRepository.findByUserOrderByCreatedAtDesc(user))
                .thenReturn(List.of(
                        entry(1L, TrustScope.DOMAIN, "university.edu"),
                        entry(2L, TrustScope.SENDER, "ercan@university.edu")
                ));

        TrustListResponse response = senderTrustService.list("user@example.com");

        assertEquals(1, response.senders().size());
        assertEquals("ercan@university.edu", response.senders().getFirst().value());
        assertEquals(1, response.domains().size());
        assertEquals("university.edu", response.domains().getFirst().value());
    }

    @Test
    void buildsTrustContextForExactSenderAndDomain() {
        when(senderTrustRepository.existsByUserAndScopeAndTrustedValueIgnoreCase(
                user,
                TrustScope.SENDER,
                "ercan@university.edu"
        )).thenReturn(true);
        when(senderTrustRepository.existsByUserAndScopeAndTrustedValueIgnoreCase(
                user,
                TrustScope.DOMAIN,
                "university.edu"
        )).thenReturn(true);

        PhishingTrustContext context = senderTrustService.trustContext(
                "user@example.com",
                "Professor Ercan <ercan@university.edu>"
        );

        assertTrue(context.senderTrusted());
        assertTrue(context.domainTrusted());
    }

    @Test
    void rejectsUrlAsTrustedDomain() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> senderTrustService.trustDomain(
                        "user@example.com",
                        new TrustRequest("https://university.edu")
                )
        );

        assertEquals("Domain must not include a URL scheme", exception.getMessage());
    }

    private SenderTrustEntry entry(Long id, TrustScope scope, String value) {
        SenderTrustEntry entry = new SenderTrustEntry();
        entry.setId(id);
        entry.setUser(user);
        entry.setScope(scope);
        entry.setTrustedValue(value);
        entry.setCreatedAt(LocalDateTime.of(2026, 6, 24, 18, 30));
        return entry;
    }
}

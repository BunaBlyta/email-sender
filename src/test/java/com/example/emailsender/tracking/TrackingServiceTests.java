package com.example.emailsender.tracking;

import com.example.emailsender.send.SentMessage;
import com.example.emailsender.send.SentMessageRepository;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingServiceTests {

    private SentMessageRepository sentMessageRepository;
    private TrackingEventRepository trackingEventRepository;
    private UserRepository userRepository;
    private TrackingService trackingService;

    @BeforeEach
    void setUp() {
        sentMessageRepository = mock(SentMessageRepository.class);
        trackingEventRepository = mock(TrackingEventRepository.class);
        userRepository = mock(UserRepository.class);
        trackingService = new TrackingService(
                sentMessageRepository,
                trackingEventRepository,
                userRepository,
                Clock.fixed(
                        Instant.parse("2026-06-22T16:00:00Z"),
                        ZoneOffset.UTC
                ),
                "https://mail.example.com/"
        );
    }

    @Test
    void createsEscapedHtmlBodyWithTrackingPixel() {
        TrackingService.TrackedBody tracked =
                trackingService.createTrackedBody("<script>alert('x')</script>");

        assertFalse(tracked.trackingId().isBlank());
        assertFalse(tracked.htmlBody().contains("<script>"));
        assertEquals(true, tracked.htmlBody().contains(
                "https://mail.example.com/track/" + tracked.trackingId() + ".png"));
    }

    @Test
    void recordsFirstAndSubsequentPixelLoadsWithRequestEvidence() {
        SentMessage message = new SentMessage();
        message.setTrackingEnabled(true);
        message.setTrackingId("tracking-123");
        message.setPixelLoadCount(1);
        message.setFirstPixelLoadedAt(LocalDateTime.of(2026, 6, 22, 15, 0));
        when(sentMessageRepository.findByTrackingId("tracking-123"))
                .thenReturn(Optional.of(message));

        trackingService.recordPixelLoad(
                "tracking-123",
                new TrackingService.TrackingRequestMetadata(
                        "Mozilla/5.0 (compatible; GoogleImageProxy)",
                        "png"
                )
        );

        assertEquals(2, message.getPixelLoadCount());
        assertEquals(LocalDateTime.of(2026, 6, 22, 15, 0),
                message.getFirstPixelLoadedAt());
        assertEquals(LocalDateTime.of(2026, 6, 22, 16, 0),
                message.getLastPixelLoadedAt());

        ArgumentCaptor<TrackingEvent> eventCaptor =
                ArgumentCaptor.forClass(TrackingEvent.class);
        verify(trackingEventRepository).save(eventCaptor.capture());
        TrackingEvent event = eventCaptor.getValue();
        assertSame(message, event.getSentMessage());
        assertEquals("tracking-123", event.getTrackingId());
        assertEquals(LocalDateTime.of(2026, 6, 22, 16, 0), event.getLoadedAt());
        assertEquals(TrackingEvent.Source.GOOGLE_IMAGE_PROXY, event.getSource());
        assertEquals("png", event.getImageFormat());

        verify(sentMessageRepository).save(message);
    }

    @Test
    void includesRecentEventsInStatusResponse() {
        SentMessage message = new SentMessage();
        message.setTrackingEnabled(true);
        message.setTrackingId("tracking-123");
        message.setPixelLoadCount(1);
        message.setFirstPixelLoadedAt(LocalDateTime.of(2026, 6, 22, 16, 0));
        message.setLastPixelLoadedAt(LocalDateTime.of(2026, 6, 22, 16, 0));

        TrackingEvent event = new TrackingEvent();
        event.setId(5L);
        event.setSentMessage(message);
        event.setTrackingId("tracking-123");
        event.setLoadedAt(LocalDateTime.of(2026, 6, 22, 16, 0));
        event.setSource(TrackingEvent.Source.BROWSER);
        event.setImageFormat("gif");
        event.setUserAgent("curl/8.0");
        when(trackingEventRepository.findTop10BySentMessageOrderByLoadedAtDesc(message))
                .thenReturn(List.of(event));

        var response = trackingService.toResponse(message);

        assertEquals("IMAGE_LOAD_DETECTED", response.status());
        assertEquals(1, response.recentEvents().size());
        assertEquals(5L, response.recentEvents().getFirst().id());
        assertEquals("BROWSER", response.recentEvents().getFirst().source());
        assertEquals("gif", response.recentEvents().getFirst().imageFormat());
    }

    @Test
    void listsRecentTrackedMessagesForAuthenticatedUser() {
        User user = new User();
        user.setEmail("user@example.com");
        SentMessage detected = mock(SentMessage.class);
        when(detected.getId()).thenReturn(42L);
        when(detected.getRecipient()).thenReturn("recipient@example.com");
        when(detected.getSubject()).thenReturn("Thesis update");
        when(detected.getSentAt()).thenReturn(
                LocalDateTime.of(2026, 6, 22, 15, 30));
        when(detected.isTrackingEnabled()).thenReturn(true);
        when(detected.getPixelLoadCount()).thenReturn(2);
        when(detected.getFirstPixelLoadedAt()).thenReturn(
                LocalDateTime.of(2026, 6, 22, 15, 45));
        when(detected.getLastPixelLoadedAt()).thenReturn(
                LocalDateTime.of(2026, 6, 22, 16, 0));
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user));
        when(sentMessageRepository
                .findTop50ByUserAndTrackingEnabledTrueOrderBySentAtDesc(user))
                .thenReturn(List.of(detected));

        List<TrackedMessageSummaryResponse> response =
                trackingService.listRecent("user@example.com");

        assertEquals(1, response.size());
        TrackedMessageSummaryResponse summary = response.getFirst();
        assertEquals(42L, summary.sentMessageId());
        assertEquals("recipient@example.com", summary.recipient());
        assertEquals("Thesis update", summary.subject());
        assertEquals("IMAGE_LOAD_DETECTED", summary.status());
        assertEquals(2, summary.pixelLoadCount());
        verify(sentMessageRepository)
                .findTop50ByUserAndTrackingEnabledTrueOrderBySentAtDesc(user);
        verify(trackingEventRepository, never())
                .findTop10BySentMessageOrderByLoadedAtDesc(any());
    }

    @Test
    void ignoresUnknownTrackingIdWithoutExposingExistence() {
        when(sentMessageRepository.findByTrackingId("unknown"))
                .thenReturn(Optional.empty());

        trackingService.recordPixelLoad("unknown");

        verify(sentMessageRepository).findByTrackingId("unknown");
        verify(trackingEventRepository, never()).save(any());
    }
}

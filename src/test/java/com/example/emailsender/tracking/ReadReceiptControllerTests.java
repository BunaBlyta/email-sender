package com.example.emailsender.tracking;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadReceiptControllerTests {

    @Test
    void recordsRequestMetadataAndReturnsPngBadge() {
        TrackingService trackingService = mock(TrackingService.class);
        TrackingPixelService trackingPixelService = mock(TrackingPixelService.class);
        byte[] badge = new byte[] {1, 2, 3};
        when(trackingPixelService.badge()).thenReturn(badge);

        ReadReceiptController controller =
                new ReadReceiptController(trackingService, trackingPixelService);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/track/tracking-123.gif");
        request.addHeader(HttpHeaders.USER_AGENT, "GoogleImageProxy");

        ResponseEntity<byte[]> response = controller.track("tracking-123", request);

        ArgumentCaptor<TrackingService.TrackingRequestMetadata> metadataCaptor =
                ArgumentCaptor.forClass(TrackingService.TrackingRequestMetadata.class);
        verify(trackingService).recordPixelLoad(eq("tracking-123"), metadataCaptor.capture());
        assertEquals("GoogleImageProxy", metadataCaptor.getValue().userAgent());
        assertEquals("gif", metadataCaptor.getValue().imageFormat());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertEquals("no-cache", response.getHeaders().getFirst(HttpHeaders.PRAGMA));
        assertArrayEquals(badge, response.getBody());
    }

    @Test
    void listsRecentTrackedMessagesForAuthenticatedUser() {
        TrackingService trackingService = mock(TrackingService.class);
        TrackingPixelService trackingPixelService = mock(TrackingPixelService.class);
        OAuth2User principal = mock(OAuth2User.class);
        var messages = java.util.List.of(new TrackedMessageSummaryResponse(
                42L,
                "recipient@example.com",
                "Thesis update",
                java.time.LocalDateTime.of(2026, 6, 22, 15, 30),
                false,
                "AWAITING_IMAGE_LOAD",
                0,
                null,
                null
        ));
        when(principal.getAttribute("email")).thenReturn("user@example.com");
        when(trackingService.listRecent("user@example.com")).thenReturn(messages);
        ReadReceiptController controller =
                new ReadReceiptController(trackingService, trackingPixelService);

        var response = controller.recent(principal);

        assertSame(messages, response);
        verify(trackingService).listRecent("user@example.com");
    }
}

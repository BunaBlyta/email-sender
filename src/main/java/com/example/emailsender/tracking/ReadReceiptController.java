package com.example.emailsender.tracking;

import com.example.emailsender.send.TrackingResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReadReceiptController {

    private final TrackingService trackingService;
    private final TrackingPixelService trackingPixelService;

    public ReadReceiptController(
            TrackingService trackingService,
            TrackingPixelService trackingPixelService) {
        this.trackingService = trackingService;
        this.trackingPixelService = trackingPixelService;
    }

    @GetMapping(value = {
            "/track/{trackingId}.png",
            "/track/{trackingId}.gif"
    }, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> track(
            @PathVariable String trackingId,
            HttpServletRequest request) {
        trackingService.recordPixelLoad(
                trackingId,
                new TrackingService.TrackingRequestMetadata(
                        request.getHeader(HttpHeaders.USER_AGENT),
                        imageFormat(request)
                )
        );
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(trackingPixelService.badge());
    }

    @GetMapping("/tracking/sent/{sentMessageId}")
    public TrackingResponse status(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long sentMessageId) {
        return trackingService.getStatus(
                principal.getAttribute("email"),
                sentMessageId
        );
    }

    @GetMapping("/tracking/sent")
    public List<TrackedMessageSummaryResponse> recent(
            @AuthenticationPrincipal OAuth2User principal) {
        return trackingService.listRecent(principal.getAttribute("email"));
    }

    private String imageFormat(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.endsWith(".gif")) {
            return "gif";
        }
        return "png";
    }
}

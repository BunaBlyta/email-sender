package com.example.emailsender.tracking;

import com.example.emailsender.send.SentMessage;
import com.example.emailsender.send.SentMessageRepository;
import com.example.emailsender.send.TrackingEventResponse;
import com.example.emailsender.send.TrackingResponse;
import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TrackingService {

    private static final int USER_AGENT_MAX_LENGTH = 1000;
    private static final int IMAGE_FORMAT_MAX_LENGTH = 16;

    private final SentMessageRepository sentMessageRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final String publicBaseUrl;

    public TrackingService(
            SentMessageRepository sentMessageRepository,
            TrackingEventRepository trackingEventRepository,
            UserRepository userRepository,
            Clock clock,
            @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.sentMessageRepository = sentMessageRepository;
        this.trackingEventRepository = trackingEventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    public TrackedBody createTrackedBody(String plainTextBody) {
        String trackingId = UUID.randomUUID().toString();
        String pixelUrl = publicBaseUrl + "/track/" + trackingId + ".png";
        String html = "<div style=\"white-space:pre-wrap\">"
                + escapeHtml(plainTextBody)
                + "</div>"
                + "<br><br>"
                + "<div style=\"font-size:12px;color:#777;font-family:Arial,sans-serif\">"
                + "Sent with Email Platform"
                + "<br>"
                + "<img src=\""
                + pixelUrl
                + "\" width=\"96\" height=\"24\" alt=\"Email Platform\" "
                + "style=\"border:0;display:block;margin-top:4px\"/>"
                + "</div>";
        return new TrackedBody(trackingId, html);
    }

    @Transactional
    public void recordPixelLoad(String trackingId) {
        recordPixelLoad(trackingId, TrackingRequestMetadata.empty());
    }

    @Transactional
    public void recordPixelLoad(String trackingId, TrackingRequestMetadata metadata) {
        TrackingRequestMetadata safeMetadata =
                metadata == null ? TrackingRequestMetadata.empty() : metadata;
        sentMessageRepository.findByTrackingId(trackingId).ifPresent(message -> {
            LocalDateTime now = LocalDateTime.now(clock);
            if (message.getFirstPixelLoadedAt() == null) {
                message.setFirstPixelLoadedAt(now);
            }
            message.setLastPixelLoadedAt(now);
            message.setPixelLoadCount(message.getPixelLoadCount() + 1);
            trackingEventRepository.save(toEvent(message, now, safeMetadata));
            sentMessageRepository.save(message);
        });
    }

    @Transactional(readOnly = true)
    public TrackingResponse getStatus(String email, Long sentMessageId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SentMessage message = sentMessageRepository.findByIdAndUser(sentMessageId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Sent message not found"));
        return toResponse(message);
    }

    public TrackingResponse toResponse(SentMessage message) {
        return new TrackingResponse(
                message.isTrackingEnabled(),
                status(message),
                message.getTrackingId(),
                message.getPixelLoadCount(),
                message.getFirstPixelLoadedAt(),
                message.getLastPixelLoadedAt(),
                recentEvents(message)
        );
    }

    private TrackingEvent toEvent(
            SentMessage message,
            LocalDateTime loadedAt,
            TrackingRequestMetadata metadata) {
        TrackingEvent event = new TrackingEvent();
        event.setSentMessage(message);
        event.setTrackingId(message.getTrackingId());
        event.setLoadedAt(loadedAt);
        event.setSource(classifySource(metadata.userAgent()));
        event.setImageFormat(normalizeFormat(metadata.imageFormat()));
        event.setUserAgent(truncate(metadata.userAgent(), USER_AGENT_MAX_LENGTH));
        return event;
    }

    private List<TrackingEventResponse> recentEvents(SentMessage message) {
        if (!message.isTrackingEnabled()) {
            return List.of();
        }

        List<TrackingEvent> events =
                trackingEventRepository.findTop10BySentMessageOrderByLoadedAtDesc(message);
        if (events == null) {
            return List.of();
        }

        return events.stream()
                .map(event -> new TrackingEventResponse(
                        event.getId(),
                        event.getLoadedAt(),
                        event.getSource().name(),
                        event.getImageFormat(),
                        event.getUserAgent()
                ))
                .toList();
    }

    private String status(SentMessage message) {
        if (!message.isTrackingEnabled()) {
            return "DISABLED";
        }
        if (message.getPixelLoadCount() > 0 || message.getFirstPixelLoadedAt() != null) {
            return "IMAGE_LOAD_DETECTED";
        }
        return "AWAITING_IMAGE_LOAD";
    }

    private TrackingEvent.Source classifySource(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return TrackingEvent.Source.UNKNOWN;
        }

        String value = userAgent.toLowerCase(Locale.ROOT);
        if (value.contains("googleimageproxy")) {
            return TrackingEvent.Source.GOOGLE_IMAGE_PROXY;
        }
        if (value.contains("icloud") || value.contains("mailprivacy")
                || value.contains("applemail")) {
            return TrackingEvent.Source.APPLE_MAIL_PRIVACY_PROXY;
        }
        if (value.contains("outlook") || value.contains("microsoft")
                || value.contains("hotmail")) {
            return TrackingEvent.Source.MICROSOFT_IMAGE_PROXY;
        }
        if (value.contains("proofpoint") || value.contains("mimecast")
                || value.contains("barracuda") || value.contains("scanner")
                || value.contains("urlscan") || value.contains("safelinks")) {
            return TrackingEvent.Source.SECURITY_SCANNER;
        }
        if (value.contains("mozilla") || value.contains("chrome")
                || value.contains("safari") || value.contains("firefox")
                || value.contains("edg/") || value.contains("curl")
                || value.contains("wget")) {
            return TrackingEvent.Source.BROWSER;
        }
        return TrackingEvent.Source.UNKNOWN;
    }

    private String normalizeFormat(String imageFormat) {
        if (imageFormat == null || imageFormat.isBlank()) {
            return "png";
        }
        return truncate(imageFormat.toLowerCase(Locale.ROOT), IMAGE_FORMAT_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record TrackedBody(String trackingId, String htmlBody) {
    }

    public record TrackingRequestMetadata(String userAgent, String imageFormat) {

        public static TrackingRequestMetadata empty() {
            return new TrackingRequestMetadata(null, null);
        }
    }
}

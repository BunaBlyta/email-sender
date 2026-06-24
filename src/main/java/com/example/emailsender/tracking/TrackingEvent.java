package com.example.emailsender.tracking;

import com.example.emailsender.send.SentMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(indexes = {
        @Index(name = "idx_tracking_event_message_loaded_at", columnList = "sent_message_id, loaded_at"),
        @Index(name = "idx_tracking_event_tracking_id", columnList = "tracking_id")
})
public class TrackingEvent {

    public enum Source {
        GOOGLE_IMAGE_PROXY,
        APPLE_MAIL_PRIVACY_PROXY,
        MICROSOFT_IMAGE_PROXY,
        SECURITY_SCANNER,
        BROWSER,
        UNKNOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sent_message_id", nullable = false)
    private SentMessage sentMessage;

    @Column(name = "tracking_id", nullable = false)
    private String trackingId;

    @Column(name = "loaded_at", nullable = false)
    private LocalDateTime loadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Source source = Source.UNKNOWN;

    @Column(name = "image_format", length = 16)
    private String imageFormat;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SentMessage getSentMessage() { return sentMessage; }
    public void setSentMessage(SentMessage sentMessage) { this.sentMessage = sentMessage; }

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }

    public LocalDateTime getLoadedAt() { return loadedAt; }
    public void setLoadedAt(LocalDateTime loadedAt) { this.loadedAt = loadedAt; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public String getImageFormat() { return imageFormat; }
    public void setImageFormat(String imageFormat) { this.imageFormat = imageFormat; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}

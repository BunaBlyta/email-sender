package com.example.emailsender.send;

import com.example.emailsender.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class SentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String externalMessageId;
    private String externalThreadId;
    private String recipient;
    private String subject;

    @Lob
    private String body;

    private LocalDateTime sentAt;
    private boolean scheduled;
    private String attachmentFilename;
    private String attachmentMimeType;
    private Long attachmentSizeBytes;
    private Boolean trackingEnabled = false;

    @Column(unique = true)
    private String trackingId;

    private Integer pixelLoadCount = 0;
    private LocalDateTime firstPixelLoadedAt;
    private LocalDateTime lastPixelLoadedAt;

    public SentMessage() {}

    public SentMessage(String recipient, String subject, String body, LocalDateTime sentAt, boolean scheduled) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.sentAt = sentAt;
        this.scheduled = scheduled;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getExternalMessageId() { return externalMessageId; }
    public void setExternalMessageId(String externalMessageId) { this.externalMessageId = externalMessageId; }

    public String getExternalThreadId() { return externalThreadId; }
    public void setExternalThreadId(String externalThreadId) { this.externalThreadId = externalThreadId; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public boolean isScheduled() { return scheduled; }
    public void setScheduled(boolean scheduled) { this.scheduled = scheduled; }

    public String getAttachmentFilename() { return attachmentFilename; }
    public void setAttachmentFilename(String attachmentFilename) { this.attachmentFilename = attachmentFilename; }

    public String getAttachmentMimeType() { return attachmentMimeType; }
    public void setAttachmentMimeType(String attachmentMimeType) { this.attachmentMimeType = attachmentMimeType; }

    public Long getAttachmentSizeBytes() { return attachmentSizeBytes; }
    public void setAttachmentSizeBytes(Long attachmentSizeBytes) { this.attachmentSizeBytes = attachmentSizeBytes; }

    public boolean isTrackingEnabled() { return Boolean.TRUE.equals(trackingEnabled); }
    public void setTrackingEnabled(boolean trackingEnabled) { this.trackingEnabled = trackingEnabled; }

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }

    public int getPixelLoadCount() { return pixelLoadCount == null ? 0 : pixelLoadCount; }
    public void setPixelLoadCount(int pixelLoadCount) { this.pixelLoadCount = pixelLoadCount; }

    public LocalDateTime getFirstPixelLoadedAt() { return firstPixelLoadedAt; }
    public void setFirstPixelLoadedAt(LocalDateTime firstPixelLoadedAt) { this.firstPixelLoadedAt = firstPixelLoadedAt; }

    public LocalDateTime getLastPixelLoadedAt() { return lastPixelLoadedAt; }
    public void setLastPixelLoadedAt(LocalDateTime lastPixelLoadedAt) { this.lastPixelLoadedAt = lastPixelLoadedAt; }
}

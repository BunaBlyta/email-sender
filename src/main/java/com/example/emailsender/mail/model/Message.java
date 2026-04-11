package com.example.emailsender.mail.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
public class Message {

    public enum Direction { INBOUND, OUTBOUND }
    public enum Status { DRAFT, SCHEDULED, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private MailThread thread;

    private String externalMessageId;
    private String sender;

    @ElementCollection
    private List<String> recipients;

    @Lob
    private String body;

    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime scheduledFor;
    private boolean isRead;
    private String trackingId;

    public Message() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MailThread getThread() { return thread; }
    public void setThread(MailThread thread) { this.thread = thread; }

    public String getExternalMessageId() { return externalMessageId; }
    public void setExternalMessageId(String externalMessageId) { this.externalMessageId = externalMessageId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public List<String> getRecipients() { return recipients; }
    public void setRecipients(List<String> recipients) { this.recipients = recipients; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(LocalDateTime scheduledFor) { this.scheduledFor = scheduledFor; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
}

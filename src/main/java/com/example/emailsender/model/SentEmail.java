package com.example.emailsender.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class SentEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipient;
    private String subject;

    @Lob
    private String body;

    private LocalDateTime sentAt;

    private boolean scheduled;

    public SentEmail() {}

    public SentEmail(String recipient, String subject, String body, LocalDateTime sentAt, boolean scheduled) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.sentAt = sentAt;
        this.scheduled = scheduled;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public LocalDateTime getSentAt() { return sentAt; }
    public boolean isScheduled() { return scheduled; }

    public void setId(Long id) { this.id = id; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setBody(String body) { this.body = body; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public void setScheduled(boolean scheduled) { this.scheduled = scheduled; }
}

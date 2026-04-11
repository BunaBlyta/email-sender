package com.example.emailsender.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ScheduledEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipient;
    private String subject;

    @Lob
    private String body;

    private LocalDateTime scheduledTime;

    private boolean sent = false;

    public ScheduledEmail() {}

    public ScheduledEmail(String recipient, String subject, String body, LocalDateTime scheduledTime) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.scheduledTime = scheduledTime;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public boolean isSent() { return sent; }

    public void setId(Long id) { this.id = id; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setBody(String body) { this.body = body; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
    public void setSent(boolean sent) { this.sent = sent; }
}

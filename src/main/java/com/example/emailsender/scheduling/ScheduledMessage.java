package com.example.emailsender.scheduling;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ScheduledMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipient;
    private String subject;

    @Lob
    private String body;

    private LocalDateTime scheduledTime;
    private boolean sent = false;

    public ScheduledMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
}

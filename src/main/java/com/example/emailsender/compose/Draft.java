package com.example.emailsender.compose;

import com.example.emailsender.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String recipient;
    private String subject;

    @Column(length = 5000)
    private String body;

    private LocalDateTime scheduledTime;

    @ManyToOne
    private User user;

    public Draft() {}

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

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}

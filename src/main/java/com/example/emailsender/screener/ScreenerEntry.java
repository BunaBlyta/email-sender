package com.example.emailsender.screener;

import com.example.emailsender.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ScreenerEntry {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    private String senderEmail;
    private String senderDomain;
    private LocalDateTime firstContactAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime decidedAt;

    public ScreenerEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public String getSenderDomain() { return senderDomain; }
    public void setSenderDomain(String senderDomain) { this.senderDomain = senderDomain; }

    public LocalDateTime getFirstContactAt() { return firstContactAt; }
    public void setFirstContactAt(LocalDateTime firstContactAt) { this.firstContactAt = firstContactAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}

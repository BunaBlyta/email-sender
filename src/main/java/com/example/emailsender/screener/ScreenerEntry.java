package com.example.emailsender.screener;

import com.example.emailsender.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "screener_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "sender_email"})
)
public class ScreenerEntry {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "sender_email", nullable = false)
    private String senderEmail;

    @Column(nullable = false)
    private String senderDomain;

    @Column(nullable = false)
    private LocalDateTime firstContactAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
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

package com.example.emailsender.security;

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
        name = "sender_trust_entries",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "scope", "trusted_value"}
        )
)
public class SenderTrustEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrustScope scope;

    @Column(name = "trusted_value", nullable = false)
    private String trustedValue;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public SenderTrustEntry() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TrustScope getScope() {
        return scope;
    }

    public void setScope(TrustScope scope) {
        this.scope = scope;
    }

    public String getTrustedValue() {
        return trustedValue;
    }

    public void setTrustedValue(String trustedValue) {
        this.trustedValue = trustedValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

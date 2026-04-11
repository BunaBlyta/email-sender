package com.example.emailsender.mail.model;

import com.example.emailsender.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
public class MailThread {

    public enum ScreenerStatus { APPROVED, REJECTED, PENDING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    private String externalThreadId;
    private String subject;

    @ElementCollection
    private List<String> participants;

    private LocalDateTime lastMessageAt;
    private boolean hasUnread;
    private LocalDateTime followUpAt;

    @Enumerated(EnumType.STRING)
    private ScreenerStatus screenerStatus;

    public MailThread() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getExternalThreadId() { return externalThreadId; }
    public void setExternalThreadId(String externalThreadId) { this.externalThreadId = externalThreadId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public boolean isHasUnread() { return hasUnread; }
    public void setHasUnread(boolean hasUnread) { this.hasUnread = hasUnread; }

    public LocalDateTime getFollowUpAt() { return followUpAt; }
    public void setFollowUpAt(LocalDateTime followUpAt) { this.followUpAt = followUpAt; }

    public ScreenerStatus getScreenerStatus() { return screenerStatus; }
    public void setScreenerStatus(ScreenerStatus screenerStatus) { this.screenerStatus = screenerStatus; }
}

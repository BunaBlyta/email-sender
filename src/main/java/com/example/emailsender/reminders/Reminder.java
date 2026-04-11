package com.example.emailsender.reminders;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Reminder {

    public enum ReminderType { NO_REPLY, CUSTOM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private MailThread thread;

    private LocalDateTime remindAt;
    private boolean triggered;

    @Enumerated(EnumType.STRING)
    private ReminderType type;

    public Reminder() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public MailThread getThread() { return thread; }
    public void setThread(MailThread thread) { this.thread = thread; }

    public LocalDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(LocalDateTime remindAt) { this.remindAt = remindAt; }

    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }

    public ReminderType getType() { return type; }
    public void setType(ReminderType type) { this.type = type; }
}

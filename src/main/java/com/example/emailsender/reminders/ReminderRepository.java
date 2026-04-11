package com.example.emailsender.reminders;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    // TODO: Find untriggered reminders with remindAt before a given time, find by user
}

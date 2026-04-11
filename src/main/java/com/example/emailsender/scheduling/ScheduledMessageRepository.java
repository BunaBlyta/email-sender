package com.example.emailsender.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledMessageRepository extends JpaRepository<ScheduledMessage, Long> {
    // TODO: Find unsent messages with scheduledTime before a given LocalDateTime
}

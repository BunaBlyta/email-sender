package com.example.emailsender.send;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SentMessageRepository extends JpaRepository<SentMessage, Long> {
    // TODO: Find by recipient, by date range, by scheduled flag
}

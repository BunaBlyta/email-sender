package com.example.emailsender.screener;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenerRepository extends JpaRepository<ScreenerEntry, Long> {
    // TODO: Find by user and senderEmail, find pending entries by user
}

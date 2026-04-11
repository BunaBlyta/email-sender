package com.example.emailsender.repository;

import com.example.emailsender.model.ScheduledEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledEmailRepository extends JpaRepository<ScheduledEmail, Long> {
}

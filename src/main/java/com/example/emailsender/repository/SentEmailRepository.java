package com.example.emailsender.repository;

import com.example.emailsender.model.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
}

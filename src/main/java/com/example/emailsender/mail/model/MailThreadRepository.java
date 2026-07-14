package com.example.emailsender.mail.model;

import com.example.emailsender.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailThreadRepository extends JpaRepository<MailThread, Long> {

    Optional<MailThread> findFirstByUserAndExternalThreadIdOrderByIdAsc(
            User user,
            String externalThreadId);
}

package com.example.emailsender.send;

import com.example.emailsender.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SentMessageRepository extends JpaRepository<SentMessage, Long> {

    Optional<SentMessage> findByTrackingId(String trackingId);

    Optional<SentMessage> findByIdAndUser(Long id, User user);
}

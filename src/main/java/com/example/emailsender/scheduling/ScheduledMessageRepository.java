package com.example.emailsender.scheduling;

import com.example.emailsender.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledMessageRepository extends JpaRepository<ScheduledMessage, Long> {

    List<ScheduledMessage> findByUserOrderByScheduledTimeDesc(User user);

    Optional<ScheduledMessage> findByIdAndUser(Long id, User user);

    List<ScheduledMessage> findTop50ByStatusAndScheduledTimeLessThanEqualOrderByScheduledTimeAsc(
            ScheduledMessage.Status status,
            LocalDateTime scheduledTime);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ScheduledMessage message
            set message.status = :processing
            where message.id = :id and message.status = :pending
            """)
    int claimForSending(
            @Param("id") Long id,
            @Param("pending") ScheduledMessage.Status pending,
            @Param("processing") ScheduledMessage.Status processing);
}

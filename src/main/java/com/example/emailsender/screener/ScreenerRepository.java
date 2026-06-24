package com.example.emailsender.screener;

import com.example.emailsender.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScreenerRepository extends JpaRepository<ScreenerEntry, Long> {

    List<ScreenerEntry> findByUserAndStatusOrderByFirstContactAtDesc(
            User user,
            ScreenerEntry.Status status
    );

    Optional<ScreenerEntry> findByIdAndUser(Long id, User user);

    Optional<ScreenerEntry> findByUserAndSenderEmailIgnoreCase(User user, String senderEmail);
}

package com.example.emailsender.compose;

import com.example.emailsender.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, Long> {

    List<Draft> findByUserOrderByUpdatedAtDesc(User user);

    Optional<Draft> findByIdAndUser(Long id, User user);
}

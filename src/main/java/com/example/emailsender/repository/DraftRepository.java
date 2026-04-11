package com.example.emailsender.repository;

import com.example.emailsender.model.Draft;
import com.example.emailsender.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DraftRepository extends JpaRepository<Draft, Long> {
    List<Draft> findByUser(User user);
}

package com.example.emailsender.compose;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftRepository extends JpaRepository<Draft, Long> {
    // TODO: Find drafts by user
}

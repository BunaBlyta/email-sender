package com.example.emailsender.recipients;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientGroupRepository extends JpaRepository<RecipientGroup, Long> {
    // TODO: Find groups by user, search by name
}

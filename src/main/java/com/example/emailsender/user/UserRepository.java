package com.example.emailsender.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // TODO: Find by email, find by provider
}

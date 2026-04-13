package com.example.emailsender.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, Long> {

    List<ConnectedAccount> findByUser(User user);
}

package com.example.emailsender.security;

import com.example.emailsender.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SenderTrustRepository extends JpaRepository<SenderTrustEntry, Long> {

    List<SenderTrustEntry> findByUserOrderByCreatedAtDesc(User user);

    Optional<SenderTrustEntry> findByIdAndUser(Long id, User user);

    Optional<SenderTrustEntry> findByUserAndScopeAndTrustedValueIgnoreCase(
            User user,
            TrustScope scope,
            String trustedValue
    );

    boolean existsByUserAndScopeAndTrustedValueIgnoreCase(
            User user,
            TrustScope scope,
            String trustedValue
    );
}

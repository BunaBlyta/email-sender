package com.example.emailsender.recipients;

import com.example.emailsender.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipientGroupRepository extends JpaRepository<RecipientGroup, Long> {

    List<RecipientGroup> findByUserOrderByNameAsc(User user);

    Optional<RecipientGroup> findByIdAndUser(Long id, User user);

    boolean existsByUserAndNameIgnoreCase(User user, String name);

    boolean existsByUserAndNameIgnoreCaseAndIdNot(User user, String name, Long id);
}

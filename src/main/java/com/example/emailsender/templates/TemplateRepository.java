package com.example.emailsender.templates;

import com.example.emailsender.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    List<Template> findByUserOrderByCreatedAtDesc(User user);

    Optional<Template> findByIdAndUser(Long id, User user);

    boolean existsByUserAndNameIgnoreCase(User user, String name);

    boolean existsByUserAndNameIgnoreCaseAndIdNot(User user, String name, Long id);
}

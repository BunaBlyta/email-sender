package com.example.emailsender.templates;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, Long> {
    // TODO: Find by user, by category, search by name or subject
}

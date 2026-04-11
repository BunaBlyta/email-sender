package com.example.emailsender.repository;

import com.example.emailsender.model.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {
    // Standard CRUD repository
}

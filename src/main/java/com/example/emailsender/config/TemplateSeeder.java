package com.example.emailsender.config;

import com.example.emailsender.model.Template;
import com.example.emailsender.repository.TemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemplateSeeder {

    @Bean
    public CommandLineRunner seedTemplates(TemplateRepository repo) {
        return args -> {

            // Do not duplicate on every restart
            if (repo.count() > 0) {
                return;
            }

            repo.save(new Template(
                    "Welcome Email",
                    "Hello and welcome! We are happy to have you here."
            ));

            repo.save(new Template(
                    "Password Reset",
                    "Click the link below to reset your password."
            ));

            repo.save(new Template(
                    "Thank You",
                    "Thank you for contacting us. We will reply shortly."
            ));
        };
    }
}

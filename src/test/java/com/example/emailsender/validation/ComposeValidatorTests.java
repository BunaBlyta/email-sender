package com.example.emailsender.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeValidatorTests {

    private final ComposeValidator validator = new ComposeValidator();

    @Test
    void acceptsValidMessage() {
        ValidationResult result = validator.validate(
                List.of("recipient@example.com"),
                "Project update",
                "The project is ready for review."
        );

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void reportsAllBasicValidationErrors() {
        ValidationResult result = validator.validate(
                List.of("invalid-address", "duplicate@example.com", "duplicate@example.com"),
                " ",
                null
        );

        assertFalse(result.isValid());
        assertEquals(4, result.getErrors().size());
        assertTrue(result.getErrors().contains("Invalid recipient email: invalid-address"));
        assertTrue(result.getErrors().contains("Duplicate recipient: duplicate@example.com"));
        assertTrue(result.getErrors().contains("Subject is required"));
        assertTrue(result.getErrors().contains("Body is required"));
    }

    @Test
    void requiresAtLeastOneRecipient() {
        ValidationResult result = validator.validate(
                List.of(),
                "Project update",
                "Body"
        );

        assertFalse(result.isValid());
        assertEquals(List.of("At least one recipient is required"), result.getErrors());
    }
}

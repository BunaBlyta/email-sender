package com.example.emailsender.send;

import java.util.List;

public class ComposeValidationException extends RuntimeException {

    private final List<String> errors;

    public ComposeValidationException(List<String> errors) {
        super("Message validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}

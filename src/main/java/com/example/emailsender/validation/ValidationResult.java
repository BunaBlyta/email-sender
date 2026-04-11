package com.example.emailsender.validation;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    // TODO: Carry validation success/failure state and error messages back to the caller

    private boolean valid;
    private final List<String> errors = new ArrayList<>();

    public ValidationResult(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }

    public void addError(String error) {
        errors.add(error);
        valid = false;
    }
}

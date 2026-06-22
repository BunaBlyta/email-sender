package com.example.emailsender.validation;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ComposeValidator {

    private static final int MAX_RECIPIENTS = 50;
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_BODY_LENGTH = 100_000;

    public ValidationResult validate(List<String> recipients, String subject, String body) {
        ValidationResult result = new ValidationResult(true);

        validateRecipients(recipients, result);

        if (subject == null || subject.isBlank()) {
            result.addError("Subject is required");
        } else if (subject.length() > MAX_SUBJECT_LENGTH) {
            result.addError("Subject must not exceed 255 characters");
        }

        if (body == null || body.isBlank()) {
            result.addError("Body is required");
        } else if (body.length() > MAX_BODY_LENGTH) {
            result.addError("Body must not exceed 100000 characters");
        }

        return result;
    }

    private void validateRecipients(List<String> recipients, ValidationResult result) {
        if (recipients == null || recipients.isEmpty()) {
            result.addError("At least one recipient is required");
            return;
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            result.addError("A message cannot have more than 50 recipients");
        }

        Set<String> uniqueRecipients = new HashSet<>();
        for (String recipient : recipients) {
            if (!isValidEmail(recipient)) {
                result.addError("Invalid recipient email: " + recipient);
                continue;
            }
            if (!uniqueRecipients.add(recipient.toLowerCase(Locale.ROOT))) {
                result.addError("Duplicate recipient: " + recipient);
            }
        }
    }

    private boolean isValidEmail(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return value.equals(address.getAddress()) && value.contains("@");
        } catch (AddressException exception) {
            return false;
        }
    }
}

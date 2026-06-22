package com.example.emailsender.send;

import com.example.emailsender.recipients.RecipientService;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import com.example.emailsender.validation.ComposeValidator;
import com.example.emailsender.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BulkSendService {

    private static final int MAX_BULK_RECIPIENTS = 100;

    private final UserRepository userRepository;
    private final RecipientService recipientService;
    private final ComposeValidator composeValidator;
    private final SendService sendService;

    public BulkSendService(
            UserRepository userRepository,
            RecipientService recipientService,
            ComposeValidator composeValidator,
            SendService sendService) {
        this.userRepository = userRepository;
        this.recipientService = recipientService;
        this.composeValidator = composeValidator;
        this.sendService = sendService;
    }

    public BulkSendResponse send(String email, BulkSendRequest request) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!request.confirmed()) {
            throw new IllegalArgumentException("Bulk send must be explicitly confirmed");
        }

        String subject = normalize(request.subject());
        String body = normalize(request.body());
        List<String> recipients =
                recipientService.resolveMembers(email, request.recipientGroupIds());

        if (recipients.size() > MAX_BULK_RECIPIENTS) {
            throw new IllegalArgumentException(
                    "A bulk send cannot have more than 100 recipients");
        }

        ValidationResult validation =
                composeValidator.validate(List.of(recipients.getFirst()), subject, body);
        if (!validation.isValid()) {
            throw new ComposeValidationException(validation.getErrors());
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        List<BulkRecipientResult> results = new ArrayList<>();

        for (String recipient : recipients) {
            results.add(sendToRecipient(user, recipient, subject, body));
        }

        int sentCount = (int) results.stream()
                .filter(result -> result.status() == BulkRecipientResult.Status.SENT)
                .count();
        return new BulkSendResponse(
                results.size(),
                sentCount,
                results.size() - sentCount,
                List.copyOf(results)
        );
    }

    private BulkRecipientResult sendToRecipient(
            User user, String recipient, String subject, String body) {
        try {
            SendResponse response =
                    sendService.sendBulkRecipient(user, recipient, subject, body);
            return new BulkRecipientResult(
                    recipient,
                    BulkRecipientResult.Status.SENT,
                    response.externalMessageId(),
                    response.externalThreadId(),
                    null
            );
        } catch (RuntimeException exception) {
            return new BulkRecipientResult(
                    recipient,
                    BulkRecipientResult.Status.FAILED,
                    null,
                    null,
                    errorMessage(exception)
            );
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String errorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Send failed";
        }
        return exception.getMessage();
    }
}

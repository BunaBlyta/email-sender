package com.example.emailsender.send;

import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.mail.provider.MailSendResult;
import com.example.emailsender.mail.provider.OutgoingAttachment;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import com.example.emailsender.validation.ComposeValidator;
import com.example.emailsender.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SendService {

    private final UserRepository userRepository;
    private final GmailProvider gmailProvider;
    private final ComposeValidator composeValidator;
    private final SentMessageRepository sentMessageRepository;
    private final AttachmentValidator attachmentValidator;
    private final Clock clock;

    public SendService(
            UserRepository userRepository,
            GmailProvider gmailProvider,
            ComposeValidator composeValidator,
            SentMessageRepository sentMessageRepository,
            AttachmentValidator attachmentValidator,
            Clock clock) {
        this.userRepository = userRepository;
        this.gmailProvider = gmailProvider;
        this.composeValidator = composeValidator;
        this.sentMessageRepository = sentMessageRepository;
        this.attachmentValidator = attachmentValidator;
        this.clock = clock;
    }

    public SendResponse send(String email, SendRequest request) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        List<String> recipients = normalizeRecipients(request.recipients());
        String subject = normalize(request.subject());
        String body = normalize(request.body());

        ValidationResult validation = composeValidator.validate(recipients, subject, body);
        if (!validation.isValid()) {
            throw new ComposeValidationException(validation.getErrors());
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        return sendForUser(user, recipients, subject, body, false, null);
    }

    public SendResponse sendWithAttachment(
            String email,
            SendRequest request,
            org.springframework.web.multipart.MultipartFile file) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        List<String> recipients = normalizeRecipients(request.recipients());
        String subject = normalize(request.subject());
        String body = normalize(request.body());
        ValidationResult validation = composeValidator.validate(recipients, subject, body);
        if (!validation.isValid()) {
            throw new ComposeValidationException(validation.getErrors());
        }

        OutgoingAttachment attachment = attachmentValidator.validate(file);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        return sendForUser(user, recipients, subject, body, false, attachment);
    }

    public SendResponse sendScheduled(
            User user, List<String> recipients, String subject, String body) {
        ValidationResult validation = composeValidator.validate(recipients, subject, body);
        if (!validation.isValid()) {
            throw new ComposeValidationException(validation.getErrors());
        }
        return sendForUser(user, recipients, subject, body, true, null);
    }

    public SendResponse sendBulkRecipient(
            User user, String recipient, String subject, String body) {
        List<String> recipients = List.of(recipient);
        ValidationResult validation = composeValidator.validate(recipients, subject, body);
        if (!validation.isValid()) {
            throw new ComposeValidationException(validation.getErrors());
        }
        return sendForUser(user, recipients, subject, body, false, null);
    }

    private SendResponse sendForUser(
            User user,
            List<String> recipients,
            String subject,
            String body,
            boolean scheduled,
            OutgoingAttachment attachment) {
        MailSendResult providerResult = attachment == null
                ? gmailProvider.sendMessage(user, recipients, subject, body)
                : gmailProvider.sendMessageWithAttachment(
                        user, recipients, subject, body, attachment);

        SentMessage sentMessage = new SentMessage();
        sentMessage.setUser(user);
        sentMessage.setExternalMessageId(providerResult.externalMessageId());
        sentMessage.setExternalThreadId(providerResult.externalThreadId());
        sentMessage.setRecipient(String.join(", ", recipients));
        sentMessage.setSubject(subject);
        sentMessage.setBody(body);
        sentMessage.setSentAt(LocalDateTime.now(clock));
        sentMessage.setScheduled(scheduled);
        if (attachment != null) {
            sentMessage.setAttachmentFilename(attachment.filename());
            sentMessage.setAttachmentMimeType(attachment.contentType());
            sentMessage.setAttachmentSizeBytes((long) attachment.content().length);
        }

        SentMessage saved = sentMessageRepository.save(sentMessage);
        return new SendResponse(
                saved.getId(),
                saved.getExternalMessageId(),
                saved.getExternalThreadId(),
                recipients,
                saved.getSubject(),
                saved.getSentAt(),
                saved.isScheduled(),
                attachmentResponse(saved)
        );
    }

    private AttachmentResponse attachmentResponse(SentMessage message) {
        if (message.getAttachmentFilename() == null) {
            return null;
        }
        return new AttachmentResponse(
                message.getAttachmentFilename(),
                message.getAttachmentMimeType(),
                message.getAttachmentSizeBytes()
        );
    }

    private List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null) {
            return null;
        }
        return recipients.stream()
                .map(this::normalize)
                .toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}

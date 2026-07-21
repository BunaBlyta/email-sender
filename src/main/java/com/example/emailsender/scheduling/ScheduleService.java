package com.example.emailsender.scheduling;

import com.example.emailsender.send.ComposeValidationException;
import com.example.emailsender.send.SendResponse;
import com.example.emailsender.send.SendService;
import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import com.example.emailsender.validation.ComposeValidator;
import com.example.emailsender.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@Service
public class ScheduleService {

    private static final String RECIPIENT_SEPARATOR = "\n";

    private final UserRepository userRepository;
    private final ScheduledMessageRepository scheduledMessageRepository;
    private final ComposeValidator composeValidator;
    private final SendService sendService;
    private final Clock clock;

    public ScheduleService(
            UserRepository userRepository,
            ScheduledMessageRepository scheduledMessageRepository,
            ComposeValidator composeValidator,
            SendService sendService,
            Clock clock) {
        this.userRepository = userRepository;
        this.scheduledMessageRepository = scheduledMessageRepository;
        this.composeValidator = composeValidator;
        this.sendService = sendService;
        this.clock = clock;
    }

    @Transactional
    public ScheduledMessageResponse create(String email, ScheduleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        User user = findUser(email);
        List<String> recipients = normalizeRecipients(request.recipients());
        String subject = normalize(request.subject());
        String body = normalize(request.body());
        ValidationResult validation = composeValidator.validate(recipients, subject, body);
        if (!validation.isValid()) {
            throw new ComposeValidationException(validation.getErrors());
        }
        if (request.scheduledFor() == null) {
            throw new IllegalArgumentException("scheduledFor is required");
        }

        Instant now = clock.instant();
        if (!request.scheduledFor().isAfter(now)) {
            throw new IllegalArgumentException("scheduledFor must be in the future");
        }

        ScheduledMessage message = new ScheduledMessage();
        message.setUser(user);
        message.setRecipient(String.join(RECIPIENT_SEPARATOR, recipients));
        message.setSubject(subject);
        message.setBody(body);
        message.setScheduledTime(toUtcLocalDateTime(request.scheduledFor()));
        message.setStatus(ScheduledMessage.Status.PENDING);
        message.setCreatedAt(LocalDateTime.now(clock));
        return toResponse(scheduledMessageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public List<ScheduledMessageResponse> list(String email) {
        User user = findUser(email);
        return scheduledMessageRepository.findByUserOrderByScheduledTimeDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScheduledMessageResponse cancel(String email, Long id) {
        User user = findUser(email);
        ScheduledMessage message = findOwnedMessage(user, id);
        if (message.getStatus() != ScheduledMessage.Status.PENDING) {
            throw new IllegalStateException("Only pending messages can be cancelled");
        }
        message.setStatus(ScheduledMessage.Status.CANCELLED);
        return toResponse(scheduledMessageRepository.save(message));
    }

    @Transactional
    public void delete(String email, Long id) {
        User user = findUser(email);
        ScheduledMessage message = findOwnedMessage(user, id);
        scheduledMessageRepository.delete(message);
    }

    @Transactional(readOnly = true)
    public List<Long> findDueMessageIds() {
        return scheduledMessageRepository
                .findTop50ByStatusAndScheduledTimeLessThanEqualOrderByScheduledTimeAsc(
                        ScheduledMessage.Status.PENDING,
                        LocalDateTime.now(clock))
                .stream()
                .map(ScheduledMessage::getId)
                .toList();
    }

    @Transactional
    public boolean claim(Long id) {
        return scheduledMessageRepository.claimForSending(
                id,
                ScheduledMessage.Status.PENDING,
                ScheduledMessage.Status.PROCESSING
        ) == 1;
    }

    @Transactional
    public void sendClaimed(Long id) {
        ScheduledMessage message = scheduledMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled message not found"));
        if (message.getStatus() != ScheduledMessage.Status.PROCESSING) {
            throw new IllegalStateException("Scheduled message has not been claimed");
        }

        try {
            SendResponse response = sendService.sendScheduled(
                    message.getUser(),
                    recipients(message),
                    message.getSubject(),
                    message.getBody()
            );
            message.setStatus(ScheduledMessage.Status.SENT);
            message.setSent(true);
            message.setSentAt(LocalDateTime.now(clock));
            message.setExternalMessageId(response.externalMessageId());
            message.setExternalThreadId(response.externalThreadId());
            message.setFailureReason(null);
        } catch (RuntimeException exception) {
            message.setStatus(ScheduledMessage.Status.FAILED);
            message.setFailureReason(truncate(exception.getMessage()));
        }
        scheduledMessageRepository.save(message);
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private ScheduledMessage findOwnedMessage(User user, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Scheduled message id is required");
        }
        return scheduledMessageRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled message not found"));
    }

    private List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null) {
            return null;
        }
        return recipients.stream().map(this::normalize).toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private List<String> recipients(ScheduledMessage message) {
        return Arrays.asList(message.getRecipient().split(RECIPIENT_SEPARATOR));
    }

    private ScheduledMessageResponse toResponse(ScheduledMessage message) {
        return new ScheduledMessageResponse(
                message.getId(),
                recipients(message),
                message.getSubject(),
                message.getBody(),
                toInstant(message.getScheduledTime()),
                message.getStatus(),
                toInstant(message.getCreatedAt()),
                toInstant(message.getSentAt()),
                message.getExternalMessageId(),
                message.getExternalThreadId(),
                message.getFailureReason()
        );
    }

    private LocalDateTime toUtcLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Scheduled send failed";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}

package com.example.emailsender.compose;

import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComposeService {

    private static final int MAX_RECIPIENTS = 50;
    private static final int MAX_RECIPIENT_LENGTH = 320;
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_BODY_LENGTH = 100_000;

    private final UserRepository userRepository;
    private final DraftRepository draftRepository;
    private final Clock clock;

    public ComposeService(
            UserRepository userRepository,
            DraftRepository draftRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.draftRepository = draftRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DraftResponse> list(String email) {
        User user = findUser(email);
        return draftRepository.findByUserOrderByUpdatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DraftResponse get(String email, Long id) {
        User user = findUser(email);
        return toResponse(findOwnedDraft(user, id));
    }

    @Transactional
    public DraftResponse create(String email, DraftRequest request) {
        User user = findUser(email);
        ValidatedDraft validated = validate(request);
        LocalDateTime now = LocalDateTime.now(clock);

        Draft draft = new Draft();
        draft.setUser(user);
        draft.setCreatedAt(now);
        apply(draft, validated, now);
        return toResponse(draftRepository.save(draft));
    }

    @Transactional
    public DraftResponse update(String email, Long id, DraftRequest request) {
        User user = findUser(email);
        Draft draft = findOwnedDraft(user, id);
        apply(draft, validate(request), LocalDateTime.now(clock));
        return toResponse(draftRepository.save(draft));
    }

    @Transactional
    public void delete(String email, Long id) {
        User user = findUser(email);
        draftRepository.delete(findOwnedDraft(user, id));
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Draft findOwnedDraft(User user, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Draft id is required");
        }
        return draftRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));
    }

    private ValidatedDraft validate(DraftRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        List<String> recipients = normalizeRecipients(request.recipients());
        String subject = normalizeOptional(request.subject());
        String body = normalizeOptional(request.body());

        if (subject != null && subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("Subject must not exceed 255 characters");
        }
        if (body != null && body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Body must not exceed 100000 characters");
        }
        return new ValidatedDraft(
                recipients,
                subject,
                body,
                toLocalDateTime(request.scheduledFor())
        );
    }

    private List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException(
                    "A draft cannot have more than 50 recipients");
        }

        Map<String, String> uniqueRecipients = new LinkedHashMap<>();
        for (String recipient : recipients) {
            String normalized = normalizeOptional(recipient);
            if (normalized == null) {
                continue;
            }
            if (normalized.length() > MAX_RECIPIENT_LENGTH) {
                throw new IllegalArgumentException(
                        "Recipient must not exceed 320 characters");
            }
            uniqueRecipients.putIfAbsent(
                    normalized.toLowerCase(java.util.Locale.ROOT),
                    normalized
            );
        }
        return List.copyOf(uniqueRecipients.values());
    }

    private void apply(
            Draft draft,
            ValidatedDraft validated,
            LocalDateTime updatedAt) {
        draft.setRecipient(String.join(", ", validated.recipients()));
        draft.setSubject(validated.subject());
        draft.setBody(validated.body());
        draft.setScheduledTime(validated.scheduledTime());
        draft.setUpdatedAt(updatedAt);
    }

    private DraftResponse toResponse(Draft draft) {
        return new DraftResponse(
                draft.getId(),
                parseRecipients(draft.getRecipient()),
                draft.getSubject(),
                draft.getBody(),
                toInstant(draft.getScheduledTime()),
                toInstant(draft.getCreatedAt()),
                toInstant(draft.getUpdatedAt())
        );
    }

    private List<String> parseRecipients(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(recipient -> !recipient.isBlank())
                .toList();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null
                ? null
                : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record ValidatedDraft(
            List<String> recipients,
            String subject,
            String body,
            LocalDateTime scheduledTime
    ) {
    }
}

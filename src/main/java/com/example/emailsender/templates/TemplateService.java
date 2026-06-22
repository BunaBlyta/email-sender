package com.example.emailsender.templates;

import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TemplateService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_BODY_LENGTH = 5000;
    private static final int MAX_CATEGORY_LENGTH = 100;

    private final UserRepository userRepository;
    private final TemplateRepository templateRepository;
    private final Clock clock;

    public TemplateService(
            UserRepository userRepository,
            TemplateRepository templateRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(String email) {
        User user = findUser(email);
        return templateRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(String email, Long id) {
        User user = findUser(email);
        return toResponse(findOwnedTemplate(user, id));
    }

    @Transactional
    public TemplateResponse create(String email, TemplateRequest request) {
        User user = findUser(email);
        ValidatedTemplate validated = validate(request);
        if (templateRepository.existsByUserAndNameIgnoreCase(user, validated.name())) {
            throw new IllegalArgumentException("A template with this name already exists");
        }

        Template template = new Template();
        template.setUser(user);
        apply(template, validated);
        template.setUsageCount(0);
        template.setCreatedAt(LocalDateTime.now(clock));
        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse update(String email, Long id, TemplateRequest request) {
        User user = findUser(email);
        Template template = findOwnedTemplate(user, id);
        ValidatedTemplate validated = validate(request);
        if (templateRepository.existsByUserAndNameIgnoreCaseAndIdNot(
                user, validated.name(), id)) {
            throw new IllegalArgumentException("A template with this name already exists");
        }

        apply(template, validated);
        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public void delete(String email, Long id) {
        User user = findUser(email);
        templateRepository.delete(findOwnedTemplate(user, id));
    }

    @Transactional
    public TemplateResponse use(String email, Long id) {
        User user = findUser(email);
        Template template = findOwnedTemplate(user, id);
        template.setUsageCount(template.getUsageCount() + 1);
        return toResponse(templateRepository.save(template));
    }

    private User findUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user has no email address");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Template findOwnedTemplate(User user, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Template id is required");
        }
        return templateRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
    }

    private ValidatedTemplate validate(TemplateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        String name = normalizeRequired(request.name(), "Name");
        String subject = normalizeRequired(request.subject(), "Subject");
        String body = normalizeRequired(request.body(), "Body");
        String category = normalizeOptional(request.category());

        validateLength(name, MAX_NAME_LENGTH, "Name");
        validateLength(subject, MAX_SUBJECT_LENGTH, "Subject");
        validateLength(body, MAX_BODY_LENGTH, "Body");
        if (category != null) {
            validateLength(category, MAX_CATEGORY_LENGTH, "Category");
        }
        return new ValidatedTemplate(name, subject, body, category);
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateLength(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters");
        }
    }

    private void apply(Template template, ValidatedTemplate validated) {
        template.setName(validated.name());
        template.setSubject(validated.subject());
        template.setBody(validated.body());
        template.setCategory(validated.category());
    }

    private TemplateResponse toResponse(Template template) {
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getSubject(),
                template.getBody(),
                template.getCategory(),
                template.getUsageCount(),
                template.getCreatedAt()
        );
    }

    private record ValidatedTemplate(
            String name,
            String subject,
            String body,
            String category
    ) {
    }
}

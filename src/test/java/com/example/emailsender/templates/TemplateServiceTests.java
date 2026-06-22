package com.example.emailsender.templates;

import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateServiceTests {

    private UserRepository userRepository;
    private TemplateRepository templateRepository;
    private TemplateService templateService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        templateRepository = mock(TemplateRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-22T14:00:00Z"),
                ZoneOffset.UTC
        );
        templateService = new TemplateService(userRepository, templateRepository, clock);

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void createsNormalizedUserOwnedTemplate() {
        when(templateRepository.existsByUserAndNameIgnoreCase(user, "Follow up"))
                .thenReturn(false);
        when(templateRepository.save(any(Template.class))).thenAnswer(invocation -> {
            Template template = invocation.getArgument(0);
            template.setId(5L);
            return template;
        });

        TemplateResponse response = templateService.create(
                "user@example.com",
                new TemplateRequest(
                        " Follow up ",
                        " Checking in ",
                        " Are you available this week? ",
                        " Work "
                )
        );

        assertEquals(5L, response.id());
        assertEquals("Follow up", response.name());
        assertEquals("Checking in", response.subject());
        assertEquals("Are you available this week?", response.body());
        assertEquals("Work", response.category());
        assertEquals(0, response.usageCount());
        assertEquals(LocalDateTime.of(2026, 6, 22, 14, 0), response.createdAt());
        verify(templateRepository).save(any(Template.class));
    }

    @Test
    void listsOnlyAuthenticatedUsersTemplates() {
        Template template = template(1L, "First");
        when(templateRepository.findByUserOrderByCreatedAtDesc(user))
                .thenReturn(List.of(template));

        List<TemplateResponse> responses = templateService.list("user@example.com");

        assertEquals(1, responses.size());
        assertEquals("First", responses.getFirst().name());
        verify(templateRepository).findByUserOrderByCreatedAtDesc(user);
    }

    @Test
    void incrementsUsageCountWhenTemplateIsApplied() {
        Template template = template(8L, "Reply");
        template.setUsageCount(2);
        when(templateRepository.findByIdAndUser(8L, user))
                .thenReturn(Optional.of(template));
        when(templateRepository.save(template)).thenReturn(template);

        TemplateResponse response = templateService.use("user@example.com", 8L);

        assertEquals(3, response.usageCount());
        verify(templateRepository).save(template);
    }

    @Test
    void rejectsDuplicateTemplateName() {
        when(templateRepository.existsByUserAndNameIgnoreCase(user, "Follow up"))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> templateService.create(
                        "user@example.com",
                        new TemplateRequest("Follow up", "Subject", "Body", null)
                )
        );

        assertEquals("A template with this name already exists", exception.getMessage());
        verify(templateRepository, never()).save(any());
    }

    @Test
    void doesNotExposeAnotherUsersTemplate() {
        when(templateRepository.findByIdAndUser(99L, user))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> templateService.get("user@example.com", 99L)
        );
    }

    private Template template(Long id, String name) {
        Template template = new Template();
        template.setId(id);
        template.setUser(user);
        template.setName(name);
        template.setSubject("Subject");
        template.setBody("Body");
        template.setCreatedAt(LocalDateTime.of(2026, 6, 22, 14, 0));
        return template;
    }
}

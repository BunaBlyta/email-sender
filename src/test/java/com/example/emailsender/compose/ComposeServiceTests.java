package com.example.emailsender.compose;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComposeServiceTests {

    private UserRepository userRepository;
    private DraftRepository draftRepository;
    private ComposeService composeService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        draftRepository = mock(DraftRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-25T14:00:00Z"),
                ZoneOffset.UTC
        );
        composeService = new ComposeService(userRepository, draftRepository, clock);

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void createsPermissiveDraftForAutosave() {
        when(draftRepository.save(org.mockito.ArgumentMatchers.any(Draft.class)))
                .thenAnswer(invocation -> {
                    Draft draft = invocation.getArgument(0);
                    draft.setId(12L);
                    return draft;
                });

        DraftResponse response = composeService.create(
                "user@example.com",
                new DraftRequest(
                        List.of("ercan@", " ercan@ "),
                        " Thesis question ",
                        " Draft body ",
                        Instant.parse("2026-06-26T10:00:00Z")
                )
        );

        assertEquals(12L, response.id());
        assertEquals(List.of("ercan@"), response.recipients());
        assertEquals("Thesis question", response.subject());
        assertEquals("Draft body", response.body());
        assertEquals(Instant.parse("2026-06-26T10:00:00Z"), response.scheduledFor());
        assertEquals(Instant.parse("2026-06-25T14:00:00Z"), response.createdAt());
        assertEquals(Instant.parse("2026-06-25T14:00:00Z"), response.updatedAt());
    }

    @Test
    void listsDraftsForUser() {
        Draft draft = draft(7L);
        draft.setRecipient("one@example.com, two@example.com");
        when(draftRepository.findByUserOrderByUpdatedAtDesc(user))
                .thenReturn(List.of(draft));

        List<DraftResponse> responses = composeService.list("user@example.com");

        assertEquals(1, responses.size());
        assertEquals(7L, responses.getFirst().id());
        assertEquals(List.of("one@example.com", "two@example.com"),
                responses.getFirst().recipients());
    }

    @Test
    void updatesOwnedDraft() {
        Draft draft = draft(15L);
        when(draftRepository.findByIdAndUser(15L, user)).thenReturn(Optional.of(draft));
        when(draftRepository.save(draft)).thenReturn(draft);

        DraftResponse response = composeService.update(
                "user@example.com",
                15L,
                new DraftRequest(
                        List.of("new@example.com"),
                        "Updated",
                        "Updated body",
                        null
                )
        );

        assertEquals(15L, response.id());
        assertEquals(List.of("new@example.com"), response.recipients());
        assertEquals("Updated", response.subject());
        assertEquals("Updated body", response.body());
        assertEquals(Instant.parse("2026-06-25T14:00:00Z"), response.updatedAt());
    }

    @Test
    void deletesOwnedDraft() {
        Draft draft = draft(20L);
        when(draftRepository.findByIdAndUser(20L, user)).thenReturn(Optional.of(draft));

        composeService.delete("user@example.com", 20L);

        verify(draftRepository).delete(draft);
    }

    @Test
    void rejectsBodyThatIsTooLong() {
        String body = "a".repeat(100_001);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> composeService.create(
                        "user@example.com",
                        new DraftRequest(List.of(), "", body, null)
                )
        );

        assertEquals("Body must not exceed 100000 characters", exception.getMessage());
        verifyNoInteractions(draftRepository);
    }

    @Test
    void rejectsUnknownDraft() {
        when(draftRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> composeService.get("user@example.com", 99L)
        );
    }

    private Draft draft(Long id) {
        Draft draft = new Draft();
        draft.setId(id);
        draft.setUser(user);
        draft.setSubject("Subject");
        draft.setBody("Body");
        draft.setCreatedAt(LocalDateTime.of(2026, 6, 25, 13, 0));
        draft.setUpdatedAt(LocalDateTime.of(2026, 6, 25, 13, 30));
        return draft;
    }
}

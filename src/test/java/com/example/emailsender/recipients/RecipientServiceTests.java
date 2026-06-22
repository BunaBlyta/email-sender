package com.example.emailsender.recipients;

import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipientServiceTests {

    private UserRepository userRepository;
    private RecipientGroupRepository recipientGroupRepository;
    private RecipientService recipientService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        recipientGroupRepository = mock(RecipientGroupRepository.class);
        recipientService = new RecipientService(userRepository, recipientGroupRepository);

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void createsNormalizedGroupAndRemovesDuplicateMembers() {
        when(recipientGroupRepository.existsByUserAndNameIgnoreCase(user, "Thesis group"))
                .thenReturn(false);
        when(recipientGroupRepository.save(any(RecipientGroup.class)))
                .thenAnswer(invocation -> {
                    RecipientGroup group = invocation.getArgument(0);
                    group.setId(3L);
                    return group;
                });

        RecipientGroupResponse response = recipientService.create(
                "user@example.com",
                new RecipientGroupRequest(
                        " Thesis group ",
                        List.of(
                                "first@example.com",
                                " First@example.com ",
                                "second@example.com"
                        )
                )
        );

        assertEquals(3L, response.id());
        assertEquals("Thesis group", response.name());
        assertEquals(
                List.of("first@example.com", "second@example.com"),
                response.members()
        );
        assertEquals(2, response.memberCount());
    }

    @Test
    void rejectsInvalidMemberBeforeSaving() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> recipientService.create(
                        "user@example.com",
                        new RecipientGroupRequest(
                                "Group",
                                List.of("not-an-email")
                        )
                )
        );

        assertEquals("Invalid member email: not-an-email", exception.getMessage());
        verify(recipientGroupRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateGroupNameForUser() {
        when(recipientGroupRepository.existsByUserAndNameIgnoreCase(user, "Team"))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> recipientService.create(
                        "user@example.com",
                        new RecipientGroupRequest("Team", List.of("one@example.com"))
                )
        );
    }

    @Test
    void listsOnlyAuthenticatedUsersGroups() {
        RecipientGroup group = group(4L, "Team", List.of("one@example.com"));
        when(recipientGroupRepository.findByUserOrderByNameAsc(user))
                .thenReturn(List.of(group));

        List<RecipientGroupResponse> responses =
                recipientService.list("user@example.com");

        assertEquals(1, responses.size());
        assertEquals("Team", responses.getFirst().name());
        verify(recipientGroupRepository).findByUserOrderByNameAsc(user);
    }

    @Test
    void doesNotExposeAnotherUsersGroup() {
        when(recipientGroupRepository.findByIdAndUser(99L, user))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> recipientService.get("user@example.com", 99L)
        );
    }

    private RecipientGroup group(Long id, String name, List<String> members) {
        RecipientGroup group = new RecipientGroup();
        group.setId(id);
        group.setUser(user);
        group.setName(name);
        group.setMembers(members);
        return group;
    }
}

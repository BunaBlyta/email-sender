package com.example.emailsender.send;

import com.example.emailsender.recipients.RecipientService;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import com.example.emailsender.validation.ComposeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkSendServiceTests {

    private UserRepository userRepository;
    private RecipientService recipientService;
    private SendService sendService;
    private BulkSendService bulkSendService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        recipientService = mock(RecipientService.class);
        sendService = mock(SendService.class);
        bulkSendService = new BulkSendService(
                userRepository,
                recipientService,
                new ComposeValidator(),
                sendService
        );

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void sendsSeparateMessageToEachResolvedRecipient() {
        when(recipientService.resolveMembers("user@example.com", List.of(1L, 2L)))
                .thenReturn(List.of("first@example.com", "second@example.com"));
        when(sendService.sendBulkRecipient(
                user, "first@example.com", "Update", "Body"))
                .thenReturn(response("first@example.com", "message-1", "thread-1"));
        when(sendService.sendBulkRecipient(
                user, "second@example.com", "Update", "Body"))
                .thenReturn(response("second@example.com", "message-2", "thread-2"));

        BulkSendResponse response = bulkSendService.send(
                "user@example.com",
                new BulkSendRequest(List.of(1L, 2L), " Update ", " Body ", true)
        );

        assertEquals(2, response.totalRecipients());
        assertEquals(2, response.sentCount());
        assertEquals(0, response.failedCount());
        assertEquals("message-1", response.results().getFirst().externalMessageId());
        verify(sendService).sendBulkRecipient(
                user, "first@example.com", "Update", "Body");
        verify(sendService).sendBulkRecipient(
                user, "second@example.com", "Update", "Body");
    }

    @Test
    void continuesAfterIndividualRecipientFailure() {
        when(recipientService.resolveMembers("user@example.com", List.of(1L)))
                .thenReturn(List.of("first@example.com", "second@example.com"));
        when(sendService.sendBulkRecipient(
                user, "first@example.com", "Update", "Body"))
                .thenThrow(new RuntimeException("Gmail rejected message"));
        when(sendService.sendBulkRecipient(
                user, "second@example.com", "Update", "Body"))
                .thenReturn(response("second@example.com", "message-2", "thread-2"));

        BulkSendResponse response = bulkSendService.send(
                "user@example.com",
                new BulkSendRequest(List.of(1L), "Update", "Body", true)
        );

        assertEquals(1, response.sentCount());
        assertEquals(1, response.failedCount());
        assertEquals(BulkRecipientResult.Status.FAILED,
                response.results().getFirst().status());
        assertEquals("Gmail rejected message", response.results().getFirst().error());
    }

    @Test
    void requiresExplicitConfirmationBeforeResolvingRecipients() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bulkSendService.send(
                        "user@example.com",
                        new BulkSendRequest(List.of(1L), "Update", "Body", false)
                )
        );

        assertEquals("Bulk send must be explicitly confirmed", exception.getMessage());
        verify(recipientService, never()).resolveMembers(
                "user@example.com", List.of(1L));
    }

    @Test
    void enforcesBulkRecipientLimit() {
        List<String> recipients = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> "user" + index + "@example.com")
                .toList();
        when(recipientService.resolveMembers("user@example.com", List.of(1L)))
                .thenReturn(recipients);

        assertThrows(
                IllegalArgumentException.class,
                () -> bulkSendService.send(
                        "user@example.com",
                        new BulkSendRequest(List.of(1L), "Update", "Body", true)
                )
        );
        verify(sendService, never()).sendBulkRecipient(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private SendResponse response(
            String recipient, String messageId, String threadId) {
        return new SendResponse(
                1L,
                messageId,
                threadId,
                List.of(recipient),
                "Update",
                LocalDateTime.of(2026, 6, 22, 15, 0),
                false,
                null
        );
    }
}

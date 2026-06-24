package com.example.emailsender.scheduling;

import com.example.emailsender.send.SendResponse;
import com.example.emailsender.send.SendService;
import com.example.emailsender.shared.exception.ResourceNotFoundException;
import com.example.emailsender.user.User;
import com.example.emailsender.user.UserRepository;
import com.example.emailsender.validation.ComposeValidator;
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

class ScheduleServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-22T15:00:00Z");

    private UserRepository userRepository;
    private ScheduledMessageRepository scheduledMessageRepository;
    private SendService sendService;
    private ScheduleService scheduleService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        scheduledMessageRepository = mock(ScheduledMessageRepository.class);
        sendService = mock(SendService.class);
        scheduleService = new ScheduleService(
                userRepository,
                scheduledMessageRepository,
                new ComposeValidator(),
                sendService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void createsPendingMessageUsingUtcInstant() {
        when(scheduledMessageRepository.save(any(ScheduledMessage.class)))
                .thenAnswer(invocation -> {
                    ScheduledMessage message = invocation.getArgument(0);
                    message.setId(12L);
                    return message;
                });

        ScheduledMessageResponse response = scheduleService.create(
                "user@example.com",
                new ScheduleRequest(
                        List.of(" recipient@example.com "),
                        " Scheduled update ",
                        " This should arrive later. ",
                        NOW.plusSeconds(120)
                )
        );

        assertEquals(12L, response.id());
        assertEquals(List.of("recipient@example.com"), response.recipients());
        assertEquals(NOW.plusSeconds(120), response.scheduledFor());
        assertEquals(ScheduledMessage.Status.PENDING, response.status());
        assertEquals(NOW, response.createdAt());
    }

    @Test
    void rejectsPastScheduleTime() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduleService.create(
                        "user@example.com",
                        new ScheduleRequest(
                                List.of("recipient@example.com"),
                                "Subject",
                                "Body",
                                NOW.minusSeconds(1)
                        )
                )
        );

        assertEquals("scheduledFor must be in the future", exception.getMessage());
        verify(scheduledMessageRepository, never()).save(any());
    }

    @Test
    void cancelsOnlyOwnedPendingMessage() {
        ScheduledMessage message = message(7L, ScheduledMessage.Status.PENDING);
        when(scheduledMessageRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(message));
        when(scheduledMessageRepository.save(message)).thenReturn(message);

        ScheduledMessageResponse response = scheduleService.cancel("user@example.com", 7L);

        assertEquals(ScheduledMessage.Status.CANCELLED, response.status());
    }

    @Test
    void preventsCancellingSentMessage() {
        ScheduledMessage message = message(7L, ScheduledMessage.Status.SENT);
        when(scheduledMessageRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(message));

        assertThrows(
                IllegalStateException.class,
                () -> scheduleService.cancel("user@example.com", 7L)
        );
    }

    @Test
    void claimsPendingMessageAtomically() {
        when(scheduledMessageRepository.claimForSending(
                3L,
                ScheduledMessage.Status.PENDING,
                ScheduledMessage.Status.PROCESSING
        )).thenReturn(1);

        assertEquals(true, scheduleService.claim(3L));
    }

    @Test
    void marksClaimedMessageSentAfterSuccessfulDelivery() {
        ScheduledMessage message = message(9L, ScheduledMessage.Status.PROCESSING);
        when(scheduledMessageRepository.findById(9L)).thenReturn(Optional.of(message));
        when(sendService.sendScheduled(
                user,
                List.of("recipient@example.com"),
                "Subject",
                "Body"
        )).thenReturn(new SendResponse(
                4L,
                "gmail-message",
                "gmail-thread",
                List.of("recipient@example.com"),
                "Subject",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                true,
                null,
                null
        ));

        scheduleService.sendClaimed(9L);

        assertEquals(ScheduledMessage.Status.SENT, message.getStatus());
        assertEquals("gmail-message", message.getExternalMessageId());
        assertEquals("gmail-thread", message.getExternalThreadId());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), message.getSentAt());
        verify(scheduledMessageRepository).save(message);
    }

    @Test
    void refusesToSendMessageThatWasNotClaimed() {
        ScheduledMessage message = message(10L, ScheduledMessage.Status.CANCELLED);
        when(scheduledMessageRepository.findById(10L)).thenReturn(Optional.of(message));

        assertThrows(IllegalStateException.class, () -> scheduleService.sendClaimed(10L));
        verify(sendService, never()).sendScheduled(any(), any(), any(), any());
    }

    @Test
    void hidesAnotherUsersScheduledMessage() {
        when(scheduledMessageRepository.findByIdAndUser(99L, user))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> scheduleService.cancel("user@example.com", 99L)
        );
    }

    private ScheduledMessage message(Long id, ScheduledMessage.Status status) {
        ScheduledMessage message = new ScheduledMessage();
        message.setId(id);
        message.setUser(user);
        message.setRecipient("recipient@example.com");
        message.setSubject("Subject");
        message.setBody("Body");
        message.setScheduledTime(LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));
        message.setCreatedAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        message.setStatus(status);
        return message;
    }
}

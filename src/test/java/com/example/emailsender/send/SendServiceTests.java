package com.example.emailsender.send;

import com.example.emailsender.mail.provider.GmailProvider;
import com.example.emailsender.mail.provider.MailSendResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendServiceTests {

    private UserRepository userRepository;
    private GmailProvider gmailProvider;
    private SentMessageRepository sentMessageRepository;
    private SendService sendService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        gmailProvider = mock(GmailProvider.class);
        sentMessageRepository = mock(SentMessageRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-22T13:00:00Z"),
                ZoneOffset.UTC
        );
        sendService = new SendService(
                userRepository,
                gmailProvider,
                new ComposeValidator(),
                sentMessageRepository,
                clock
        );
    }

    @Test
    void sendsThroughGmailAndPersistsAuditRecord() {
        User user = new User();
        user.setEmail("sender@example.com");
        when(userRepository.findByEmail("sender@example.com"))
                .thenReturn(Optional.of(user));
        when(gmailProvider.sendMessage(
                user,
                List.of("recipient@example.com"),
                "Project update",
                "Ready for review."
        )).thenReturn(new MailSendResult("message-123", "thread-456"));
        when(sentMessageRepository.save(any(SentMessage.class)))
                .thenAnswer(invocation -> {
                    SentMessage message = invocation.getArgument(0);
                    message.setId(10L);
                    return message;
                });

        SendResponse response = sendService.send(
                "sender@example.com",
                new SendRequest(
                        List.of(" recipient@example.com "),
                        " Project update ",
                        " Ready for review. "
                )
        );

        assertEquals(10L, response.id());
        assertEquals("message-123", response.externalMessageId());
        assertEquals("thread-456", response.externalThreadId());
        assertEquals(List.of("recipient@example.com"), response.recipients());
        assertEquals(LocalDateTime.of(2026, 6, 22, 13, 0), response.sentAt());
        assertFalse(response.scheduled());
        verify(gmailProvider).sendMessage(
                user,
                List.of("recipient@example.com"),
                "Project update",
                "Ready for review."
        );
        verify(sentMessageRepository).save(any(SentMessage.class));
    }

    @Test
    void doesNotCallProviderWhenValidationFails() {
        ComposeValidationException exception = assertThrows(
                ComposeValidationException.class,
                () -> sendService.send(
                        "sender@example.com",
                        new SendRequest(List.of("not-an-email"), "", "")
                )
        );

        assertEquals(3, exception.getErrors().size());
        verify(gmailProvider, never()).sendMessage(any(), any(), any(), any());
        verify(sentMessageRepository, never()).save(any());
    }
}

package com.example.emailsender.mail.provider;

import com.example.emailsender.auth.TokenStore;
import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.shared.exception.MailProviderException;
import com.example.emailsender.user.User;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ListThreadsResponse;
import com.google.api.services.gmail.model.Thread;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Base64;
import java.util.Properties;

@Service
public class GmailProvider implements MailProvider {

    private final TokenStore tokenStore;

    public GmailProvider(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public List<MailThread> fetchThreads(User user, int maxResults) {
        try {
            String accessToken = tokenStore.getValidAccessToken(user);
            Gmail gmail = new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
            ).setApplicationName("email-platform").build();

            ListThreadsResponse response = gmail.users().threads().list("me")
                    .setMaxResults((long) maxResults)
                    .execute();

            List<Thread> threads = response.getThreads();
            if (threads == null) {
                return Collections.emptyList();
            }

            List<MailThread> result = new ArrayList<>();
            for (Thread threadSummary : threads) {
                Thread thread = gmail.users().threads().get("me", threadSummary.getId())
                        .setFormat("metadata")
                        .setMetadataHeaders(List.of("Subject", "From", "To", "Cc"))
                        .execute();

                List<com.google.api.services.gmail.model.Message> messages = thread.getMessages();
                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                com.google.api.services.gmail.model.Message latestMessage =
                        messages.get(messages.size() - 1);

                MailThread mailThread = new MailThread();
                mailThread.setExternalThreadId(thread.getId());
                mailThread.setSubject(getHeader(latestMessage, "Subject"));
                mailThread.setParticipants(getParticipants(messages));
                mailThread.setLastMessageAt(toLocalDateTime(latestMessage.getInternalDate()));
                mailThread.setHasUnread(messages.stream()
                        .anyMatch(message -> message.getLabelIds() != null
                                && message.getLabelIds().contains("UNREAD")));
                mailThread.setWorkflowState(MailThread.WorkflowState.ACTIVE);
                result.add(mailThread);
            }
            return result;
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to fetch threads", e);
        }
    }

    @Override
    public List<Message> fetchMessages(User user, String threadId) {
        try {
            String accessToken = tokenStore.getValidAccessToken(user);
            Gmail gmail = new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
            ).setApplicationName("email-platform").build();

            Thread thread = gmail.users().threads().get("me", threadId).execute();

            List<com.google.api.services.gmail.model.Message> gmailMessages = thread.getMessages();
            if (gmailMessages == null) {
                return Collections.emptyList();
            }

            List<Message> result = new ArrayList<>();
            for (com.google.api.services.gmail.model.Message gmailMessage : gmailMessages) {
                Message msg = new Message();
                msg.setExternalMessageId(gmailMessage.getId());
                msg.setSentAt(LocalDateTime.now());
                msg.setDirection(Message.Direction.INBOUND);
                msg.setStatus(Message.Status.SENT);
                msg.setRead(false);
                result.add(msg);
            }
            return result;
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to fetch messages", e);
        }
    }

    @Override
    public MailSendResult sendMessage(
            User user, List<String> recipients, String subject, String body) {
        try {
            return sendMimeMessage(
                    user,
                    createMimeMessage(user, recipients, subject, body, null)
            );
        } catch (Exception exception) {
            throw new MailProviderException("Failed to send Gmail message", exception);
        }
    }

    @Override
    public MailSendResult sendMessageWithAttachment(
            User user,
            List<String> recipients,
            String subject,
            String body,
            OutgoingAttachment attachment) {
        try {
            return sendMimeMessage(
                    user,
                    createMimeMessage(user, recipients, subject, body, attachment)
            );
        } catch (Exception exception) {
            throw new MailProviderException(
                    "Failed to send Gmail message with attachment", exception);
        }
    }

    private MimeMessage createMimeMessage(
            User user,
            List<String> recipients,
            String subject,
            String body,
            OutgoingAttachment attachment) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(user.getEmail()));
        for (String recipient : recipients) {
            message.addRecipient(
                    jakarta.mail.Message.RecipientType.TO,
                    new InternetAddress(recipient)
            );
        }
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        if (attachment == null) {
            message.setText(body, StandardCharsets.UTF_8.name());
            return message;
        }

        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(body, StandardCharsets.UTF_8.name());

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new jakarta.activation.DataHandler(
                new ByteArrayDataSource(attachment.content(), attachment.contentType())
        ));
        attachmentPart.setFileName(MimeUtility.encodeText(
                attachment.filename(),
                StandardCharsets.UTF_8.name(),
                null
        ));

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(bodyPart);
        multipart.addBodyPart(attachmentPart);
        message.setContent(multipart);
        return message;
    }

    private MailSendResult sendMimeMessage(User user, MimeMessage mimeMessage)
            throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);

        com.google.api.services.gmail.model.Message gmailMessage =
                new com.google.api.services.gmail.model.Message();
        gmailMessage.setRaw(Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(outputStream.toByteArray()));

        com.google.api.services.gmail.model.Message sentMessage =
                buildGmailClient(user).users().messages()
                        .send("me", gmailMessage)
                        .execute();
        return new MailSendResult(sentMessage.getId(), sentMessage.getThreadId());
    }

    private Gmail buildGmailClient(User user)
            throws GeneralSecurityException, IOException {
        String accessToken = tokenStore.getValidAccessToken(user);
        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("email-platform").build();
    }

    private String getHeader(com.google.api.services.gmail.model.Message message, String name) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return "";
        }

        return message.getPayload().getHeaders().stream()
                .filter(header -> name.equalsIgnoreCase(header.getName()))
                .map(MessagePartHeader::getValue)
                .map(this::decodeHeader)
                .findFirst()
                .orElse("");
    }

    String decodeHeader(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            String decoded = MimeUtility.decodeText(value);
            return repairUtf8Mojibake(decoded);
        } catch (Exception exception) {
            return repairUtf8Mojibake(value);
        }
    }

    private String repairUtf8Mojibake(String value) {
        if (!looksLikeUtf8Mojibake(value)) {
            return value;
        }

        String repaired = new String(value.getBytes(Charset.forName("windows-1252")),
                StandardCharsets.UTF_8);
        return repaired.contains("\uFFFD") ? value : repaired;
    }

    private boolean looksLikeUtf8Mojibake(String value) {
        return value.contains("Ã")
                || value.contains("Â")
                || value.contains("ðŸ")
                || value.contains("â€");
    }

    private List<String> getParticipants(
            List<com.google.api.services.gmail.model.Message> messages) {
        Set<String> participants = new LinkedHashSet<>();
        for (com.google.api.services.gmail.model.Message message : messages) {
            addHeaderValues(participants, getHeader(message, "From"));
            addHeaderValues(participants, getHeader(message, "To"));
            addHeaderValues(participants, getHeader(message, "Cc"));
        }
        return new ArrayList<>(participants);
    }

    private void addHeaderValues(Set<String> participants, String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return;
        }

        Arrays.stream(headerValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(participants::add);
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return LocalDateTime.ofEpochSecond(
                epochMillis / 1000,
                (int) ((epochMillis % 1000) * 1_000_000),
                ZoneOffset.UTC
        );
    }
}

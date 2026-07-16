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
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.ModifyThreadRequest;
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
import java.util.regex.Pattern;

@Service
public class GmailProvider implements MailProvider {

    private final TokenStore tokenStore;

    public GmailProvider(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public List<MailThread> fetchThreads(User user, int maxResults) {
        return listThreads(user, null, maxResults, "Failed to fetch threads");
    }

    @Override
    public List<MailThread> searchThreads(User user, String query, int maxResults) {
        return listThreads(user, query, maxResults, "Failed to search threads");
    }

    private List<MailThread> listThreads(
            User user,
            String query,
            int maxResults,
            String failureMessage) {
        try {
            Gmail gmail = buildGmailClient(user);

            var listRequest = gmail.users().threads().list("me")
                    .setMaxResults((long) maxResults);
            if (query != null && !query.isBlank()) {
                listRequest.setQ(query);
            }
            ListThreadsResponse response = listRequest.execute();

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
            throw new RuntimeException(failureMessage, e);
        }
    }

    @Override
    public List<Message> fetchMessages(User user, String threadId) {
        return fetchThread(user, threadId).messages().stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public FetchedThread fetchThread(User user, String threadId) {
        try {
            Thread thread = buildGmailClient(user).users().threads()
                    .get("me", threadId)
                    .setFormat("full")
                    .execute();

            List<com.google.api.services.gmail.model.Message> gmailMessages = thread.getMessages();
            if (gmailMessages == null || gmailMessages.isEmpty()) {
                return new FetchedThread(threadId, "", List.of(), null, false, List.of());
            }

            List<FetchedMessage> messages = new ArrayList<>();
            for (com.google.api.services.gmail.model.Message gmailMessage : gmailMessages) {
                messages.add(toFetchedMessage(user, gmailMessage));
            }
            com.google.api.services.gmail.model.Message latestMessage =
                    gmailMessages.get(gmailMessages.size() - 1);
            return new FetchedThread(
                    thread.getId(),
                    getHeader(latestMessage, "Subject"),
                    getParticipants(gmailMessages),
                    toLocalDateTime(latestMessage.getInternalDate()),
                    gmailMessages.stream()
                            .anyMatch(message -> message.getLabelIds() != null
                                    && message.getLabelIds().contains("UNREAD")),
                    List.copyOf(messages)
            );
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to fetch thread", e);
        }
    }

    @Override
    public void markThreadRead(User user, String threadId) {
        modifyThreadLabels(
                user,
                threadId,
                List.of(),
                List.of("UNREAD"),
                "Failed to mark Gmail thread as read"
        );
    }

    @Override
    public void markThreadUnread(User user, String threadId) {
        modifyThreadLabels(
                user,
                threadId,
                List.of("UNREAD"),
                List.of(),
                "Failed to mark Gmail thread as unread"
        );
    }

    @Override
    public void archiveThread(User user, String threadId) {
        modifyThreadLabels(
                user,
                threadId,
                List.of(),
                List.of("INBOX"),
                "Failed to archive Gmail thread"
        );
    }

    private Message toMessage(FetchedMessage fetchedMessage) {
        Message message = new Message();
        message.setExternalMessageId(fetchedMessage.externalMessageId());
        message.setSender(fetchedMessage.sender());
        message.setRecipients(fetchedMessage.recipients());
        message.setBody(fetchedMessage.body());
        message.setSentAt(fetchedMessage.sentAt());
        message.setDirection(fetchedMessage.direction());
        message.setStatus(Message.Status.SENT);
        message.setRead(fetchedMessage.read());
        return message;
    }

    private FetchedMessage toFetchedMessage(
            User user,
            com.google.api.services.gmail.model.Message gmailMessage) {
        String sender = getHeader(gmailMessage, "From");
        List<String> recipients = recipients(gmailMessage);
        boolean read = gmailMessage.getLabelIds() == null
                || !gmailMessage.getLabelIds().contains("UNREAD");
        return new FetchedMessage(
                gmailMessage.getId(),
                sender,
                recipients,
                extractBody(gmailMessage.getPayload()),
                gmailMessage.getSnippet() == null ? "" : gmailMessage.getSnippet(),
                toLocalDateTime(gmailMessage.getInternalDate()),
                direction(sender, user.getEmail()),
                read,
                listUnsubscribeHeader(gmailMessage),
                extractAttachments(gmailMessage.getPayload())
        );
    }

    @Override
    public MailSendResult sendMessage(
            User user, List<String> recipients, String subject, String body) {
        try {
            return sendMimeMessage(
                    user,
                    createMimeMessage(user, recipients, subject, body, false, null)
            );
        } catch (Exception exception) {
            throw new MailProviderException("Failed to send Gmail message", exception);
        }
    }

    @Override
    public MailSendResult sendHtmlMessage(
            User user, List<String> recipients, String subject, String htmlBody) {
        try {
            return sendMimeMessage(
                    user,
                    createMimeMessage(user, recipients, subject, htmlBody, true, null)
            );
        } catch (Exception exception) {
            throw new MailProviderException("Failed to send tracked Gmail message", exception);
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
                    createMimeMessage(user, recipients, subject, body, false, attachment)
            );
        } catch (Exception exception) {
            throw new MailProviderException(
                    "Failed to send Gmail message with attachment", exception);
        }
    }

    @Override
    public MailSendResult sendHtmlMessageWithAttachment(
            User user,
            List<String> recipients,
            String subject,
            String htmlBody,
            OutgoingAttachment attachment) {
        try {
            return sendMimeMessage(
                    user,
                    createMimeMessage(user, recipients, subject, htmlBody, true, attachment)
            );
        } catch (Exception exception) {
            throw new MailProviderException(
                    "Failed to send tracked Gmail message with attachment", exception);
        }
    }

    private MimeMessage createMimeMessage(
            User user,
            List<String> recipients,
            String subject,
            String body,
            boolean html,
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
            if (html) {
                message.setContent(body, "text/html; charset=UTF-8");
            } else {
                message.setText(body, StandardCharsets.UTF_8.name());
            }
            return message;
        }

        MimeBodyPart bodyPart = new MimeBodyPart();
        if (html) {
            bodyPart.setContent(body, "text/html; charset=UTF-8");
        } else {
            bodyPart.setText(body, StandardCharsets.UTF_8.name());
        }

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

    private void modifyThreadLabels(
            User user,
            String threadId,
            List<String> labelsToAdd,
            List<String> labelsToRemove,
            String failureMessage) {
        try {
            ModifyThreadRequest request = new ModifyThreadRequest()
                    .setAddLabelIds(labelsToAdd)
                    .setRemoveLabelIds(labelsToRemove);
            buildGmailClient(user).users().threads()
                    .modify("me", threadId, request)
                    .execute();
        } catch (IOException | GeneralSecurityException exception) {
            throw new MailProviderException(failureMessage, exception);
        }
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

    String listUnsubscribeHeader(com.google.api.services.gmail.model.Message message) {
        return getHeader(message, "List-Unsubscribe");
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

    private List<String> recipients(com.google.api.services.gmail.model.Message message) {
        Set<String> recipients = new LinkedHashSet<>();
        addHeaderValues(recipients, getHeader(message, "To"));
        addHeaderValues(recipients, getHeader(message, "Cc"));
        addHeaderValues(recipients, getHeader(message, "Bcc"));
        return List.copyOf(recipients);
    }

    String extractBody(MessagePart payload) {
        if (payload == null) {
            return "";
        }
        String plainText = findBody(payload, "text/plain");
        if (!plainText.isBlank()) {
            return plainText;
        }
        String html = findBody(payload, "text/html");
        if (!html.isBlank()) {
            return htmlToText(html);
        }
        return "";
    }

    private String findBody(MessagePart part, String mimeType) {
        if (part == null) {
            return "";
        }
        if (mimeType.equalsIgnoreCase(part.getMimeType())
                && part.getBody() != null
                && part.getBody().getData() != null) {
            return decodeBase64Url(part.getBody().getData());
        }
        if (part.getParts() == null) {
            return "";
        }
        for (MessagePart child : part.getParts()) {
            String body = findBody(child, mimeType);
            if (!body.isBlank()) {
                return body;
            }
        }
        return "";
    }

    List<FetchedAttachment> extractAttachments(MessagePart payload) {
        if (payload == null) {
            return List.of();
        }
        List<FetchedAttachment> attachments = new ArrayList<>();
        collectAttachments(payload, attachments);
        return List.copyOf(attachments);
    }

    private void collectAttachments(
            MessagePart part,
            List<FetchedAttachment> attachments) {
        if (part == null) {
            return;
        }
        if (part.getFilename() != null && !part.getFilename().isBlank()) {
            Long size = part.getBody() == null || part.getBody().getSize() == null
                    ? null
                    : part.getBody().getSize().longValue();
            attachments.add(new FetchedAttachment(
                    decodeHeader(part.getFilename()),
                    part.getMimeType(),
                    size
            ));
        }
        if (part.getParts() == null) {
            return;
        }
        for (MessagePart child : part.getParts()) {
            collectAttachments(child, attachments);
        }
    }

    private Message.Direction direction(String sender, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return Message.Direction.INBOUND;
        }
        return extractEmailAddresses(sender).stream()
                .anyMatch(address -> address.equalsIgnoreCase(userEmail))
                ? Message.Direction.OUTBOUND
                : Message.Direction.INBOUND;
    }

    private List<String> extractEmailAddresses(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(InternetAddress.parse(value, false))
                    .map(InternetAddress::getAddress)
                    .filter(address -> address != null && !address.isBlank())
                    .map(address -> address.toLowerCase(java.util.Locale.ROOT))
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String decodeBase64Url(String value) {
        String padded = value;
        int remainder = padded.length() % 4;
        if (remainder > 0) {
            padded = padded + "=".repeat(4 - remainder);
        }
        return new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
    }

    private String htmlToText(String html) {
        String withBreaks = html
                .replaceAll("(?i)<\\s*br\\s*/?>", "\n")
                .replaceAll("(?i)</\\s*p\\s*>", "\n");
        String withoutTags = Pattern.compile("<[^>]+>").matcher(withBreaks).replaceAll(" ");
        return withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .trim();
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

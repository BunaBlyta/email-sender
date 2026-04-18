package com.example.emailsender.mail.provider;

import com.example.emailsender.auth.TokenStore;
import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.user.User;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListThreadsResponse;
import com.google.api.services.gmail.model.Thread;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GmailProvider implements MailProvider {

    private final TokenStore tokenStore;

    public GmailProvider(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

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
            for (Thread thread : threads) {
                MailThread mailThread = new MailThread();
                mailThread.setExternalThreadId(thread.getId());
                mailThread.setUser(user);
                mailThread.setSubject("");
                mailThread.setLastMessageAt(LocalDateTime.now());
                mailThread.setHasUnread(false);
                mailThread.setWorkflowState(MailThread.WorkflowState.ACTIVE);
                result.add(mailThread);
            }
            return result;
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to fetch threads", e);
        }
    }

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
}

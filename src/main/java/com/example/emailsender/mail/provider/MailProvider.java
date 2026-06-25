package com.example.emailsender.mail.provider;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.user.User;

import java.util.List;

public interface MailProvider {

    List<MailThread> fetchThreads(User user, int maxResults);

    List<MailThread> searchThreads(User user, String query, int maxResults);

    List<Message> fetchMessages(User user, String threadId);

    FetchedThread fetchThread(User user, String threadId);

    void markThreadRead(User user, String threadId);

    void markThreadUnread(User user, String threadId);

    void archiveThread(User user, String threadId);

    MailSendResult sendMessage(User user, List<String> recipients, String subject, String body);

    MailSendResult sendHtmlMessage(
            User user, List<String> recipients, String subject, String htmlBody);

    MailSendResult sendMessageWithAttachment(
            User user,
            List<String> recipients,
            String subject,
            String body,
            OutgoingAttachment attachment);

    MailSendResult sendHtmlMessageWithAttachment(
            User user,
            List<String> recipients,
            String subject,
            String htmlBody,
            OutgoingAttachment attachment);
}

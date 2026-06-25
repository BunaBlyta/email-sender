package com.example.emailsender.mail.provider;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.shared.exception.MailProviderException;
import com.example.emailsender.user.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutlookProvider implements MailProvider {

    @Override
    public List<MailThread> fetchThreads(User user, int maxResults) {
        throw unsupported();
    }

    @Override
    public List<Message> fetchMessages(User user, String threadId) {
        throw unsupported();
    }

    @Override
    public FetchedThread fetchThread(User user, String threadId) {
        throw unsupported();
    }

    @Override
    public MailSendResult sendMessage(
            User user, List<String> recipients, String subject, String body) {
        throw unsupported();
    }

    @Override
    public MailSendResult sendHtmlMessage(
            User user, List<String> recipients, String subject, String htmlBody) {
        throw unsupported();
    }

    @Override
    public MailSendResult sendMessageWithAttachment(
            User user,
            List<String> recipients,
            String subject,
            String body,
            OutgoingAttachment attachment) {
        throw unsupported();
    }

    @Override
    public MailSendResult sendHtmlMessageWithAttachment(
            User user,
            List<String> recipients,
            String subject,
            String htmlBody,
            OutgoingAttachment attachment) {
        throw unsupported();
    }

    private MailProviderException unsupported() {
        return new MailProviderException("Outlook support is not implemented");
    }
}

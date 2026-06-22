package com.example.emailsender.mail.provider;

import com.example.emailsender.mail.model.MailThread;
import com.example.emailsender.mail.model.Message;
import com.example.emailsender.user.User;

import java.util.List;

public interface MailProvider {

    List<MailThread> fetchThreads(User user, int maxResults);

    List<Message> fetchMessages(User user, String threadId);

    MailSendResult sendMessage(User user, List<String> recipients, String subject, String body);
}

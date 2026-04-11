package com.example.emailsender.service;

import com.example.emailsender.model.SentEmail;
import com.example.emailsender.repository.SentEmailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;

@Service
public class EmailSenderService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private SentEmailRepository sentEmailRepository;

    // IMMEDIATE send (persisted)
    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);

        sentEmailRepository.save(new SentEmail(to, subject, body, LocalDateTime.now(), false));
    }

    public void sendEmailWithAttachment(String to, String subject, String body, byte[] fileBytes, String fileName) {
        sendRaw(to, subject, body, fileBytes, fileName);
        sentEmailRepository.save(new SentEmail(to, subject, body, LocalDateTime.now(), false));
    }

    // SCHEDULED send (persisted ONCE)
    public void sendScheduled(String to, String subject, String body, byte[] fileBytes, String fileName) {
        sendRaw(to, subject, body, fileBytes, fileName);
        sentEmailRepository.save(new SentEmail(to, subject, body, LocalDateTime.now(), true));
    }

    // RAW sender – NEVER persists
    private void sendRaw(String to, String subject, String body, byte[] fileBytes, String fileName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);

            if (fileBytes != null) {
                helper.addAttachment(fileName, new ByteArrayResource(fileBytes));
            }

            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

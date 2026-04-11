package com.example.emailsender.compose;

import org.springframework.stereotype.Service;

@Service
public class ComposeService {
    /*
     * TODO: Compose-specific business logic — auto-saving drafts, applying templates, validation
     *
     * --- Reference logic from old EmailSenderService ---
     *
     * Old sendEmail (immediate, no attachment):
     *   SimpleMailMessage msg = new SimpleMailMessage();
     *   msg.setTo(to); msg.setSubject(subject); msg.setText(body);
     *   mailSender.send(msg);
     *   sentEmailRepository.save(new SentEmail(to, subject, body, LocalDateTime.now(), false));
     *
     * Old sendEmailWithAttachment:
     *   sendRaw(to, subject, body, fileBytes, fileName);
     *   sentEmailRepository.save(new SentEmail(to, subject, body, LocalDateTime.now(), false));
     */
}

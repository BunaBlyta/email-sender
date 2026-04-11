package com.example.emailsender.send;

import org.springframework.stereotype.Service;

@Service
public class SendService {
    /*
     * TODO: Orchestrate sending via the appropriate MailProvider, persist SentMessage, handle attachments
     *
     * --- Reference logic from old EmailSenderService ---
     *
     * Old sendRaw (private helper):
     *   MimeMessage message = mailSender.createMimeMessage();
     *   MimeMessageHelper helper = new MimeMessageHelper(message, true);
     *   helper.setTo(to); helper.setSubject(subject); helper.setText(body);
     *   if (fileBytes != null) helper.addAttachment(fileName, new ByteArrayResource(fileBytes));
     *   mailSender.send(message);
     *
     * Old sendScheduled:
     *   sendRaw(to, subject, body, fileBytes, fileName);
     *   sentEmailRepository.save(new SentEmail(to, subject, body, LocalDateTime.now(), true));
     *
     * Old scheduling trigger (in EmailController POST /send):
     *   taskScheduler.schedule(() -> {
     *       emailSenderService.sendScheduled(to, subject, body, bytes, filename);
     *       scheduled.setSent(true);
     *       scheduledEmailRepository.save(scheduled);
     *   }, time.atZone(ZoneId.systemDefault()).toInstant());
     */
}

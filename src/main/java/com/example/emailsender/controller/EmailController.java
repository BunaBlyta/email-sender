package com.example.emailsender.controller;

import com.example.emailsender.model.ScheduledEmail;
import com.example.emailsender.repository.ScheduledEmailRepository;
import com.example.emailsender.repository.SentEmailRepository;
import com.example.emailsender.service.EmailSenderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.emailsender.repository.TemplateRepository;
import com.example.emailsender.model.Draft;
import com.example.emailsender.repository.DraftRepository;
import com.example.emailsender.repository.UserRepository;




import java.time.LocalDateTime;
import java.time.ZoneId;

@Controller
public class EmailController {

    @Autowired
    private EmailSenderService emailSenderService;

    @Autowired
    private ScheduledEmailRepository scheduledEmailRepository;

    @Autowired
    private SentEmailRepository sentEmailRepository;

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private DraftRepository draftRepository;

    @Autowired
    private UserRepository userRepository;




    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("drafts", draftRepository.findAll());
        model.addAttribute("templates", templateRepository.findAll());
        model.addAttribute("scheduledEmails", scheduledEmailRepository.findAll());
        model.addAttribute("sentEmails", sentEmailRepository.findAll());

        return "home";
    }

    @GetMapping("/draft/{id}")
    public String loadDraft(@PathVariable Long id, Model model) {

        Draft d = draftRepository.findById(id).orElseThrow();

        model.addAttribute("drafts", draftRepository.findAll());
        model.addAttribute("templates", templateRepository.findAll());
        model.addAttribute("scheduledEmails", scheduledEmailRepository.findAll());
        model.addAttribute("sentEmails", sentEmailRepository.findAll());

        model.addAttribute("selectedDraft", d);

        return "home";
    }



    @PostMapping("/send")
    public String sendEmail(@RequestParam String to,
                            @RequestParam String subject,
                            @RequestParam String body,
                            @RequestParam String action,
                            @RequestParam(required = false) Long draftId,
                            @RequestParam(required = false) MultipartFile file,
                            @RequestParam(required = false) String scheduledTime) throws Exception {

        if ("save".equals(action)) {
            draftRepository.save(new Draft(to, subject, body, userRepository.findById(1L).get()));
            return "redirect:/home";
        }

        if (draftId != null) {
            draftRepository.deleteById(draftId);
        }

        if (scheduledTime != null && !scheduledTime.isEmpty()) {
            LocalDateTime time = LocalDateTime.parse(scheduledTime);
            byte[] bytes = file != null && !file.isEmpty() ? file.getBytes() : null;
            String filename = file != null ? file.getOriginalFilename() : null;

            ScheduledEmail scheduled = scheduledEmailRepository.save(
                    new ScheduledEmail(to, subject, body, time)
            );

            taskScheduler.schedule(() -> {
                emailSenderService.sendScheduled(to, subject, body, bytes, filename);
                scheduled.setSent(true);
                scheduledEmailRepository.save(scheduled);
            }, time.atZone(ZoneId.systemDefault()).toInstant());

        } else {
            if (file != null && !file.isEmpty()) {
                emailSenderService.sendEmailWithAttachment(to, subject, body, file.getBytes(), file.getOriginalFilename());
            } else {
                emailSenderService.sendEmail(to, subject, body);
            }
        }
        return "redirect:/home";
    }

    @PostMapping("/save-draft")
    public String saveDraft(@RequestParam String to,
                            @RequestParam String subject,
                            @RequestParam String body) {

        Draft draft = new Draft();
        draft.setRecipient(to);
        draft.setSubject(subject);
        draft.setBody(body);

        draftRepository.save(draft);

        return "redirect:/home";
    }


}

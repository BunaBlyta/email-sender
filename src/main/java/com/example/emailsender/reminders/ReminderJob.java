package com.example.emailsender.reminders;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderJob {
    // TODO: Scheduled job that polls ReminderRepository for due reminders and fires ReminderService
}

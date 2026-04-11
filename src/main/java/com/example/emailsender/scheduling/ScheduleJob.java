package com.example.emailsender.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduleJob {
    // TODO: Poll ScheduledMessageRepository for due messages and trigger SendService
}

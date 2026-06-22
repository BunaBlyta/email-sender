package com.example.emailsender.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduleJob {

    private final ScheduleService scheduleService;

    public ScheduleJob(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Scheduled(fixedDelayString = "${app.scheduling.poll-delay-ms:10000}")
    public void sendDueMessages() {
        for (Long id : scheduleService.findDueMessageIds()) {
            if (scheduleService.claim(id)) {
                scheduleService.sendClaimed(id);
            }
        }
    }
}

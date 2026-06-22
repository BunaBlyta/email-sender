package com.example.emailsender.scheduling;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/scheduled")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledMessageResponse create(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody ScheduleRequest request) {
        return scheduleService.create(email(principal), request);
    }

    @GetMapping
    public List<ScheduledMessageResponse> list(
            @AuthenticationPrincipal OAuth2User principal) {
        return scheduleService.list(email(principal));
    }

    @PostMapping("/{id}/cancel")
    public ScheduledMessageResponse cancel(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        return scheduleService.cancel(email(principal), id);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

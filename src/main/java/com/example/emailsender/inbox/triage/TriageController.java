package com.example.emailsender.inbox.triage;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inbox/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @GetMapping
    public TriageInboxResponse triage(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(defaultValue = "20") int maxResults) {
        return triageService.triageInbox(principal.getAttribute("email"), maxResults);
    }
}

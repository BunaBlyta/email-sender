package com.example.emailsender.inbox;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inbox")
public class InboxController {

    private final InboxService inboxService;

    public InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping("/threads")
    public List<InboxThreadResponse> getThreads(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(defaultValue = "20") int maxResults) {

        String email = principal.getAttribute("email");
        return inboxService.getThreadsForUser(email, maxResults);
    }
}

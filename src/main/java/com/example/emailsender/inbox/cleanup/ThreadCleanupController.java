package com.example.emailsender.inbox.cleanup;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inbox/threads/{threadId}")
public class ThreadCleanupController {

    private final ThreadCleanupService threadCleanupService;

    public ThreadCleanupController(ThreadCleanupService threadCleanupService) {
        this.threadCleanupService = threadCleanupService;
    }

    @PostMapping("/mark-read")
    public ThreadCleanupResponse markRead(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String threadId) {
        return threadCleanupService.markRead(email(principal), threadId);
    }

    @PostMapping("/mark-unread")
    public ThreadCleanupResponse markUnread(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String threadId) {
        return threadCleanupService.markUnread(email(principal), threadId);
    }

    @PostMapping("/archive")
    public ThreadCleanupResponse archive(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String threadId) {
        return threadCleanupService.archive(email(principal), threadId);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

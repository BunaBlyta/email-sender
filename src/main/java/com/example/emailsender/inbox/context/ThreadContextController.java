package com.example.emailsender.inbox.context;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inbox/threads/{threadId}")
public class ThreadContextController {

    private final ThreadContextService threadContextService;

    public ThreadContextController(ThreadContextService threadContextService) {
        this.threadContextService = threadContextService;
    }

    @GetMapping("/context")
    public ThreadContextResponse getContext(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String threadId) {
        return threadContextService.getContext(email(principal), threadId);
    }

    @PostMapping("/category")
    public ThreadContextResponse updateCategory(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String threadId,
            @RequestBody ThreadCategoryRequest request) {
        return threadContextService.updateCategory(email(principal), threadId, request);
    }

    @PostMapping("/workflow-state")
    public ThreadContextResponse updateWorkflowState(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String threadId,
            @RequestBody ThreadWorkflowStateRequest request) {
        return threadContextService.updateWorkflowState(email(principal), threadId, request);
    }

    @PostMapping("/trust-sender")
    public ThreadContextResponse trustSender(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String threadId) {
        return threadContextService.trustSender(email(principal), threadId);
    }

    @PostMapping("/trust-domain")
    public ThreadContextResponse trustDomain(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String threadId) {
        return threadContextService.trustDomain(email(principal), threadId);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

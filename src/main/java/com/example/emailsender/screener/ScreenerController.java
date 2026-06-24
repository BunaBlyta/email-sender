package com.example.emailsender.screener;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/screener")
public class ScreenerController {

    private final ScreenerService screenerService;

    public ScreenerController(ScreenerService screenerService) {
        this.screenerService = screenerService;
    }

    @PostMapping("/evaluate")
    public ScreenerEvaluationResponse evaluate(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody ScreenerEvaluateRequest request) {
        return screenerService.evaluate(email(principal), request);
    }

    @GetMapping("/pending")
    public List<ScreenerEntryResponse> pending(
            @AuthenticationPrincipal OAuth2User principal) {
        return screenerService.pending(email(principal));
    }

    @PostMapping("/{id}/approve-sender")
    public ScreenerDecisionResponse approveSender(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        return screenerService.approveSender(email(principal), id);
    }

    @PostMapping("/{id}/approve-domain")
    public ScreenerDecisionResponse approveDomain(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        return screenerService.approveDomain(email(principal), id);
    }

    @PostMapping("/{id}/reject")
    public ScreenerDecisionResponse rejectSender(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        return screenerService.rejectSender(email(principal), id);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

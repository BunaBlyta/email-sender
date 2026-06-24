package com.example.emailsender.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/security/trust")
public class SenderTrustController {

    private final SenderTrustService senderTrustService;

    public SenderTrustController(SenderTrustService senderTrustService) {
        this.senderTrustService = senderTrustService;
    }

    @GetMapping
    public TrustListResponse list(@AuthenticationPrincipal OAuth2User principal) {
        return senderTrustService.list(email(principal));
    }

    @PostMapping("/senders")
    @ResponseStatus(HttpStatus.CREATED)
    public TrustEntryResponse trustSender(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody TrustRequest request) {
        return senderTrustService.trustSender(email(principal), request);
    }

    @PostMapping("/domains")
    @ResponseStatus(HttpStatus.CREATED)
    public TrustEntryResponse trustDomain(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody TrustRequest request) {
        return senderTrustService.trustDomain(email(principal), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        senderTrustService.delete(email(principal), id);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

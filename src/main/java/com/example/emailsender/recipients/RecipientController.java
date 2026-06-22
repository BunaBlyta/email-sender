package com.example.emailsender.recipients;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recipient-groups")
public class RecipientController {

    private final RecipientService recipientService;

    public RecipientController(RecipientService recipientService) {
        this.recipientService = recipientService;
    }

    @GetMapping
    public List<RecipientGroupResponse> list(
            @AuthenticationPrincipal OAuth2User principal) {
        return recipientService.list(email(principal));
    }

    @GetMapping("/{id}")
    public RecipientGroupResponse get(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        return recipientService.get(email(principal), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecipientGroupResponse create(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody RecipientGroupRequest request) {
        return recipientService.create(email(principal), request);
    }

    @PutMapping("/{id}")
    public RecipientGroupResponse update(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id,
            @RequestBody RecipientGroupRequest request) {
        return recipientService.update(email(principal), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        recipientService.delete(email(principal), id);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

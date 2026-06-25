package com.example.emailsender.compose;

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
@RequestMapping("/drafts")
public class DraftController {

    private final ComposeService composeService;

    public DraftController(ComposeService composeService) {
        this.composeService = composeService;
    }

    @GetMapping
    public List<DraftResponse> list(@AuthenticationPrincipal OAuth2User principal) {
        return composeService.list(email(principal));
    }

    @GetMapping("/{id}")
    public DraftResponse get(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        return composeService.get(email(principal), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftResponse create(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody DraftRequest request) {
        return composeService.create(email(principal), request);
    }

    @PutMapping("/{id}")
    public DraftResponse update(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id,
            @RequestBody DraftRequest request) {
        return composeService.update(email(principal), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        composeService.delete(email(principal), id);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

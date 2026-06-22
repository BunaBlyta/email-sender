package com.example.emailsender.templates;

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
@RequestMapping("/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<TemplateResponse> list(@AuthenticationPrincipal OAuth2User principal) {
        return templateService.list(email(principal));
    }

    @GetMapping("/{id}")
    public TemplateResponse get(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        return templateService.get(email(principal), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody TemplateRequest request) {
        return templateService.create(email(principal), request);
    }

    @PutMapping("/{id}")
    public TemplateResponse update(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id,
            @RequestBody TemplateRequest request) {
        return templateService.update(email(principal), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        templateService.delete(email(principal), id);
    }

    @PostMapping("/{id}/use")
    public TemplateResponse use(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id) {
        return templateService.use(email(principal), id);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

package com.example.emailsender.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account")
public class CurrentUserController {

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal OAuth2User principal) {
        return new CurrentUserResponse(
                principal.getAttribute("email"),
                principal.getAttribute("name"),
                principal.getAttribute("picture")
        );
    }
}

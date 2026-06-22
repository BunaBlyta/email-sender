package com.example.emailsender.send;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/send")
public class SendController {

    private final SendService sendService;
    private final BulkSendService bulkSendService;

    public SendController(SendService sendService, BulkSendService bulkSendService) {
        this.sendService = sendService;
        this.bulkSendService = bulkSendService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SendResponse send(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody SendRequest request) {
        String email = principal.getAttribute("email");
        return sendService.send(email, request);
    }

    @PostMapping("/bulk")
    public BulkSendResponse sendBulk(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody BulkSendRequest request) {
        String email = principal.getAttribute("email");
        return bulkSendService.send(email, request);
    }
}

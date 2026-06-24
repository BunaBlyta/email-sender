package com.example.emailsender.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/security/phishing")
public class PhishingAnalysisController {

    private final PhishingDetector phishingDetector;
    private final SenderTrustService senderTrustService;

    public PhishingAnalysisController(
            PhishingDetector phishingDetector,
            SenderTrustService senderTrustService) {
        this.phishingDetector = phishingDetector;
        this.senderTrustService = senderTrustService;
    }

    @PostMapping("/analyze")
    public PhishingAnalysisResponse analyze(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody PhishingAnalysisRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        PhishingTrustContext trustContext =
                senderTrustService.trustContext(email(principal), request.sender());
        return phishingDetector.analyze(request, trustContext);
    }

    private String email(OAuth2User principal) {
        return principal.getAttribute("email");
    }
}

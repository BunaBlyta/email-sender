package com.example.emailsender.security;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/security/phishing")
public class PhishingAnalysisController {

    private final PhishingDetector phishingDetector;

    public PhishingAnalysisController(PhishingDetector phishingDetector) {
        this.phishingDetector = phishingDetector;
    }

    @PostMapping("/analyze")
    public PhishingAnalysisResponse analyze(@RequestBody PhishingAnalysisRequest request) {
        return phishingDetector.analyze(request);
    }
}

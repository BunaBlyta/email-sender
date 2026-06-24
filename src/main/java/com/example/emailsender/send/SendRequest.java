package com.example.emailsender.send;

import java.util.List;

public record SendRequest(
        List<String> recipients,
        String subject,
        String body,
        Boolean trackOpens
) {
    public SendRequest(List<String> recipients, String subject, String body) {
        this(recipients, subject, body, false);
    }

    public boolean isTrackingEnabled() {
        return Boolean.TRUE.equals(trackOpens);
    }
}

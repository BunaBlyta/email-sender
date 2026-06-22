package com.example.emailsender.send;

import java.util.List;

public record SendRequest(
        List<String> recipients,
        String subject,
        String body
) {
}

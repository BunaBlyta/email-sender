package com.example.emailsender.recipients;

import java.util.List;

public record RecipientGroupRequest(
        String name,
        List<String> members
) {
}

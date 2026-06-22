package com.example.emailsender.recipients;

import java.util.List;

public record RecipientGroupResponse(
        Long id,
        String name,
        List<String> members,
        int memberCount
) {
}

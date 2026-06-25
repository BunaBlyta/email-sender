package com.example.emailsender.search;

import com.example.emailsender.inbox.InboxThreadResponse;

import java.util.List;

public record SearchResponse(
        String query,
        int resultCount,
        List<InboxThreadResponse> threads
) {
}

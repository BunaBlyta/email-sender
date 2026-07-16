package com.example.emailsender.inbox;

import com.example.emailsender.inbox.cleanup.UnsubscribeMethod;

public record InboxUnsubscribeResponse(
        UnsubscribeMethod method,
        String url,
        String destination
) {
}

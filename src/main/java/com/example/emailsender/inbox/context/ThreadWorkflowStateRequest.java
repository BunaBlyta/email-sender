package com.example.emailsender.inbox.context;

import com.example.emailsender.mail.model.MailThread;

public record ThreadWorkflowStateRequest(MailThread.WorkflowState workflowState) {
}

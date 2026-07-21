import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const initialComposeForm = {
  recipients: "",
  subject: "",
  body: "",
  trackOpens: true
};

const initialTemplateForm = {
  name: "",
  subject: "",
  body: "",
  category: "General"
};

const starterTemplates = [
  {
    id: "starter-follow-up",
    builtIn: true,
    name: "Polite Follow-Up",
    category: "Follow-up",
    subject: "Following up",
    body: `Hi [Name],

I wanted to follow up on my previous message and see if you had a chance to review it.

No rush if this is still in progress. Let me know if there is anything you need from me.

Best,
[Your Name]`
  },
  {
    id: "starter-meeting-request",
    builtIn: true,
    name: "Meeting Request",
    category: "Scheduling",
    subject: "Quick meeting?",
    body: `Hi [Name],

Would you be available for a quick meeting to discuss [topic]?

I am available [option 1], [option 2], or [option 3]. Let me know what works best for you.

Best,
[Your Name]`
  },
  {
    id: "starter-meeting-recap",
    builtIn: true,
    name: "Meeting Recap",
    category: "Recap",
    subject: "Recap and next steps",
    body: `Hi [Name],

Thanks for the conversation today. Here is a quick recap:

- [Decision or takeaway]
- [Open item]
- [Next step and owner]

I will [your next step] by [date].

Best,
[Your Name]`
  },
  {
    id: "starter-status-update",
    builtIn: true,
    name: "Status Update",
    category: "Update",
    subject: "Status update on [topic]",
    body: `Hi [Name],

Quick update on [topic]:

- Completed: [what is done]
- In progress: [what is moving]
- Blocked: [anything blocked, or "none"]
- Next: [next action]

I will send another update by [date].

Best,
[Your Name]`
  },
  {
    id: "starter-request-info",
    builtIn: true,
    name: "Request Information",
    category: "Request",
    subject: "Request for [information/document]",
    body: `Hi [Name],

Could you please send over [information/document] when you have a chance?

It would be helpful to have it by [date] so I can [reason].

Thanks,
[Your Name]`
  },
  {
    id: "starter-thank-you",
    builtIn: true,
    name: "Thank You",
    category: "Relationship",
    subject: "Thank you",
    body: `Hi [Name],

Thank you for your help with [topic]. I appreciate the time and effort you put into it.

It made a real difference for [result/context].

Best,
[Your Name]`
  },
  {
    id: "starter-delay-apology",
    builtIn: true,
    name: "Delay Apology",
    category: "Support",
    subject: "Apologies for the delay",
    body: `Hi [Name],

Apologies for the delay getting back to you.

I have now reviewed this, and [answer/update]. The next step is [next step].

Thank you for your patience.

Best,
[Your Name]`
  },
  {
    id: "starter-introduction",
    builtIn: true,
    name: "Introduction",
    category: "Relationship",
    subject: "Introduction: [Name] <> [Name]",
    body: `Hi [Name A] and [Name B],

I wanted to introduce you both.

[Name A], [brief context].
[Name B], [brief context].

I think it would be useful for you to connect about [reason].

I will let you both take it from here.

Best,
[Your Name]`
  }
];

const initialGroupForm = {
  name: "",
  members: ""
};

const initialBulkForm = {
  selectedGroupIds: [],
  subject: "",
  body: "",
  confirmed: false
};

const initialScreenerForm = {
  sender: "New Sender <new@example.com>",
  subject: "Hello",
  body: "Could we talk?"
};

const trackingLinkStorageKey = "email-platform-tracked-message-links";
const themeStorageKey = "email-platform-theme";

const phishingSample = {
  sender: "PayPal Support <support@paypal-alerts.example>",
  subject: "Urgent: verify your account",
  body: "Your account is suspended. Login at http://192.168.1.10/login to update payment."
};

const threadCategories = ["PEOPLE", "THINGS", "NOISE"];
const threadWorkflowStates = ["ACTIVE", "NEEDS_ACTION", "AWAITING_REPLY", "DONE", "ARCHIVED"];

function App() {
  const [activeView, setActiveView] = useState("inbox");
  const [navExpanded, setNavExpanded] = useState(false);
  const [theme, setTheme] = useState(readThemePreference);
  const [account, setAccount] = useState(null);
  const [inboxThreads, setInboxThreads] = useState([]);
  const [triageInbox, setTriageInbox] = useState(null);
  const [selectedThread, setSelectedThread] = useState(null);
  const [inboxFocusMode, setInboxFocusMode] = useState(false);
  const [threadContext, setThreadContext] = useState(null);
  const [inboxMaxResults, setInboxMaxResults] = useState(20);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResult, setSearchResult] = useState(null);
  const [manageTab, setManageTab] = useState("scheduled");
  const [templates, setTemplates] = useState([]);
  const [drafts, setDrafts] = useState([]);
  const [activeDraftId, setActiveDraftId] = useState(null);
  const [draftReturnAvailable, setDraftReturnAvailable] = useState(false);
  const [templateReturnTarget, setTemplateReturnTarget] = useState(null);
  const [composeListReturnTarget, setComposeListReturnTarget] = useState(null);
  const [lastDraftSnapshot, setLastDraftSnapshot] = useState("");
  const [draftStatus, setDraftStatus] = useState("");
  const [recipientGroups, setRecipientGroups] = useState([]);
  const [scheduledMessages, setScheduledMessages] = useState([]);
  const [pending, setPending] = useState([]);
  const [trust, setTrust] = useState({ senders: [], domains: [] });
  const [composeForm, setComposeForm] = useState(initialComposeForm);
  const [composeFile, setComposeFile] = useState(null);
  const [scheduleAt, setScheduleAt] = useState(defaultDateTimeLocal());
  const [templateForm, setTemplateForm] = useState(initialTemplateForm);
  const [groupForm, setGroupForm] = useState(initialGroupForm);
  const [bulkForm, setBulkForm] = useState(initialBulkForm);
  const [screenerForm, setScreenerForm] = useState(initialScreenerForm);
  const [phishingForm, setPhishingForm] = useState(phishingSample);
  const [evaluation, setEvaluation] = useState(null);
  const [phishingResult, setPhishingResult] = useState(null);
  const [lastSend, setLastSend] = useState(null);
  const [bulkResult, setBulkResult] = useState(null);
  const [trackedMessages, setTrackedMessages] = useState([]);
  const [trackedMessageLinks, setTrackedMessageLinks] = useState(readTrackedMessageLinks);
  const [trackingMessageId, setTrackingMessageId] = useState("");
  const [trackingResult, setTrackingResult] = useState(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const visibleTemplates = mergeStarterTemplates(templates);

  useEffect(() => {
    loadInitialData();
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      window.localStorage.setItem(themeStorageKey, theme);
    } catch {
      // Theme still applies for this session if browser storage is unavailable.
    }
  }, [theme]);

  useEffect(() => {
    if (!notice && !error) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setNotice("");
      setError("");
    }, error ? 5200 : 3400);
    return () => window.clearTimeout(timer);
  }, [notice, error]);

  useEffect(() => {
    if (activeView !== "compose") {
      return undefined;
    }
    if (!hasDraftContent(composeForm)) {
      return undefined;
    }

    const snapshot = draftSnapshot(composeForm, scheduleAt);
    if (snapshot === lastDraftSnapshot) {
      return undefined;
    }

    setDraftStatus("Saving...");
    const timer = window.setTimeout(() => {
      saveDraftSnapshot(snapshot, true);
    }, 1400);
    return () => window.clearTimeout(timer);
  }, [activeView, composeForm, scheduleAt, activeDraftId, lastDraftSnapshot]);

  async function api(path, options = {}) {
    const response = await fetch(path, {
      headers: {
        "Content-Type": "application/json",
        ...(options.headers || {})
      },
      ...options
    });
    return handleResponse(response);
  }

  async function apiForm(path, formData) {
    const response = await fetch(path, {
      method: "POST",
      body: formData
    });
    return handleResponse(response);
  }

  async function handleResponse(response) {
    if (response.status === 401 || response.status === 403) {
      throw new Error("Authentication is required. Sign in through Spring Boot first.");
    }
    if (!response.ok) {
      const body = await safeJson(response);
      throw new Error(body?.error || body?.message || body?.errors?.join(", ") || `Request failed: ${response.status}`);
    }
    if (response.status === 204) {
      return null;
    }
    return response.json();
  }

  async function safeJson(response) {
    try {
      return await response.json();
    } catch {
      return null;
    }
  }

  async function run(action) {
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await action();
    } catch (exception) {
      setError(exception.message);
    } finally {
      setLoading(false);
    }
  }

  async function loadInitialData() {
    setLoading(true);
    setError("");
    const tasks = [
      refreshAccount(),
      refreshTriage(),
      refreshTemplates(),
      refreshDrafts(),
      refreshRecipientGroups(),
      refreshScheduledMessages(),
      refreshTrackedMessages(),
      refreshTrust(),
      refreshPending()
    ];
    const results = await Promise.allSettled(tasks);
    const failed = results.find((result) => result.status === "rejected");
    if (failed) {
      setError(failed.reason.message);
    }
    setLoading(false);
  }

  async function refreshAccount() {
    setAccount(await api("/account/me"));
  }

  async function refreshInbox(limit = inboxMaxResults) {
    setInboxThreads(await api(`/inbox/threads?maxResults=${limit}`));
  }

  async function refreshTriage(limit = inboxMaxResults) {
    setTriageInbox(await api(`/inbox/triage?maxResults=${limit}`));
  }

  async function loadThread(threadId) {
    const encodedThreadId = encodeURIComponent(threadId);
    setThreadContext(null);
    const [thread, context] = await Promise.all([
      api(`/inbox/threads/${encodedThreadId}`),
      api(`/inbox/threads/${encodedThreadId}/context`)
    ]);
    setSelectedThread(thread);
    setThreadContext(context);
  }

  function closeThread() {
    setSelectedThread(null);
    setThreadContext(null);
    setInboxFocusMode(false);
  }

  async function openThread(threadId) {
    if (selectedThread?.externalThreadId === threadId) {
      closeThread();
      return;
    }

    await run(async () => {
      await loadThread(threadId);
    });
  }

  async function submitSearch(event) {
    event.preventDefault();
    await run(async () => {
      const query = searchQuery.trim();
      if (!query) {
        throw new Error("Search query is required");
      }
      const result = await api(
        `/inbox/search?q=${encodeURIComponent(query)}&maxResults=${inboxMaxResults}`
      );
      setSearchResult(result);
      setActiveView("search");
    });
  }

  async function cleanupThread(threadId, action) {
    const endpoints = {
      read: "mark-read",
      unread: "mark-unread",
      archive: "archive"
    };
    const messages = {
      read: "Thread marked read.",
      unread: "Thread marked unread.",
      archive: "Thread archived."
    };
    if (action === "archive" && !window.confirm("Archive this thread in Gmail?")) {
      return;
    }
    await run(async () => {
      await api(`/inbox/threads/${encodeURIComponent(threadId)}/${endpoints[action]}`, {
        method: "POST"
      });
      setNotice(messages[action]);
      await refreshTriage();
      if (action === "archive") {
        setSelectedThread(null);
        setThreadContext(null);
      } else {
        await loadThread(threadId);
      }
    });
  }

  function beginUnsubscribe(option) {
    setError("");
    setNotice("");

    try {
      const parsedUrl = new URL(option?.url || "");
      const isHttps = option?.method === "HTTPS" && parsedUrl.protocol === "https:";
      const isMailto = option?.method === "MAILTO" && parsedUrl.protocol === "mailto:";
      if (!isHttps && !isMailto) {
        throw new Error("This unsubscribe option is not supported safely.");
      }

      const destination = isHttps ? parsedUrl.host : option.destination;
      const prompt = isHttps
        ? `Open the unsubscribe URL at ${destination}?\n\nVisiting it may unsubscribe you immediately, depending on the sender.`
        : `Prepare an unsubscribe email to ${destination}?\n\nReview and send the draft in your email app to complete the request.`;
      if (!window.confirm(prompt)) {
        return;
      }

      if (isHttps) {
        window.open(option.url, "_blank", "noopener,noreferrer");
        setNotice("Unsubscribe URL opened. The sender may have applied the request.");
      } else {
        window.location.assign(option.url);
        setNotice("Unsubscribe email prepared. Review and send it to complete the request.");
      }
    } catch {
      setError("This unsubscribe option is not supported safely.");
    }
  }

  async function updateThreadCategory(threadId, category) {
    await run(async () => {
      const context = await api(`/inbox/threads/${encodeURIComponent(threadId)}/category`, {
        method: "POST",
        body: JSON.stringify({ category })
      });
      setThreadContext(context);
      applyThreadContextToLists(context);
      setNotice("Thread category updated.");
    });
  }

  async function updateThreadWorkflowState(threadId, workflowState) {
    await run(async () => {
      const context = await api(`/inbox/threads/${encodeURIComponent(threadId)}/workflow-state`, {
        method: "POST",
        body: JSON.stringify({ workflowState })
      });
      setThreadContext(context);
      applyThreadContextToLists(context);
      setNotice("Thread status updated.");
    });
  }

  async function trustThreadSender(threadId) {
    await run(async () => {
      const context = await api(`/inbox/threads/${encodeURIComponent(threadId)}/trust-sender`, {
        method: "POST"
      });
      setThreadContext(context);
      applyThreadContextToLists(context);
      await refreshTrust();
      setNotice("Sender trusted.");
    });
  }

  async function trustThreadDomain(threadId) {
    await run(async () => {
      const context = await api(`/inbox/threads/${encodeURIComponent(threadId)}/trust-domain`, {
        method: "POST"
      });
      setThreadContext(context);
      applyThreadContextToLists(context);
      await refreshTrust();
      setNotice("Domain trusted.");
    });
  }

  function applyThreadContextToLists(context) {
    if (!context?.threadId) {
      return;
    }
    const patch = {
      category: context.category,
      categoryOverride: context.categoryOverride,
      suggestedCategory: context.suggestedCategory,
      workflowState: context.workflowState,
      screenerStatus: context.screenerStatus,
      senderTrusted: context.senderTrusted,
      domainTrusted: context.domainTrusted,
      phishingRiskLevel: context.phishingRiskLevel,
      phishingScore: context.phishingScore
    };
    setTriageInbox((current) => patchThreadListResponse(current, context.threadId, patch));
    setSearchResult((current) => patchThreadListResponse(current, context.threadId, patch));
  }

  async function refreshTemplates() {
    setTemplates(await api("/templates"));
  }

  async function refreshDrafts() {
    setDrafts(await api("/drafts"));
  }

  async function refreshRecipientGroups() {
    setRecipientGroups(await api("/recipient-groups"));
  }

  async function refreshScheduledMessages() {
    setScheduledMessages(await api("/scheduled"));
  }

  async function refreshTrackedMessages() {
    setTrackedMessages(await api("/tracking/sent"));
  }

  async function refreshPending() {
    setPending(await api("/screener/pending"));
  }

  async function refreshTrust() {
    setTrust(await api("/security/trust"));
  }

  async function sendMessage(event) {
    event.preventDefault();
    await run(async () => {
      const payload = {
        recipients: parseList(composeForm.recipients),
        subject: composeForm.subject,
        body: composeForm.body,
        trackOpens: composeForm.trackOpens
      };

      const response = composeFile
        ? await sendWithAttachment(payload)
        : await api("/send", {
            method: "POST",
            body: JSON.stringify(payload)
          });
      setLastSend(response);
      rememberTrackedMessageLink(response);
      setNotice("Message sent.");
      if (response?.id) {
        setTrackingMessageId(String(response.id));
      }
      resetComposeAfterDelivery();
      await discardActiveDraft();
    });
  }

  async function sendWithAttachment(payload) {
    const formData = new FormData();
    formData.append("message", new Blob([JSON.stringify(payload)], { type: "application/json" }));
    formData.append("file", composeFile);
    return apiForm("/send/attachment", formData);
  }

  async function scheduleMessage(event) {
    event.preventDefault();
    await run(async () => {
      const response = await api("/scheduled", {
        method: "POST",
        body: JSON.stringify({
          recipients: parseList(composeForm.recipients),
          subject: composeForm.subject,
          body: composeForm.body,
          scheduledFor: new Date(scheduleAt).toISOString()
        })
      });
      setNotice("Message scheduled.");
      await refreshScheduledMessages();
      setManageTab("scheduled");
      setActiveView("drafts");
      setLastSend(response);
      resetComposeAfterDelivery();
      await discardActiveDraft();
    });
  }

  async function saveDraft(event) {
    if (event) {
      event.preventDefault();
    }
    await run(async () => {
      const snapshot = draftSnapshot(composeForm, scheduleAt);
      await saveDraftSnapshot(snapshot, false);
      setComposeForm(initialComposeForm);
      setComposeFile(null);
      setScheduleAt(defaultDateTimeLocal());
      setActiveDraftId(null);
      setDraftReturnAvailable(false);
      setTemplateReturnTarget(null);
      setLastDraftSnapshot("");
      setDraftStatus("");
      setNotice("Draft saved.");
    });
  }

  async function saveDraftSnapshot(snapshot, silent) {
    const payload = JSON.parse(snapshot);
    const path = activeDraftId ? `/drafts/${activeDraftId}` : "/drafts";
    const method = activeDraftId ? "PUT" : "POST";
    try {
      const saved = await api(path, {
        method,
        body: JSON.stringify(payload)
      });
      setActiveDraftId(saved.id);
      setLastDraftSnapshot(snapshot);
      setDraftStatus(`Saved ${formatTimeOnly(saved.updatedAt)}`);
      await refreshDrafts();
    } catch (exception) {
      setDraftStatus("Draft not saved");
      if (!silent) {
        throw exception;
      }
    }
  }

  async function loadDraft(draft) {
    const openedFromDraftsPage = activeView === "drafts";
    setActiveDraftId(draft.id);
    setTemplateReturnTarget(null);
    setComposeForm({
      recipients: (draft.recipients || []).join(", "),
      subject: draft.subject || "",
      body: draft.body || "",
      trackOpens: composeForm.trackOpens
    });
    const scheduledDate = draft.scheduledFor
      ? toDateTimeLocal(new Date(draft.scheduledFor))
      : defaultDateTimeLocal();
    setScheduleAt(scheduledDate);
    setLastDraftSnapshot(draftSnapshot({
      recipients: (draft.recipients || []).join(", "),
      subject: draft.subject || "",
      body: draft.body || "",
      trackOpens: composeForm.trackOpens
    }, scheduledDate));
    setDraftStatus(`Loaded ${formatTimeOnly(draft.updatedAt)}`);
    setDraftReturnAvailable(openedFromDraftsPage);
    setActiveView("compose");
  }

  function loadScheduledMessage(message) {
    const scheduledDate = message.scheduledFor
      ? toDateTimeLocal(new Date(message.scheduledFor))
      : defaultDateTimeLocal();
    setActiveDraftId(null);
    setTemplateReturnTarget(null);
    setComposeForm({
      recipients: (message.recipients || []).join(", "),
      subject: message.subject || "",
      body: message.body || "",
      trackOpens: composeForm.trackOpens
    });
    setScheduleAt(scheduledDate);
    setLastDraftSnapshot("");
    setDraftStatus(`Scheduled ${formatDate(message.scheduledFor)}`);
    setDraftReturnAvailable(true);
    setActiveView("compose");
  }

  async function deleteDraft(id) {
    if (!window.confirm("Delete this draft?")) {
      return;
    }
    await run(async () => {
      await api(`/drafts/${id}`, { method: "DELETE" });
      if (activeDraftId === id) {
        setActiveDraftId(null);
        setLastDraftSnapshot(draftSnapshot(composeForm, scheduleAt));
        setDraftStatus("Draft deleted; current content is unsaved");
      }
      await refreshDrafts();
      setNotice("Draft deleted.");
    });
  }

  async function discardActiveDraft() {
    if (!activeDraftId) {
      return;
    }
    await api(`/drafts/${activeDraftId}`, { method: "DELETE" });
    setActiveDraftId(null);
    setDraftReturnAvailable(false);
    setTemplateReturnTarget(null);
    setLastDraftSnapshot("");
    setDraftStatus("");
    await refreshDrafts();
  }

  function resetComposeAfterDelivery() {
    setComposeForm(initialComposeForm);
    setComposeFile(null);
    setScheduleAt(defaultDateTimeLocal());
    setDraftReturnAvailable(false);
    setTemplateReturnTarget(null);
    setLastDraftSnapshot("");
    setDraftStatus("");
  }

  async function createTemplate(event) {
    event.preventDefault();
    await run(async () => {
      await api("/templates", {
        method: "POST",
        body: JSON.stringify(templateForm)
      });
      setTemplateForm(initialTemplateForm);
      setNotice("Template saved.");
      await refreshTemplates();
    });
  }

  async function deleteTemplate(id) {
    if (!window.confirm("Delete this template?")) {
      return;
    }
    await run(async () => {
      await api(`/templates/${id}`, { method: "DELETE" });
      setNotice("Template deleted.");
      await refreshTemplates();
    });
  }

  async function useTemplate(template) {
    const returnTarget = activeView === "manage" ? "manage-templates" : activeView === "templates" ? "templates" : null;
    if (template.builtIn) {
      applyTemplate(template);
      setDraftReturnAvailable(false);
      setTemplateReturnTarget(returnTarget);
      setActiveView("compose");
      setNotice("Template applied.");
      return;
    }
    await run(async () => {
      const response = await api(`/templates/${template.id}/use`, { method: "POST" });
      applyTemplate(response);
      await refreshTemplates();
      setDraftReturnAvailable(false);
      setTemplateReturnTarget(returnTarget);
      setActiveView("compose");
      setNotice("Template applied.");
    });
  }

  function applyTemplate(template) {
    setComposeForm((current) => ({
      ...current,
      subject: template.subject || "",
      body: template.body || ""
    }));
  }

  function mergeStarterTemplates(savedTemplates) {
    const savedNames = new Set((savedTemplates || []).map((template) => template.name?.toLowerCase()));
    const missingStarters = starterTemplates.filter((template) => !savedNames.has(template.name.toLowerCase()));
    return [...missingStarters, ...(savedTemplates || [])];
  }

  async function createRecipientGroup(event) {
    event.preventDefault();
    await run(async () => {
      await api("/recipient-groups", {
        method: "POST",
        body: JSON.stringify({
          name: groupForm.name,
          members: parseList(groupForm.members)
        })
      });
      setGroupForm(initialGroupForm);
      setNotice("Recipient group saved.");
      await refreshRecipientGroups();
    });
  }

  async function deleteRecipientGroup(id) {
    if (!window.confirm("Delete this recipient group?")) {
      return;
    }
    await run(async () => {
      await api(`/recipient-groups/${id}`, { method: "DELETE" });
      setNotice("Recipient group deleted.");
      await refreshRecipientGroups();
    });
  }

  async function sendBulk(event) {
    event.preventDefault();
    await run(async () => {
      if (!bulkForm.confirmed) {
        throw new Error("Confirm the private bulk send before continuing.");
      }
      const response = await api("/send/bulk", {
        method: "POST",
        body: JSON.stringify({
          recipientGroupIds: bulkForm.selectedGroupIds,
          subject: bulkForm.subject,
          body: bulkForm.body,
          confirmed: bulkForm.confirmed
        })
      });
      setBulkResult(response);
      setBulkForm((current) => ({ ...current, confirmed: false }));
      setNotice("Bulk send completed.");
    });
  }

  async function cancelScheduled(id) {
    if (!window.confirm("Cancel this scheduled message?")) {
      return;
    }
    await run(async () => {
      await api(`/scheduled/${id}/cancel`, { method: "POST" });
      setNotice("Scheduled message cancelled.");
      await refreshScheduledMessages();
    });
  }

  async function deleteScheduled(id) {
    if (!window.confirm("Delete this scheduled message?")) {
      return;
    }
    await run(async () => {
      await api(`/scheduled/${id}`, { method: "DELETE" });
      setNotice("Scheduled message deleted.");
      await refreshScheduledMessages();
    });
  }

  async function openTrackingSignals(sentMessageId, stayInPlace = false) {
    await run(async () => {
      const response = await api(`/tracking/sent/${sentMessageId}`);
      setTrackingMessageId(String(sentMessageId));
      setTrackingResult(response);
      await refreshTrackedMessages();
      if (!stayInPlace) {
        setActiveView("tracking");
      }
      setNotice("Open signals loaded.");
    });
  }

  function rememberTrackedMessageLink(response) {
    if (!response?.id || !response?.externalMessageId) {
      return;
    }
    setTrackedMessageLinks((current) => {
      const next = [
        {
          sentMessageId: String(response.id),
          externalMessageId: response.externalMessageId,
          externalThreadId: response.externalThreadId || ""
        },
        ...current.filter((link) => link.externalMessageId !== response.externalMessageId)
      ].slice(0, 100);
      writeTrackedMessageLinks(next);
      return next;
    });
  }

  async function evaluateSender(event) {
    event.preventDefault();
    await run(async () => {
      const data = await api("/screener/evaluate", {
        method: "POST",
        body: JSON.stringify(screenerForm)
      });
      setEvaluation(data);
      setNotice("Sender evaluated.");
      await refreshPending();
      await refreshTrust();
    });
  }

  async function analyzePhishing(event) {
    event.preventDefault();
    await run(async () => {
      const data = await api("/security/phishing/analyze", {
        method: "POST",
        body: JSON.stringify(phishingForm)
      });
      setPhishingResult(data);
      setNotice("Message analyzed.");
    });
  }

  async function decide(path, successMessage, confirmationMessage = "") {
    if (confirmationMessage && !window.confirm(confirmationMessage)) {
      return;
    }
    await run(async () => {
      const data = await api(path, { method: "POST" });
      setNotice(successMessage);
      setEvaluation(data?.entry ? { ...evaluation, entry: data.entry, status: data.entry.status } : evaluation);
      await refreshPending();
      await refreshTrust();
    });
  }

  async function trustValue(path, value, successMessage) {
    await run(async () => {
      await api(path, {
        method: "POST",
        body: JSON.stringify({ value })
      });
      setNotice(successMessage);
      await refreshTrust();
    });
  }

  return (
    <main className={`app-shell ${navExpanded ? "nav-expanded" : "nav-collapsed"}`}>
      <aside className="sidebar" aria-label="Application navigation">
        <button
          type="button"
          className="nav-toggle"
          aria-label={navExpanded ? "Collapse navigation" : "Expand navigation"}
          aria-expanded={navExpanded}
          onClick={() => setNavExpanded((current) => !current)}
        >
          <NavIcon name="menu" />
        </button>
        <nav className="nav">
          <NavGroup
            title="Workspace"
            items={[
              ["inbox", "Attention", "attention"],
              ["compose", "Compose", "compose"],
              ["tracking", "Sent Emails", "tracking"]
            ]}
            activeView={activeView}
            setActiveView={setActiveView}
            toggleNavigation={() => setNavExpanded((current) => !current)}
          />
          <NavGroup
            title="Organize"
            items={[
              ["drafts", "Drafts", "drafts"],
              ["templates", "Templates & Groups", "templates"],
              ["screener", "Screener & Trust", "screener"]
            ]}
            activeView={activeView}
            setActiveView={setActiveView}
            toggleNavigation={() => setNavExpanded((current) => !current)}
          />
        </nav>
        <button
          type="button"
          className="theme-toggle"
          aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
          aria-pressed={theme === "dark"}
          onClick={() => setTheme((current) => (current === "dark" ? "light" : "dark"))}
        >
          <NavIcon name={theme === "dark" ? "sun" : "moon"} />
          <span className="nav-label">{theme === "dark" ? "Light mode" : "Dark mode"}</span>
        </button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <form className="app-search" role="search" onSubmit={submitSearch}>
            <label className="sr-only" htmlFor="app-search-input">Search mail</label>
            <span className="search-icon" aria-hidden="true">⌕</span>
            <input
              id="app-search-input"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="Search mail"
              autoComplete="off"
            />
            <button
              type="submit"
              aria-label={loading ? "Searching mail" : "Search mail"}
              disabled={loading}
            >
              {loading ? "Working…" : "Search"}
            </button>
          </form>
          <div className="topbar-actions">
            {activeView === "inbox" && selectedThread && !inboxFocusMode && (
              <button
                type="button"
                className="secondary compose-shortcut"
                onClick={() => setInboxFocusMode(true)}
              >
                Focus
              </button>
            )}
            <button
              type="button"
              className="primary compose-shortcut"
              onClick={() => setActiveView("compose")}
            >
              Compose
            </button>
            <a
              className="topbar-account"
              href="http://localhost:8080/oauth2/authorization/google"
              title={account?.email ? "Reconnect Google" : "Google sign-in"}
            >
              <span>{account?.name || account?.email || "Not signed in"}</span>
            </a>
          </div>
        </header>

        {(notice || error) && (
          <div className="toast-stack" aria-live="polite" aria-atomic="true">
            {notice && (
              <div className="toast notice" role="status">
                <span>{notice}</span>
                <button type="button" aria-label="Dismiss notification" onClick={() => setNotice("")}>×</button>
              </div>
            )}
            {error && (
              <div className="toast error" role="alert">
                <span>{error}</span>
                <button type="button" aria-label="Dismiss error" onClick={() => setError("")}>×</button>
              </div>
            )}
          </div>
        )}

        {activeView === "inbox" && (
          <InboxView
            triage={triageInbox}
            selectedThread={selectedThread}
            threadContext={threadContext}
            maxResults={inboxMaxResults}
            setMaxResults={setInboxMaxResults}
            searchQuery={searchQuery}
            setSearchQuery={setSearchQuery}
            onSearch={submitSearch}
            onRefresh={() => run(() => refreshTriage())}
            onOpenThread={openThread}
            onCloseThread={closeThread}
            onCleanup={cleanupThread}
            onUnsubscribe={beginUnsubscribe}
            onUpdateCategory={updateThreadCategory}
            onUpdateWorkflowState={updateThreadWorkflowState}
            onTrustThreadSender={trustThreadSender}
            onTrustThreadDomain={trustThreadDomain}
            trackedMessageLinks={trackedMessageLinks}
            trackingMessageId={trackingMessageId}
            trackingResult={trackingResult}
            onViewOpenSignals={(id) => openTrackingSignals(id, true)}
            focusMode={inboxFocusMode}
            setFocusMode={setInboxFocusMode}
          />
        )}

        {activeView === "search" && (
          <SearchView
            query={searchQuery}
            setQuery={setSearchQuery}
            result={searchResult}
            selectedThread={selectedThread}
            threadContext={threadContext}
            maxResults={inboxMaxResults}
            setMaxResults={setInboxMaxResults}
            onSearch={submitSearch}
            onOpenThread={openThread}
            onCloseThread={closeThread}
            onCleanup={cleanupThread}
            onUnsubscribe={beginUnsubscribe}
            onUpdateCategory={updateThreadCategory}
            onUpdateWorkflowState={updateThreadWorkflowState}
            onTrustThreadSender={trustThreadSender}
            onTrustThreadDomain={trustThreadDomain}
            trackedMessageLinks={trackedMessageLinks}
            trackingMessageId={trackingMessageId}
            trackingResult={trackingResult}
            onViewOpenSignals={(id) => openTrackingSignals(id, true)}
          />
        )}

        {activeView === "compose" && (
          <ComposeView
            form={composeForm}
            setForm={setComposeForm}
            file={composeFile}
            setFile={setComposeFile}
            templates={visibleTemplates}
            drafts={drafts}
            groups={recipientGroups}
            activeDraftId={activeDraftId}
            draftStatus={draftStatus}
            draftReturnAvailable={draftReturnAvailable}
            templateReturnTarget={templateReturnTarget}
            scheduleAt={scheduleAt}
            setScheduleAt={setScheduleAt}
            lastSend={lastSend}
            onSend={sendMessage}
            onSchedule={scheduleMessage}
            onSaveDraft={saveDraft}
            onLoadDraft={loadDraft}
            onDeleteDraft={deleteDraft}
            onApplyTemplate={applyTemplate}
            onSeeAllTemplates={() => {
              setComposeListReturnTarget("templates");
              setActiveView("templates");
            }}
            onSeeAllDrafts={() => {
              setComposeListReturnTarget("drafts");
              setActiveView("drafts");
            }}
            onBackToDrafts={() => {
              setDraftReturnAvailable(false);
              setActiveView("drafts");
            }}
            onBackToTemplates={() => {
              const target = templateReturnTarget;
              setTemplateReturnTarget(null);
              if (target === "manage-templates") {
                setManageTab("templates");
                setActiveView("manage");
                return;
              }
              setActiveView("templates");
            }}
            trackingMessageId={trackingMessageId}
            trackingResult={trackingResult}
            onViewOpenSignals={(id) => openTrackingSignals(id, true)}
          />
        )}

        {activeView === "drafts" && (
          <DraftsView
            drafts={drafts}
            onRefresh={() => run(refreshDrafts)}
            onLoadDraft={loadDraft}
            onDeleteDraft={deleteDraft}
            scheduledMessages={scheduledMessages}
            onRefreshScheduled={() => run(refreshScheduledMessages)}
            onCancelScheduled={cancelScheduled}
            onDeleteScheduled={deleteScheduled}
            onLoadScheduled={loadScheduledMessage}
            onBackToCompose={composeListReturnTarget === "drafts" ? () => {
              setComposeListReturnTarget(null);
              setActiveView("compose");
            } : null}
          />
        )}

        {activeView === "manage" && (
          <ManageView
            activeTab={manageTab}
            setActiveTab={setManageTab}
            templates={visibleTemplates}
            templateForm={templateForm}
            setTemplateForm={setTemplateForm}
            onCreateTemplate={createTemplate}
            onDeleteTemplate={deleteTemplate}
            onUseTemplate={useTemplate}
            onRefreshTemplates={() => run(refreshTemplates)}
            groups={recipientGroups}
            groupForm={groupForm}
            setGroupForm={setGroupForm}
            bulkForm={bulkForm}
            setBulkForm={setBulkForm}
            bulkResult={bulkResult}
            onCreateGroup={createRecipientGroup}
            onDeleteGroup={deleteRecipientGroup}
            onBulkSend={sendBulk}
            onRefreshGroups={() => run(refreshRecipientGroups)}
            scheduledMessages={scheduledMessages}
            onRefreshScheduled={() => run(refreshScheduledMessages)}
            onCancelScheduled={cancelScheduled}
            onDeleteScheduled={deleteScheduled}
            screenerForm={screenerForm}
            setScreenerForm={setScreenerForm}
            evaluation={evaluation}
            pending={pending}
            onEvaluateSender={evaluateSender}
            onRefreshPending={() => run(refreshPending)}
            onApproveSender={(id) => decide(`/screener/${id}/approve-sender`, "Sender approved.")}
            onApproveDomain={(id) => decide(`/screener/${id}/approve-domain`, "Domain approved.")}
            onRejectSender={(id) => decide(
              `/screener/${id}/reject`,
              "Sender rejected.",
              "Reject this sender?"
            )}
            trackedMessages={trackedMessages}
            trackingMessageId={trackingMessageId}
            trackingResult={trackingResult}
            onSelectTracking={openTrackingSignals}
            onRefreshTracking={() => run(refreshTrackedMessages)}
            phishingForm={phishingForm}
            setPhishingForm={setPhishingForm}
            phishingResult={phishingResult}
            onAnalyzePhishing={analyzePhishing}
            trust={trust}
            onRefreshTrust={() => run(refreshTrust)}
            onTrustSender={(value) => trustValue("/security/trust/senders", value, "Sender trusted.")}
            onTrustDomain={(value) => trustValue("/security/trust/domains", value, "Domain trusted.")}
          />
        )}

        {(activeView === "templates" || activeView === "groups") && (
          <TemplatesGroupsView
            templates={visibleTemplates}
            templateForm={templateForm}
            setTemplateForm={setTemplateForm}
            onCreateTemplate={createTemplate}
            onDeleteTemplate={deleteTemplate}
            onUseTemplate={useTemplate}
            onRefreshTemplates={() => run(refreshTemplates)}
            groups={recipientGroups}
            groupForm={groupForm}
            setGroupForm={setGroupForm}
            onCreateGroup={createRecipientGroup}
            onDeleteGroup={deleteRecipientGroup}
            onRefreshGroups={() => run(refreshRecipientGroups)}
            onBackToCompose={composeListReturnTarget === "templates" ? () => {
              setComposeListReturnTarget(null);
              setActiveView("compose");
            } : null}
          />
        )}

        {activeView === "tracking" && (
          <TrackingView
            messages={trackedMessages}
            selectedMessageId={trackingMessageId}
            result={trackingResult}
            onSelect={openTrackingSignals}
            onRefresh={() => run(refreshTrackedMessages)}
          />
        )}

        {(activeView === "screener" || activeView === "trust") && (
          <ScreenerTrustView
            form={screenerForm}
            setForm={setScreenerForm}
            evaluation={evaluation}
            pending={pending}
            onEvaluate={evaluateSender}
            onRefresh={() => run(refreshPending)}
            onApproveSender={(id) => decide(`/screener/${id}/approve-sender`, "Sender approved.")}
            onApproveDomain={(id) => decide(`/screener/${id}/approve-domain`, "Domain approved.")}
            onReject={(id) => decide(
              `/screener/${id}/reject`,
              "Sender rejected.",
              "Reject this sender?"
            )}
            trust={trust}
            onRefreshTrust={() => run(refreshTrust)}
            onTrustSender={(value) => trustValue("/security/trust/senders", value, "Sender trusted.")}
            onTrustDomain={(value) => trustValue("/security/trust/domains", value, "Domain trusted.")}
          />
        )}

        {activeView === "security" && (
          <SecurityView
            form={phishingForm}
            setForm={setPhishingForm}
            result={phishingResult}
            onAnalyze={analyzePhishing}
          />
        )}

      </section>
    </main>
  );
}

function InboxView({
  triage,
  selectedThread,
  threadContext,
  maxResults,
  setMaxResults,
  searchQuery,
  setSearchQuery,
  onSearch,
  onRefresh,
  onOpenThread,
  onCloseThread,
  onCleanup,
  onUnsubscribe,
  onUpdateCategory,
  onUpdateWorkflowState,
  onTrustThreadSender,
  onTrustThreadDomain,
  trackedMessageLinks,
  trackingMessageId,
  trackingResult,
  onViewOpenSignals,
  focusMode,
  setFocusMode
}) {
  const [activeFilter, setActiveFilter] = useState("PEOPLE");
  const threads = triage?.threads || [];
  const selectedTriage = threads.find(
    (thread) => thread.externalThreadId === selectedThread?.externalThreadId
  );
  const visibleThreads = threads.filter((thread) => (
    (thread.category || thread.suggestedCategory) === activeFilter
  ));
  const categoryTabs = threadCategories.map((category) => ({
    key: category,
    label: categoryLabel(category)
  }));
  const threadWorkspace = (
    <ThreadWorkspace
      thread={selectedThread}
      triage={selectedTriage}
      onCleanup={onCleanup}
      onUnsubscribe={onUnsubscribe}
      context={threadContext}
      onUpdateCategory={onUpdateCategory}
      onUpdateWorkflowState={onUpdateWorkflowState}
      onTrustThreadSender={onTrustThreadSender}
      onTrustThreadDomain={onTrustThreadDomain}
      trackedMessageLinks={trackedMessageLinks}
      trackingMessageId={trackingMessageId}
      trackingResult={trackingResult}
      onViewOpenSignals={onViewOpenSignals}
      onClose={focusMode ? () => setFocusMode(false) : onCloseThread}
      showHeaderDate={focusMode}
    />
  );

  if (focusMode && selectedThread) {
    return (
      <div className="focus-layout">
        {threadWorkspace}
      </div>
    );
  }

  return (
    <div className={`process-layout attention-layout ${selectedThread ? "" : "no-thread-selected"}`}>
      <section className="queue-panel">
        <div className="queue-header">
          <InboxFilterBar
            filters={categoryTabs}
            activeFilter={activeFilter}
            onChange={setActiveFilter}
          />
          <div className="toolbar">
            <button
              type="button"
              className="icon-button queue-refresh"
              aria-label="Refresh attention queue"
              title="Refresh"
              onClick={onRefresh}
            >
              ↻
            </button>
          </div>
        </div>
        {visibleThreads.length > 0 ? (
          <div className="thread-list">
            {visibleThreads.map((thread) => (
              <TriageThreadRow
                key={thread.externalThreadId}
                thread={thread}
                selected={false}
                onOpenThread={onOpenThread}
              />
            ))}
          </div>
        ) : (
          <EmptyState label={threads.length ? `No ${categoryLabel(activeFilter).toLowerCase()} threads.` : "No triage results loaded."} />
        )}
        <div className="queue-footer">
          <label>
            <span>Rows</span>
            <select value={maxResults} onChange={(event) => setMaxResults(Number(event.target.value))}>
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
            </select>
          </label>
        </div>
      </section>

      {selectedThread && threadWorkspace}
    </div>
  );
}

function InboxFilterBar({ filters, activeFilter, onChange }) {
  return (
    <div className="filter-bar" aria-label="Inbox filters">
      {filters.map((filter) => (
        <button
          type="button"
          key={filter.key}
          className={activeFilter === filter.key ? "active" : ""}
          onClick={() => onChange(filter.key)}
        >
          {filter.label}
        </button>
      ))}
    </div>
  );
}

function SearchView({
  query,
  setQuery,
  result,
  selectedThread,
  threadContext,
  maxResults,
  setMaxResults,
  onSearch,
  onOpenThread,
  onCloseThread,
  onCleanup,
  onUnsubscribe,
  onUpdateCategory,
  onUpdateWorkflowState,
  onTrustThreadSender,
  onTrustThreadDomain,
  trackedMessageLinks,
  trackingMessageId,
  trackingResult,
  onViewOpenSignals
}) {
  const selectedSearchThread = result?.threads?.find(
    (thread) => thread.externalThreadId === selectedThread?.externalThreadId
  );

  return (
    <div className="process-layout">
      <section className="queue-panel">
        <div className="queue-header">
          <div>
            <h2>Search</h2>
            {result && <p className="subtle">{result.resultCount} results for "{result.query}"</p>}
          </div>
          <div className="toolbar">
            <select value={maxResults} onChange={(event) => setMaxResults(Number(event.target.value))}>
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
            </select>
          </div>
        </div>
        {result?.threads?.length > 0 ? (
          <div className="thread-list quiet">
            {result.threads.map((thread) => (
              <TriageThreadRow
                key={thread.externalThreadId}
                thread={thread}
                selected={selectedThread?.externalThreadId === thread.externalThreadId}
                onOpenThread={onOpenThread}
              />
            ))}
          </div>
        ) : (
          <EmptyState label={result ? "No matching threads." : "Search returns conversation threads."} />
        )}
      </section>

      <ThreadWorkspace
        thread={selectedThread}
        triage={selectedSearchThread}
        onCleanup={onCleanup}
        onUnsubscribe={onUnsubscribe}
        context={threadContext}
        onUpdateCategory={onUpdateCategory}
        onUpdateWorkflowState={onUpdateWorkflowState}
        onTrustThreadSender={onTrustThreadSender}
        onTrustThreadDomain={onTrustThreadDomain}
        trackedMessageLinks={trackedMessageLinks}
        trackingMessageId={trackingMessageId}
        trackingResult={trackingResult}
        onViewOpenSignals={onViewOpenSignals}
        onClose={onCloseThread}
      />
    </div>
  );
}

function ThreadWorkspace({
  thread,
  triage,
  context,
  onCleanup,
  onUnsubscribe,
  onUpdateCategory,
  onUpdateWorkflowState,
  onTrustThreadSender,
  onTrustThreadDomain,
  trackedMessageLinks,
  trackingMessageId,
  trackingResult,
  onViewOpenSignals,
  onClose,
  showHeaderDate = true
}) {
  if (!thread) {
    return (
      <section className="thread-workspace empty-thread">
        <EmptyState label="Select a conversation." />
      </section>
    );
  }

  return (
    <section className="thread-workspace">
      <ThreadReader
        thread={thread}
        trackedMessageLinks={trackedMessageLinks}
        trackingMessageId={trackingMessageId}
        trackingResult={trackingResult}
        onViewOpenSignals={onViewOpenSignals}
        onClose={onClose}
        showHeaderDate={showHeaderDate}
      />
      <ThreadContextPanel
        thread={thread}
        triage={triage}
        context={context}
        onCleanup={onCleanup}
        onUnsubscribe={onUnsubscribe}
        onUpdateCategory={onUpdateCategory}
        onUpdateWorkflowState={onUpdateWorkflowState}
        onTrustThreadSender={onTrustThreadSender}
        onTrustThreadDomain={onTrustThreadDomain}
      />
    </section>
  );
}

function ThreadReader({
  thread,
  trackedMessageLinks = [],
  trackingMessageId,
  trackingResult,
  onViewOpenSignals,
  onClose,
  showHeaderDate = true
}) {
  return (
    <div className="reader">
      <div className="reader-header">
        {onClose && (
          <button
            type="button"
            className="thread-close-button"
            aria-label="Close conversation"
            title="Close conversation"
            onClick={onClose}
          >
            ←
          </button>
        )}
        <div>
          <h2>{thread.subject || "(No subject)"}</h2>
          <div className="participants reader-participants">
            {formatParticipants(thread.participants)}
          </div>
        </div>
        <div className="reader-header-actions">
          {showHeaderDate && <span>{formatDate(thread.lastMessageAt)}</span>}
        </div>
      </div>
      <div className="message-stack">
        {thread.messages?.map((message) => {
          const trackedLink = message.direction === "OUTBOUND"
            ? trackedMessageLinks.find((link) => link.externalMessageId === message.externalMessageId)
            : null;
          const showTracking = trackedLink
            && String(trackedLink.sentMessageId) === String(trackingMessageId)
            && trackingResult;
          return (
          <article className={`message-card ${message.direction?.toLowerCase()}`} key={message.externalMessageId}>
            <div className="message-meta">
              <div>
                <strong>{message.sender || "Unknown sender"}</strong>
                <span>{message.direction}</span>
              </div>
              <span>{formatDate(message.sentAt)}</span>
            </div>
            {message.recipients?.length > 0 && (
              <div className="message-recipients">
                To: {message.recipients.join(", ")}
              </div>
            )}
            <pre className="message-body">{message.body || message.snippet || "(No message body)"}</pre>
            {message.attachments?.length > 0 && (
              <div className="attachment-list">
                {message.attachments.map((attachment) => (
                  <span className="attachment-pill" key={`${message.externalMessageId}-${attachment.filename}`}>
                    {attachment.filename} {attachment.sizeBytes ? `(${formatBytes(attachment.sizeBytes)})` : ""}
                  </span>
                ))}
              </div>
            )}
            {trackedLink && (
              <div className="message-open-signals">
                <div>
                  <strong>Open signals</strong>
                  <span>Image-load activity for this sent message</span>
                </div>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => onViewOpenSignals(trackedLink.sentMessageId)}
                >
                  {showTracking ? "Refresh signals" : "View signals"}
                </button>
                {showTracking && (
                  <div className="message-tracking-detail">
                    <TrackingSummary tracking={trackingResult} />
                  </div>
                )}
              </div>
            )}
          </article>
          );
        })}
      </div>
    </div>
  );
}

function ThreadContextPanel({
  thread,
  triage,
  context,
  onCleanup,
  onUnsubscribe,
  onUpdateCategory,
  onUpdateWorkflowState,
  onTrustThreadSender,
  onTrustThreadDomain
}) {
  const attachments = (thread.messages || []).flatMap((message) => message.attachments || []);
  const latestMessage = [...(thread.messages || [])]
    .sort((left, right) => new Date(right.sentAt || 0) - new Date(left.sentAt || 0))[0];
  const unsubscribe = [...(thread.messages || [])]
    .filter((message) => message.direction === "INBOUND" && message.unsubscribe)
    .sort((left, right) => new Date(right.sentAt || 0) - new Date(left.sentAt || 0))[0]
    ?.unsubscribe;
  const category = context?.category || "";
  const workflowState = context?.workflowState || "";
  const riskLevel = context?.phishingRiskLevel || "LOW";
  const displayWorkflow = workflowState || "ACTIVE";
  const showWorkflowBadge = displayWorkflow && displayWorkflow !== "ACTIVE";
  const reasons = context?.reasons?.length
    ? context.reasons
    : triage?.reasons?.length
      ? triage.reasons
      : ["No attention signal loaded."];

  return (
    <aside className="context-panel">
      <div className="context-hero">
        <div>
          {showWorkflowBadge && (
            <span className={`status-chip ${workflowTone(displayWorkflow)}`}>
              {workflowLabel(displayWorkflow)}
            </span>
          )}
          <strong>
            {categoryLabel(category)}
            {!context?.categoryOverride && " (Suggested)"}
          </strong>
          <span className={`label-pill ${labelTone(triage?.label)}`}>
            {labelText(triage?.label || (thread.hasUnread ? "IMPORTANT" : "FYI"))}
          </span>
        </div>
        {triage?.suggestedAction && <p>{triage.suggestedAction}</p>}
      </div>
      <div className="context-section info-section conversation-section">
        <h3>Conversation</h3>
        <strong>{primaryParticipant(thread.participants, latestMessage?.sender)}</strong>
        <span>{latestMessage?.sender || "Unknown sender"}</span>
      </div>
      {unsubscribe && (
        <div className="context-section unsubscribe-section mailing-list-section">
          <h3>Mailing list</h3>
          <strong>Unsubscribe available</strong>
          <span>
            {unsubscribe.method === "HTTPS" ? "Secure page" : "Email request"}
            {` · ${unsubscribe.destination}`}
          </span>
          <p>The sender controls this step. Opening the URL may act immediately; the app cannot confirm final status.</p>
          <button
            type="button"
            className="secondary"
            onClick={() => onUnsubscribe(unsubscribe)}
          >
            {unsubscribe.method === "HTTPS" ? "Open unsubscribe page" : "Prepare unsubscribe email"}
          </button>
        </div>
      )}
      <div className="context-section control-section">
        <h3>Control</h3>
        <label className="context-control">
          <span>Status</span>
          <select
            value={workflowState}
            disabled={!context}
            onChange={(event) => onUpdateWorkflowState(thread.externalThreadId, event.target.value)}
          >
            <option value="" disabled>Loading</option>
            {threadWorkflowStates.map((value) => (
              <option value={value} key={value}>{workflowLabel(value)}</option>
            ))}
          </select>
        </label>
        <label className="context-control">
          <span>Category</span>
          <select
            value={category}
            disabled={!context}
            onChange={(event) => onUpdateCategory(thread.externalThreadId, event.target.value)}
          >
            <option value="" disabled>Loading</option>
            {threadCategories.map((value) => (
              <option value={value} key={value}>{categoryLabel(value)}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="context-section trust-risk-section">
        <div className="section-heading-row">
          <h3>Trust & Risk</h3>
          <span className={`risk-score ${riskScoreClass(context?.phishingScore)}`}>
            {context ? `Phishing risk ${context.phishingScore}/100 · ${riskScoreLabel(context.phishingScore)}` : "Loading"}
          </span>
        </div>
        {context?.screenerStatus && (
          <div className="trust-state-grid">
            <span>Screener: {workflowLabel(context.screenerStatus)}</span>
          </div>
        )}
        {context?.phishingSignals?.length > 0 && (
          <div className="context-list compact">
            {context.phishingSignals.slice(0, 3).map((signal) => (
              <span key={signal.code}>{signal.description}</span>
            ))}
          </div>
        )}
        <div className="context-actions inline">
          <button
            type="button"
            className="secondary"
            disabled={!context || context.senderTrusted || !context.senderEmail}
            onClick={() => onTrustThreadSender(thread.externalThreadId)}
          >
            Trust sender
          </button>
          <button
            type="button"
            className="secondary"
            disabled={!context || context.domainTrusted || !context.senderDomain}
            onClick={() => onTrustThreadDomain(thread.externalThreadId)}
          >
            Trust domain
          </button>
        </div>
      </div>
      <div className="context-section info-section surfaced-section">
        <h3>Why Surfaced</h3>
        <div className="context-list">
          {reasons.map((reason) => (
            <span key={reason}>{reason}</span>
          ))}
        </div>
      </div>
      <div className="context-section info-section attachments-section">
        <h3>Attachments</h3>
        {attachments.length > 0 ? (
          <div className="context-list">
            {attachments.map((attachment) => (
              <span key={`${attachment.filename}-${attachment.sizeBytes}`}>
                {attachment.filename} {attachment.sizeBytes ? `(${formatBytes(attachment.sizeBytes)})` : ""}
              </span>
            ))}
          </div>
        ) : (
          <span className="muted">None</span>
        )}
      </div>
      <div className="context-actions quick-actions">
        <button onClick={() => onCleanup(thread.externalThreadId, "read")}>Mark read</button>
        <button onClick={() => onCleanup(thread.externalThreadId, "unread")}>Mark unread</button>
        <button
          type="button"
          disabled={!context}
          onClick={() => onUpdateWorkflowState(thread.externalThreadId, "DONE")}
        >
          Done
        </button>
        <button
          type="button"
          disabled={!context}
          onClick={() => onUpdateWorkflowState(thread.externalThreadId, "AWAITING_REPLY")}
        >
          Awaiting reply
        </button>
        <button className="danger" onClick={() => onCleanup(thread.externalThreadId, "archive")}>Archive</button>
      </div>
    </aside>
  );
}

function TriageSection({ title, label, threads, selectedThread, onOpenThread, collapsed = false }) {
  const matching = threads.filter((thread) => thread.label === label);
  if (!matching.length) {
    return null;
  }

  const content = (
    <div className="thread-list">
      {matching.map((thread) => (
        <TriageThreadRow
          key={thread.externalThreadId}
          thread={thread}
          selected={selectedThread?.externalThreadId === thread.externalThreadId}
          onOpenThread={onOpenThread}
        />
      ))}
    </div>
  );

  if (collapsed) {
    return (
      <details className="triage-section collapsed-section">
        <summary>
          <span>{title}</span>
          <strong>{matching.length}</strong>
        </summary>
        {content}
      </details>
    );
  }

  return (
    <section className="triage-section">
      <div className="section-title">
        <span>{title}</span>
        <strong>{matching.length}</strong>
      </div>
      {content}
    </section>
  );
}

function TriageThreadRow({ thread, selected, onOpenThread }) {
  return (
    <button
      className={`thread-row triage-row ${thread.hasUnread ? "unread" : ""} ${selected ? "selected" : ""}`}
      onClick={() => onOpenThread(thread.externalThreadId)}
    >
      <div className="thread-main">
        <div className="thread-title">
          <strong>{primaryParticipant(thread.participants)}</strong>
          <span>{formatDate(thread.lastMessageAt)}</span>
        </div>
        <div className="thread-subject">{thread.subject || "(No subject)"}</div>
      </div>
    </button>
  );
}

function SummaryCount({ label, value, tone }) {
  return (
    <div className={`summary-count ${tone}`}>
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function ComposeView({
  form,
  setForm,
  file,
  setFile,
  templates,
  drafts,
  groups = [],
  activeDraftId,
  draftStatus,
  draftReturnAvailable,
  templateReturnTarget,
  scheduleAt,
  setScheduleAt,
  lastSend,
  onSend,
  onSchedule,
  onSaveDraft,
  onLoadDraft,
  onDeleteDraft,
  onApplyTemplate,
  onSeeAllTemplates,
  onSeeAllDrafts,
  onBackToDrafts,
  onBackToTemplates,
  trackingMessageId,
  trackingResult,
  onViewOpenSignals
}) {
  const showBackButton = draftReturnAvailable || templateReturnTarget;

  return (
    <div className="compose-workspace">
      <section className="compose-editor">
        <div className={`compose-header ${showBackButton ? "with-back" : ""}`}>
          {showBackButton && (
            <button
              type="button"
              className="thread-close-button"
              aria-label={draftReturnAvailable ? "Back to drafts" : "Back to templates"}
              title={draftReturnAvailable ? "Back to drafts" : "Back to templates"}
              onClick={draftReturnAvailable ? onBackToDrafts : onBackToTemplates}
            >
              ←
            </button>
          )}
          <div>
            <h2>Compose</h2>
            <p className="subtle">{draftStatus || `${wordCount(form.body)} words`}</p>
          </div>
          <div className="compose-header-actions">
            {activeDraftId && <span className="draft-chip">Draft #{activeDraftId}</span>}
          </div>
        </div>
        <form className="compose-form" onSubmit={onSend}>
          <Field label="Recipients">
            <div className="recipient-control">
              <input
                value={form.recipients}
                onChange={(event) => setForm({ ...form, recipients: event.target.value })}
                placeholder="one@example.com, two@example.com"
              />
              {groups.length > 0 && (
                <select
                  aria-label="Choose recipient group"
                  value=""
                  onChange={(event) => {
                    const group = groups.find((item) => String(item.id) === event.target.value);
                    if (!group) {
                      return;
                    }
                    setForm({
                      ...form,
                      recipients: (group.members || []).join(", ")
                    });
                  }}
                >
                  <option value="">Groups</option>
                  {groups.map((group) => (
                    <option value={group.id} key={group.id}>{group.name}</option>
                  ))}
                </select>
              )}
            </div>
          </Field>
          <Field label="Subject">
            <input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} />
          </Field>
          <Field label="Body">
            <textarea
              className="body-editor"
              value={form.body}
              onChange={(event) => setForm({ ...form, body: event.target.value })}
            />
          </Field>
          <div className="compose-actions">
            <button className="primary" type="submit">Send now</button>
            <button type="button" onClick={onSchedule}>Schedule</button>
            <button type="button" onClick={onSaveDraft}>Save draft</button>
          </div>
        </form>
      </section>

      <aside className="compose-support">
        <GuidancePanel form={form} file={file} />
        <DeliveryPanel
          form={form}
          setForm={setForm}
          scheduleAt={scheduleAt}
          setScheduleAt={setScheduleAt}
          lastSend={lastSend}
          trackingMessageId={trackingMessageId}
          trackingResult={trackingResult}
          onViewOpenSignals={onViewOpenSignals}
        />
        <AttachmentPanel file={file} setFile={setFile} />
        <div className="compose-library-row">
          <TemplatePicker
            templates={templates}
            onApplyTemplate={onApplyTemplate}
            onSeeAllTemplates={onSeeAllTemplates}
          />
          <DraftShelf
            drafts={drafts}
            activeDraftId={activeDraftId}
            onLoadDraft={onLoadDraft}
            onSeeAllDrafts={onSeeAllDrafts}
          />
        </div>
      </aside>
    </div>
  );
}

function GuidancePanel({ form, file }) {
  const guidance = composeGuidance(form, file);

  return (
    <section className="support-panel">
      <div className="support-heading">
        <h2>Guidance</h2>
      </div>
      <div className="guidance-list">
        {guidance.map((item) => (
          <div className={`guidance-item ${item.tone}`} key={item.label}>
            <strong>{item.label}</strong>
            <span>{item.detail}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

function DeliveryPanel({
  form,
  setForm,
  scheduleAt,
  setScheduleAt,
  lastSend,
  trackingMessageId,
  trackingResult,
  onViewOpenSignals
}) {
  return (
    <section className="support-panel">
      <div className="support-heading">
        <h2>Delivery</h2>
      </div>
      <div className="stack">
        <Field label="Schedule">
          <input
            type="datetime-local"
            value={scheduleAt}
            onChange={(event) => setScheduleAt(event.target.value)}
          />
        </Field>
        <label className="check-row">
          <input
            type="checkbox"
            checked={form.trackOpens}
            onChange={(event) => setForm({ ...form, trackOpens: event.target.checked })}
          />
          <span>Track open signals for messages sent now</span>
        </label>
        {lastSend && (
          <div className="result-box">
            <strong>{lastSend.scheduled ? "Scheduled" : "Last sent"}</strong>
            <span>{lastSend.subject}</span>
            {lastSend.id && lastSend.tracking?.enabled && (
              <>
                <span>Open signals: {trackingStatusLabel(lastSend.tracking.status)}</span>
                <button type="button" onClick={() => onViewOpenSignals(lastSend.id)}>
                  View open signals
                </button>
                {String(lastSend.id) === String(trackingMessageId) && trackingResult && (
                  <TrackingSummary tracking={trackingResult} />
                )}
              </>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

function AttachmentPanel({ file, setFile }) {
  return (
    <section className="support-panel attachment-panel">
      <div className="support-heading">
        <h2>Attachments</h2>
      </div>
      <div className="stack">
        <Field label="File">
          <input type="file" onChange={(event) => setFile(event.target.files?.[0] || null)} />
        </Field>
        {file && <p className="subtle">{file.name}</p>}
      </div>
    </section>
  );
}

function TemplatePicker({ templates, onApplyTemplate, onSeeAllTemplates }) {
  return (
    <section className="support-panel">
      <div className="support-heading">
        <h2>Templates</h2>
      </div>
      {templates.length > 0 ? (
        <div className="asset-list">
          {templates.slice(0, 2).map((template) => (
            <button
              className="asset-row"
              type="button"
              key={template.id}
              onClick={() => onApplyTemplate(template)}
            >
              <strong>{template.name}</strong>
              <span>{template.subject}</span>
            </button>
          ))}
          {templates.length > 2 && (
            <button className="see-all-row" type="button" onClick={onSeeAllTemplates}>
              <span>See all templates</span>
              <strong>→</strong>
            </button>
          )}
        </div>
      ) : (
        <EmptyState label="No templates yet." small />
      )}
    </section>
  );
}

function DraftShelf({ drafts, activeDraftId, onLoadDraft, onSeeAllDrafts }) {
  return (
    <section className="support-panel">
      <div className="support-heading">
        <h2>Drafts</h2>
      </div>
      {drafts.length > 0 ? (
        <div className="asset-list">
          {drafts.slice(0, 2).map((draft) => (
            <div
              className={`asset-row draft-row ${activeDraftId === draft.id ? "active" : ""}`}
              key={draft.id}
            >
              <button type="button" onClick={() => onLoadDraft(draft)}>
                <strong>{draft.subject || "(No subject)"}</strong>
                <span>{formatDate(draft.updatedAt)}</span>
              </button>
            </div>
          ))}
          {drafts.length > 2 && (
            <button className="see-all-row" type="button" onClick={onSeeAllDrafts}>
              <span>See all drafts</span>
              <strong>→</strong>
            </button>
          )}
        </div>
      ) : (
        <EmptyState label="No drafts saved." small />
      )}
    </section>
  );
}

function DraftsView({
  drafts,
  onRefresh,
  onLoadDraft,
  onDeleteDraft,
  scheduledMessages,
  onRefreshScheduled,
  onCancelScheduled,
  onDeleteScheduled,
  onLoadScheduled,
  onBackToCompose
}) {
  return (
    <div className="drafts-scheduled-workspace">
      <section className="panel drafts-panel">
        <div className="panel-heading">
          {onBackToCompose && (
            <button
              type="button"
              className="thread-close-button"
              aria-label="Back to compose"
              title="Back to compose"
              onClick={onBackToCompose}
            >
              ←
            </button>
          )}
          <div>
            <h2>Drafts</h2>
            <p className="subtle">Local compose drafts.</p>
          </div>
          <button className="secondary" onClick={onRefresh}>Refresh</button>
        </div>
        {drafts.length > 0 ? (
          <div className="table-list drafts-table">
            {drafts.map((draft) => (
              <div
                className="table-row"
                key={draft.id}
                role="button"
                tabIndex={0}
                onClick={() => onLoadDraft(draft)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    onLoadDraft(draft);
                  }
                }}
              >
                <div>
                  <strong>{draft.subject || "(No subject)"}</strong>
                  <span>{(draft.recipients || []).join(", ") || "No recipients"}</span>
                  <span>Updated {formatDate(draft.updatedAt)}</span>
                </div>
                <div className="actions">
                  <button
                    className="icon-action danger"
                    type="button"
                    aria-label="Delete draft"
                    title="Delete draft"
                    onClick={(event) => {
                      event.stopPropagation();
                      onDeleteDraft(draft.id);
                    }}
                  >
                    🗑
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState label="No drafts saved." />
        )}
      </section>
      <ScheduledView
        messages={scheduledMessages}
        onRefresh={onRefreshScheduled}
        onCancel={onCancelScheduled}
        onDelete={onDeleteScheduled}
        onSelect={onLoadScheduled}
      />
    </div>
  );
}

function ManageView({
  activeTab,
  setActiveTab,
  templates,
  templateForm,
  setTemplateForm,
  onCreateTemplate,
  onDeleteTemplate,
  onUseTemplate,
  onRefreshTemplates,
  groups,
  groupForm,
  setGroupForm,
  bulkForm,
  setBulkForm,
  bulkResult,
  onCreateGroup,
  onDeleteGroup,
  onBulkSend,
  onRefreshGroups,
  scheduledMessages,
  onRefreshScheduled,
  onCancelScheduled,
  onDeleteScheduled,
  screenerForm,
  setScreenerForm,
  evaluation,
  pending,
  onEvaluateSender,
  onRefreshPending,
  onApproveSender,
  onApproveDomain,
  onRejectSender,
  trackedMessages,
  trackingMessageId,
  trackingResult,
  onSelectTracking,
  onRefreshTracking,
  phishingForm,
  setPhishingForm,
  phishingResult,
  onAnalyzePhishing,
  trust,
  onRefreshTrust,
  onTrustSender,
  onTrustDomain
}) {
  const tabs = [
    ["scheduled", "Scheduled", scheduledMessages.length],
    ["templates", "Templates & Groups", templates.length + groups.length],
    ["screener", "Screener & Trust", pending.length + (trust.senders?.length || 0) + (trust.domains?.length || 0)],
    ["signals", "Signals", trackedMessages.length]
  ];

  return (
    <div className="manage-workspace">
      <section className="manage-overview">
        <div className="manage-heading">
          <div>
            <h2>Manage</h2>
            <p className="subtle">Secondary tools and controls.</p>
          </div>
        </div>
        <div className="manage-metrics">
          <ManageMetric label="Scheduled" value={scheduledMessages.length} />
          <ManageMetric label="Templates & Groups" value={templates.length + groups.length} />
          <ManageMetric label="Screener & Trust" value={pending.length + (trust.senders?.length || 0) + (trust.domains?.length || 0)} tone={pending.length > 0 ? "amber" : ""} />
        </div>
        <div className="manage-tabs">
          {tabs.map(([key, label, count]) => (
            <button
              key={key}
              className={activeTab === key ? "active" : ""}
              onClick={() => setActiveTab(key)}
            >
              <span>{label}</span>
              <strong>{count}</strong>
            </button>
          ))}
        </div>
      </section>

      <section className="manage-content">
        {activeTab === "scheduled" && (
          <ScheduledView
            messages={scheduledMessages}
            onRefresh={onRefreshScheduled}
            onCancel={onCancelScheduled}
            onDelete={onDeleteScheduled}
          />
        )}
        {(activeTab === "templates" || activeTab === "contacts") && (
          <TemplatesGroupsView
            templates={visibleTemplates}
            templateForm={templateForm}
            setTemplateForm={setTemplateForm}
            onCreateTemplate={onCreateTemplate}
            onDeleteTemplate={onDeleteTemplate}
            onUseTemplate={onUseTemplate}
            onRefreshTemplates={onRefreshTemplates}
            groups={groups}
            groupForm={groupForm}
            setGroupForm={setGroupForm}
            onCreateGroup={onCreateGroup}
            onDeleteGroup={onDeleteGroup}
            onRefreshGroups={onRefreshGroups}
          />
        )}
        {activeTab === "screener" && (
          <ScreenerTrustView
            form={screenerForm}
            setForm={setScreenerForm}
            evaluation={evaluation}
            pending={pending}
            onEvaluate={onEvaluateSender}
            onRefresh={onRefreshPending}
            onApproveSender={onApproveSender}
            onApproveDomain={onApproveDomain}
            onReject={onRejectSender}
            trust={trust}
            onRefreshTrust={onRefreshTrust}
            onTrustSender={onTrustSender}
            onTrustDomain={onTrustDomain}
          />
        )}
        {activeTab === "signals" && (
          <div className="signals-grid">
            <TrackingView
              messages={trackedMessages}
              selectedMessageId={trackingMessageId}
              result={trackingResult}
              onSelect={onSelectTracking}
              onRefresh={onRefreshTracking}
            />
            <SecurityView
              form={phishingForm}
              setForm={setPhishingForm}
              result={phishingResult}
              onAnalyze={onAnalyzePhishing}
            />
          </div>
        )}
      </section>
    </div>
  );
}

function ManageMetric({ label, value, tone = "" }) {
  return (
    <div className={`manage-metric ${tone}`}>
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function TemplatesGroupsView({
  templates,
  templateForm,
  setTemplateForm,
  onCreateTemplate,
  onDeleteTemplate,
  onUseTemplate,
  onRefreshTemplates,
  groups,
  groupForm,
  setGroupForm,
  onCreateGroup,
  onDeleteGroup,
  onRefreshGroups,
  onBackToCompose
}) {
  return (
    <div className="templates-groups-workspace">
      <TemplatesView
        templates={templates}
        form={templateForm}
        setForm={setTemplateForm}
        onCreate={onCreateTemplate}
        onDelete={onDeleteTemplate}
        onUse={onUseTemplate}
        onRefresh={onRefreshTemplates}
        onBackToCompose={onBackToCompose}
      />
      <GroupsView
        groups={groups}
        form={groupForm}
        setForm={setGroupForm}
        onCreate={onCreateGroup}
        onDelete={onDeleteGroup}
        onRefresh={onRefreshGroups}
      />
    </div>
  );
}

function TemplatesView({ templates, form, setForm, onCreate, onDelete, onUse, onRefresh, onBackToCompose }) {
  const [creatingTemplate, setCreatingTemplate] = useState(false);
  const [activeCategory, setActiveCategory] = useState("All");
  const [creatingCategory, setCreatingCategory] = useState(false);
  const categories = ["All", ...Array.from(new Set(templates.map((template) => template.category || "General")))];
  const templateCategories = categories.filter((category) => category !== "All");
  const visibleTemplates = activeCategory === "All"
    ? templates
    : templates.filter((template) => (template.category || "General") === activeCategory);

  return (
    <div className="content-grid asset-workspace templates-workspace">
      <section className="panel asset-list-panel">
        <div className="panel-heading">
          {onBackToCompose && (
            <button
              type="button"
              className="thread-close-button"
              aria-label="Back to compose"
              title="Back to compose"
              onClick={onBackToCompose}
            >
              ←
            </button>
          )}
          <div>
            <h2>Templates</h2>
            <p className="subtle">Reusable replies and message patterns.</p>
          </div>
          <div className="actions">
            {templates.length > 0 && (
              <div className="template-filter-control">
                <select value={activeCategory} onChange={(event) => setActiveCategory(event.target.value)}>
                  {categories.map((category) => (
                    <option value={category} key={category}>{category}</option>
                  ))}
                </select>
              </div>
            )}
            <button
              className="primary"
              type="button"
              onClick={() => {
                setCreatingCategory(false);
                setCreatingTemplate(true);
              }}
            >
              Create template
            </button>
          </div>
        </div>
        {templates.length > 0 ? (
          <>
          <div className="asset-card-grid">
            {visibleTemplates.map((template) => (
              <article className="asset-card template-card" key={template.id}>
                <div className="asset-card-main">
                  <div className="asset-card-title">
                    <strong>{template.name}</strong>
                    <span>{template.builtIn ? "Starter" : template.category || "General"}</span>
                  </div>
                  <span className="asset-subject">{template.subject || "(No subject)"}</span>
                  <p>{previewText(template.body, 150)}</p>
                  <div className="asset-meta">
                    <span>Used {template.usageCount || 0} times</span>
                    {template.createdAt && <span>Created {formatDate(template.createdAt)}</span>}
                  </div>
                </div>
                <div className="actions">
                  <button className="primary" onClick={() => onUse(template)}>Use</button>
                  {!template.builtIn && (
                    <button className="danger" onClick={() => onDelete(template.id)}>Delete</button>
                  )}
                </div>
              </article>
            ))}
          </div>
          </>
        ) : (
          <EmptyState label="No templates yet." />
        )}
      </section>
      {creatingTemplate && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setCreatingTemplate(false)}>
          <section
            className="template-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-template-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-heading">
              <div>
                <h2 id="create-template-title">Create Template</h2>
                <p className="subtle">Save a reusable email pattern.</p>
              </div>
              <button
                type="button"
                className="thread-close-button"
                aria-label="Close create template"
                onClick={() => setCreatingTemplate(false)}
              >
                ×
              </button>
            </div>
            <form
              className="template-modal-form"
              onSubmit={async (event) => {
                await onCreate(event);
                setCreatingTemplate(false);
              }}
            >
              <div className="template-modal-fields">
                <Field label="Name">
                  <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
                </Field>
                <Field label="Category">
                  <select
                    value={creatingCategory ? "__new__" : form.category}
                    onChange={(event) => {
                      if (event.target.value === "__new__") {
                        setCreatingCategory(true);
                        setForm({ ...form, category: "" });
                        return;
                      }
                      setCreatingCategory(false);
                      setForm({ ...form, category: event.target.value });
                    }}
                  >
                    {templateCategories.map((category) => (
                      <option value={category} key={category}>{category}</option>
                    ))}
                    <option value="__new__">New category…</option>
                  </select>
                </Field>
                {creatingCategory && (
                  <Field label="New Category">
                    <input
                      value={form.category}
                      onChange={(event) => setForm({ ...form, category: event.target.value })}
                      placeholder="e.g. Support"
                    />
                  </Field>
                )}
                <Field label="Subject">
                  <input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} />
                </Field>
              </div>
              <Field label="Body">
                <textarea value={form.body} onChange={(event) => setForm({ ...form, body: event.target.value })} />
              </Field>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setCreatingTemplate(false)}>
                  Cancel
                </button>
                <button className="primary" type="submit">Save template</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

function GroupsView({
  groups,
  form,
  setForm,
  onCreate,
  onDelete,
  onRefresh
}) {
  const [creatingGroup, setCreatingGroup] = useState(false);

  return (
    <div className="content-grid asset-workspace groups-workspace">
      <section className="panel asset-list-panel groups-panel">
        <div className="panel-heading">
          <div>
            <h2>Groups</h2>
            <p className="subtle">Recipient relationship sets.</p>
          </div>
          <div className="actions">
            <button className="primary" type="button" onClick={() => setCreatingGroup(true)}>
              Create group
            </button>
          </div>
        </div>
        {groups.length > 0 ? (
          <div className="joined-list-card">
            {groups.map((group) => (
              <article className="joined-list-row contact-card" key={group.id}>
                <div className="asset-card-main">
                  <strong>{group.name}</strong>
                  <span className="asset-subject">{group.memberCount} members</span>
                  <p>{previewText(group.members?.join(", "), 180)}</p>
                </div>
                <div className="actions">
                  <button className="danger" onClick={() => onDelete(group.id)}>Delete</button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <EmptyState label="No recipient groups yet." />
        )}
      </section>
      {creatingGroup && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setCreatingGroup(false)}>
          <section
            className="template-modal group-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-group-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-heading">
              <div>
                <h2 id="create-group-title">Create Group</h2>
                <p className="subtle">Save a reusable recipient set.</p>
              </div>
              <button
                type="button"
                className="thread-close-button"
                aria-label="Close create group"
                onClick={() => setCreatingGroup(false)}
              >
                ×
              </button>
            </div>
            <form
              className="template-modal-form group-modal-form"
              onSubmit={async (event) => {
                await onCreate(event);
                setCreatingGroup(false);
              }}
            >
              <Field label="Name">
                <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
              </Field>
              <Field label="Members">
                <textarea
                  value={form.members}
                  onChange={(event) => setForm({ ...form, members: event.target.value })}
                  placeholder="one@example.com, two@example.com"
                />
              </Field>
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setCreatingGroup(false)}>
                  Cancel
                </button>
                <button className="primary" type="submit">Save group</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

function ContactsTrustView({
  groups,
  form,
  setForm,
  bulkForm,
  setBulkForm,
  bulkResult,
  onCreate,
  onDelete,
  onBulkSend,
  onRefresh,
  trust,
  onRefreshTrust,
  onTrustSender,
  onTrustDomain
}) {
  return (
    <div className="contacts-trust-workspace">
      <GroupsView
        groups={groups}
        form={form}
        setForm={setForm}
        bulkForm={bulkForm}
        setBulkForm={setBulkForm}
        bulkResult={bulkResult}
        onCreate={onCreate}
        onDelete={onDelete}
        onBulkSend={onBulkSend}
        onRefresh={onRefresh}
      />
      <TrustView
        trust={trust}
        onRefresh={onRefreshTrust}
        onTrustSender={onTrustSender}
        onTrustDomain={onTrustDomain}
      />
    </div>
  );
}

function ScheduledView({ messages, onRefresh, onCancel, onDelete, onSelect }) {
  return (
    <section className="panel scheduled-panel">
      <div className="panel-heading">
        <h2>Scheduled Messages</h2>
        <button className="secondary" onClick={onRefresh}>Refresh</button>
      </div>
      {messages.length > 0 ? (
        <div className="table-list scheduled-list">
          {messages.map((message) => (
            <div
              className="table-row scheduled-row"
              key={message.id}
              role="button"
              tabIndex={0}
              onClick={() => onSelect?.(message)}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  onSelect?.(message);
                }
              }}
            >
              <div className="scheduled-row-main">
                <strong>{message.subject || "(No subject)"}</strong>
                <span>{(message.recipients || []).join(", ") || "No recipients"}</span>
                {message.failureReason && <span className="scheduled-error">{message.failureReason}</span>}
              </div>
              <div className="scheduled-row-side">
                <span className={`status-chip inline ${statusTone(message.status)}`}>
                  {workflowLabel(message.status)}
                </span>
                <span>{formatDate(message.scheduledFor)}</span>
                {String(message.status || "").toUpperCase() === "PENDING" && (
                  <button
                    className="danger"
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      onCancel(message.id);
                    }}
                  >
                    Cancel
                  </button>
                )}
                <button
                  className="icon-action danger"
                  type="button"
                  aria-label="Delete scheduled message"
                  title="Delete scheduled message"
                  onClick={(event) => {
                    event.stopPropagation();
                    onDelete(message.id);
                  }}
                >
                  🗑
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState label="No scheduled messages." />
      )}
    </section>
  );
}

function TrackingView({ messages, selectedMessageId, result, onSelect, onRefresh }) {
  const selectedMessage = messages.find((message) => String(message.sentMessageId) === String(selectedMessageId));

  return (
    <div className="content-grid tracking-workspace">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Sent Emails</h2>
            <p className="subtle">Recent sent messages with open-signal tracking.</p>
          </div>
          <button className="secondary" type="button" onClick={onRefresh}>Refresh</button>
        </div>
        {messages.length > 0 ? (
          <div className="asset-list tracking-list">
            {messages.map((message) => (
              <button
                className={`thread-row triage-row tracking-message-row ${
                  String(message.sentMessageId) === String(selectedMessageId) ? "active" : ""
                }`}
                type="button"
                key={message.sentMessageId}
                onClick={() => onSelect(message.sentMessageId)}
              >
                <div className="thread-main">
                  <div className="thread-title">
                    <strong>{message.recipient || "No recipient"}</strong>
                  </div>
                  <div className="thread-subject">{message.subject || "(No subject)"}</div>
                </div>
              </button>
            ))}
          </div>
        ) : (
          <EmptyState label="No tracked messages yet." />
        )}
      </section>
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Email Signals</h2>
            <p className="subtle">Open-tracking status for the selected sent email.</p>
          </div>
        </div>
        {result ? (
          <TrackingSummary tracking={result} message={selectedMessage} variant="text" />
        ) : (
          <EmptyState label="No open signals loaded." />
        )}
      </section>
    </div>
  );
}

function TrackingSummary({ tracking, message, variant = "cards" }) {
  if (variant === "text") {
    const opened = tracking.status === "IMAGE_LOAD_DETECTED" || tracking.pixelLoadCount > 0;
    const insight = !tracking.enabled
      ? "Open tracking is off for this email."
      : opened
        ? "This email has loaded the tracking image at least once."
        : "No tracking image load has been seen yet.";
    const rows = [
      ["Tracking", tracking.enabled ? "On" : "Off"],
      ["Current state", trackingStatusLabel(tracking.status), trackingTone(tracking.status)],
      ["First signal", formatDate(tracking.firstPixelLoadedAt) || "None"],
      ["Last signal", formatDate(tracking.lastPixelLoadedAt) || "None"]
    ];

    return (
      <div className="tracking-text-summary">
        <div className="tracking-summary-top">
          {message && (
            <div className="tracking-email-context">
              <strong>{message.subject || "(No subject)"}</strong>
              <span>{message.recipient || "No recipient"}</span>
              <span>Sent {formatDate(message.sentAt)}</span>
            </div>
          )}
          <div className={`tracking-insight ${opened ? "green" : "blue"}`}>
            <strong>{opened ? "Open activity found" : "Waiting for activity"}</strong>
            <span>{insight}</span>
          </div>
        </div>
        <div className="tracking-summary-body">
          <div className="tracking-details-heading">
            <h3>Tracking Details</h3>
            <strong>{tracking.pixelLoadCount} {tracking.pixelLoadCount === 1 ? "signal" : "signals"}</strong>
          </div>
          <dl>
            {rows.map(([label, value, tone]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd className={tone || undefined}>{value}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="tracking-events-text">
          <h3>Signal History</h3>
          {(tracking.recentEvents || []).length > 0 ? (
            <ul>
              {tracking.recentEvents.map((event) => (
                <li key={event.id}>
                  <strong>{eventSourceLabel(event.source)}</strong>
                  <span>{event.imageFormat} · {formatDate(event.loadedAt)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="subtle">No image-load signals detected.</p>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="phishing open-signals">
      <div className="metric-row">
        <div>
          <span>Enabled</span>
          <strong>{tracking.enabled ? "Yes" : "No"}</strong>
        </div>
        <div>
          <span>Status</span>
          <strong className={trackingTone(tracking.status)}>{trackingStatusLabel(tracking.status)}</strong>
        </div>
        <div>
          <span>Signals</span>
          <strong>{tracking.pixelLoadCount}</strong>
        </div>
        <div>
          <span>First signal</span>
          <strong>{formatDate(tracking.firstPixelLoadedAt) || "None"}</strong>
        </div>
        <div>
          <span>Last signal</span>
          <strong>{formatDate(tracking.lastPixelLoadedAt) || "None"}</strong>
        </div>
      </div>
      {(tracking.recentEvents || []).length > 0 ? (
        <div className="table-list">
          {tracking.recentEvents.map((event) => (
            <div className="trust-row" key={event.id}>
              <strong>{eventSourceLabel(event.source)}</strong>
              <span>{event.imageFormat} · {formatDate(event.loadedAt)}</span>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState label="No image-load signals detected." small />
      )}
    </div>
  );
}

function ScreenerView({
  form,
  setForm,
  evaluation,
  pending,
  onEvaluate,
  onRefresh,
  onApproveSender,
  onApproveDomain,
  onReject
}) {
  return (
    <section className="panel screener-decision-card">
      <div className="screener-decision-section">
        <div className="panel-heading">
          <h2>Evaluate Sender</h2>
        </div>
        <form className="stack sender-evaluate-form" onSubmit={onEvaluate}>
          <Field label="Sender">
            <input value={form.sender} onChange={(event) => setForm({ ...form, sender: event.target.value })} />
          </Field>
          <Field label="Subject">
            <input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} />
          </Field>
          <Field label="Body">
            <textarea value={form.body} onChange={(event) => setForm({ ...form, body: event.target.value })} />
          </Field>
          <button className="primary" type="submit">Evaluate</button>
        </form>
      </div>
      <div className="screener-decision-split" />
      <div className="screener-decision-section">
        <div className="panel-heading">
          <h2>Decision</h2>
        </div>
        {evaluation ? (
          <DecisionCard
            evaluation={evaluation}
            onApproveSender={onApproveSender}
            onApproveDomain={onApproveDomain}
            onReject={onReject}
          />
        ) : (
          <div className="decision-placeholder">
            <strong>No sender evaluated</strong>
            <span>Run an evaluation to see trust status, risk score, and available actions here.</span>
          </div>
        )}
      </div>
    </section>
  );
}

function ScreenerTrustView({
  form,
  setForm,
  evaluation,
  pending,
  onEvaluate,
  onRefresh,
  onApproveSender,
  onApproveDomain,
  onReject,
  trust,
  onRefreshTrust,
  onTrustSender,
  onTrustDomain
}) {
  return (
    <div className="screener-trust-workspace">
      <ScreenerView
        form={form}
        setForm={setForm}
        evaluation={evaluation}
        pending={pending}
        onEvaluate={onEvaluate}
        onRefresh={onRefresh}
        onApproveSender={onApproveSender}
        onApproveDomain={onApproveDomain}
        onReject={onReject}
      />
      <section className="panel screener-trust-bottom-card">
        <div className="screener-trust-column trust-side">
          <TrustView
            trust={trust}
            onRefresh={onRefreshTrust}
            onTrustSender={onTrustSender}
            onTrustDomain={onTrustDomain}
          />
        </div>
      </section>
    </div>
  );
}

function SecurityView({ form, setForm, result, onAnalyze }) {
  return (
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <h2>Analyze Message</h2>
        </div>
        <form className="stack" onSubmit={onAnalyze}>
          <Field label="Sender">
            <input value={form.sender} onChange={(event) => setForm({ ...form, sender: event.target.value })} />
          </Field>
          <Field label="Subject">
            <input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} />
          </Field>
          <Field label="Body">
            <textarea value={form.body} onChange={(event) => setForm({ ...form, body: event.target.value })} />
          </Field>
          <button className="primary" type="submit">Analyze</button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <h2>Signals</h2>
        </div>
        {result ? <PhishingSummary phishing={result} /> : <EmptyState label="No analysis yet." />}
      </section>
    </div>
  );
}

function TrustView({ trust, onRefresh, onTrustSender, onTrustDomain }) {
  const [sender, setSender] = useState("ercan@university.edu");
  const [domain, setDomain] = useState("university.edu");

  return (
    <div className="content-grid">
      <section className="panel trust-joined-panel">
        <div className="trust-layout">
          <div className="trust-left-column">
            <div className="panel-heading">
              <div>
                <h2>Trust</h2>
                <p className="subtle">Approved senders and domains.</p>
              </div>
            </div>
            <div className="stack joined-panel-section trust-control-rows">
              <Field label="Domain">
                <div className="inline-form">
                  <input value={domain} onChange={(event) => setDomain(event.target.value)} />
                  <button onClick={() => onTrustDomain(domain)}>Trust</button>
                </div>
              </Field>
              <Field label="Sender">
                <div className="inline-form">
                  <input value={sender} onChange={(event) => setSender(event.target.value)} />
                  <button onClick={() => onTrustSender(sender)}>Trust</button>
                </div>
              </Field>
            </div>
          </div>
          <div className="trust-right-column">
            <TrustList title="Domains" entries={trust.domains || []} />
            <div className="joined-section-divider" />
            <TrustList title="Senders" entries={trust.senders || []} />
          </div>
        </div>
      </section>
    </div>
  );
}

function DecisionCard({ evaluation, onApproveSender, onApproveDomain, onReject }) {
  const entry = evaluation.entry;
  const phishing = evaluation.phishing || {};
  const signals = phishing.signals || [];

  return (
    <div className="decision-card">
      <div className="decision-summary">
        <div>
          <span>Status</span>
          <strong>{workflowLabel(evaluation.status)}</strong>
        </div>
        <div>
          <span>Trust</span>
          <strong>{trustLabel(phishing.trust)}</strong>
        </div>
      </div>
      {entry && (
        <div className="identity">
          <strong>{entry.senderEmail}</strong>
          <span>{entry.senderDomain}</span>
        </div>
      )}
      <div className="decision-signals">
        <h3>Signals</h3>
        {signals.length > 0 ? (
          signals.slice(0, 3).map((signal) => (
            <div className="decision-signal-row" key={signal.code}>
              <strong>{signal.code}</strong>
              <span>{signal.description}</span>
            </div>
          ))
        ) : (
          <div className="decision-signal-row muted">
            <strong>Clean</strong>
            <span>No suspicious signals detected.</span>
          </div>
        )}
      </div>
      {entry && evaluation.requiresDecision && (
        <div className="actions stretch">
          <button onClick={() => onApproveSender(entry.id)}>Approve sender</button>
          <button onClick={() => onApproveDomain(entry.id)}>Approve domain</button>
          <button className="danger" onClick={() => onReject(entry.id)}>Reject</button>
        </div>
      )}
    </div>
  );
}

function PhishingSummary({ phishing, compact = false }) {
  if (!phishing) {
    return null;
  }

  return (
    <div className={compact ? "phishing compact" : "phishing"}>
      <div className="metric-row">
        <div>
          <span>Risk</span>
          <strong className={riskClass(phishing.riskLevel)}>{phishing.riskLevel}</strong>
        </div>
        <div>
          <span>Score</span>
          <strong>{phishing.score}</strong>
        </div>
        <div>
          <span>Trust</span>
          <strong>{trustLabel(phishing.trust)}</strong>
        </div>
      </div>
      {phishing.signals?.length > 0 ? (
        <ul className="signals">
          {phishing.signals.map((signal) => (
            <li key={signal.code}>
              <strong>{signal.code}</strong>
              <span>{signal.description}</span>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState label="No phishing signals detected." small />
      )}
    </div>
  );
}

function TrustList({ title, entries }) {
  return (
    <div className="trust-list">
      <h3>{title}</h3>
      {entries.length > 0 ? (
        entries.map((entry) => (
          <div className="joined-list-row trust-row" key={entry.id}>
            <strong>{entry.value}</strong>
            <span>{entry.scope}</span>
          </div>
        ))
      ) : (
        <EmptyState label={`No trusted ${title.toLowerCase()}.`} small />
      )}
    </div>
  );
}

function NavGroup({ title, items, activeView, setActiveView, toggleNavigation }) {
  return (
    <div className="nav-group">
      <span>{title}</span>
      {items.map(([key, label, icon]) => (
        <button
          key={key}
          className={activeView === key ? "active" : ""}
          onClick={() => setActiveView(key)}
          onDoubleClick={toggleNavigation}
          aria-current={activeView === key ? "page" : undefined}
          aria-label={label}
          title={label}
        >
          <NavIcon name={icon} />
          <span className="nav-label">{label}</span>
        </button>
      ))}
    </div>
  );
}

function NavIcon({ name }) {
  const paths = {
    menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
    collapse: <><path d="M15 5l-7 7 7 7" /><path d="M20 5l-7 7 7 7" /></>,
    attention: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" /><path d="M10 21h4" /></>,
    compose: <><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z" /></>,
    drafts: <><path d="M6 2h9l4 4v16H6z" /><path d="M14 2v5h5M9 12h6M9 16h6" /></>,
    templates: <><rect x="4" y="4" width="16" height="16" rx="2" /><path d="M4 9h16M9 9v11" /></>,
    contacts: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></>,
    scheduled: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
    tracking: <><path d="M4 17c5-8 11-8 16 0" /><path d="M9 17a3 3 0 0 1 6 0" /><path d="M12 17h.01" /></>,
    screener: <><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10" /><path d="m9 12 2 2 4-4" /></>,
    trust: <><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10" /><path d="M9 12h6M12 9v6" /></>,
    moon: <><path d="M21 12.8A8.6 8.6 0 1 1 11.2 3a6.7 6.7 0 0 0 9.8 9.8Z" /></>,
    sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" /></>
  };
  return (
    <svg className="nav-icon" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {paths[name] || paths.menu}
    </svg>
  );
}

function AccountAvatar({ account }) {
  if (account?.picture) {
    return <img className="avatar compact" src={account.picture} alt="" />;
  }
  const initial = account?.email?.slice(0, 1).toUpperCase() || "?";
  return <span className="avatar compact placeholder" aria-hidden="true">{initial}</span>;
}

function AccountCard({ account }) {
  if (!account?.email) {
    return (
      <div className="account-card">
        <div className="avatar placeholder">?</div>
        <div>
          <strong>Not signed in</strong>
          <span>Connect Gmail to load mail.</span>
        </div>
      </div>
    );
  }

  return (
    <div className="account-card">
      {account.picture ? (
        <img className="avatar" src={account.picture} alt="" />
      ) : (
        <div className="avatar placeholder">{account.email.slice(0, 1).toUpperCase()}</div>
      )}
      <div>
        <strong>{account.name || account.email}</strong>
        <span>{account.email}</span>
      </div>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}

function EmptyState({ label, small = false }) {
  return <div className={small ? "empty small" : "empty"}>{label}</div>;
}

function viewTitle(view) {
  const titles = {
    inbox: "Attention",
    search: "Search results",
    compose: "Compose",
    drafts: "Drafts & Scheduled",
    manage: "Manage",
    templates: "Templates & Groups",
    groups: "Templates & Groups",
    trust: "Screener & Trust",
    tracking: "Sent Activity",
    screener: "Screener & Trust",
    security: "Security signals"
  };
  return titles[view] || "Workspace";
}

function modeLabel(view) {
  if (["compose", "drafts", "templates", "groups"].includes(view)) {
    return "Create";
  }
  if (view === "manage") {
    return "Control";
  }
  if (view === "tracking") {
    return "Manage";
  }
  if (["security", "trust"].includes(view)) {
    return "Safety";
  }
  return "Workspace";
}

function readThemePreference() {
  try {
    const saved = window.localStorage.getItem(themeStorageKey);
    if (saved === "dark" || saved === "light") {
      return saved;
    }
  } catch {
    // Fall through to system preference.
  }
  if (typeof window !== "undefined" && window.matchMedia?.("(prefers-color-scheme: dark)").matches) {
    return "dark";
  }
  return "light";
}

function readTrackedMessageLinks() {
  try {
    const value = window.localStorage.getItem(trackingLinkStorageKey);
    const links = value ? JSON.parse(value) : [];
    return Array.isArray(links) ? links : [];
  } catch {
    return [];
  }
}

function writeTrackedMessageLinks(links) {
  try {
    window.localStorage.setItem(trackingLinkStorageKey, JSON.stringify(links));
  } catch {
    // Signal details still work during this session if browser storage is unavailable.
  }
}

function parseList(value) {
  return (value || "")
    .split(/[\n,;]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function patchThreadListResponse(current, threadId, patch) {
  if (!current?.threads?.length) {
    return current;
  }
  return {
    ...current,
    threads: current.threads.map((thread) => (
      thread.externalThreadId === threadId ? { ...thread, ...patch } : thread
    ))
  };
}

function hasDraftContent(form) {
  return Boolean(
    form.recipients?.trim()
    || form.subject?.trim()
    || form.body?.trim()
  );
}

function draftSnapshot(form, scheduleAt) {
  return JSON.stringify({
    recipients: parseList(form.recipients),
    subject: form.subject || "",
    body: form.body || "",
    scheduledFor: scheduleAt ? new Date(scheduleAt).toISOString() : null
  });
}

function composeGuidance(form, file) {
  const recipients = parseList(form.recipients);
  const body = form.body || "";
  const linkCount = (body.match(/https?:\/\//gi) || []).length;
  const items = [];

  if (!recipients.length) {
    items.push({
      tone: "warning",
      label: "Recipient missing",
      detail: "Add at least one recipient before sending."
    });
  } else {
    items.push({
      tone: "ok",
      label: "Recipients ready",
      detail: `${recipients.length} recipient${recipients.length === 1 ? "" : "s"} selected.`
    });
  }

  if (!form.subject?.trim()) {
    items.push({
      tone: "warning",
      label: "Subject missing",
      detail: "A clear subject helps the message get handled."
    });
  } else {
    items.push({
      tone: "ok",
      label: "Subject ready",
      detail: "The message has a subject."
    });
  }

  if (!body.trim()) {
    items.push({
      tone: "warning",
      label: "Body missing",
      detail: "The message body is empty."
    });
  } else if (body.trim().length < 25) {
    items.push({
      tone: "notice",
      label: "Short body",
      detail: "Check that the recipient has enough context."
    });
  } else {
    items.push({
      tone: "ok",
      label: "Body ready",
      detail: `${body.trim().split(/\s+/).length} words.`
    });
  }

  if (linkCount > 3) {
    items.push({
      tone: "notice",
      label: "Several links",
      detail: `${linkCount} links may make the message harder to scan.`
    });
  }

  if (file) {
    items.push({
      tone: "ok",
      label: "Attachment added",
      detail: file.name
    });
  }

  return items;
}

function defaultDateTimeLocal() {
  const date = new Date(Date.now() + 30 * 60 * 1000);
  date.setSeconds(0, 0);
  return toDateTimeLocal(date);
}

function toDateTimeLocal(date) {
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function formatDate(value) {
  if (!value) {
    return "";
  }
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatTimeOnly(value) {
  if (!value) {
    return "";
  }
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatParticipants(participants = []) {
  if (!participants.length) {
    return "No participants";
  }
  return participants.slice(0, 4).join(", ");
}

function wordCount(value) {
  const words = (value || "").trim().split(/\s+/).filter(Boolean);
  return words.length;
}

function previewText(value, limit = 120) {
  const text = (value || "").replace(/\s+/g, " ").trim();
  if (!text) {
    return "No preview";
  }
  if (text.length <= limit) {
    return text;
  }
  return `${text.slice(0, limit - 1).trim()}...`;
}

function primaryParticipant(participants = [], fallback = "") {
  const value = participants.find((participant) => participant && !participant.includes("No participants"))
    || fallback
    || "Unknown";
  return value.replace(/<[^>]+>/g, "").trim() || value;
}

function formatBytes(value) {
  if (!value) {
    return "";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${Math.round(value / 1024)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function riskClass(level) {
  if (level === "HIGH") {
    return "high";
  }
  if (level === "MEDIUM") {
    return "medium";
  }
  return "low";
}

function riskScoreClass(score) {
  if (score == null) {
    return "neutral";
  }
  if (score >= 70) {
    return "high";
  }
  if (score >= 35) {
    return "medium";
  }
  return "low";
}

function riskScoreLabel(score) {
  if (score == null) {
    return "Unknown";
  }
  if (score >= 70) {
    return "High";
  }
  if (score >= 35) {
    return "Moderate";
  }
  return "Low";
}

function trustLabel(trust) {
  if (!trust) {
    return "None";
  }
  if (trust.senderTrusted) {
    return "Sender";
  }
  if (trust.domainTrusted) {
    return "Domain";
  }
  return "None";
}

function labelText(label) {
  const labels = {
    SECURITY_REVIEW: "Security review",
    NEEDS_REPLY: "Needs action",
    IMPORTANT: "Important",
    WAITING: "Awaiting reply",
    LOW_PRIORITY: "Low priority",
    FYI: "FYI"
  };
  return labels[label] || "FYI";
}

function labelTone(label) {
  const tones = {
    SECURITY_REVIEW: "red",
    NEEDS_REPLY: "amber",
    IMPORTANT: "blue",
    WAITING: "neutral",
    LOW_PRIORITY: "soft",
    FYI: "soft"
  };
  return tones[label] || "soft";
}

function workflowTone(value) {
  const tones = {
    NEEDS_ACTION: "amber",
    AWAITING_REPLY: "blue",
    DONE: "green",
    ARCHIVED: "soft",
    SNOOZED: "purple",
    ACTIVE: "neutral"
  };
  return tones[value] || "neutral";
}

function categoryTone(value) {
  const tones = {
    PEOPLE: "blue",
    THINGS: "neutral",
    NOISE: "soft"
  };
  return tones[value] || "neutral";
}

function riskTone(value) {
  const tones = {
    HIGH: "red",
    MEDIUM: "amber",
    LOW: "soft"
  };
  return tones[value] || "neutral";
}

function statusTone(value) {
  const tones = {
    PENDING: "blue",
    SENT: "green",
    FAILED: "red",
    CANCELLED: "soft",
    CANCELED: "soft"
  };
  return tones[String(value || "").toUpperCase()] || "neutral";
}

function trackingTone(value) {
  if (value === "IMAGE_LOAD_DETECTED") {
    return "green";
  }
  if (value === "AWAITING_IMAGE_LOAD") {
    return "amber";
  }
  return "neutral";
}

function trackingStatusLabel(value) {
  const labels = {
    IMAGE_LOAD_DETECTED: "Open signal detected",
    AWAITING_IMAGE_LOAD: "Awaiting open signal",
    DISABLED: "Off"
  };
  return labels[value] || workflowLabel(value);
}

function eventSourceLabel(value) {
  const labels = {
    GOOGLE_IMAGE_PROXY: "Google image proxy",
    APPLE_MAIL_PRIVACY_PROXY: "Apple Mail privacy proxy",
    MICROSOFT_IMAGE_PROXY: "Microsoft image proxy",
    SECURITY_SCANNER: "Security scanner",
    BROWSER: "Browser",
    UNKNOWN: "Unknown source"
  };
  return labels[value] || "Unknown source";
}

function categoryLabel(value) {
  const labels = {
    PEOPLE: "People",
    THINGS: "Things",
    NOISE: "Noise"
  };
  return labels[value] || "Not set";
}

function workflowLabel(value) {
  const labels = {
    ACTIVE: "Active",
    NEEDS_ACTION: "Needs action",
    AWAITING_REPLY: "Awaiting reply",
    DONE: "Done",
    ARCHIVED: "Archived",
    SNOOZED: "Snoozed",
    PENDING: "Pending",
    CANCELLED: "Canceled",
    CANCELED: "Canceled",
    APPROVED: "Approved",
    REJECTED: "Rejected"
  };
  return labels[String(value || "").toUpperCase()] || "Not set";
}

createRoot(document.getElementById("root")).render(<App />);

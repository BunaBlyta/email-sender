import React, { useEffect, useMemo, useState } from "react";
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

const phishingSample = {
  sender: "PayPal Support <support@paypal-alerts.example>",
  subject: "Urgent: verify your account",
  body: "Your account is suspended. Login at http://192.168.1.10/login to update payment."
};

const threadCategories = ["PEOPLE", "THINGS", "NOISE"];
const threadWorkflowStates = ["ACTIVE", "NEEDS_ACTION", "AWAITING_REPLY", "DONE", "ARCHIVED"];
const inboxFilters = [
  { key: "ALL", label: "All", labels: null },
  { key: "NEEDS_REPLY", label: "Needs Action", labels: ["NEEDS_REPLY"] },
  { key: "WAITING", label: "Awaiting Reply", labels: ["WAITING"] },
  { key: "SECURITY_REVIEW", label: "Security Review", labels: ["SECURITY_REVIEW"] },
  { key: "IMPORTANT", label: "Important", labels: ["IMPORTANT"] },
  { key: "FYI", label: "FYI", labels: ["FYI"] },
  { key: "LOW_PRIORITY", label: "Low Priority", labels: ["LOW_PRIORITY"] }
];

function App() {
  const [activeView, setActiveView] = useState("inbox");
  const [account, setAccount] = useState(null);
  const [inboxThreads, setInboxThreads] = useState([]);
  const [triageInbox, setTriageInbox] = useState(null);
  const [selectedThread, setSelectedThread] = useState(null);
  const [threadContext, setThreadContext] = useState(null);
  const [inboxMaxResults, setInboxMaxResults] = useState(20);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResult, setSearchResult] = useState(null);
  const [manageTab, setManageTab] = useState("scheduled");
  const [templates, setTemplates] = useState([]);
  const [drafts, setDrafts] = useState([]);
  const [activeDraftId, setActiveDraftId] = useState(null);
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
  const [trackingMessageId, setTrackingMessageId] = useState("");
  const [trackingResult, setTrackingResult] = useState(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadInitialData();
  }, []);

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

  const riskTone = useMemo(() => {
    const level = evaluation?.phishing?.riskLevel || phishingResult?.riskLevel;
    return riskClass(level);
  }, [evaluation, phishingResult]);

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

  async function openThread(threadId) {
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
      setActiveView("scheduled");
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
    setActiveDraftId(draft.id);
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
    setLastDraftSnapshot("");
    setDraftStatus("");
    await refreshDrafts();
  }

  function resetComposeAfterDelivery() {
    setComposeForm(initialComposeForm);
    setComposeFile(null);
    setScheduleAt(defaultDateTimeLocal());
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
    await run(async () => {
      const response = await api(`/templates/${template.id}/use`, { method: "POST" });
      applyTemplate(response);
      await refreshTemplates();
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

  async function openTrackingSignals(sentMessageId) {
    await run(async () => {
      const response = await api(`/tracking/sent/${sentMessageId}`);
      setTrackingMessageId(String(sentMessageId));
      setTrackingResult(response);
      await refreshTrackedMessages();
      setActiveView("tracking");
      setNotice("Open signals loaded.");
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
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">EC</div>
          <div>
            <strong>Email Control Center</strong>
            <span>Communication workspace</span>
          </div>
        </div>
        <nav className="nav">
          <NavGroup
            title="Process"
            items={[
              ["inbox", "Inbox"],
              ["search", "Search"],
              ["screener", "Screener"]
            ]}
            activeView={activeView}
            setActiveView={setActiveView}
          />
          <NavGroup
            title="Send"
            items={[
              ["compose", "Compose"],
              ["scheduled", "Scheduled"],
              ["templates", "Templates"],
              ["drafts", "Drafts"]
            ]}
            activeView={activeView}
            setActiveView={setActiveView}
          />
          <NavGroup
            title="Relationships"
            items={[
              ["groups", "Contacts"],
              ["tracking", "Open signals"],
              ["trust", "Trust"]
            ]}
            activeView={activeView}
            setActiveView={setActiveView}
          />
        </nav>
        <AccountCard account={account} />
        <a className="login-link" href="http://localhost:8080/oauth2/authorization/google">
          {account?.email ? "Reconnect Google" : "Google sign-in"}
        </a>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">{modeLabel(activeView)}</p>
            <h1>{viewTitle(activeView)}</h1>
          </div>
          <div className="topbar-actions">
            {account?.email && <span className="account-chip">{account.email}</span>}
            <div className={`status-pill ${riskTone}`}>{loading ? "Working" : "Ready"}</div>
          </div>
        </header>

        {notice && <div className="notice">{notice}</div>}
        {error && <div className="error">{error}</div>}

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
            onCleanup={cleanupThread}
            onUnsubscribe={beginUnsubscribe}
            onUpdateCategory={updateThreadCategory}
            onUpdateWorkflowState={updateThreadWorkflowState}
            onTrustThreadSender={trustThreadSender}
            onTrustThreadDomain={trustThreadDomain}
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
            onCleanup={cleanupThread}
            onUnsubscribe={beginUnsubscribe}
            onUpdateCategory={updateThreadCategory}
            onUpdateWorkflowState={updateThreadWorkflowState}
            onTrustThreadSender={trustThreadSender}
            onTrustThreadDomain={trustThreadDomain}
          />
        )}

        {activeView === "compose" && (
          <ComposeView
            form={composeForm}
            setForm={setComposeForm}
            file={composeFile}
            setFile={setComposeFile}
            templates={templates}
            drafts={drafts}
            activeDraftId={activeDraftId}
            draftStatus={draftStatus}
            scheduleAt={scheduleAt}
            setScheduleAt={setScheduleAt}
            lastSend={lastSend}
            onSend={sendMessage}
            onSchedule={scheduleMessage}
            onSaveDraft={saveDraft}
            onLoadDraft={loadDraft}
            onDeleteDraft={deleteDraft}
            onApplyTemplate={applyTemplate}
            onViewOpenSignals={openTrackingSignals}
          />
        )}

        {activeView === "drafts" && (
          <DraftsView
            drafts={drafts}
            onRefresh={() => run(refreshDrafts)}
            onLoadDraft={loadDraft}
            onDeleteDraft={deleteDraft}
          />
        )}

        {activeView === "manage" && (
          <ManageView
            activeTab={manageTab}
            setActiveTab={setManageTab}
            templates={templates}
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

        {activeView === "templates" && (
          <TemplatesView
            templates={templates}
            form={templateForm}
            setForm={setTemplateForm}
            onCreate={createTemplate}
            onDelete={deleteTemplate}
            onUse={useTemplate}
            onRefresh={() => run(refreshTemplates)}
          />
        )}

        {activeView === "groups" && (
          <GroupsView
            groups={recipientGroups}
            form={groupForm}
            setForm={setGroupForm}
            bulkForm={bulkForm}
            setBulkForm={setBulkForm}
            bulkResult={bulkResult}
            onCreate={createRecipientGroup}
            onDelete={deleteRecipientGroup}
            onBulkSend={sendBulk}
            onRefresh={() => run(refreshRecipientGroups)}
          />
        )}

        {activeView === "scheduled" && (
          <ScheduledView
            messages={scheduledMessages}
            onRefresh={() => run(refreshScheduledMessages)}
            onCancel={cancelScheduled}
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

        {activeView === "screener" && (
          <ScreenerView
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

        {activeView === "trust" && (
          <TrustView
            trust={trust}
            onRefresh={() => run(refreshTrust)}
            onTrustSender={(value) => trustValue("/security/trust/senders", value, "Sender trusted.")}
            onTrustDomain={(value) => trustValue("/security/trust/domains", value, "Domain trusted.")}
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
  onCleanup,
  onUnsubscribe,
  onUpdateCategory,
  onUpdateWorkflowState,
  onTrustThreadSender,
  onTrustThreadDomain
}) {
  const [activeFilter, setActiveFilter] = useState("ALL");
  const [focusMode, setFocusMode] = useState(false);
  const threads = triage?.threads || [];
  const selectedTriage = threads.find(
    (thread) => thread.externalThreadId === selectedThread?.externalThreadId
  );
  const activeFilterConfig = inboxFilters.find((filter) => filter.key === activeFilter) || inboxFilters[0];
  const visibleThreads = activeFilterConfig.labels
    ? threads.filter((thread) => activeFilterConfig.labels.includes(thread.label))
    : threads;
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
    />
  );

  if (focusMode && selectedThread) {
    return (
      <div className="focus-layout">
        <div className="focus-topbar">
          <div>
            <p className="eyebrow">Triage Mode</p>
            <h2>{selectedThread.subject || "Selected conversation"}</h2>
          </div>
          <button type="button" onClick={() => setFocusMode(false)}>Back to queue</button>
        </div>
        {threadWorkspace}
      </div>
    );
  }

  return (
    <div className="process-layout">
      <section className="queue-panel">
        <div className="queue-header">
          <div>
            <div className="queue-title-line">
              <h2>Attention Queue</h2>
              <span>{triage?.totalThreads || threads.length || 0} threads</span>
            </div>
            <div className="summary-strip">
              <SummaryCount label="Needs Action" value={triage?.needsReplyCount || 0} tone="amber" />
              <SummaryCount label="Important" value={triage?.importantCount || 0} tone="blue" />
              <SummaryCount label="Waiting" value={triage?.waitingCount || 0} tone="neutral" />
              <SummaryCount label="Risk" value={triage?.securityReviewCount || 0} tone="red" />
            </div>
          </div>
          <div className="toolbar">
            <select value={maxResults} onChange={(event) => setMaxResults(Number(event.target.value))}>
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
            </select>
            <button type="button" className="secondary" disabled={!selectedThread} onClick={() => setFocusMode(true)}>
              Focus
            </button>
            <button className="secondary" onClick={onRefresh}>Refresh</button>
          </div>
        </div>
        <form className="search-strip" onSubmit={onSearch}>
          <input
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
            placeholder="Search conversations"
          />
          <button type="submit">Search</button>
        </form>
        <InboxFilterBar
          filters={inboxFilters}
          activeFilter={activeFilter}
          onChange={setActiveFilter}
        />
        {visibleThreads.length > 0 ? (
          <div className="triage-sections">
            <TriageSection
              title="Needs Action"
              label="NEEDS_REPLY"
              threads={visibleThreads}
              selectedThread={selectedThread}
              onOpenThread={onOpenThread}
            />
            <TriageSection
              title="Security Review"
              label="SECURITY_REVIEW"
              threads={visibleThreads}
              selectedThread={selectedThread}
              onOpenThread={onOpenThread}
            />
            <TriageSection
              title="Important"
              label="IMPORTANT"
              threads={visibleThreads}
              selectedThread={selectedThread}
              onOpenThread={onOpenThread}
            />
            <TriageSection
              title="Waiting"
              label="WAITING"
              threads={visibleThreads}
              selectedThread={selectedThread}
              onOpenThread={onOpenThread}
            />
            <TriageSection
              title="FYI"
              label="FYI"
              threads={visibleThreads}
              selectedThread={selectedThread}
              onOpenThread={onOpenThread}
            />
            <TriageSection
              title="Low Priority"
              label="LOW_PRIORITY"
              threads={visibleThreads}
              selectedThread={selectedThread}
              onOpenThread={onOpenThread}
              collapsed
            />
          </div>
        ) : (
          <EmptyState label={threads.length ? "No threads in this filter." : "No triage results loaded."} />
        )}
      </section>

      {threadWorkspace}
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
  onCleanup,
  onUnsubscribe,
  onUpdateCategory,
  onUpdateWorkflowState,
  onTrustThreadSender,
  onTrustThreadDomain
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
        <form className="search-strip prominent" onSubmit={onSearch}>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search sender, subject, or Gmail query"
          />
          <button type="submit">Search</button>
        </form>
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
  onTrustThreadDomain
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
      <ThreadReader thread={thread} />
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

function ThreadReader({ thread }) {
  return (
    <div className="reader">
      <div className="reader-header">
        <div>
          <h2>{thread.subject || "(No subject)"}</h2>
          <div className="participants reader-participants">
            {formatParticipants(thread.participants)}
          </div>
        </div>
        <span>{formatDate(thread.lastMessageAt)}</span>
      </div>
      <div className="message-stack">
        {thread.messages?.map((message) => (
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
          </article>
        ))}
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
  const reasons = context?.reasons?.length
    ? context.reasons
    : triage?.reasons?.length
      ? triage.reasons
      : ["No attention signal loaded."];

  return (
    <aside className="context-panel">
      <div className="context-hero">
        <div>
          <span className={`status-chip ${workflowTone(displayWorkflow)}`}>
            {workflowLabel(displayWorkflow)}
          </span>
          <strong>{categoryLabel(category)}</strong>
        </div>
        <span className={`label-pill ${labelTone(triage?.label)}`}>
          {labelText(triage?.label || (thread.hasUnread ? "IMPORTANT" : "FYI"))}
        </span>
        {triage?.suggestedAction && <p>{triage.suggestedAction}</p>}
      </div>
      <div className="context-section">
        <h3>Conversation</h3>
        <strong>{primaryParticipant(thread.participants, latestMessage?.sender)}</strong>
        <span>{latestMessage?.sender || "Unknown sender"}</span>
      </div>
      {unsubscribe && (
        <div className="context-section unsubscribe-section">
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
      <div className="context-section">
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
        <div className="context-meta">
          {context?.categoryOverride ? "Manual category" : `Suggested: ${categoryLabel(context?.suggestedCategory)}`}
        </div>
      </div>
      <div className="context-section">
        <h3>Trust & Risk</h3>
        <div className="risk-line">
          <span className={`risk ${riskClass(riskLevel)}`}>{riskLevel}</span>
          <span>{context ? `Risk score ${context.phishingScore}` : "Loading"}</span>
        </div>
        <div className="trust-state-grid">
          <span className={context?.senderTrusted ? "trusted" : ""}>
            {context?.senderTrusted ? "Sender trusted" : "Sender untrusted"}
          </span>
          <span className={context?.domainTrusted ? "trusted" : ""}>
            {context?.domainTrusted ? "Domain trusted" : "Domain untrusted"}
          </span>
          {context?.screenerStatus && <span>Screener: {workflowLabel(context.screenerStatus)}</span>}
        </div>
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
      <div className="context-section">
        <h3>Why Surfaced</h3>
        <div className="context-list">
          {reasons.map((reason) => (
            <span key={reason}>{reason}</span>
          ))}
        </div>
      </div>
      <div className="context-section">
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
  const category = thread.category || thread.suggestedCategory;
  const workflowState = thread.workflowState || "ACTIVE";
  const trusted = thread.senderTrusted || thread.domainTrusted;
  const riskLevel = thread.phishingRiskLevel;

  return (
    <button
      className={`thread-row triage-row ${selected ? "selected" : ""}`}
      onClick={() => onOpenThread(thread.externalThreadId)}
    >
      <div className={thread.hasUnread ? "unread-dot active" : "unread-dot"} />
      <div className="thread-main">
        <div className="thread-title">
          <strong>{primaryParticipant(thread.participants)}</strong>
          <span>{formatDate(thread.lastMessageAt)}</span>
        </div>
        <div className="thread-subject">{thread.subject || "(No subject)"}</div>
        {thread.suggestedAction && <div className="thread-suggestion">{thread.suggestedAction}</div>}
        <div className="thread-footer">
          {thread.label && <span className={`label-pill ${labelTone(thread.label)}`}>{labelText(thread.label)}</span>}
          {category && (
            <span className={`status-chip ${categoryTone(category)}`}>
              {categoryLabel(category)}{thread.categoryOverride ? " · manual" : ""}
            </span>
          )}
          {workflowState !== "ACTIVE" && (
            <span className={`status-chip ${workflowTone(workflowState)}`}>
              {workflowLabel(workflowState)}
            </span>
          )}
          {trusted && <span className="score-pill">Trusted</span>}
          {riskLevel && riskLevel !== "LOW" && (
            <span className={`status-chip ${riskTone(riskLevel)}`}>
              {riskLevel} risk
            </span>
          )}
          {thread.attentionScore != null && <span className="score-pill">Score {thread.attentionScore}</span>}
        </div>
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
  activeDraftId,
  draftStatus,
  scheduleAt,
  setScheduleAt,
  lastSend,
  onSend,
  onSchedule,
  onSaveDraft,
  onLoadDraft,
  onDeleteDraft,
  onApplyTemplate,
  onViewOpenSignals
}) {
  return (
    <div className="compose-workspace">
      <section className="compose-editor">
        <div className="compose-header">
          <div>
            <h2>Compose</h2>
            <p className="subtle">{draftStatus || `${wordCount(form.body)} words`}</p>
          </div>
          <div className="compose-header-actions">
            {activeDraftId && <span className="draft-chip">Draft #{activeDraftId}</span>}
            <button type="button" onClick={onSaveDraft}>Save draft</button>
          </div>
        </div>
        <form className="compose-form" onSubmit={onSend}>
          <Field label="Recipients">
            <input
              value={form.recipients}
              onChange={(event) => setForm({ ...form, recipients: event.target.value })}
              placeholder="one@example.com, two@example.com"
            />
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
          file={file}
          setFile={setFile}
          scheduleAt={scheduleAt}
          setScheduleAt={setScheduleAt}
          lastSend={lastSend}
          onViewOpenSignals={onViewOpenSignals}
        />
        <TemplatePicker templates={templates} onApplyTemplate={onApplyTemplate} />
        <DraftShelf
          drafts={drafts}
          activeDraftId={activeDraftId}
          onLoadDraft={onLoadDraft}
          onDeleteDraft={onDeleteDraft}
        />
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
  file,
  setFile,
  scheduleAt,
  setScheduleAt,
  lastSend,
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
        <p className="subtle">Scheduled messages currently do not include open-signal tracking.</p>
        <Field label="Attachment">
          <input type="file" onChange={(event) => setFile(event.target.files?.[0] || null)} />
        </Field>
        {file && <p className="subtle">{file.name}</p>}
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
              </>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

function TemplatePicker({ templates, onApplyTemplate }) {
  return (
    <section className="support-panel">
      <div className="support-heading">
        <h2>Templates</h2>
      </div>
      {templates.length > 0 ? (
        <div className="asset-list">
          {templates.slice(0, 5).map((template) => (
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
        </div>
      ) : (
        <EmptyState label="No templates yet." small />
      )}
    </section>
  );
}

function DraftShelf({ drafts, activeDraftId, onLoadDraft, onDeleteDraft }) {
  return (
    <section className="support-panel">
      <div className="support-heading">
        <h2>Drafts</h2>
      </div>
      {drafts.length > 0 ? (
        <div className="asset-list">
          {drafts.slice(0, 5).map((draft) => (
            <div
              className={`asset-row draft-row ${activeDraftId === draft.id ? "active" : ""}`}
              key={draft.id}
            >
              <button type="button" onClick={() => onLoadDraft(draft)}>
                <strong>{draft.subject || "(No subject)"}</strong>
                <span>{formatDate(draft.updatedAt)}</span>
              </button>
              <button className="icon-action danger" type="button" onClick={() => onDeleteDraft(draft.id)}>
                Delete
              </button>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState label="No drafts saved." small />
      )}
    </section>
  );
}

function DraftsView({ drafts, onRefresh, onLoadDraft, onDeleteDraft }) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Drafts</h2>
          <p className="subtle">Local compose drafts.</p>
        </div>
        <button className="secondary" onClick={onRefresh}>Refresh</button>
      </div>
      {drafts.length > 0 ? (
        <div className="table-list">
          {drafts.map((draft) => (
            <div className="table-row" key={draft.id}>
              <div>
                <strong>{draft.subject || "(No subject)"}</strong>
                <span>{(draft.recipients || []).join(", ") || "No recipients"}</span>
                <span>Updated {formatDate(draft.updatedAt)}</span>
              </div>
              <div className="actions">
                <button onClick={() => onLoadDraft(draft)}>Open</button>
                <button className="danger" onClick={() => onDeleteDraft(draft.id)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState label="No drafts saved." />
      )}
    </section>
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
    ["templates", "Templates", templates.length],
    ["contacts", "Contacts", groups.length],
    ["screener", "Screener", pending.length],
    ["signals", "Signals", (trust.senders?.length || 0) + (trust.domains?.length || 0)]
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
          <ManageMetric label="Templates" value={templates.length} />
          <ManageMetric label="Contacts" value={groups.length} />
          <ManageMetric label="Screener" value={pending.length} tone={pending.length > 0 ? "amber" : ""} />
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
          />
        )}
        {activeTab === "templates" && (
          <TemplatesView
            templates={templates}
            form={templateForm}
            setForm={setTemplateForm}
            onCreate={onCreateTemplate}
            onDelete={onDeleteTemplate}
            onUse={onUseTemplate}
            onRefresh={onRefreshTemplates}
          />
        )}
        {activeTab === "contacts" && (
          <GroupsView
            groups={groups}
            form={groupForm}
            setForm={setGroupForm}
            bulkForm={bulkForm}
            setBulkForm={setBulkForm}
            bulkResult={bulkResult}
            onCreate={onCreateGroup}
            onDelete={onDeleteGroup}
            onBulkSend={onBulkSend}
            onRefresh={onRefreshGroups}
          />
        )}
        {activeTab === "screener" && (
          <ScreenerView
            form={screenerForm}
            setForm={setScreenerForm}
            evaluation={evaluation}
            pending={pending}
            onEvaluate={onEvaluateSender}
            onRefresh={onRefreshPending}
            onApproveSender={onApproveSender}
            onApproveDomain={onApproveDomain}
            onReject={onRejectSender}
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
            <TrustView
              trust={trust}
              onRefresh={onRefreshTrust}
              onTrustSender={onTrustSender}
              onTrustDomain={onTrustDomain}
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

function TemplatesView({ templates, form, setForm, onCreate, onDelete, onUse, onRefresh }) {
  return (
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Create Template</h2>
            <p className="subtle">Reusable message asset.</p>
          </div>
        </div>
        <form className="stack" onSubmit={onCreate}>
          <Field label="Name">
            <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          </Field>
          <Field label="Category">
            <input value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value })} />
          </Field>
          <Field label="Subject">
            <input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} />
          </Field>
          <Field label="Body">
            <textarea value={form.body} onChange={(event) => setForm({ ...form, body: event.target.value })} />
          </Field>
          <button className="primary" type="submit">Save template</button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <h2>Templates</h2>
          <button className="secondary" onClick={onRefresh}>Refresh</button>
        </div>
        {templates.length > 0 ? (
          <div className="asset-card-grid">
            {templates.map((template) => (
              <article className="asset-card template-card" key={template.id}>
                <div className="asset-card-main">
                  <div className="asset-card-title">
                    <strong>{template.name}</strong>
                    <span>{template.category || "General"}</span>
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
                  <button className="danger" onClick={() => onDelete(template.id)}>Delete</button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <EmptyState label="No templates yet." />
        )}
      </section>
    </div>
  );
}

function GroupsView({
  groups,
  form,
  setForm,
  bulkForm,
  setBulkForm,
  bulkResult,
  onCreate,
  onDelete,
  onBulkSend,
  onRefresh
}) {
  function toggleGroup(id) {
    const selected = bulkForm.selectedGroupIds.includes(id);
    setBulkForm({
      ...bulkForm,
      selectedGroupIds: selected
        ? bulkForm.selectedGroupIds.filter((item) => item !== id)
        : [...bulkForm.selectedGroupIds, id],
      confirmed: false
    });
  }

  return (
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Create Group</h2>
            <p className="subtle">Recipient relationship set.</p>
          </div>
        </div>
        <form className="stack" onSubmit={onCreate}>
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
          <button className="primary" type="submit">Save group</button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <h2>Groups</h2>
          <button className="secondary" onClick={onRefresh}>Refresh</button>
        </div>
        {groups.length > 0 ? (
          <div className="asset-card-grid">
            {groups.map((group) => (
              <article className="asset-card contact-card" key={group.id}>
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

      <section className="panel wide">
        <div className="panel-heading">
          <h2>Bulk Send</h2>
        </div>
        <form className="bulk-grid" onSubmit={onBulkSend}>
          <div className="stack">
            {groups.map((group) => (
              <label className="check-row" key={group.id}>
                <input
                  type="checkbox"
                  checked={bulkForm.selectedGroupIds.includes(group.id)}
                  onChange={() => toggleGroup(group.id)}
                />
                <span>{group.name} ({group.memberCount})</span>
              </label>
            ))}
          </div>
          <div className="stack">
            <Field label="Subject">
              <input
                value={bulkForm.subject}
                onChange={(event) => setBulkForm({
                  ...bulkForm,
                  subject: event.target.value,
                  confirmed: false
                })}
              />
            </Field>
            <Field label="Body">
              <textarea
                value={bulkForm.body}
                onChange={(event) => setBulkForm({
                  ...bulkForm,
                  body: event.target.value,
                  confirmed: false
                })}
              />
            </Field>
            <label className="check-row">
              <input
                type="checkbox"
                checked={bulkForm.confirmed}
                onChange={(event) => setBulkForm({
                  ...bulkForm,
                  confirmed: event.target.checked
                })}
              />
              <span>I confirm this private send to the selected groups.</span>
            </label>
            <button
              className="primary"
              type="submit"
              disabled={!bulkForm.confirmed || bulkForm.selectedGroupIds.length === 0}
            >
              Send private bulk messages
            </button>
          </div>
        </form>
        {bulkResult && (
          <div className="result-box">
            <strong>{bulkResult.sentCount} sent, {bulkResult.failedCount} failed</strong>
            <span>Total recipients: {bulkResult.totalRecipients}</span>
          </div>
        )}
      </section>
    </div>
  );
}

function ScheduledView({ messages, onRefresh, onCancel }) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <h2>Scheduled Messages</h2>
        <button className="secondary" onClick={onRefresh}>Refresh</button>
      </div>
      {messages.length > 0 ? (
        <div className="table-list">
          {messages.map((message) => (
            <div className="table-row" key={message.id}>
              <div>
                <strong>{message.subject}</strong>
                <span>{message.recipients?.join(", ")}</span>
                <span>
                  <span className={`status-chip inline ${statusTone(message.status)}`}>
                    {workflowLabel(message.status)}
                  </span>
                  {formatDate(message.scheduledFor)}
                </span>
                {message.failureReason && <span>{message.failureReason}</span>}
              </div>
              <div className="actions">
                {message.status === "PENDING" && (
                  <button className="danger" onClick={() => onCancel(message.id)}>Cancel</button>
                )}
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
  return (
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Open Signals</h2>
            <p className="subtle">Recent tracked messages and their image-load signals.</p>
          </div>
          <button className="secondary" type="button" onClick={onRefresh}>Refresh</button>
        </div>
        {messages.length > 0 ? (
          <div className="asset-list">
            {messages.map((message) => (
              <button
                className={`asset-row tracking-message-row ${
                  String(message.sentMessageId) === String(selectedMessageId) ? "active" : ""
                }`}
                type="button"
                key={message.sentMessageId}
                onClick={() => onSelect(message.sentMessageId)}
              >
                <strong>{message.subject || "(No subject)"}</strong>
                <span>{message.recipient || "No recipient"}</span>
                <span>
                  {trackingStatusLabel(message.status)} · {message.pixelLoadCount} {message.pixelLoadCount === 1 ? "signal" : "signals"} · {formatDate(message.sentAt)}
                </span>
              </button>
            ))}
          </div>
        ) : (
          <EmptyState label="No tracked messages yet." />
        )}
      </section>
      <section className="panel">
        <div className="panel-heading">
          <h2>Signal State</h2>
        </div>
        {result ? <TrackingSummary tracking={result} /> : <EmptyState label="No open signals loaded." />}
      </section>
    </div>
  );
}

function TrackingSummary({ tracking }) {
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
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <h2>Evaluate Sender</h2>
        </div>
        <form className="stack" onSubmit={onEvaluate}>
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
      </section>

      <section className="panel">
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
          <EmptyState label="No sender evaluated yet." />
        )}
      </section>

      <section className="panel wide">
        <div className="panel-heading">
          <h2>Pending Senders</h2>
          <button className="secondary" onClick={onRefresh}>Refresh</button>
        </div>
        {pending.length > 0 ? (
          <div className="table-list">
            {pending.map((entry) => (
              <div className="table-row" key={entry.id}>
                <div>
                  <strong>{entry.senderEmail}</strong>
                  <span>{entry.senderDomain}</span>
                </div>
                <div className="actions">
                  <button onClick={() => onApproveSender(entry.id)}>Approve sender</button>
                  <button onClick={() => onApproveDomain(entry.id)}>Approve domain</button>
                  <button className="danger" onClick={() => onReject(entry.id)}>Reject</button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState label="No pending senders." />
        )}
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
      <section className="panel">
        <div className="panel-heading">
          <h2>Add Trust</h2>
        </div>
        <div className="stack">
          <Field label="Sender">
            <div className="inline-form">
              <input value={sender} onChange={(event) => setSender(event.target.value)} />
              <button onClick={() => onTrustSender(sender)}>Trust</button>
            </div>
          </Field>
          <Field label="Domain">
            <div className="inline-form">
              <input value={domain} onChange={(event) => setDomain(event.target.value)} />
              <button onClick={() => onTrustDomain(domain)}>Trust</button>
            </div>
          </Field>
        </div>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <h2>Trusted</h2>
          <button className="secondary" onClick={onRefresh}>Refresh</button>
        </div>
        <TrustList title="Senders" entries={trust.senders || []} />
        <TrustList title="Domains" entries={trust.domains || []} />
      </section>
    </div>
  );
}

function DecisionCard({ evaluation, onApproveSender, onApproveDomain, onReject }) {
  const entry = evaluation.entry;

  return (
    <div className="decision-card">
      <div className="decision-top">
        <span className={`risk ${riskClass(evaluation.phishing?.riskLevel)}`}>
          {evaluation.phishing?.riskLevel || "LOW"}
        </span>
        <span>{evaluation.status}</span>
      </div>
      {entry && (
        <div className="identity">
          <strong>{entry.senderEmail}</strong>
          <span>{entry.senderDomain}</span>
        </div>
      )}
      <PhishingSummary phishing={evaluation.phishing} compact />
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
          <div className="trust-row" key={entry.id}>
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

function NavGroup({ title, items, activeView, setActiveView }) {
  return (
    <div className="nav-group">
      <span>{title}</span>
      {items.map(([key, label]) => (
        <button
          key={key}
          className={activeView === key ? "active" : ""}
          onClick={() => setActiveView(key)}
        >
          {label}
        </button>
      ))}
    </div>
  );
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
    inbox: "Inbox / Process",
    search: "Search",
    compose: "Compose / Send",
    drafts: "Drafts",
    manage: "Manage",
    templates: "Templates",
    groups: "Contacts",
    scheduled: "Scheduled",
    tracking: "Open Signals",
    screener: "Screener",
    security: "Security signals",
    trust: "Sender trust"
  };
  return titles[view] || "Workspace";
}

function modeLabel(view) {
  if (["compose", "drafts", "templates", "scheduled"].includes(view)) {
    return "Create";
  }
  if (["groups"].includes(view)) {
    return "Relationships";
  }
  if (view === "manage") {
    return "Control";
  }
  if (["tracking", "security", "trust"].includes(view)) {
    return "Signals";
  }
  return "Process";
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
  return tones[value] || "neutral";
}

function trackingTone(value) {
  if (value === "IMAGE_LOAD_DETECTED") {
    return "green";
  }
  if (value === "AWAITING_IMAGE_LOAD") {
    return "blue";
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
    APPROVED: "Approved",
    REJECTED: "Rejected"
  };
  return labels[value] || "Not set";
}

createRoot(document.getElementById("root")).render(<App />);

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
  confirmed: true
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

function App() {
  const [activeView, setActiveView] = useState("inbox");
  const [account, setAccount] = useState(null);
  const [inboxThreads, setInboxThreads] = useState([]);
  const [selectedThread, setSelectedThread] = useState(null);
  const [inboxMaxResults, setInboxMaxResults] = useState(20);
  const [templates, setTemplates] = useState([]);
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
  const [trackingMessageId, setTrackingMessageId] = useState("");
  const [trackingResult, setTrackingResult] = useState(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadInitialData();
  }, []);

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
      refreshInbox(),
      refreshTemplates(),
      refreshRecipientGroups(),
      refreshScheduledMessages(),
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

  async function openThread(threadId) {
    await run(async () => {
      const thread = await api(`/inbox/threads/${encodeURIComponent(threadId)}`);
      setSelectedThread(thread);
    });
  }

  async function refreshTemplates() {
    setTemplates(await api("/templates"));
  }

  async function refreshRecipientGroups() {
    setRecipientGroups(await api("/recipient-groups"));
  }

  async function refreshScheduledMessages() {
    setScheduledMessages(await api("/scheduled"));
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
      setActiveView("scheduled");
      setLastSend(response);
    });
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
    await run(async () => {
      await api(`/recipient-groups/${id}`, { method: "DELETE" });
      setNotice("Recipient group deleted.");
      await refreshRecipientGroups();
    });
  }

  async function sendBulk(event) {
    event.preventDefault();
    await run(async () => {
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
      setNotice("Bulk send completed.");
    });
  }

  async function cancelScheduled(id) {
    await run(async () => {
      await api(`/scheduled/${id}/cancel`, { method: "POST" });
      setNotice("Scheduled message cancelled.");
      await refreshScheduledMessages();
    });
  }

  async function lookupTracking(event) {
    event.preventDefault();
    await run(async () => {
      const response = await api(`/tracking/sent/${trackingMessageId}`);
      setTrackingResult(response);
      setNotice("Tracking status loaded.");
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

  async function decide(path, successMessage) {
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
          <div className="brand-mark">E</div>
          <div>
            <strong>Email Platform</strong>
            <span>Attention workspace</span>
          </div>
        </div>
        <nav className="nav">
          {[
            ["inbox", "Inbox"],
            ["compose", "Compose"],
            ["templates", "Templates"],
            ["groups", "Groups"],
            ["scheduled", "Scheduled"],
            ["tracking", "Tracking"],
            ["screener", "Screener"],
            ["security", "Security"],
            ["trust", "Trust"]
          ].map(([key, label]) => (
            <button
              key={key}
              className={activeView === key ? "active" : ""}
              onClick={() => setActiveView(key)}
            >
              {label}
            </button>
          ))}
        </nav>
        <AccountCard account={account} />
        <a className="login-link" href="http://localhost:8080/oauth2/authorization/google">
          {account?.email ? "Reconnect Google" : "Google sign-in"}
        </a>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Phase 2</p>
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
            threads={inboxThreads}
            selectedThread={selectedThread}
            maxResults={inboxMaxResults}
            setMaxResults={setInboxMaxResults}
            onRefresh={() => run(() => refreshInbox())}
            onOpenThread={openThread}
          />
        )}

        {activeView === "compose" && (
          <ComposeView
            form={composeForm}
            setForm={setComposeForm}
            file={composeFile}
            setFile={setComposeFile}
            templates={templates}
            scheduleAt={scheduleAt}
            setScheduleAt={setScheduleAt}
            lastSend={lastSend}
            onSend={sendMessage}
            onSchedule={scheduleMessage}
            onApplyTemplate={applyTemplate}
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
            messageId={trackingMessageId}
            setMessageId={setTrackingMessageId}
            result={trackingResult}
            lastSend={lastSend}
            onLookup={lookupTracking}
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
            onReject={(id) => decide(`/screener/${id}/reject`, "Sender rejected.")}
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
  threads,
  selectedThread,
  maxResults,
  setMaxResults,
  onRefresh,
  onOpenThread
}) {
  return (
    <div className="mail-layout">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Gmail Threads</h2>
            <p className="subtle">Fetched from the connected Gmail account.</p>
          </div>
          <div className="toolbar">
            <select value={maxResults} onChange={(event) => setMaxResults(Number(event.target.value))}>
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
            </select>
            <button className="secondary" onClick={onRefresh}>Refresh</button>
          </div>
        </div>
        {threads.length > 0 ? (
          <div className="thread-list">
            {threads.map((thread) => (
              <button
                className={`thread-row ${selectedThread?.externalThreadId === thread.externalThreadId ? "selected" : ""}`}
                key={thread.externalThreadId}
                onClick={() => onOpenThread(thread.externalThreadId)}
              >
                <div className={thread.hasUnread ? "unread-dot active" : "unread-dot"} />
                <div className="thread-main">
                  <div className="thread-title">
                    <strong>{thread.subject || "(No subject)"}</strong>
                    <span>{formatDate(thread.lastMessageAt)}</span>
                  </div>
                  <div className="participants">{formatParticipants(thread.participants)}</div>
                </div>
              </button>
            ))}
          </div>
        ) : (
          <EmptyState label="No inbox threads loaded." />
        )}
      </section>

      <section className="panel reader-panel">
        <div className="panel-heading">
          <h2>Thread</h2>
        </div>
        {selectedThread ? (
          <ThreadReader thread={selectedThread} />
        ) : (
          <EmptyState label="Select a thread to read it." />
        )}
      </section>
    </div>
  );
}

function ThreadReader({ thread }) {
  return (
    <div className="reader">
      <div className="reader-header">
        <h2>{thread.subject || "(No subject)"}</h2>
        <span>{formatDate(thread.lastMessageAt)}</span>
      </div>
      <div className="participants reader-participants">
        {formatParticipants(thread.participants)}
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

function ComposeView({
  form,
  setForm,
  file,
  setFile,
  templates,
  scheduleAt,
  setScheduleAt,
  lastSend,
  onSend,
  onSchedule,
  onApplyTemplate
}) {
  return (
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <h2>Message</h2>
        </div>
        <form className="stack" onSubmit={onSend}>
          <Field label="Recipients">
            <textarea
              className="compact-textarea"
              value={form.recipients}
              onChange={(event) => setForm({ ...form, recipients: event.target.value })}
              placeholder="one@example.com, two@example.com"
            />
          </Field>
          <Field label="Subject">
            <input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} />
          </Field>
          <Field label="Body">
            <textarea value={form.body} onChange={(event) => setForm({ ...form, body: event.target.value })} />
          </Field>
          <label className="check-row">
            <input
              type="checkbox"
              checked={form.trackOpens}
              onChange={(event) => setForm({ ...form, trackOpens: event.target.checked })}
            />
            <span>Track open signals</span>
          </label>
          <Field label="Attachment">
            <input type="file" onChange={(event) => setFile(event.target.files?.[0] || null)} />
          </Field>
          {file && <p className="subtle">Attachment selected: {file.name}</p>}
          <div className="actions stretch">
            <button className="primary" type="submit">Send now</button>
          </div>
        </form>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <h2>Tools</h2>
        </div>
        <div className="stack">
          <Field label="Apply Template">
            <select
              defaultValue=""
              onChange={(event) => {
                const template = templates.find((item) => String(item.id) === event.target.value);
                if (template) onApplyTemplate(template);
              }}
            >
              <option value="">Choose template</option>
              {templates.map((template) => (
                <option key={template.id} value={template.id}>{template.name}</option>
              ))}
            </select>
          </Field>
          <form className="stack" onSubmit={onSchedule}>
            <Field label="Schedule Time">
              <input
                type="datetime-local"
                value={scheduleAt}
                onChange={(event) => setScheduleAt(event.target.value)}
              />
            </Field>
            <button type="submit">Schedule message</button>
          </form>
          {lastSend && (
            <div className="result-box">
              <strong>{lastSend.scheduled ? "Scheduled" : "Last sent"}</strong>
              <span>{lastSend.subject}</span>
              {lastSend.id && <span>Message ID: {lastSend.id}</span>}
              {lastSend.tracking?.enabled && <span>Tracking: {lastSend.tracking.status}</span>}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function TemplatesView({ templates, form, setForm, onCreate, onDelete, onUse, onRefresh }) {
  return (
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <h2>Create Template</h2>
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
          <div className="table-list">
            {templates.map((template) => (
              <div className="table-row" key={template.id}>
                <div>
                  <strong>{template.name}</strong>
                  <span>{template.subject}</span>
                  <span>{template.category} · used {template.usageCount} times</span>
                </div>
                <div className="actions">
                  <button onClick={() => onUse(template)}>Use</button>
                  <button className="danger" onClick={() => onDelete(template.id)}>Delete</button>
                </div>
              </div>
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
        : [...bulkForm.selectedGroupIds, id]
    });
  }

  return (
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <h2>Create Group</h2>
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
          <div className="table-list">
            {groups.map((group) => (
              <div className="table-row" key={group.id}>
                <div>
                  <strong>{group.name}</strong>
                  <span>{group.memberCount} members</span>
                  <span>{group.members?.join(", ")}</span>
                </div>
                <div className="actions">
                  <button className="danger" onClick={() => onDelete(group.id)}>Delete</button>
                </div>
              </div>
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
              <input value={bulkForm.subject} onChange={(event) => setBulkForm({ ...bulkForm, subject: event.target.value })} />
            </Field>
            <Field label="Body">
              <textarea value={bulkForm.body} onChange={(event) => setBulkForm({ ...bulkForm, body: event.target.value })} />
            </Field>
            <button className="primary" type="submit">Send private bulk messages</button>
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
                <span>{message.status} · {formatDate(message.scheduledFor)}</span>
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

function TrackingView({ messageId, setMessageId, result, lastSend, onLookup }) {
  return (
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading">
          <h2>Lookup Tracking</h2>
        </div>
        <form className="stack" onSubmit={onLookup}>
          <Field label="Sent Message ID">
            <input value={messageId} onChange={(event) => setMessageId(event.target.value)} />
          </Field>
          {lastSend?.id && <p className="subtle">Last sent ID: {lastSend.id}</p>}
          <button className="primary" type="submit">Load tracking status</button>
        </form>
      </section>
      <section className="panel">
        <div className="panel-heading">
          <h2>Status</h2>
        </div>
        {result ? <TrackingSummary tracking={result} /> : <EmptyState label="No tracking status loaded." />}
      </section>
    </div>
  );
}

function TrackingSummary({ tracking }) {
  return (
    <div className="phishing">
      <div className="metric-row">
        <div>
          <span>Enabled</span>
          <strong>{tracking.enabled ? "Yes" : "No"}</strong>
        </div>
        <div>
          <span>Status</span>
          <strong>{tracking.status}</strong>
        </div>
        <div>
          <span>Loads</span>
          <strong>{tracking.pixelLoadCount}</strong>
        </div>
      </div>
      <div className="table-list">
        {(tracking.recentEvents || []).map((event) => (
          <div className="trust-row" key={event.id}>
            <strong>{event.source}</strong>
            <span>{event.imageFormat} · {formatDate(event.loadedAt)}</span>
          </div>
        ))}
      </div>
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
    inbox: "Inbox",
    compose: "Compose",
    templates: "Templates",
    groups: "Recipient groups",
    scheduled: "Scheduled",
    tracking: "Tracking",
    screener: "Screener",
    security: "Security signals",
    trust: "Sender trust"
  };
  return titles[view] || "Workspace";
}

function parseList(value) {
  return (value || "")
    .split(/[\n,;]/)
    .map((item) => item.trim())
    .filter(Boolean);
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

function formatParticipants(participants = []) {
  if (!participants.length) {
    return "No participants";
  }
  return participants.slice(0, 4).join(", ");
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

createRoot(document.getElementById("root")).render(<App />);

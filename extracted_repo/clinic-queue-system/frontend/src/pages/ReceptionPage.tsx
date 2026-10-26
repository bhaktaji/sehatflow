import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { useAuthStore } from "../store/auth";
import { useSessionEvents } from "../hooks/useSessionEvents";
import { useOnline } from "../hooks/useOnline";
import {
  availableActions,
  copyToClipboard,
  formatTime,
  statusClass,
  TOKEN_STATUS_LABEL,
} from "../lib/format";
import ReasonDialog from "../components/ReasonDialog";
import { EmptyState, ErrorState, Loading, SuccessFlash } from "../components/States";
import type { AuditEvent, QueueSession, QueueToken, QueueView, SessionSummary } from "../api/types";

type PendingAction =
  | { kind: "skip"; token: QueueToken }
  | { kind: "no-show"; token: QueueToken }
  | { kind: "return"; token: QueueToken }
  | { kind: "cancel"; token: QueueToken }
  | { kind: "priority"; token: QueueToken; enabled: boolean }
  | { kind: "reorder"; token: QueueToken }
  | { kind: "close" }
  | { kind: "pause" }
  | null;

const ACTION_TITLES: Record<Exclude<PendingAction, null>["kind"], string> = {
  skip: "Skip token",
  "no-show": "Mark no-show",
  return: "Return to waiting",
  cancel: "Cancel token",
  priority: "Change priority",
  reorder: "Reorder token",
  close: "Close session",
  pause: "Pause queue",
};

export default function ReceptionPage() {
  const user = useAuthStore((state) => state.user);
  const online = useOnline();

  const [sessions, setSessions] = useState<QueueSession[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [view, setView] = useState<QueueView | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);

  const [newPatient, setNewPatient] = useState("");
  const [adding, setAdding] = useState(false);
  const [pending, setPending] = useState<PendingAction>(null);
  const [busy, setBusy] = useState(false);
  const [targetPosition, setTargetPosition] = useState("1");
  const [showAudit, setShowAudit] = useState(false);
  const [audit, setAudit] = useState<AuditEvent[]>([]);
  const [showSummary, setShowSummary] = useState(false);
  const [summary, setSummary] = useState<SessionSummary | null>(null);

  const isOwner = user?.role === "OWNER";
  const canConfigure = isOwner || user?.role === "RECEPTIONIST";
  const mutationsDisabled = !online;

  // Optional ?date=YYYY-MM-DD lets staff (and tests) open a specific day.
  const [searchParams] = useSearchParams();
  const businessDate = searchParams.get("date") ?? undefined;

  const refreshQueue = useCallback(async () => {
    if (!sessionId) return;
    try {
      const next = await api.queueView(sessionId);
      setView(next);
      setLoadError(null);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Could not load the queue");
    }
  }, [sessionId]);

  const loadSessions = useCallback(async () => {
    try {
      const list = await api.sessions(businessDate);
      setSessions(list);
      setSessionId((current) => current ?? list.find((s) => s.status !== "CLOSED")?.id ?? list[0]?.id ?? null);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Could not load sessions");
    }
  }, [businessDate]);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    if (!sessionId) {
      setView(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    void refreshQueue().finally(() => setLoading(false));
  }, [sessionId, refreshQueue]);

  const sse = useSessionEvents(sessionId, () => void refreshQueue());

  // Polling fallback while the SSE stream is down (REST remains source of truth).
  useEffect(() => {
    if (sse.connected || !sessionId) return;
    const timer = window.setInterval(() => void refreshQueue(), 5000);
    return () => window.clearInterval(timer);
  }, [sse.connected, sessionId, refreshQueue]);

  // Converge to server state as soon as connectivity returns.
  useEffect(() => {
    if (online && sessionId) {
      void refreshQueue();
    }
  }, [online, sessionId, refreshQueue]);

  async function addPatient() {
    setAdding(true);
    setActionError(null);
    try {
      const token = await api.checkIn(sessionId!, newPatient.trim() || undefined);
      setNewPatient("");
      setFlash(`Token ${token.label} created`);
      await refreshQueue();
      if (token.patientUrl) {
        await copyToClipboard(token.patientUrl);
        setFlash(`Token ${token.label} created — patient link copied`);
      }
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "Check-in failed");
    } finally {
      setAdding(false);
    }
  }

  async function runPending(action: Exclude<PendingAction, null>, reason: string) {
    setBusy(true);
    setActionError(null);
    const token = "token" in action ? action.token : null;
    try {
      switch (action.kind) {
        case "skip":
          await api.skip(token!.id, reason);
          break;
        case "no-show":
          await api.noShow(token!.id, reason);
          break;
        case "return":
          await api.returnToWaiting(token!.id, reason);
          break;
        case "cancel":
          await api.cancel(token!.id, reason);
          break;
        case "priority":
          await api.setPriority(token!.id, action.enabled, reason);
          break;
        case "reorder": {
          const position = Number(targetPosition);
          if (!Number.isInteger(position) || position < 1) {
            setActionError("Enter a position of 1 or more");
            setBusy(false);
            return;
          }
          await api.reorder(token!.id, position, reason);
          break;
        }
        case "pause":
          await api.pauseSession(sessionId!, reason || "The doctor is temporarily delayed.");
          break;
        case "close":
          await api.closeSession(sessionId!, "CANCEL", reason);
          break;
      }
      setPending(null);
      setFlash("Action completed");
      await refreshQueue();
      await loadSessions();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "Action failed");
    } finally {
      setBusy(false);
    }
  }

  const waitingTokens = useMemo(
    () => (view?.tokens ?? []).filter((t) => t.status === "WAITING"),
    [view],
  );

  return (
    <div className="page">
      <section className="toolbar" aria-label="Session">
        <label htmlFor="session-select">Session</label>
        <select
          id="session-select"
          value={sessionId ?? ""}
          onChange={(event) => setSessionId(event.target.value || null)}
        >
          {sessions.length === 0 && <option value="">No sessions</option>}
          {sessions.map((session) => (
            <option key={session.id} value={session.id}>
              {session.businessDate} · {session.doctorName ?? "Doctor"} · {session.status}
            </option>
          ))}
        </select>

        {view && (
          <span className={`chip chip-${view.session.status.toLowerCase()}`} data-testid="session-status">
            {view.session.status}
            {view.session.pauseMessage ? ` — ${view.session.pauseMessage}` : ""}
          </span>
        )}

        <span className={`chip ${sse.connected ? "chip-live" : "chip-stale"}`} data-testid="live-chip">
          {sse.connected ? "Live" : "Reconnecting…"}
        </span>

        {canConfigure && view && view.session.status !== "CLOSED" && (
          <div className="toolbar-actions">
            {view.session.status === "PAUSED" ? (
              <button
                type="button"
                className="btn btn-ghost"
                onClick={async () => {
                  await api.resumeSession(sessionId!);
                  await refreshQueue();
                  await loadSessions();
                }}
              >
                Resume
              </button>
            ) : (
              <button type="button" className="btn btn-ghost" onClick={() => setPending({ kind: "pause" })}>
                Pause
              </button>
            )}
            <button type="button" className="btn btn-danger" onClick={() => setPending({ kind: "close" })}>
              Close
            </button>
          </div>
        )}
      </section>

      {loadError && <ErrorState message={loadError} onRetry={() => void refreshQueue()} />}
      {actionError && (
        <p className="field-error" role="alert">
          {actionError}
        </p>
      )}
      <SuccessFlash message={flash} />

      <section className="card" aria-label="Now serving">
        <h2>Now serving</h2>
        {view?.nowServing ? (
          <p className="now-serving" data-testid="now-serving">
            <strong>{view.nowServing.label}</strong>
            <span className={`chip ${statusClass(view.nowServing.status)}`}>
              {TOKEN_STATUS_LABEL[view.nowServing.status]}
            </span>
          </p>
        ) : (
          <p className="muted">No token called yet.</p>
        )}
        <p className="muted">
          {view?.peopleWaiting ?? 0} waiting ·{" "}
          {view?.session.fallbackConsultMinutes ?? 10} min fallback per patient
        </p>
        <button
          type="button"
          className="btn btn-primary btn-lg"
          data-testid="call-next"
          disabled={mutationsDisabled || view?.session.status !== "OPEN"}
          onClick={async () => {
            setActionError(null);
            try {
              await api.callNext(sessionId!);
              setFlash("Next token called");
              await refreshQueue();
            } catch (error) {
              setActionError(error instanceof Error ? error.message : "Call failed");
            }
          }}
        >
          Call next
        </button>      </section>

      <section className="card" aria-label="Add patient">
        <h2>Add patient</h2>
        <div className="add-row">
          <label htmlFor="display-name">Display name (optional)</label>
          <input
            id="display-name"
            value={newPatient}
            maxLength={120}
            onChange={(event) => setNewPatient(event.target.value)}
            placeholder="Optional name for staff view"
          />
          <button
            type="button"
            className="btn btn-primary btn-lg"
            data-testid="add-patient"
            disabled={adding || mutationsDisabled || !canConfigure || view?.session.status !== "OPEN"}
            onClick={() => void addPatient()}
          >
            {adding ? "Adding…" : "Add patient"}
          </button>
        </div>
      </section>

      <section className="card" aria-label="Queue">
        <h2>Queue ({waitingTokens.length} waiting)</h2>
        {loading && <Loading label="Loading queue…" />}
        {!loading && !view && <EmptyState message="Select or open a session to see its queue." />}
        {!loading && view && (
          <table className="token-table" data-testid="token-table">
            <thead>
              <tr>
                <th scope="col">Token</th>
                <th scope="col">Status</th>
                <th scope="col">Position</th>
                <th scope="col">Name</th>
                <th scope="col">Checked in</th>
                <th scope="col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {view.tokens.map((token) => {
                const actions = availableActions(token);
                return (
                  <tr key={token.id} data-testid={`token-row-${token.label}`}>
                    <td>
                      {token.label}
                      {token.priority && (
                        <span className="chip chip-priority" title="Priority is staff-controlled and audited">
                          priority
                        </span>
                      )}
                    </td>
                    <td>
                      <span className={`chip ${statusClass(token.status)}`}>
                        {TOKEN_STATUS_LABEL[token.status]}
                      </span>
                    </td>
                    <td>{token.queuePosition}</td>
                    <td>{token.displayName ?? "—"}</td>
                    <td>{formatTime(token.checkedInAt)}</td>
                    <td className="actions-cell">
                      {actions.canStart && (
                        <button type="button" className="btn btn-small" onClick={() => void api.startConsultation(token.id).then(refreshQueue)}>
                          Start
                        </button>
                      )}
                      {actions.canComplete && (
                        <button type="button" className="btn btn-small btn-primary" onClick={() => void api.complete(token.id).then(refreshQueue)}>
                          Complete
                        </button>
                      )}
                      {actions.canSkip && (
                        <button type="button" className="btn btn-small" onClick={() => setPending({ kind: "skip", token })}>
                          Skip
                        </button>
                      )}
                      {actions.canNoShow && (
                        <button type="button" className="btn btn-small" onClick={() => setPending({ kind: "no-show", token })}>
                          No-show
                        </button>
                      )}
                      {actions.canReturn && (
                        <button type="button" className="btn btn-small" onClick={() => setPending({ kind: "return", token })}>
                          Return
                        </button>
                      )}
                      {actions.canCancel && (
                        <button type="button" className="btn btn-small btn-danger" onClick={() => setPending({ kind: "cancel", token })}>
                          Cancel
                        </button>
                      )}
                      {actions.canPriority && (
                        <button
                          type="button"
                          className="btn btn-small"
                          onClick={() => setPending({ kind: "priority", token, enabled: !token.priority })}
                        >
                          {token.priority ? "Unprioritize" : "Priority"}
                        </button>
                      )}
                      {actions.canReorder && (
                        <button
                          type="button"
                          className="btn btn-small"
                          onClick={() => {
                            setTargetPosition("1");
                            setPending({ kind: "reorder", token });
                          }}
                        >
                          Reorder
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>

      <section className="card" aria-label="Display and reports">
        <h2>Display link and reports</h2>
        {view?.session.publicDisplaySecret && (
          <p className="muted">
            Waiting-room display:{" "}
            <a href={`/display/${view.session.publicDisplaySecret}`} target="_blank" rel="noreferrer">
              /display/{view.session.publicDisplaySecret.slice(0, 8)}…
            </a>
          </p>
        )}
        <div className="toolbar-actions">
          <button
            type="button"
            className="btn btn-ghost"
            onClick={async () => {
              if (!showAudit) {
                setAudit(await api.audit(sessionId!));
              }
              setShowAudit(!showAudit);
            }}
          >
            {showAudit ? "Hide audit" : "Show audit"}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={async () => {
              if (!showSummary) {
                setSummary(await api.summary(sessionId!));
              }
              setShowSummary(!showSummary);
            }}
          >
            {showSummary ? "Hide summary" : "Show summary"}
          </button>
        </div>

        {showSummary && summary && (
          <dl className="summary" data-testid="summary">
            <div>
              <dt>Total tokens</dt>
              <dd>{summary.totalTokens}</dd>
            </div>
            <div>
              <dt>Completed</dt>
              <dd>{summary.completedCount}</dd>
            </div>
            <div>
              <dt>Median consult</dt>
              <dd>{summary.medianConsultMinutes != null ? `${summary.medianConsultMinutes} min (n=${summary.medianSampleSize})` : "not enough data"}</dd>
            </div>
            <div>
              <dt>Waiting</dt>
              <dd>{summary.waitingCount}</dd>
            </div>
          </dl>
        )}

        {showAudit && (
          <table className="token-table" data-testid="audit-table">
            <thead>
              <tr>
                <th scope="col">Time</th>
                <th scope="col">Action</th>
                <th scope="col">Token</th>
                <th scope="col">Reason</th>
                <th scope="col">Actor</th>
              </tr>
            </thead>
            <tbody>
              {audit.map((event) => (
                <tr key={event.id}>
                  <td>{formatTime(event.createdAt)}</td>
                  <td>{event.action}</td>
                  <td>{event.tokenLabel ?? "—"}</td>
                  <td>{event.reason ?? "—"}</td>
                  <td>{event.actorDisplayName ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {pending && (
        <ReasonDialog
          title={ACTION_TITLES[pending.kind]}
          confirmLabel={pending.kind === "pause" ? "Pause queue" : "Confirm"}
          busy={busy}
          error={actionError}
          onCancel={() => setPending(null)}
          onConfirm={(reason) => void runPending(pending, reason)}
        >
          {"token" in pending && pending.token ? (
            <p className="muted">
              Token {pending.token.label} — a reason is required and will be recorded in the audit trail.
            </p>
          ) : (
            <p className="muted">
              Open tokens will be cancelled with this reason. This is recorded in the audit trail.
            </p>
          )}
          {pending.kind === "reorder" && (
            <label htmlFor="target-position">
              New position (1 = front of the waiting line)
              <input
                id="target-position"
                type="number"
                min={1}
                value={targetPosition}
                onChange={(event) => setTargetPosition(event.target.value)}
              />
            </label>
          )}
        </ReasonDialog>
      )}
    </div>
  );
}

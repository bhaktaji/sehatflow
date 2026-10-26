import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { useAuthStore } from "../store/auth";
import { useSessionEvents } from "../hooks/useSessionEvents";
import { useOnline } from "../hooks/useOnline";
import {
  availableActions,
  formatTime,
  statusClass,
  TOKEN_STATUS_LABEL,
} from "../lib/format";
import ReasonDialog from "../components/ReasonDialog";
import { EmptyState, ErrorState, Loading, SuccessFlash } from "../components/States";
import type { QueueToken, QueueView } from "../api/types";

/**
 * Doctor view: read-only queue with the actions a doctor may perform
 * (call next, start, complete, return to waiting, skip with reason).
 */
export default function DoctorPage() {
  const user = useAuthStore((state) => state.user);
  const online = useOnline();
  const [searchParams] = useSearchParams();

  const [sessionId, setSessionId] = useState<string | null>(null);
  const [view, setView] = useState<QueueView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);
  const [pending, setPending] = useState<QueueToken | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    if (!sessionId) return;
    try {
      setView(await api.queueView(sessionId));
      setError(null);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Could not load the queue");
    }
  }, [sessionId]);

  useEffect(() => {
    void (async () => {
      try {
        const list = await api.sessions(searchParams.get("date") ?? undefined);
        const own = list.find((s) => s.doctorId === user?.doctorId && s.status !== "CLOSED")
          ?? list.find((s) => s.status !== "CLOSED")
          ?? list[0];
        setSessionId(own?.id ?? null);
      } catch (requestError) {
        setError(requestError instanceof Error ? requestError.message : "Could not load sessions");
      }
    })();
  }, [user?.doctorId, searchParams]);

  useEffect(() => {
    if (!sessionId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    void refresh().finally(() => setLoading(false));
  }, [sessionId, refresh]);

  const sse = useSessionEvents(sessionId, () => void refresh());

  useEffect(() => {
    if (sse.connected || !sessionId) return;
    const timer = window.setInterval(() => void refresh(), 5000);
    return () => window.clearInterval(timer);
  }, [sse.connected, sessionId, refresh]);

  const waiting = (view?.tokens ?? []).filter((t) => t.status === "WAITING");

  return (
    <div className="page">
      <section className="toolbar">
        <h1>Consultation queue</h1>
        <span className={`chip ${sse.connected ? "chip-live" : "chip-stale"}`}>
          {sse.connected ? "Live" : "Reconnecting…"}
        </span>
        {view && (
          <span className={`chip chip-${view.session.status.toLowerCase()}`}>{view.session.status}</span>
        )}
      </section>

      {!online && (
        <p className="banner banner-offline">Offline — changes are not saved.</p>
      )}
      {error && <ErrorState message={error} onRetry={() => void refresh()} />}
      <SuccessFlash message={flash} />

      <section className="card">
        <h2>Current patient</h2>
        {view?.nowServing ? (
          <>
            <p className="now-serving" data-testid="doctor-current">
              <strong>{view.nowServing.label}</strong>
              <span className={`chip ${statusClass(view.nowServing.status)}`}>
                {TOKEN_STATUS_LABEL[view.nowServing.status]}
              </span>
            </p>
            <div className="toolbar-actions">
              {availableActions(view.nowServing).canStart && (
                <button
                  type="button"
                  className="btn btn-primary btn-lg"
                  onClick={async () => {
                    await api.startConsultation(view.nowServing!.id);
                    setFlash("Consultation started");
                    await refresh();
                  }}
                >
                  Start consultation
                </button>
              )}
              {availableActions(view.nowServing).canComplete && (
                <button
                  type="button"
                  className="btn btn-primary btn-lg"
                  data-testid="doctor-complete"
                  onClick={async () => {
                    await api.complete(view.nowServing!.id);
                    setFlash("Consultation completed");
                    await refresh();
                  }}
                >
                  Complete consultation
                </button>
              )}
              {availableActions(view.nowServing).canReturn && (
                <button type="button" className="btn btn-ghost" onClick={() => setPending(view.nowServing)}>
                  Return to waiting
                </button>
              )}
            </div>
          </>
        ) : (
          <p className="muted">No patient is being seen.</p>
        )}
      </section>

      <section className="card">
        <h2>Waiting ({waiting.length})</h2>
        {loading && <Loading />}
        {!loading && waiting.length === 0 && <EmptyState message="The waiting line is empty." />}
        <ul className="waiting-list" data-testid="doctor-waiting">
          {waiting.map((token) => (
            <li key={token.id}>
              <strong>{token.label}</strong>
              {token.priority && <span className="chip chip-priority">priority</span>}
              <span className="muted"> checked in {formatTime(token.checkedInAt)}</span>
            </li>
          ))}
        </ul>
      </section>

      {pending && (
        <ReasonDialog
          title="Return token to waiting"
          confirmLabel="Return"
          busy={busy}
          onCancel={() => setPending(null)}
          onConfirm={async (reason) => {
            setBusy(true);
            try {
              await api.returnToWaiting(pending.id, reason);
              setPending(null);
              setFlash("Token returned to the queue");
              await refresh();
            } finally {
              setBusy(false);
            }
          }}
        >
          <p className="muted">
            Token {pending.label} will rejoin the waiting line at the end. A reason is required.
          </p>
        </ReasonDialog>
      )}
    </div>
  );
}

import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api/client";
import { ErrorState, Loading } from "../components/States";
import type { PublicDisplayView } from "../api/types";

/**
 * Waiting-room display: token labels only, full-screen, no patient data.
 * Uses SSE with a polling fallback and always refetches the snapshot.
 */
export default function DisplayPage() {
  const { secret = "" } = useParams();
  const [view, setView] = useState<PublicDisplayView | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchSnapshot = useRef<(showError: boolean) => Promise<void>>(async () => {});

  fetchSnapshot.current = async (showError: boolean) => {
    try {
      const next = await api.publicDisplay(secret);
      setView(next);
      setError(null);
    } catch (requestError) {
      if (showError) {
        setError(requestError instanceof Error ? requestError.message : "Display link is invalid.");
      }
    }
  };

  useEffect(() => {
    void fetchSnapshot.current(true);
    const timer = window.setInterval(() => void fetchSnapshot.current(false), 8000);
    return () => window.clearInterval(timer);
  }, [secret]);

  useEffect(() => {
    const source = new EventSource(`/api/v1/public/queues/${secret}/events`, { withCredentials: true });
    source.onmessage = () => void fetchSnapshot.current(false);
    source.onerror = () => source.close();
    return () => source.close();
  }, [secret]);

  if (error) {
    return (
      <main className="display-page">
        <ErrorState message={error} />
      </main>
    );
  }

  if (!view) {
    return (
      <main className="display-page">
        <Loading label="Loading display…" />
      </main>
    );
  }

  return (
    <main className="display-page" aria-live="polite">
      <p className="display-clinic">Waiting room</p>

      <section className="display-current">
        <p className="display-label">Now serving</p>
        <p className="display-token" data-testid="display-now-serving">
          {view.nowServingLabel ?? "—"}
        </p>
        {view.sessionStatus === "PAUSED" && (
          <p className="display-paused" data-testid="display-paused">
            {view.pauseMessage ?? "The doctor is temporarily delayed."}
          </p>
        )}
        {view.sessionStatus === "CLOSED" && <p className="display-paused">Clinic is closed for today.</p>}
      </section>

      <section className="display-next">
        <p className="display-label">Next</p>
        <ul>
          {view.nextLabels.map((label) => (
            <li key={label}>{label}</li>
          ))}
        </ul>
      </section>

      <p className="muted small">Estimates are approximate. Please keep your token with you.</p>
    </main>
  );
}

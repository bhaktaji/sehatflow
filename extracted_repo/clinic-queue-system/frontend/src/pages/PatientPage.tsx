import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api/client";
import { etaDisplay } from "../lib/format";
import { ErrorState, Loading } from "../components/States";
import type { PublicTokenView } from "../api/types";

/**
 * Patient token page: no login, no name, no other patient's data.
 * Shows own token status, people ahead and an approximate wait only.
 */
export default function PatientPage() {
  const { secret = "" } = useParams();
  const [view, setView] = useState<PublicTokenView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const lastFetched = useRef<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchOnce() {
      try {
        const next = await api.publicToken(secret);
        if (cancelled) return;
        setView(next);
        lastFetched.current = next.updatedAt;
        setError(null);
      } catch (requestError) {
        if (!cancelled) {
          setError(
            requestError instanceof Error ? requestError.message : "This link is invalid or has been revoked.",
          );
        }
      }
    }

    void fetchOnce();
    // Polling fallback keeps the page fresh within a few seconds.
    const timer = window.setInterval(fetchOnce, 5000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [secret]);

  if (error) {
    return (
      <main className="public-page">
        <ErrorState message={error} />
      </main>
    );
  }

  if (!view) {
    return (
      <main className="public-page">
        <Loading label="Loading your token…" />
      </main>
    );
  }

  const eta = etaDisplay(view);

  return (
    <main className="public-page" aria-live="polite">
      <p className="public-clinic">Your clinic</p>
      <p className="public-token" data-testid="patient-token">
        {view.tokenLabel}
      </p>
      <p className="public-status" data-testid="patient-status">
        {view.status === "WAITING" && "You are in the queue"}
        {view.status === "CALLED" && "Your token was called — please proceed"}
        {view.status === "IN_CONSULTATION" && "Please proceed to the doctor"}
        {view.status === "SKIPPED" && "Your token was skipped — please see the reception desk"}
        {view.status === "NO_SHOW" && "This token was marked as not present"}
        {view.status === "CANCELLED" && "This token was cancelled"}
        {view.status === "COMPLETED" && "Your visit is complete. Thank you."}
      </p>

      {view.status === "WAITING" && (
        <p className="public-ahead" data-testid="people-ahead">
          {view.peopleAhead === 0
            ? "You are next"
            : `${view.peopleAhead} ${view.peopleAhead === 1 ? "person" : "people"} ahead of you`}
        </p>
      )}

      <p className="public-eta" data-testid="patient-eta">
        {eta.primary}
      </p>
      <p className="muted small">{eta.secondary}</p>
      <p className="muted small">{view.message}</p>

      {lastFetched.current && (
        <p className="muted small">Last updated {new Date(lastFetched.current).toLocaleTimeString()}</p>
      )}
    </main>
  );
}

import { useEffect, useRef, useState } from "react";

interface SseState {
  connected: boolean;
  lastEventAt: Date | null;
  /** Bumped on every server event so callers can refetch snapshots. */
  revision: number;
}

/**
 * Subscribes to the staff SSE stream for a queue session. The stream is a
 * notification only: every event triggers a fresh REST snapshot fetch, so
 * missed or duplicate events always converge to server state.
 */
export function useSessionEvents(sessionId: string | null, onEvent: () => void): SseState {
  const [state, setState] = useState<SseState>({ connected: false, lastEventAt: null, revision: 0 });
  const callbackRef = useRef(onEvent);
  callbackRef.current = onEvent;

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const source = new EventSource(`/api/v1/queue-sessions/${sessionId}/events`, { withCredentials: true });
    source.onopen = () => setState((s) => ({ ...s, connected: true }));
    source.onerror = () => setState((s) => ({ ...s, connected: false }));
    source.onmessage = () => {
      setState((s) => ({
        connected: true,
        lastEventAt: new Date(),
        revision: s.revision + 1,
      }));
      callbackRef.current();
    };
    return () => source.close();
  }, [sessionId]);

  return state;
}

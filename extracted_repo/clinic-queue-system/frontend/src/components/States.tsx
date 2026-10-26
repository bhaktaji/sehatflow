/** Display/hint states for the reception and doctor views. */
export function Loading({ label = "Loading…" }: { label?: string }) {
  return (
    <p className="state state-loading" role="status">
      {label}
    </p>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="btn btn-ghost" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return <p className="state state-empty">{message}</p>;
}

/** Toast-style success feedback. */
export function SuccessFlash({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <p className="flash-success" role="status">
      {message}
    </p>
  );
}

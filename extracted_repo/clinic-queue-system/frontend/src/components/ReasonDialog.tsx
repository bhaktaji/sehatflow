import { useEffect, useRef, useState } from "react";

interface ReasonDialogProps {
  title: string;
  confirmLabel: string;
  busy?: boolean;
  error?: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
  /** Optional extra control rendered inside the dialog. */
  children?: React.ReactNode;
  extraControls?: (setExtra: (value: string) => void) => React.ReactNode;
}

/**
 * Modal that requires a non-empty reason before confirming staff actions
 * such as reorder, priority, cancel or state corrections (LLD section 9).
 */
export default function ReasonDialog({
  title,
  confirmLabel,
  busy = false,
  error = null,
  onConfirm,
  onCancel,
  children,
}: ReasonDialogProps) {
  const [reason, setReason] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onCancel();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onCancel]);

  const trimmed = reason.trim();

  return (
    <div className="modal-backdrop" role="presentation" onClick={onCancel}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="dialog-title">{title}</h2>
        {children}
        <label htmlFor="dialog-reason">
          Reason (required)
          <input
            id="dialog-reason"
            ref={inputRef}
            type="text"
            value={reason}
            maxLength={240}
            onChange={(event) => setReason(event.target.value)}
            aria-describedby={error ? "dialog-error" : undefined}
          />
        </label>
        {error && (
          <p className="field-error" id="dialog-error" role="alert">
            {error}
          </p>
        )}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn-primary"
            disabled={busy || trimmed.length === 0}
            onClick={() => onConfirm(trimmed)}
          >
            {busy ? "Working…" : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

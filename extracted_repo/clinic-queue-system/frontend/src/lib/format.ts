import type { PublicTokenView, QueueToken, TokenStatus } from "../api/types";

/** Human-facing labels for token states (used by staff screens). */
export const TOKEN_STATUS_LABEL: Record<TokenStatus, string> = {
  WAITING: "Waiting",
  CALLED: "Called",
  IN_CONSULTATION: "With doctor",
  COMPLETED: "Completed",
  SKIPPED: "Skipped",
  NO_SHOW: "No show",
  CANCELLED: "Cancelled",
};

const STATUS_CLASS: Record<TokenStatus, string> = {
  WAITING: "status-waiting",
  CALLED: "status-called",
  IN_CONSULTATION: "status-consulting",
  COMPLETED: "status-completed",
  SKIPPED: "status-skipped",
  NO_SHOW: "status-skipped",
  CANCELLED: "status-cancelled",
};

export function statusClass(status: TokenStatus): string {
  return STATUS_CLASS[status] ?? "status-waiting";
}

/** Which actions are valid for a token in its current state (mirrors the backend state machine). */
export function availableActions(token: QueueToken): {
  canStart: boolean;
  canComplete: boolean;
  canSkip: boolean;
  canNoShow: boolean;
  canReturn: boolean;
  canCancel: boolean;
  canPriority: boolean;
  canReorder: boolean;
} {
  switch (token.status) {
    case "WAITING":
      return { canStart: false, canComplete: false, canSkip: true, canNoShow: true, canReturn: false, canCancel: true, canPriority: true, canReorder: true };
    case "CALLED":
      return { canStart: true, canComplete: false, canSkip: true, canNoShow: true, canReturn: true, canCancel: true, canPriority: false, canReorder: false };
    case "IN_CONSULTATION":
      return { canStart: false, canComplete: true, canSkip: false, canNoShow: false, canReturn: true, canCancel: false, canPriority: false, canReorder: false };
    case "SKIPPED":
      return { canStart: false, canComplete: false, canSkip: false, canNoShow: false, canReturn: true, canCancel: true, canPriority: false, canReorder: false };
    default:
      return { canStart: false, canComplete: false, canSkip: false, canNoShow: false, canReturn: false, canCancel: false, canPriority: false, canReorder: false };
  }
}

/** Patient-facing ETA line: never promises, always approximate. */
export function etaDisplay(view: Pick<PublicTokenView, "estimatedWaitMinutes" | "estimateQuality">): {
  primary: string;
  secondary: string;
} {
  if (view.estimatedWaitMinutes == null) {
    return { primary: "Wait time unavailable", secondary: "Please check with the reception desk." };
  }
  const minutes = view.estimatedWaitMinutes;
  const rounded = minutes <= 1 ? "about 1 minute" : `about ${minutes} minutes`;
  const quality = view.estimateQuality === "LOW" ? " (rough estimate)" : "";
  return {
    primary: `Estimated wait: ${rounded}${quality}`,
    secondary: "Estimates are approximate only and may change.",
  };
}

export function formatTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

/** Copy helper used for the patient link. */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

/** Builds the patient page URL for a token secret. */
export function patientUrl(secret: string): string {
  return `${location.origin}/p/${secret}`;
}

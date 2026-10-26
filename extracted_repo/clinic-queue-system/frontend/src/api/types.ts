// Shared API types (mirror the backend DTOs).

export type Role = "OWNER" | "RECEPTIONIST" | "DOCTOR";

export interface UserInfo {
  userId: string;
  clinicId: string;
  clinicName: string | null;
  username: string;
  displayName: string;
  role: Role;
  doctorId: string | null;
}

export interface Doctor {
  id: string;
  displayName: string;
  specialty: string | null;
  active: boolean;
}

export type SessionStatus = "OPEN" | "PAUSED" | "CLOSED";

export interface QueueSession {
  id: string;
  clinicId: string;
  doctorId: string;
  doctorName: string | null;
  businessDate: string;
  status: SessionStatus;
  tokenPrefix: string;
  nextTokenNumber: number;
  fallbackConsultMinutes: number;
  pauseMessage: string | null;
  openedAt: string;
  closedAt: string | null;
  publicDisplaySecret: string | null;
  waitingCount: number;
  totalTokens: number;
}

export type TokenStatus =
  | "WAITING"
  | "CALLED"
  | "IN_CONSULTATION"
  | "COMPLETED"
  | "SKIPPED"
  | "NO_SHOW"
  | "CANCELLED";

export interface QueueToken {
  id: string;
  tokenNumber: number;
  label: string;
  status: TokenStatus;
  priority: boolean;
  queuePosition: number;
  displayName: string | null;
  checkedInAt: string;
  calledAt: string | null;
  patientUrl: string | null;
}

export interface QueueView {
  session: QueueSession;
  tokens: QueueToken[];
  nowServing: QueueToken | null;
  peopleWaiting: number;
}

export interface PublicTokenView {
  tokenLabel: string;
  status: TokenStatus;
  peopleAhead: number;
  estimatedWaitMinutes: number | null;
  estimateQuality: "HIGH" | "MEDIUM" | "LOW" | "UNAVAILABLE";
  message: string;
  updatedAt: string;
}

export interface PublicDisplayView {
  sessionStatus: SessionStatus;
  pauseMessage: string | null;
  nowServingLabel: string | null;
  nextLabels: string[];
  updatedAt: string;
}

export interface AuditEvent {
  id: string;
  action: string;
  reason: string | null;
  tokenId: string | null;
  tokenLabel: string | null;
  actorUserId: string | null;
  actorDisplayName: string | null;
  beforeState: string | null;
  afterState: string | null;
  createdAt: string;
}

export interface SessionSummary {
  sessionId: string;
  businessDate: string;
  totalTokens: number;
  countsByStatus: Record<string, number>;
  completedCount: number;
  medianConsultMinutes: number | null;
  medianSampleSize: number;
  fallbackMinutes: number;
  waitingCount: number;
}

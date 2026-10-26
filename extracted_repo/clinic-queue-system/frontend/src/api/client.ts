import axios, { AxiosError, type AxiosInstance, type AxiosResponse } from "axios";
import type {
  AuditEvent,
  Doctor,
  PublicDisplayView,
  PublicTokenView,
  QueueSession,
  QueueToken,
  QueueView,
  SessionSummary,
  UserInfo,
} from "./types";

/** Reads the CSRF cookie the backend sets on GET /api/v1/auth/csrf. */
export function readCsrfCookie(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    public readonly detail: string | undefined,
  ) {
    super(detail ?? code);
    this.name = "ApiRequestError";
  }
}

export function createApiClient(baseUrl = ""): AxiosInstance {
  const client = axios.create({
    baseURL: `${baseUrl}/api/v1`,
    withCredentials: true,
    headers: { "Content-Type": "application/json" },
  });

  client.interceptors.request.use((config) => {
    const method = (config.method ?? "get").toUpperCase();
    if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
      const token = readCsrfCookie();
      if (token) {
        config.headers["X-XSRF-TOKEN"] = token;
      }
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    (error: AxiosError<{ detail?: string; code?: string }>) => {
      const code = error.response?.data?.code;
      const detail = error.response?.data?.detail;
      const requestUrl = error.config?.url ?? "";
      const isSessionProbe = requestUrl.includes("/auth/me");
      const isPublicPage = location.pathname.startsWith("/p/") || location.pathname.startsWith("/display/");
      // Only bounce to the login screen for staff pages; the session probe and
      // public pages handle 401/404 themselves.
      if (code === "UNAUTHENTICATED" && !isSessionProbe && !isPublicPage && !location.pathname.startsWith("/login")) {
        location.assign("/login");
      }
      return Promise.reject(new ApiRequestError(error.response?.status ?? 0, code ?? "UNKNOWN", detail));
    },
  );

  return client;
}

let sharedClient: AxiosInstance | null = null;

function client(): AxiosInstance {
  if (!sharedClient) {
    sharedClient = createApiClient(import.meta.env.VITE_API_BASE_URL ?? "");
  }
  return sharedClient;
}

/** Unwraps the axios response body; errors become ApiRequestError. */
async function unwrap<T>(promise: Promise<AxiosResponse<T>>): Promise<T> {
  const response = await promise;
  return response.data;
}

/** Bootstraps the CSRF cookie before the first state-changing request. */
export async function bootstrapCsrf(): Promise<void> {
  await client().get("/auth/csrf");
}

export const api = {
  auth: {
    login: (username: string, password: string, clinicSlug?: string) =>
      unwrap(client().post<UserInfo>("/auth/login", { username, password, clinicSlug })),
    logout: () => unwrap(client().post<void>("/auth/logout")),
    me: () => unwrap(client().get<UserInfo>("/auth/me")),
    csrf: () => unwrap(client().get<void>("/auth/csrf")),
  },
  clinic: () => unwrap(client().get<{ id: string; name: string; slug: string; timezone: string }>("/clinic")),
  doctors: () => unwrap(client().get<Doctor[]>("/doctors")),
  createDoctor: (displayName: string, specialty: string) =>
    unwrap(client().post<Doctor>("/doctors", { displayName, specialty })),
  sessions: (businessDate?: string) =>
    unwrap(client().get<QueueSession[]>("/queue-sessions", {
      params: businessDate ? { businessDate } : undefined,
    })),
  createSession: (doctorId: string, businessDate?: string) =>
    unwrap(client().post<QueueSession>("/queue-sessions", { doctorId, businessDate })),
  pauseSession: (sessionId: string, message: string) =>
    unwrap(client().post<QueueSession>(`/queue-sessions/${sessionId}/pause`, { message })),
  resumeSession: (sessionId: string) =>
    unwrap(client().post<QueueSession>(`/queue-sessions/${sessionId}/resume`)),
  closeSession: (sessionId: string, resolution?: string, reason?: string) =>
    unwrap(client().post<QueueSession>(`/queue-sessions/${sessionId}/close`, { resolution, reason })),
  rotateDisplaySecret: (sessionId: string) =>
    unwrap(client().post<QueueSession>(`/queue-sessions/${sessionId}/rotate-display-secret`)),
  queueView: (sessionId: string) =>
    unwrap(client().get<QueueView>(`/queue-sessions/${sessionId}/queue`)),
  checkIn: (sessionId: string, displayName?: string) =>
    unwrap(client().post<QueueToken>(`/queue-sessions/${sessionId}/tokens`, {
      displayName: displayName || undefined,
    })),
  callNext: (sessionId: string) =>
    unwrap(client().post<QueueToken>(`/queue-sessions/${sessionId}/call-next`)),
  startConsultation: (tokenId: string) =>
    unwrap(client().post<QueueToken>(`/tokens/${tokenId}/start`)),
  complete: (tokenId: string) =>
    unwrap(client().post<QueueToken>(`/tokens/${tokenId}/complete`)),
  skip: (tokenId: string, reason: string) =>
    unwrap(client().post<QueueToken>(`/tokens/${tokenId}/skip`, { reason })),
  noShow: (tokenId: string, reason: string) =>
    unwrap(client().post<QueueToken>(`/tokens/${tokenId}/no-show`, { reason })),
  returnToWaiting: (tokenId: string, reason: string, targetPosition?: number) =>
    unwrap(client().post<QueueToken>(`/tokens/${tokenId}/return-to-waiting`, { reason, targetPosition })),
  cancel: (tokenId: string, reason: string) =>
    unwrap(client().post<QueueToken>(`/tokens/${tokenId}/cancel`, { reason })),
  setPriority: (tokenId: string, enabled: boolean, reason: string) =>
    unwrap(client().post<QueueToken>(`/tokens/${tokenId}/priority`, { enabled, reason })),
  reorder: (tokenId: string, targetPosition: number, reason: string) =>
    unwrap(client().post<QueueToken>(`/tokens/${tokenId}/reorder`, { targetPosition, reason })),
  audit: (sessionId: string) =>
    unwrap(client().get<AuditEvent[]>(`/queue-sessions/${sessionId}/audit`)),
  summary: (sessionId: string) =>
    unwrap(client().get<SessionSummary>(`/queue-sessions/${sessionId}/summary`)),
  publicToken: (secret: string) =>
    unwrap(client().get<PublicTokenView>(`/public/tokens/${secret}`)),
  publicDisplay: (secret: string) =>
    unwrap(client().get<PublicDisplayView>(`/public/queues/${secret}`)),
};

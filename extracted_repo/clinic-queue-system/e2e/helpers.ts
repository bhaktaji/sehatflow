import { execSync } from "node:child_process";
import { request as playwrightRequest, type APIRequestContext } from "@playwright/test";

/**
 * Deterministic E2E setup helpers.
 *
 * - Resets the queue data for a fixed business date via SQL (dev database).
 * - Seeds a second clinic through psql/pgcrypto for the tenant-isolation spec.
 *
 * The dev demo seed (owner/owner123, reception/recep123, doctor/doc123) is
 * created by the backend on first boot of an empty database.
 */

export const E2E_DATE = "2027-06-15";
/** Second date used by the tenant-isolation spec (Clinic B). */
export const E2E_DATE_B = "2027-06-16";
export const API_BASE = "http://localhost:8090/api/v1";

export const DEMO = {
  owner: { username: "owner", password: "owner123" },
  reception: { username: "reception", password: "recep123" },
  doctor: { username: "doctor", password: "doc123" },
};

const PSQL = "C:/Users/Aakash/pg16/bin/psql.exe";

/** Runs one single-line SQL statement (multi-line args break through cmd.exe). */
function sql(statement: string): string {
  const flat = statement.replace(/\s+/g, " ").trim();
  return execSync(`"${PSQL}" -p 5433 -U postgres -d clinicqueue -v ON_ERROR_STOP=1 -c "${flat.replace(/"/g, '\\"')}"`, {
    stdio: "pipe",
  }).toString();
}

/** Removes any sessions/tokens/audit rows for the E2E dates so reruns are clean. */
export function resetE2eDate(): void {
  for (const date of [E2E_DATE, E2E_DATE_B]) {
    const sessionIds = `(SELECT id FROM queue_sessions WHERE business_date = '${date}')`;
    sql(`DELETE FROM queue_audit_events WHERE queue_session_id IN ${sessionIds}`);
    sql(`DELETE FROM queue_tokens WHERE queue_session_id IN ${sessionIds}`);
    sql(`DELETE FROM queue_sessions WHERE business_date = '${date}'`);
  }
}

/** Seeds Clinic B (slug clinic-b) with a receptionist for cross-tenant tests. */
export function seedClinicB(): { username: string; password: string } {
  const password = "clinicb-pass";
  sql("CREATE EXTENSION IF NOT EXISTS pgcrypto");
  sql(
    "INSERT INTO clinics (id, name, slug, timezone, active, created_at, updated_at) " +
      "VALUES (gen_random_uuid(), 'Clinic B Test', 'clinic-b', 'Asia/Kolkata', true, now(), now()) " +
      "ON CONFLICT (slug) DO NOTHING",
  );
  sql(
    "INSERT INTO doctors (id, clinic_id, display_name, specialty, active, created_at, updated_at) " +
      "VALUES (gen_random_uuid(), (SELECT id FROM clinics WHERE slug='clinic-b'), 'Dr. Clinic B', 'General', true, now(), now()) " +
      "ON CONFLICT DO NOTHING",
  );
  sql(
    "INSERT INTO staff_users (id, clinic_id, display_name, username, password_hash, role, doctor_id, active, created_at, updated_at) " +
      `VALUES (gen_random_uuid(), (SELECT id FROM clinics WHERE slug='clinic-b'), 'Clinic B Reception', 'clinicb-reception', ` +
      `crypt('${password}', gen_salt('bf', 10)), 'RECEPTIONIST', ` +
      "(SELECT id FROM doctors WHERE display_name='Dr. Clinic B' LIMIT 1), true, now(), now()) " +
      "ON CONFLICT (clinic_id, username) DO NOTHING",
  );
  return { username: "clinicb-reception", password };
}

/**
 * An authenticated API session that carries the CSRF token on every
 * state-changing request (cookies ride along via the request context).
 */
export class ApiSession {
  constructor(
    private readonly api: APIRequestContext,
    private readonly csrfToken: string,
  ) {}

  get(path: string) {
    return this.api.get(`${API_BASE}${path}`);
  }

  post(path: string, data?: unknown) {
    return this.api.post(`${API_BASE}${path}`, {
      data,
      headers: { "X-XSRF-TOKEN": this.csrfToken },
    });
  }

  json<T>(path: string): Promise<T> {
    return this.api.get(`${API_BASE}${path}`).then((response) => response.json()) as Promise<T>;
  }

  dispose() {
    return this.api.dispose();
  }
}

/** Logs in through the API and returns a CSRF-carrying session. */
export async function loginApi(username: string, password: string): Promise<ApiSession> {
  const api = await playwrightRequest.newContext();
  const csrf = await api.get(`${API_BASE}/auth/csrf`);
  if (!csrf.ok()) {
    throw new Error(`csrf bootstrap failed: ${csrf.status()}`);
  }
  const token = /XSRF-TOKEN=([^;]*)/.exec(csrf.headers()["set-cookie"] ?? "")?.[1];
  if (!token) {
    throw new Error("no XSRF-TOKEN cookie in csrf response");
  }
  const login = await api.post(`${API_BASE}/auth/login`, {
    data: { username, password },
    headers: { "X-XSRF-TOKEN": decodeURIComponent(token) },
  });
  if (!login.ok()) {
    throw new Error(`login failed for ${username}: ${login.status()} ${await login.text()}`);
  }
  return new ApiSession(api, decodeURIComponent(token));
}

/** Creates a fresh session for the E2E date and returns its id + display secret. */
export async function createSession(
  session: ApiSession,
  doctorId: string,
  businessDate = E2E_DATE,
): Promise<{ sessionId: string; displaySecret: string }> {
  const response = await session.post("/queue-sessions", { doctorId, businessDate });
  if (!response.ok()) {
    throw new Error(`create session failed: ${response.status()} ${await response.text()}`);
  }
  const body = await response.json();
  return { sessionId: body.id, displaySecret: body.publicDisplaySecret };
}

export async function firstDoctorId(session: ApiSession): Promise<string> {
  const doctors = (await session.json<Array<{ id: string }>>("/doctors")) ?? [];
  if (doctors.length === 0) {
    throw new Error("no doctors seeded");
  }
  return doctors[0].id;
}

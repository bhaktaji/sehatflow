# Clinic Queue System

A walk-in queue for small clinics: one queue per doctor per day, a shared
waiting-room display, patient token pages with approximate waits, and a full
audit trail of every staff action.

- **Backend** — Spring Boot 3.3.5 / Java 21 / PostgreSQL 16 / Flyway
- **Frontend** — React 18 + TypeScript + Vite
- **Tests** — JUnit (unit + PostgreSQL integration), Vitest, Playwright

## Contents

- [What it does](#what-it-does)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Running locally](#running-locally)
- [Demo accounts](#demo-accounts)
- [Configuration](#configuration)
- [API overview](#api-overview)
- [Testing](#testing)
- [Security model](#security-model)
- [Requirement coverage](#requirement-coverage)
- [Known limitations](#known-limitations)

> New here? Follow **[GETTING_STARTED.md](GETTING_STARTED.md)** for a
> clone-to-running walkthrough with verification steps.

## What it does

| Surface | Who | Purpose |
| --- | --- | --- |
| `/login` | staff | Session login (cookie-based) |
| `/` | owner, receptionist | Check in walk-ins, call next, skip, no-show, cancel, priority, reorder, pause/resume/close, audit and daily summary |
| `/doctor` | doctor | See the current patient, start/complete consultations, return a token to the queue |
| `/p/:secret` | patient | Own token label, status, people ahead, approximate wait — no names, no other patients |
| `/display/:secret` | waiting room | Large "now serving" and "next" labels only |

Key behaviours:

- Tokens are labelled per session (`A1`, `A2`, …) and ordered by priority, then
  position, then arrival.
- Every state change is validated server-side by an explicit state machine;
  terminal states cannot be transitioned again.
- Priority, reorder, skip, no-show, cancel, pause and close require a **reason**
  and are written to an append-only audit log with actor and before/after state.
- Waits are estimated from the rolling median of recent consultation durations,
  with a configurable fallback and a `HIGH`/`MEDIUM`/`LOW`/`UNAVAILABLE` quality
  flag. Paused and closed sessions never show a confident number.
- A session row lock serializes check-in and call-next, so concurrent staff
  actions cannot duplicate token numbers or call the same token twice.

## Architecture

```
React SPA (Vite, port 5180)
   │  same-origin /api via dev proxy (or a reverse proxy in production)
   ▼
Spring Boot API (port 8090)
   ├── Spring Security: cookie session + CSRF, deny-by-default, roles
   ├── Rate limit filter (login + public endpoints)
   ├── Services: queue state machine, ETA, public views, audit
   ├── SSE hub (staff + display streams; clients always refetch snapshots)
   └── JPA / Flyway
          ▼
     PostgreSQL 16 (port 5433)
```

Design notes worth knowing:

- **REST is the source of truth.** SSE messages are notifications; every event
  triggers a fresh snapshot fetch, so a missed event only delays an update.
- **Tenant identity comes from the session**, never from a request parameter.
  Cross-clinic reads return `404`, cross-clinic mutations `403
  FORBIDDEN_TENANT_SCOPE`, and no other clinic's data is ever serialized.
- **Public pages are scoped by an unguessable secret** (32 random bytes, stored
  only as a SHA-256 hash). Invalid secrets return a uniform `404`.
- **No secrets or PII in logs**; audit stores ids, actions, reasons and state
  summaries, not patient names.

## Project structure

```
clinic-queue-system/
├── backend/                          Spring Boot API (Java 21, Maven)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/clinicqueue/
│       │   │   ├── ClinicQueueApplication.java     entry point
│       │   │   ├── auth/                           login filter, session handling
│       │   │   ├── clinic/                         clinic + doctor endpoints
│       │   │   ├── common/                         config, security, error mapping
│       │   │   ├── identity/                       AuthController, staff accounts
│       │   │   ├── publicview/                     unauthenticated patient/display
│       │   │   ├── queue/                          QueueController, services, state machine
│       │   │   └── reporting/                      audit + daily summary
│       │   └── resources/
│       │       ├── application.yml                 env-driven configuration
│       │       └── db/migration/                   Flyway schema migration
│       └── test/java/com/clinicqueue/              unit + PostgreSQL integration tests
│
├── frontend/                         React 18 + TypeScript + Vite SPA
│   ├── package.json
│   ├── vite.config.ts               dev server on :5180, /api proxy to :8090
│   └── src/
│       ├── main.tsx · App.tsx       router; public pages are not behind auth
│       ├── api/                     typed API client + CSRF handling
│       ├── store/                   Zustand auth store
│       ├── hooks/                   SSE + connectivity hooks
│       ├── components/              layout, reason dialog, states
│       ├── pages/                   login · reception · doctor · patient · display
│       ├── lib/                     display helpers (ETA text, action matrix)
│       └── styles.css
│
├── e2e/                              Playwright end-to-end suite (separate npm project)
│   ├── playwright.config.ts          starts PostgreSQL, backend and Vite itself
│   ├── helpers.ts                    seeding, date reset, API login
│   └── specs/                        one file per regression scenario (E2E-01..05)
│
├── scripts/                          test runners (bash)
│   ├── backend-test.sh               unit / integration suites
│   ├── frontend.sh                   lint · test · build · dev
│   ├── e2e-test.sh                   full Playwright run
│   ├── ensure-postgres.sh            starts the local instance on :5433
│   └── api-smoke.sh                  39-assertion curl smoke test
│
├── docs/                             HLD, LLD, regression plan
├── start-all.bat                     Windows one-click launcher (see below)
├── stop-all.bat                      stops backend, frontend and PostgreSQL
└── README.md
```

Build output (`target/`, `dist/`), `node_modules/`, `.env` files and local
tooling state are ignored — see `.gitignore`.

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 | `JAVA_HOME` must point at a JDK (not a JRE) |
| Maven | 3.9+ | or the Maven wrapper if added later |
| Node.js | 20+ | 22 LTS tested |
| PostgreSQL | 16 | local instance on port 5433 |

Create the databases once:

```bash
psql -p 5433 -U postgres -c "CREATE DATABASE clinicqueue;"
psql -p 5433 -U postgres -c "CREATE DATABASE clinicqueue_test;"
```

Flyway creates the schema on first start; no manual DDL is needed.

## Running locally

### One-click launcher (Windows)

Double-click **`start-all.bat`** in the repository root, or run it from a
terminal:

```bat
start-all.bat                 :: build if needed, start everything, open the browser
start-all.bat --rebuild       :: mvn clean package first
start-all.bat --no-build      :: use the existing jar
start-all.bat --no-browser    :: do not open a browser
```

It handles the three things that break a naive launcher on this machine:

- **JDK 21** — `java` on `PATH` is a JRE 8, which cannot compile this project.
  The script locates a real JDK ≥ 21 and exports `JAVA_HOME` for the backend
  window (resolution order: `JAVA_HOME` → Microsoft → Adoptium → Oracle →
  `~\.jdks`).
- **PostgreSQL on port 5433** — the default `pg_ctl start` brings the server up
  on 5432, where the backend cannot reach it. The script always passes
  `-o "-p 5433"`, waits for the socket, and creates the `clinicqueue` /
  `clinicqueue_test` databases on first run.
- **Ports** — backend `8090`, frontend `5180`, database `5433`. The backend and
  frontend run in their own console windows so their logs stay readable.

Stop everything with **`stop-all.bat`**. PostgreSQL is detached, so closing the
console windows is not enough; the script also matches the backend/frontend
processes by command line rather than by image name, so it never kills other
projects' `java.exe` or `node.exe`.

### Manual steps

### 1. Database

```bash
# Start a local PostgreSQL 16 instance on port 5433 (adjust to your setup)
pg_ctl -D /path/to/pgdata -o "-p 5433" -l /path/to/pg.log start
```

`scripts/ensure-postgres.sh` does this automatically for the test scripts.

### 2. Backend

```bash
cd backend
mvn -DskipTests package
java -jar target/clinic-queue-backend-0.1.0.jar --server.port=8090
```

Health check: <http://localhost:8090/actuator/health> · OpenAPI:
<http://localhost:8090/swagger-ui.html>

On the first start against an empty database, a demo clinic is seeded
(Sunrise Family Clinic, one doctor, three staff accounts — see below).

### 3. Frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:5180
```

The dev server proxies `/api` to `http://localhost:8090`, so cookies, CSRF and
SSE all work same-origin. Override the proxy target with
`VITE_API_PROXY_TARGET` or point the SPA at another host with
`VITE_API_BASE_URL`.

### One-command smoke test

With the backend running:

```bash
bash scripts/api-smoke.sh http://localhost:8090
```

It exercises login, check-in, call-next, public views, pause/resume, priority,
audit, CSRF and rate limiting (39 assertions).

## Demo accounts

| Username | Password | Role |
| --- | --- | --- |
| `owner` | `owner123` | OWNER (full access, clinic settings) |
| `reception` | `recep123` | RECEPTIONIST (queue operations) |
| `doctor` | `doc123` | DOCTOR (own queue, consultation flow) |

**Change these before any real deployment.** They exist only for local demo and
tests.

## Configuration

Backend (environment variables or `application.yml`):

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_DB_URL` | `jdbc:postgresql://localhost:5433/clinicqueue` | JDBC URL |
| `APP_DB_USER` / `APP_DB_PASSWORD` | `postgres` / `postgres` | DB credentials |
| `APP_CORS_ORIGINS` | `http://localhost:5180,http://localhost:5173,http://localhost:4173` | Allowed browser origins |
| `APP_COOKIE_SECURE` | `false` | Set `true` behind HTTPS |
| `APP_RATELIMIT_LOGIN_PER_MINUTE` | `10` | Login attempts per IP per minute |
| `APP_RATELIMIT_PUBLIC_PER_MINUTE` | `120` | Public requests per IP per minute |
| `APP_SEED_ENABLED` | `true` | Seed the demo clinic when the DB is empty |

Integration tests read `APP_IT_DB_URL` (default
`jdbc:postgresql://localhost:5433/clinicqueue_test`).

Frontend (`frontend/.env`):

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | empty (same origin) | API host for non-proxied deployments |
| `VITE_API_PROXY_TARGET` | `http://localhost:8090` | Dev proxy target |

## API overview

All endpoints are under `/api/v1`. Errors are RFC 7807 problem details with a
stable `code` field (`UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`,
`VALIDATION_ERROR`, `TOKEN_STATE_CONFLICT`, `QUEUE_PAUSED`, `SESSION_CLOSED`,
`QUEUE_EMPTY`, `SESSION_EXISTS`, `PUBLIC_LINK_EXPIRED`, `RATE_LIMITED`, …) and a
`requestId`.

**Auth** — `GET /auth/csrf`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`

**Clinic** — `GET /clinic`, `GET /doctors`, `POST /doctors`

**Sessions** — `GET /queue-sessions`, `POST /queue-sessions`,
`GET /queue-sessions/{id}`, `GET /queue-sessions/{id}/queue`,
`GET /queue-sessions/{id}/events` (SSE), `POST …/pause`, `POST …/resume`,
`POST …/close`, `POST …/rotate-display-secret`, `POST …/tokens`,
`POST …/call-next`, `GET …/audit`, `GET …/summary`

**Tokens** — `POST /tokens/{id}/start`, `/complete`, `/skip`, `/no-show`,
`/return-to-waiting`, `/cancel`, `/priority`, `/reorder`

**Public (no auth)** — `GET /public/tokens/{secret}`,
`GET /public/queues/{secret}`, `GET /public/queues/{secret}/events` (SSE)

CSRF: `GET /auth/csrf` sets an `XSRF-TOKEN` cookie; every state-changing request
must echo it in the `X-XSRF-TOKEN` header.

## Testing

```bash
# Unit tests (no database)
bash scripts/backend-test.sh unit

# Unit + PostgreSQL integration tests
bash scripts/backend-test.sh all

# Frontend lint, unit tests, build
bash scripts/frontend.sh lint
bash scripts/frontend.sh test
bash scripts/frontend.sh build

# End-to-end (starts PostgreSQL, backend and the dev server itself)
bash scripts/e2e-test.sh
```

Current status:

| Suite | Count | What it covers |
| --- | --- | --- |
| Backend unit | 25 | ETA rules, state machine, ordering, CSRF repository, secret hashing |
| Backend integration (PostgreSQL) | 35 | Lifecycle, concurrency, tenant isolation, public privacy, pause/close, audit, security filters, rate limiting, migration, persistence |
| Frontend unit | 7 | Action matrix, ETA text, formatting, links |
| E2E (Playwright, Chromium) | 5 | Happy path, priority override, paused doctor, tenant isolation, reconnect |

E2E specs run against a fixed business date (`2027-06-15`, plus `2027-06-16`
for the tenant-isolation spec) and reset those dates before each test, so runs
are repeatable on a dev database.

## Security model

- Deny-by-default: every endpoint requires authentication except
  `/api/v1/auth/*`, `/api/v1/public/**`, health and docs.
- Cookie sessions with `HttpOnly`, `SameSite=Lax`, `Secure` when
  `APP_COOKIE_SECURE=true`; session id rotated on login.
- CSRF double-submit token on all state-changing requests.
- Role checks are enforced server-side (`@PreAuthorize`); the frontend only
  hides controls.
- Tenant scope is applied in every repository query; object references from
  another clinic are concealed.
- Public links are unguessable secrets; responses are cached as `no-store` and
  sent with `Referrer-Policy: no-referrer`.
- Rate limiting on login and public endpoints (429 + `Retry-After`).
- Audit log is append-only: there are no update or delete endpoints.

## Requirement coverage

| Requirement | Where it is verified |
| --- | --- |
| R-001 / R-002 login, generic auth errors | `api-smoke.sh`, `SecurityWebIT` |
| R-003 unique labels per session | `QueueLifecycleIT`, `QueueConcurrencyIT` |
| R-005 / R-006 call next, no double call | `QueueLifecycleIT`, `QueueConcurrencyIT` |
| R-007 / R-008 concurrency safety | `QueueConcurrencyIT` |
| R-009 / R-010 lifecycle and illegal transitions | `QueueLifecycleIT`, `QueueServiceStateMachineTest` |
| R-011 pause/resume/close | `PauseCloseIT`, E2E-03 |
| R-012 / R-013 reason-required actions + audit | `AuditIntegrityIT`, E2E-02 |
| R-014 / R-015 skip, rejoin, people ahead | `QueueLifecycleIT`, `EtaServiceIT` |
| R-016 / R-017 / R-018 public privacy | `PublicPrivacyIT`, E2E-01/02 |
| R-019 / R-021 tenant isolation | `TenantIsolationIT`, E2E-04 |
| R-020 / R-029 unauthenticated and CSRF denial | `SecurityWebIT` |
| R-022 persistence across restarts | `PersistenceIT` |
| R-023 migrations from empty schema | `MigrationIT` |
| R-024 rate limiting | `RateLimitIT`, `api-smoke.sh` |
| R-025 audit completeness | `AuditIntegrityIT` |

## Known limitations

- Single clinic per deployment is seeded by default; there is no self-service
  clinic signup or admin console yet.
- Rate limiting is in-memory per node. A multi-instance deployment needs a
  shared store (Redis) or sticky routing.
- SSE is notification-only; public patient pages poll every 5 seconds, and the
  display polls every 8 seconds as a fallback.
- Audit events are kept indefinitely; a retention/export policy is still to be
  defined.
- No password reset or MFA flows yet.
- The frontend uses hand-written CSS (no component library) to keep the bundle
  small and the accessibility surface explicit.

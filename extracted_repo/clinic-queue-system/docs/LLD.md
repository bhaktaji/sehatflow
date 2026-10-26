# Clinic Queue --- Low-Level Design (LLD)

**Companion:** `HLD.md`\
**Implementation target:** Java 21 + Spring Boot, PostgreSQL, Flyway,
React + TypeScript + Vite.

## 1. Repository layout

``` text
clinic-queue/
  backend/
    src/main/java/.../clinicqueue/
      identity/ clinic/ queue/ publicview/ realtime/ audit/ reporting/ common/
    src/main/resources/db/migration/
    src/test/java/...
  frontend/
    src/
      app/ features/auth/ features/reception/ features/doctor/
      features/patient/ features/display/ components/ api/ types/
  e2e/
  docs/
    HLD.md LLD.md REGRESSION.md IMPLEMENTATION_PROMPT.md
  docker-compose.yml
  README.md
```

Use package-by-feature, constructor injection, DTOs at API boundaries,
service-layer transactions and repositories for persistence. Controllers
must not contain business rules.

## 2. Domain model

### `Clinic`

-   `id UUID PK`
-   `name VARCHAR(120) NOT NULL`
-   `slug VARCHAR(80) UNIQUE NOT NULL`
-   `timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata'`
-   `active BOOLEAN NOT NULL`
-   `created_at`, `updated_at TIMESTAMPTZ NOT NULL`

### `StaffUser`

-   `id UUID PK`, `clinic_id UUID FK NOT NULL`
-   `display_name VARCHAR(120) NOT NULL`
-   `username VARCHAR(190) NOT NULL`
-   `password_hash VARCHAR(255) NOT NULL`
-   `role VARCHAR(24) NOT NULL CHECK role IN ('OWNER','RECEPTIONIST','DOCTOR')`
-   `doctor_id UUID NULL` for doctor-linked accounts
-   `active BOOLEAN NOT NULL`, timestamps
-   Unique `(clinic_id, username)`; index `(clinic_id, active)`

### `Doctor`

-   `id UUID PK`, `clinic_id UUID FK NOT NULL`
-   `display_name VARCHAR(120) NOT NULL`
-   `specialty VARCHAR(120) NULL`
-   `active BOOLEAN NOT NULL`
-   timestamps
-   Unique `(clinic_id, id)` to support composite tenant-safe foreign
    keys.

### `QueueSession`

-   `id UUID PK`, `clinic_id UUID FK NOT NULL`,
    `doctor_id UUID FK NOT NULL`
-   `business_date DATE NOT NULL`
-   `status VARCHAR(16) NOT NULL CHECK IN ('OPEN','PAUSED','CLOSED')`
-   `token_prefix VARCHAR(8) NOT NULL DEFAULT 'A'`
-   `next_token_number INTEGER NOT NULL DEFAULT 1 CHECK > 0`
-   `fallback_consult_minutes INTEGER NOT NULL DEFAULT 10 CHECK > 0`
-   `pause_message VARCHAR(160) NULL`
-   `opened_at TIMESTAMPTZ NOT NULL`, `closed_at TIMESTAMPTZ NULL`
-   Unique `(clinic_id, doctor_id, business_date)` unless product later
    supports multiple sessions/day; if multiple sessions/day are
    required, add `session_sequence` and include it in the unique key.
-   Index `(clinic_id, status, business_date)`

### `QueueToken`

-   `id UUID PK`, `clinic_id UUID NOT NULL`,
    `queue_session_id UUID NOT NULL`
-   `token_number INTEGER NOT NULL CHECK > 0`
-   `public_secret_hash VARCHAR(128) UNIQUE NOT NULL`
-   `display_name VARCHAR(120) NULL` (private; never in public DTO)
-   `status VARCHAR(24) NOT NULL CHECK IN ('WAITING','CALLED','IN_CONSULTATION','COMPLETED','SKIPPED','NO_SHOW','CANCELLED')`
-   `priority BOOLEAN NOT NULL DEFAULT FALSE`
-   `queue_position INTEGER NOT NULL CHECK > 0` (lower number means
    earlier; renumber transactionally)
-   `priority_reason VARCHAR(240) NULL` (restricted staff-only; avoid
    clinical detail)
-   `checked_in_at TIMESTAMPTZ NOT NULL`
-   `called_at`, `consultation_started_at`, `completed_at` TIMESTAMPTZ
    NULL
-   `version BIGINT NOT NULL DEFAULT 0` for optimistic concurrency
-   Unique `(queue_session_id, token_number)`
-   Index `(clinic_id, queue_session_id, status, queue_position)`
-   Index `(queue_session_id, status, checked_in_at)`
-   Ensure composite foreign key `(clinic_id, queue_session_id)`
    references `QueueSession(clinic_id,id)`.

### `QueueAuditEvent`

-   `id UUID PK`, `clinic_id UUID NOT NULL`,
    `queue_session_id UUID NOT NULL`
-   `token_id UUID NULL`, `actor_user_id UUID NULL`
-   `action VARCHAR(40) NOT NULL`, `reason VARCHAR(240) NULL`
-   `before_state JSONB NULL`, `after_state JSONB NULL`
-   `created_at TIMESTAMPTZ NOT NULL`
-   Index `(clinic_id, queue_session_id, created_at DESC)` and
    `(token_id, created_at DESC)`.
-   Append-only at application level; no update/delete endpoints.

Use Flyway migrations. Add tenant-safe composite foreign keys where
practical. All timestamps are stored in UTC; business dates are computed
using clinic timezone.

## 3. Queue state machine

``` mermaid
stateDiagram-v2
  [*] --> WAITING
  WAITING --> CALLED: call / recall after prior call
  CALLED --> IN_CONSULTATION: start consultation
  CALLED --> WAITING: return to queue
  CALLED --> SKIPPED: skip
  WAITING --> SKIPPED: skip
  SKIPPED --> WAITING: rejoin with new position
  WAITING --> NO_SHOW: mark no-show
  CALLED --> NO_SHOW: mark no-show
  WAITING --> CANCELLED: cancel
  CALLED --> CANCELLED: cancel with reason
  IN_CONSULTATION --> COMPLETED: finish
  IN_CONSULTATION --> WAITING: reopen with reason
  COMPLETED --> [*]
  NO_SHOW --> [*]
  CANCELLED --> [*]
```

Rules: - Only `WAITING` tokens count as waiting ahead. - `CALLED` and
`IN_CONSULTATION` are active/current, not eligible for call-next. -
`SKIPPED` tokens are excluded until explicitly rejoined. - Terminal
states: `COMPLETED`, `NO_SHOW`, `CANCELLED`. Reopening a terminal token
requires an explicit audited administrative action, or is disallowed in
MVP. - Every state transition validates the current state and emits an
audit event in the same transaction. - Do not infer emergency priority.
A priority change only affects queue order and must be auditable.

## 4. Queue ordering and concurrency

Use one transaction per mutation. Lock `QueueSession` using
`SELECT ... FOR UPDATE` before allocating a token number, reordering, or
selecting the next token. This serializes conflicting operations for one
session.

### Create token

1.  Authorize staff and resolve session by
    `id + authenticated clinic_id`.
2.  Lock session; require `OPEN`.
3.  Read and increment `next_token_number`.
4.  Generate 32+ bytes cryptographically random public secret; return it
    once in the patient URL and store only a secure hash.
5.  Assign `queue_position = max(active queue_position) + 1`.
6.  Insert token and audit event; commit.
7.  Publish event after commit.

### Call next

1.  Lock session; require `OPEN`.
2.  Select earliest eligible `WAITING` token by
    `priority DESC, queue_position ASC, checked_in_at ASC`, with row
    lock.
3.  If none exists, return `409 QUEUE_EMPTY` or a well-defined `204`;
    choose one convention consistently (recommended: `409` with error
    code).
4.  Set status `CALLED`, `called_at=now`, increment version, write audit
    event.
5.  Commit then publish a session-changed event.
6.  A concurrent second call must select the next token, not call the
    same token twice.

### Reorder / priority

-   Validate token belongs to session and clinic and is `WAITING`.
-   Require nonblank reason; move to target position and renumber active
    waiting tokens inside the session lock.
-   Priority is a scheduling flag, not a clinical judgment. Public view
    never discloses it.
-   Audit before/after ordering and actor.

### Complete

-   Only `IN_CONSULTATION` can transition to `COMPLETED` in the normal
    flow.
-   Record `completed_at`; derive consultation duration from
    `consultation_started_at` when present.
-   Audit and publish after commit.

## 5. REST API

Base path `/api/v1`. JSON requests/responses. Use UUID IDs. All staff
endpoints require authenticated session and clinic scope.

### Auth

-   `POST /auth/login` --- `{ "username": "...", "password": "..." }`
-   `POST /auth/logout`
-   `GET /auth/me`

Use secure cookie-based sessions unless the repository's established
architecture dictates otherwise. CSRF protection is required for
cookie-authenticated state-changing requests.

### Setup

-   `GET /clinic`
-   `GET /doctors`
-   `POST /doctors` (OWNER)
-   `PATCH /doctors/{doctorId}` (OWNER)
-   `GET /queue-sessions?businessDate=YYYY-MM-DD`
-   `POST /queue-sessions` --- create/open a session; validate doctor
    belongs to clinic.
-   `POST /queue-sessions/{sessionId}/pause` ---
    `{ "message": "Doctor temporarily delayed" }`
-   `POST /queue-sessions/{sessionId}/resume`
-   `POST /queue-sessions/{sessionId}/close`

### Queue

-   `GET /queue-sessions/{sessionId}/queue`
-   `POST /queue-sessions/{sessionId}/tokens`
    -   Request: `{ "displayName": "Optional", "priority": false }`
    -   Priority should generally be set through a separate audited
        action; do not accept an unreasoned `priority=true` here.
    -   Response:
        `{ "tokenId": "...", "tokenNumber": 12, "label": "A12", "status": "WAITING", "patientUrl": "https://.../p/<secret>" }`
-   `POST /queue-sessions/{sessionId}/call-next`
-   `POST /tokens/{tokenId}/start`
-   `POST /tokens/{tokenId}/complete`
-   `POST /tokens/{tokenId}/skip` ---
    `{ "reason": "Patient not present" }`
-   `POST /tokens/{tokenId}/no-show` ---
    `{ "reason": "Patient not present" }`
-   `POST /tokens/{tokenId}/return-to-waiting` ---
    `{ "reason": "Doctor unavailable" }`
-   `POST /tokens/{tokenId}/cancel` ---
    `{ "reason": "Requested by patient" }`
-   `POST /tokens/{tokenId}/priority` ---
    `{ "enabled": true, "reason": "Approved by clinician" }`
-   `POST /tokens/{tokenId}/reorder` ---
    `{ "targetPosition": 2, "reason": "Reception correction" }`
-   `GET /queue-sessions/{sessionId}/audit` (OWNER; optionally limited
    RECEPTIONIST permission)

### Public

-   `GET /public/tokens/{secret}` --- only own token status + safe
    aggregate position.
-   `GET /public/queues/{publicDisplaySecret}` --- display-safe
    now-serving and next token labels.
-   `GET /public/queues/{publicDisplaySecret}/events` --- SSE stream if
    enabled.
-   Never use internal UUIDs as authorization for public access. Public
    secrets are bearer credentials; redact them from logs and analytics.

### Live updates

-   `GET /queue-sessions/{sessionId}/events` --- SSE,
    staff-authenticated and clinic-scoped.
-   Event payload should contain `eventId`, `sessionId`, `revision`, and
    `type`; client fetches a fresh snapshot on reconnect or revision
    gap. Do not rely on event payload as the only source of truth.
-   Publish only after transaction commit. If the current deployment is
    a single backend instance, in-process fan-out is acceptable.
    Multi-instance deployments require shared event distribution.

## 6. Response and error conventions

Success responses use stable DTOs, not JPA entities. Errors use Spring
`ProblemDetail` with: - `type`, `title`, `status`, `detail`,
`instance` - extension `code` such as `VALIDATION_ERROR`,
`QUEUE_PAUSED`, `QUEUE_EMPTY`, `TOKEN_STATE_CONFLICT`,
`FORBIDDEN_TENANT_SCOPE`, `PUBLIC_LINK_EXPIRED`. - Include `requestId`;
do not expose SQL, stack traces or secrets.

HTTP conventions: - `400` invalid input; `401` unauthenticated; `403`
insufficient role/tenant scope; `404` unknown or deliberately concealed
resource; `409` state/concurrency conflict; `429` rate limited; `500`
unexpected error. - Never return `200` with an error object. - Validate
max lengths, enum values, session state and reason fields.

## 7. ETA and position DTO

`GET /public/tokens/{secret}` returns:

``` json
{
  "tokenLabel": "A12",
  "status": "WAITING",
  "peopleAhead": 4,
  "estimatedWaitMinutes": 35,
  "estimateQuality": "LOW",
  "message": "Wait time is approximate and may change."
}
```

`estimatedWaitMinutes` may be `null`. Use rolling median of completed
consult durations when sample size is sufficient; otherwise use session
fallback. Calculate based on eligible tokens ahead and active
consultation state. Never count completed, cancelled, no-show or skipped
tokens. If paused/closed, return a safe generic message and no confident
ETA. Do not promise exact service times.

## 8. Security implementation checklist

-   Spring Security deny-by-default; explicit route authorization.
-   Clinic ID comes from authenticated principal, never a request body.
-   Verify tenant ownership in service/repository operations, not just
    frontend.
-   Secure, HttpOnly, SameSite cookies; CSRF tokens; session fixation
    protection; idle timeout.
-   Password hashing via Argon2id or BCrypt with a suitable work factor.
-   Rate limit login and public endpoints; cap request size.
-   Public secrets generated by `SecureRandom`, at least 256 bits; store
    a cryptographic hash; no sequential public URLs.
-   `Cache-Control: no-store`, `Referrer-Policy: no-referrer`,
    restrictive CSP; avoid third-party scripts on patient pages.
-   No PII in logs, SSE payloads or public DTOs.
-   Audit mutations and protect audit records from update/delete.
-   Test cross-tenant access and public-data leakage explicitly.

## 9. Frontend behavior

### Reception dashboard

-   One primary action: **Add patient**.
-   Show active queue, now serving, wait count, paused/offline state,
    token status and clear action buttons.
-   Confirm destructive actions; require reason for reorder, priority,
    cancel and state corrections.
-   Keyboard-friendly controls, large touch targets, visible
    success/error feedback.
-   Do not use patient names on public display; receptionist view may
    show optional display name.

### Patient page

-   Mobile-first, plain language, minimal payload; token label, status,
    people ahead, approximate wait and last updated.
-   No login/signup, no patient name, no other patient details.
-   Explain that clinical emergencies may change order and the estimate
    is not a guarantee.
-   Do not ask for medical details.

### Display page

-   Full-screen, high contrast, token labels only; now serving and next
    few tokens.
-   No patient names, phone numbers or priority flags.
-   Public display credential can be rotated by owner.

### Offline

-   Global banner: "Offline --- changes are not saved. Use paper tokens
    until connection returns."
-   Disable mutations while disconnected; retain last snapshot only with
    a stale timestamp.
-   Reconnect → fetch authoritative snapshot → resume controls.
-   Do not queue offline mutations in browser storage.

## 10. Testability and implementation notes

-   Inject `Clock` for deterministic timestamps.
-   Unit-test state transitions and queue ordering as pure
    domain/service logic.
-   Use Testcontainers PostgreSQL for locking, migrations, constraints
    and transaction tests.
-   Test SSE as a notification optimization; REST snapshot must recover
    from missed events.
-   Use stable test IDs for Playwright.
-   Keep OpenAPI and frontend TypeScript API types synchronized.
-   Add idempotency keys only if retry behavior requires them; do not
    create duplicate tokens on double-click. Disable submit while
    pending and support a request idempotency key for token creation if
    feasible.

# Clinic Queue --- High-Level Design (HLD)

**Status:** Implementation baseline\
**Product:** Receptionist-first queue management for solo and small
clinics in Tier-2/Tier-3 India\
**Primary goal:** Make walk-in queues visible and manageable without
requiring patients to install an app or create an account.

## 1. Product principles

1.  **Receptionist-first:** fit the existing front-desk workflow;
    routine check-in should take seconds.
2.  **No patient account:** patient access is through an unguessable,
    revocable token link or QR code.
3.  **Queue order is not clinical triage:** arrival order is the
    default, but authorized staff can reprioritize with a reason. The
    product must never make clinical triage decisions.
4.  **Honest ETAs:** estimates are ranges or approximate values, never
    promises. Pause or suppress estimates when the doctor is unavailable
    or data is insufficient.
5.  **Graceful degradation:** when connectivity fails, clearly show
    stale data and provide a paper-token fallback. Do not silently
    pretend offline changes were saved.
6.  **Minimum data:** do not build an electronic health record. Store
    only what is needed to operate a queue.
7.  **One deployable application first:** modular monolith, not
    microservices.

## 2. Users and roles

-   **Clinic owner/admin:** configures clinic, doctors, staff accounts,
    operating sessions and display settings.
-   **Receptionist:** checks in patients, assigns doctors,
    calls/recalls/skips patients, adjusts order, pauses queues and
    closes sessions.
-   **Doctor/assistant:** views queue, calls next, starts/finishes
    consultations, pauses service.
-   **Patient/attendant:** views only their token status and limited
    public queue information, without login.
-   **Waiting-room display:** read-only "Now serving" screen showing
    token numbers only.

Role permissions are enforced on the server. Every staff account belongs
to a clinic. A user may only access records belonging to their clinic.

## 3. MVP scope

### Included

-   Clinic setup and one or more doctors.
-   Staff login and role-based access.
-   Daily queue/session creation with configurable token prefix and
    reset policy.
-   Walk-in check-in with optional display name; patient name and phone
    are optional and must not appear on public screens.
-   Token creation, queue list, now serving, call next, recall, skip,
    mark no-show, return to waiting, complete, cancel.
-   Manual priority/reorder with mandatory reason and audit event.
-   Queue pause/resume and doctor delay status.
-   Patient token page with status, people ahead and cautiously
    estimated wait.
-   Public display mode with token numbers only.
-   Server-sent events (SSE) or short polling for live refresh; REST
    remains source of truth.
-   Audit history, basic daily counts and operational health checks.
-   Responsive UI for low-cost desktop, tablet and mobile browsers.
-   Docker-based local development, migrations, seed data and CI test
    gates.

### Explicit non-goals for MVP

-   Appointments, payments, billing, prescriptions, clinical records,
    insurance, pharmacy, lab integrations.
-   Automated medical triage or clinical urgency scoring.
-   Patient accounts, social login or a mobile app.
-   WhatsApp/SMS notifications, unless separately approved as a later
    phase with consent, provider setup and delivery-failure handling.
-   Multi-branch enterprise workflows, advanced analytics, AI
    predictions or guaranteed offline multi-device synchronization.

## 4. Key workflows

### Check in

1.  Receptionist selects the doctor/session and enters optional display
    name.
2.  Backend creates a token transactionally and assigns a unique number
    within that queue session.
3.  UI shows the token number and a copyable patient link/QR.
4.  Patient page can be opened without login; it exposes no patient name
    or phone.

### Call next / consultation

1.  Authorized staff requests "Call next".
2.  Backend locks the queue/session, selects the next eligible waiting
    token according to explicit queue ordering, changes its state and
    records the actor.
3.  All views refresh from server state.
4.  Staff marks consultation complete or returns the token to waiting
    with a reason.

### Reorder / priority

-   Receptionist may move a token or mark it priority.
-   A reason is required; actor, timestamp, previous order and new order
    are audited.
-   Public patient views show status and position only, not "priority",
    medical reason, or another patient's details.
-   Emergency assessment and treatment remain a human clinical
    responsibility outside the software.

### Delay / pause

-   Staff pauses the queue and may enter a patient-safe message such as
    "The doctor is temporarily delayed".
-   Do not automatically send delay notifications in MVP.
-   ETA is marked unavailable or "may change" while paused. Do not
    reveal sensitive reasons.

### Close session

-   Staff closes the session after reconciling waiting tokens.
-   Open tokens must be explicitly completed, cancelled, marked no-show
    or carried forward under a documented action. Never silently delete
    them.

## 5. ETA model

Initial MVP estimate: - Derive a rolling median consultation duration
from recent completed consultations for that doctor/session, with a
configurable fallback when history is insufficient. - Estimate remaining
time from the number of eligible tokens ahead multiplied by the duration
estimate. - Apply pause/delay state and queue order. - Display rounded
approximate time or a broad range, plus "estimate only; emergencies and
consultation length may change this." - If no reliable baseline exists,
the queue is paused, or the session is closed, show "Wait time
unavailable" rather than inventing precision. - Do not expose internal
clinical priority reasons.

## 6. System architecture

**Recommended stack** - Frontend: React + TypeScript + Vite; responsive
accessible UI. - Backend: Java 21, Spring Boot 3.x, Spring Security,
Spring Web, Bean Validation, Spring Data JPA. - Database: PostgreSQL. -
Migrations: Flyway. - Live updates: SSE initially; REST polling
fallback. No WebSocket broker required for MVP. - Tests: JUnit 5,
Mockito, Spring Boot Test, Testcontainers PostgreSQL, frontend unit
tests, Playwright end-to-end. - Packaging: Docker Compose for local
development; production deployment to a managed container/runtime and
managed PostgreSQL. - API contract: REST JSON under `/api/v1`; OpenAPI
generated from code.

Use the exact versions compatible with the repository and pin them. If
the repository already has a sound stack, inspect it and preserve it
rather than replacing it blindly.

### C4 context

``` mermaid
flowchart LR
  Receptionist[Receptionist / Owner] --> App[Clinic Queue Web App]
  Doctor[Doctor / Assistant] --> App
  Patient[Patient phone browser] --> PublicView[Token link / public queue view]
  Display[Waiting-room display] --> PublicView
  App --> API[Spring Boot API]
  PublicView --> API
  API --> DB[(PostgreSQL)]
  API -. SSE updates .-> Receptionist
  API -. SSE updates .-> Doctor
  API -. SSE updates .-> PublicView
  API -. SSE updates .-> Display
```

### Container view

``` mermaid
flowchart TB
  Browser[React SPA: staff, patient, display] -->|HTTPS REST| Backend[Spring Boot modular monolith]
  Browser <-->|SSE / polling| Backend
  Backend -->|JPA / transactions| DB[(PostgreSQL)]
  Backend --> Logs[Structured logs / metrics]
```

## 7. Backend modules

-   `identity`: login, staff users, roles and clinic scoping.
-   `clinic`: clinic profile, doctors and configuration.
-   `queue`: sessions, token lifecycle, ordering, concurrency and ETA.
-   `publicview`: opaque token access and public-safe DTOs.
-   `realtime`: clinic/session-scoped event publication; events contain
    IDs/version, not unnecessary patient data.
-   `audit`: append-only operational audit events.
-   `reporting`: basic session counts and duration aggregates.
-   `common`: error model, time provider, IDs, validation and security
    utilities.

Keep modules in one deployable service. Avoid cross-module repository
access where a service API is appropriate.

## 8. Data and tenancy

Use a shared PostgreSQL database with `clinic_id` on all clinic-owned
entities. Every query and mutation must scope by authenticated clinic.
Do not trust `clinic_id` supplied by the browser. Public token access
uses a cryptographically random opaque secret; store only a hash of the
secret where practical. Rotate/revoke public links.

Primary entities: `Clinic`, `StaffUser`, `Doctor`, `QueueSession`,
`QueueToken`, `QueueAuditEvent`.

Use UUID primary keys. Token number uniqueness is
`(queue_session_id, token_number)`. Queue ordering is represented
explicitly and changed transactionally. Keep audit events separate from
mutable token state.

## 9. Security, privacy and compliance

-   HTTPS only in production; secure, HttpOnly, SameSite cookies
    preferred for browser sessions. Avoid storing long-lived access
    tokens in localStorage.
-   Passwords hashed using a modern adaptive password encoder;
    rate-limit login and public-token requests.
-   Server-side role checks and clinic tenant scoping on every protected
    operation.
-   Public pages return only token number, own token status,
    people-ahead count, estimated wait and generic clinic/doctor labels.
    Never return names, phone numbers, internal notes or audit details.
-   Unguessable links, expiry/revocation, `Cache-Control: no-store`,
    restrictive referrer policy and no third-party analytics on public
    token pages.
-   Validate and encode all input/output; use parameterized database
    access.
-   Audit all priority changes, status changes, queue pauses and
    administrative changes.
-   Backups encrypted at rest; secrets in a secret manager; redact
    patient data from logs.
-   Define retention/deletion policy with the clinic. Do not collect
    medical information.
-   Review applicability of India's Digital Personal Data Protection
    Act/rules and any health-sector requirements with qualified counsel
    before production. This document is an engineering baseline, not
    legal advice.
-   Never claim the product performs triage or guarantees waiting time.

## 10. Reliability and offline behavior

The server database is the source of truth. MVP does **not** support
offline writes or multi-device offline synchronization. - Show a
persistent connection indicator and timestamp of last successful sync. -
When offline, disable mutation controls and show "Offline --- changes
are not saved"; offer a printable/paper-token fallback. - On reconnect,
fetch a fresh authoritative snapshot before re-enabling controls. - SSE
disconnects fall back to periodic REST refresh; duplicate/out-of-order
events trigger a snapshot refresh. - Use database transactions and row
locking for token numbering and call-next operations. - Backups and
restore procedure must be documented and tested before production.

## 11. Observability and operations

-   JSON structured logs with correlation/request ID; never log
    passwords, public secrets or patient phone/name.
-   Health endpoints: liveness and readiness; readiness checks database
    connectivity.
-   Metrics: request latency/error rate, DB pool saturation, active SSE
    connections, queue mutations, event delivery failures.
-   Alerts for repeated 5xx errors, DB unavailability and failed
    backups.
-   Daily encrypted backups, documented retention, and periodic restore
    drill.
-   Keep operational audit data separate from diagnostic logs.

## 12. Deployment topology

-   One frontend static bundle served via CDN/reverse proxy.
-   One or more stateless Spring Boot instances.
-   Managed PostgreSQL with backups and TLS.
-   SSE works behind a reverse proxy configured for streaming/timeouts;
    if multiple backend instances are used, publish events through
    PostgreSQL notification or a lightweight shared event mechanism. For
    a single instance, in-process event fan-out is acceptable initially.
-   CI: format/lint, backend unit tests, PostgreSQL integration tests,
    frontend tests, production build, Playwright smoke/regression tests,
    dependency/security scan.
-   Environments: local, staging, production. Never use production
    patient data in test environments.

## 13. Success measures for pilot

Measure with clinic consent: - Median time for receptionist to register
a walk-in. - Percentage of visits correctly represented in the queue. -
Number of "how long?" interruptions reported by staff. - Patient
walkouts/complaints before and during pilot (directional, not causal
proof). - Daily active use and weekly retention by clinic. -
Staff-reported usability and willingness to pay. - Reliability: no
lost/duplicate tokens; recovery after connectivity interruption.

Pilot first with 2--3 clinics through direct observation. Do not build
advanced features until receptionists use the MVP on busy days and at
least one clinic agrees to pay.

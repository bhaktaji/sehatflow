# Clinic Queue --- Regression Test Plan

**Release gate:** The coding agent must execute all required checks, fix
failures, rerun them, and report exact commands/results before
presenting the implementation. Do not claim a test passed unless it was
executed successfully.

## 1. Required automated commands

Document the actual commands in `README.md` and CI. Expected baseline
(adapt to the generated project):

``` bash
docker compose up -d postgres
cd backend && ./mvnw clean verify
cd ../frontend && npm ci && npm run lint && npm run test -- --run && npm run build
cd .. && npx playwright test
```

If a command differs, update this file and CI together. Integration
tests must use PostgreSQL (prefer Testcontainers); H2 is not an
acceptable substitute for locking/constraint tests. E2E tests must run
against a clean test database and deterministic seed data.

## 2. P0 release-blocking tests

  -----------------------------------------------------------------------------
  ID                      Scenario                Expected result
  ----------------------- ----------------------- -----------------------------
  R-001                   Owner logs in with      Session established; role and
                          valid credentials       clinic returned safely

  R-002                   Invalid password        Generic authentication error;
                                                  no session; no credential
                                                  disclosure

  R-003                   Receptionist creates    Unique increasing token
                          token                   number within the session;
                                                  token appears in queue

  R-004                   Double-click/retry      No accidental duplicate
                          create token            caused by a single user
                                                  action; pending UI prevents
                                                  repeat; idempotency behavior
                                                  tested if implemented

  R-005                   Call next with waiting  Earliest eligible token
                          tokens                  transitions to `CALLED`;
                                                  audit event written

  R-006                   Call next when queue    Stable `QUEUE_EMPTY`
                          empty                   response; no state mutation

  R-007                   Two simultaneous        No token is called twice; two
                          call-next requests      different eligible tokens are
                                                  selected or second receives
                                                  valid empty/conflict response

  R-008                   Two simultaneous token  Unique token numbers; no
                          creations               gaps/duplicates caused by
                                                  race beyond documented
                                                  allocation behavior

  R-009                   Start and complete      Valid state transitions and
                          consultation            timestamps; duration recorded

  R-010                   Complete a waiting      Rejected with
                          token directly          `TOKEN_STATE_CONFLICT`; state
                                                  unchanged

  R-011                   Pause queue then        Mutation rejected while
                          create/call             paused; resume restores
                                                  normal behavior

  R-012                   Priority/reorder        Validation error; no ordering
                          without reason          change

  R-013                   Priority/reorder with   Order changes
                          reason                  transactionally;
                                                  actor/reason/before/after
                                                  audited

  R-014                   Skip then rejoin        Skipped token excluded from
                                                  active queue; rejoin places
                                                  it at an explicit new
                                                  position and is audited

  R-015                   Mark no-show/cancel     Correct terminal status;
                                                  excluded from people-ahead
                                                  and ETA

  R-016                   Public token URL        Shows own token
                                                  status/position/approximate
                                                  ETA only; no name, phone,
                                                  notes, UUIDs or other
                                                  patients

  R-017                   Guess or alter public   No information disclosed;
                          secret                  invalid/revoked secret
                                                  returns safe error

  R-018                   Public display page     Shows token labels only; no
                                                  patient-identifying
                                                  information

  R-019                   Cross-tenant access     Clinic A user cannot read or
                                                  mutate Clinic B doctors,
                                                  sessions, tokens, audit or
                                                  reports

  R-020                   Patient/public role     Denied server-side even if
                          calls staff endpoint    request is handcrafted

  R-021                   Direct object reference Denied or concealed as 404;
                          to another clinic token no mutation

  R-022                   Session restart         Persisted queue state
                                                  survives backend restart

  R-023                   Database migration from Flyway builds complete schema
                          empty schema            and constraints

  R-024                   Public endpoint rate    Repeated abusive requests are
                          limit                   throttled without leaking
                                                  secret details

  R-025                   Audit integrity         All status, order,
                                                  pause/resume and admin
                                                  changes have
                                                  actor/time/action; no
                                                  update/delete API

  R-026                   Offline UI              Mutation controls disabled;
                                                  clear stale/offline message;
                                                  no false success

  R-027                   Reconnect after SSE     Client reloads authoritative
                          loss                    snapshot and converges to
                                                  server state

  R-028                   Invalid or oversized    Validation error; no stack
                          input                   trace or internal details

  R-029                   CSRF/session security   State-changing cookie-auth
                                                  requests without valid CSRF
                                                  protection are rejected

  R-030                   Production build        Backend packages and frontend
                                                  production build succeed from
                                                  clean checkout
  -----------------------------------------------------------------------------

## 3. Domain and queue ordering tests

-   Default ordering is arrival/order position, not lexical token label.
-   Priority tokens precede non-priority waiting tokens, and ties
    preserve explicit queue position.
-   Only `WAITING` tokens count as people ahead.
-   `CALLED`, `IN_CONSULTATION`, `COMPLETED`, `SKIPPED`, `NO_SHOW`,
    `CANCELLED` are not counted as waiting ahead.
-   Reordering updates all affected positions without duplicates.
-   A session cannot accept tokens after `CLOSED`.
-   A doctor/session must belong to the authenticated clinic.
-   A token cannot transition from a terminal state through normal
    endpoints.
-   Queue numbering resets only according to the configured session
    policy; it never collides within a session.
-   Business date uses the clinic timezone; timestamps are stored in
    UTC.
-   Audit event and state mutation commit or roll back together.
-   Event publication happens only after commit; clients recover from
    missed events via snapshot.

## 4. ETA tests

-   No history: fallback estimate or unavailable state is returned
    according to configuration.
-   Sufficient history: rolling median is used, not a single extreme
    duration.
-   Paused queue: no confident numeric ETA is displayed.
-   Closed session: patient sees a safe closed-session message.
-   No-show, cancelled, completed and skipped tokens do not inflate the
    waiting count.
-   ETA is rounded and accompanied by a disclaimer; it is never
    described as guaranteed.
-   Missing/invalid consultation timestamps do not crash estimate
    calculation.
-   Priority/reorder changes update position and ETA after a fresh
    snapshot.

## 5. Frontend and accessibility tests

-   Receptionist can add a patient, copy the patient URL, call next,
    start, complete, skip, no-show, pause and resume using keyboard and
    pointer.
-   Required-reason fields prevent priority/reorder/cancel actions
    without a reason.
-   Loading, empty, error, paused and offline states are distinct and
    understandable.
-   Buttons cannot submit duplicate mutations while a request is
    pending.
-   Mobile viewport does not hide primary actions or require horizontal
    scrolling for the main workflow.
-   Labels, focus order, visible focus, contrast and form errors are
    accessible.
-   Public page is usable on a narrow mobile viewport and never displays
    private fields.
-   Display mode is legible at common TV/tablet sizes.
-   SSE disconnect/reconnect does not duplicate tokens or produce stale
    ordering.
-   Browser refresh preserves correct state by fetching the backend
    snapshot.

## 6. End-to-end scenarios (Playwright)

### E2E-01: Happy path

1.  Seed clinic, doctor and receptionist.
2.  Login as receptionist.
3.  Add three walk-ins.
4.  Verify token labels and queue order.
5.  Open the first patient's public URL in a separate browser context.
6.  Call next, start and complete consultation.
7.  Verify staff dashboard, public view and display converge to the same
    server state.

### E2E-02: Priority override

1.  Add three tokens.
2.  Move the third token to the front with a reason.
3.  Verify order and audit record.
4.  Verify public page does not disclose priority reason or flag.

### E2E-03: Paused doctor

1.  Add tokens and pause session with generic message.
2.  Verify patient page displays delay/unavailable ETA messaging.
3.  Verify call-next and new token operations follow the documented
    paused-session policy.
4.  Resume and verify queue continues without losing order.

### E2E-04: Tenant isolation

1.  Create Clinic A and Clinic B with separate staff and tokens.
2.  Attempt cross-clinic GET and POST using Clinic A credentials.
3.  Verify no data leakage and no state mutation in Clinic B.

### E2E-05: Reconnect

1.  Load staff dashboard and public page.
2.  Interrupt event stream/network.
3.  Change queue through another authorized session.
4.  Restore network.
5.  Verify snapshot refresh converges to authoritative queue state and
    stale state is clearly indicated until then.

## 7. Security and privacy checks

-   Search frontend bundles, API responses and logs for seeded fake
    patient phone/name and public secrets; ensure none appear in public
    output or logs.
-   Confirm public URLs use high-entropy secrets, not sequential token
    IDs.
-   Confirm public pages set `Cache-Control: no-store` and
    `Referrer-Policy: no-referrer`.
-   Confirm staff routes deny unauthenticated access.
-   Confirm tenant filtering exists in all reads and writes, including
    audit and reporting.
-   Confirm secret/password values are not returned by DTOs or included
    in exception messages.
-   Confirm logs redact authentication cookies, passwords and public
    bearer secrets.

## 8. Manual acceptance checklist for the user

The agent should not mark these as automated passes unless automated: -
Receptionist can learn the core workflow in a short demo without
developer help. - Token creation is quick on the clinic's actual
hardware. - Staff can explain the paper fallback when internet is
unavailable. - Public display is readable from the waiting area. -
Clinic owner approves public wording, retention policy and staff
roles. - Doctor/receptionist confirms the priority workflow matches
their real process. - A pilot clinic agrees to use the system on busy
days.

## 9. Required test report

At handoff, include: - Commit SHA or working-tree state. - Environment
and Java/Node/PostgreSQL versions. - Exact commands executed. -
Pass/fail counts for unit, integration, frontend, E2E and build
checks. - Every failure encountered and resolution. - Any skipped tests
with a reason; skipped P0 tests mean the build is **not
release-ready**. - Known limitations and manual checks still pending.

Never fabricate output. If the environment prevents execution, state
"NOT RUN" and explain why.

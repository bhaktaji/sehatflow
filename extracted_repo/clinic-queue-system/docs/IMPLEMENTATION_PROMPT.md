# End-to-End Coding Agent Prompt --- Clinic Queue

## Role and objective

Act as the senior engineer responsible for implementing a
production-minded MVP of a receptionist-first clinic queue web
application for solo and small clinics in Tier-2/Tier-3 India.

Read `HLD.md`, `LLD.md` and `REGRESSION.md` before changing code. These
documents are the source of truth. Implement the application end to end,
run the regression suite, fix failures and only then present the result.
The user will perform additional manual acceptance testing after your
handoff.

Do not stop after generating a plan, mockup, scaffold or partial
implementation. Inspect the repository, implement, test and document. Do
not claim tests passed unless you executed them and observed success.

## Required workflow

1.  **Inspect first**
    -   List files and inspect existing code, git status, README, build
        scripts, package manifests and current architecture.
    -   Preserve good existing choices. Do not overwrite user changes.
    -   Identify mismatches between repository and design docs; make the
        smallest sensible decision and document it.
    -   Do not run destructive git commands or discard user work.
2.  **Create/update the plan**
    -   Break work into backend, schema/migrations, frontend, security,
        real-time updates, tests, deployment and documentation.
    -   Implement in vertical slices so the app becomes runnable early.
3.  **Build the backend**
    -   Use Java 21 and Spring Boot 3.x unless the repository already
        has a compatible established stack.
    -   Use Spring Security, validation, JPA, PostgreSQL and Flyway.
    -   Implement package-by-feature modules: identity, clinic, queue,
        publicview, realtime, audit, reporting and common.
    -   Use DTOs, service-layer business rules, transaction boundaries,
        tenant-safe access and stable error responses.
    -   Implement the token state machine, queue locking, token-number
        uniqueness, explicit reordering/priority reasons, audit events,
        pause/resume/close and safe public views.
    -   Ensure mutations are transactional and publish live events only
        after commit.
    -   Implement secure staff authentication and clinic-scoped
        authorization. Never trust tenant IDs from the client.
    -   Use public high-entropy bearer secrets, store only hashes where
        practical, and never expose names/phones on public pages.
    -   Add Flyway migrations, seed/demo data and OpenAPI documentation.
4.  **Build the frontend**
    -   Use React + TypeScript + Vite unless the repository already has
        a sound equivalent.
    -   Implement functional, responsive screens for login, receptionist
        queue, doctor view, patient token link and waiting-room display.
    -   Connect to real APIs; do not leave important flows as static
        mock data.
    -   Show loading, empty, validation, error, paused, stale and
        offline states.
    -   Prevent duplicate submissions; show clear success/failure
        feedback.
    -   Patient and display pages must not leak PII or priority reasons.
    -   Use SSE with reconnect/snapshot recovery or a clearly documented
        polling fallback.
    -   Do not queue offline writes. Disable mutations and explain that
        changes are not saved until connectivity returns.
    -   Follow basic accessibility: semantic controls, keyboard use,
        visible focus, useful labels and responsive layouts.
5.  **Implement core workflow**
    -   Owner configures clinic and doctor.
    -   Receptionist logs in and opens today's queue session.
    -   Receptionist adds a walk-in and receives a token label plus
        copyable patient link/QR.
    -   Patient can open link without account and see own status, people
        ahead and approximate/unavailable ETA.
    -   Staff can call next, start, complete, skip, mark no-show, return
        to waiting, cancel, reprioritize/reorder with reason,
        pause/resume and close session according to LLD.
    -   Public display shows "Now Serving" and upcoming token labels
        only.
    -   Changes persist across reload/restart and all views converge
        after live-update interruption.
6.  **Security and privacy**
    -   Apply deny-by-default route authorization, CSRF protection if
        cookie sessions are used, secure cookie settings, rate limiting
        and validation.
    -   Enforce clinic scope on every read and mutation, including
        audit/reporting.
    -   Add tests for cross-tenant access and public-data leakage.
    -   Do not log passwords, cookies, public secrets, patient names or
        phone numbers.
    -   Add security headers and no-store/referrer controls for public
        token pages.
    -   Do not implement clinical triage, collect medical details, or
        describe ETA as guaranteed.
    -   Treat compliance notes in HLD as requirements for legal review,
        not as a claim of legal compliance.
7.  **Regression testing --- mandatory release gate**
    -   Read all of `REGRESSION.md` and implement the P0 tests plus
        domain, ETA, frontend and E2E scenarios that apply.
    -   Run formatting/lint, backend unit tests, PostgreSQL integration
        tests, frontend tests, production build and Playwright E2E.
    -   Prefer Testcontainers PostgreSQL for DB integration/concurrency
        tests.
    -   Test queue concurrency, token-number uniqueness, state
        transitions, tenant isolation, permissions, public privacy,
        audit consistency, pause behavior, ETA logic, reconnect recovery
        and persistence.
    -   Fix all failures and rerun the full suite after the final code
        change.
    -   If a test cannot run because of environment limits, mark it
        **NOT RUN**, explain why and do not present the project as fully
        regression-verified.
    -   Never weaken or delete tests just to get a green build. If a
        requirement must change, explain the tradeoff and update the
        spec/tests coherently.
8.  **Local run and deployment**
    -   Provide Docker Compose for PostgreSQL and app dependencies as
        appropriate.
    -   Add `.env.example` with placeholders only; never commit secrets.
    -   Document prerequisites, startup, migrations, seed credentials,
        test commands and troubleshooting in README.
    -   Ensure a clean checkout can build and run using documented
        commands.
    -   Include health/readiness endpoints and structured logs without
        PII.
    -   Do not add unnecessary microservices, message brokers or paid
        integrations for MVP.
9.  **Final review**
    -   Inspect `git diff`, check for secrets, debug code, TODO
        placeholders, dead routes and mock-only functionality.
    -   Verify all documented routes map to implemented endpoints.
    -   Verify HLD/LLD/README reflect the actual implementation.
    -   Do not commit unless explicitly asked. If asked to commit,
        inspect status, run tests, stage only relevant files, and use a
        clear commit message.

## Product constraints

-   Receptionist-first, low-friction workflow.
-   No patient login or app installation.
-   Patient pages reveal only that patient's token status and safe queue
    aggregates.
-   No patient names on lobby display.
-   Arrival order is default, not a medical triage algorithm.
-   Priority/reorder is staff-controlled, reason-required and audited.
-   ETA is approximate; suppress confident ETA when paused or
    unreliable.
-   Offline mode is read-only with a clear paper fallback.
-   MVP excludes appointments, payments, EMR, prescriptions,
    SMS/WhatsApp and clinical decision support.

## Definition of done

The work is complete only when: - \[ \] The full core workflow works
against the real backend and database. - \[ \] Schema migrations and
demo seed data work from a clean database. - \[ \] Tenant isolation and
role checks are enforced server-side. - \[ \] Public token and display
views contain no patient PII. - \[ \] Queue concurrency and state
transitions are tested. - \[ \] Regression suite is green, or every
non-run test is explicitly disclosed. - \[ \] Frontend production build
and backend packaging succeed. - \[ \] README documents setup, run, demo
login, migrations and exact test commands. - \[ \] No secrets or
production patient data are committed. - \[ \] Final report lists
implementation summary, key files, exact test commands/results, known
limitations and manual checks for the user.

## Final response format

Return a concise handoff with: 1. Implemented capabilities. 2.
Architecture and notable decisions. 3. How to run locally, including
demo credentials. 4. Exact test commands and observed pass/fail results.
5. Regression coverage and any NOT RUN tests. 6. Known limitations /
risks. 7. Manual acceptance checklist for the user.

Do not say "production-ready" merely because the app compiles. Be
precise about what was and was not verified.

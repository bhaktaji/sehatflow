# SehatFlow — Low-Level Design (LLD)

**Application Package ID:** `com.aistudio.clinicqueue.rwnq`  
**Android Namespace:** `com.example`  
**Backend Group / Artifact:** `com.clinicqueue:clinic-queue-backend:0.1.0`  
**Version:** `1.0` (`versionCode = 1`)

---

## 1. Repository & Module Structure

```text
/
├── app/                                      # Android Client Application (Kotlin + Jetpack Compose)
│   ├── build.gradle.kts                      # Android build config, R8 release shrinking, signing configs
│   ├── google-services.json                  # Firebase project configuration (gen-lang-client-0880099874)
│   ├── proguard-rules.pro                    # R8/ProGuard keep rules for Retrofit, Moshi, Room, Firestore
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml           # Permissions, MainActivity, FileProvider, adaptive launcher icons
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt           # Edge-to-edge Activity host & theme state observer
│       │   │   ├── ClinicApplication.kt      # Application singleton & AppContainer DI graph
│       │   │   ├── core/
│       │   │   │   ├── common/               # Constants, RoleGuard, EtaCalculator, AppContainer
│       │   │   │   ├── payment/              # SubscriptionPaymentEngine (Direct UPI, QR, UTR, Razorpay)
│       │   │   │   └── update/               # AppUpdateManager (HTTPS manifest, SHA-256 digest, APK cert check)
│       │   │   ├── data/
│       │   │   │   ├── auth/                 # GoogleSignInHelper (Jetpack Credential Manager)
│       │   │   │   ├── firestore/            # FirestoreClinicSyncRepository (Real-time cloud sync)
│       │   │   │   ├── local/                # Room AppDatabase, Entities, DAOs, LocalClinicEngine, DataStore
│       │   │   │   ├── model/                # Domain models, Moshi DTOs, EMR/Consultation models
│       │   │   │   ├── remote/               # ClinicApiService (Retrofit), DynamicBaseUrlInterceptor, CookieJar
│       │   │   │   └── repository/           # AuthRepository, QueueRepository, ConsultationHistoryRepository
│       │   │   └── ui/
│       │   │       ├── auth/                 # LoginScreen, AuthViewModel
│       │   │       ├── common/               # SehatFlowSettingsAndHealthDialogs, Shared UI
│       │   │       ├── doctor/               # DoctorDashboardScreen, DoctorViewModel, ConsultationSheet
│       │   │       ├── medical/              # PatientMedicalHistoryDialog, ClinicalTimelineComponents
│       │   │       ├── navigation/           # AppNavHost, NavRoute
│       │   │       ├── owner/                # ClinicAdminDashboardScreen, OwnerViewModel, OnboardingWizard
│       │   │       ├── patient/              # ClinicSelectScreen, PatientLiveQueueScreen, VisitHistoryScreen
│       │   │       ├── receptionist/         # ReceptionDashboardScreen, ReceptionViewModel
│       │   │       ├── subscription/         # ClinicSubscriptionScreen (Direct UPI, GPay, QR, UTR, Plans)
│       │   │       └── theme/                # Theme.kt, Color.kt, Type.kt
│       │   └── res/                          # Vector drawables, adaptive icons, strings.xml, backup rules
│       └── test/java/com/example/            # 66 JUnit 4 + Robolectric UAT, Subscription, Update, Security tests
├── backend/                                  # Spring Boot 3.3.5 REST API (Java 21 + PostgreSQL 16 + Flyway)
│   ├── Dockerfile                            # Multi-stage Maven + Eclipse Temurin 21 JRE Alpine container
│   ├── pom.xml                               # Maven dependencies (Web, Security, Data JPA, Flyway, Actuator)
│   └── src/main/
│       ├── java/com/clinicqueue/
│       │   ├── ClinicQueueApplication.java   # Spring Boot entrypoint
│       │   ├── common/                       # ApiException, ErrorCode, GlobalExceptionHandler
│       │   ├── config/                       # SecurityConfig, RateLimitFilter, CsrfCookieFilter, SeedDataRunner
│       │   ├── controller/                   # AuthController, ReceptionQueueController, DoctorSessionController,
│       │   │                                 # PublicTokenController, OwnerController
│       │   ├── subscription/                 # SubscriptionAndUpdateController (Razorpay verify, webhooks, releases)
│       │   ├── domain/                       # JPA Entities (Clinic, StaffUser, Doctor, QueueSession, QueueToken, TokenEvent)
│       │   ├── dto/                          # Request/Response Records with Jakarta Validation
│       │   ├── repository/                   # Spring Data JPA Repositories
│       │   └── service/                      # QueueService, AuthService, EtaService, OwnerAnalyticsService
│       └── resources/
│           ├── application.yml               # Default/development configuration
│           ├── application-prod.yml          # Hardened production profile configuration
│           └── db/migration/
│               ├── V1__init_schema.sql       # Flyway relational schema & partial indexes
│               └── V2__subscriptions_and_app_updates.sql # Subscriptions, payments, webhooks & releases
├── release/
│   ├── SehatFlow-1.0-release.apk             # Signed, R8-minified public release APK
│   └── version-manifest.json                 # Cryptographic update manifest (SHA-256, cert hash, size)
├── .github/workflows/ci.yml                  # Automated GitHub Actions CI pipeline
├── docker-compose.yml                        # Production multi-container orchestration (PostgreSQL 16 + Backend)
├── firestore.rules                           # Zero-trust Cloud Firestore security rules
├── firebase-blueprint.json                   # Firestore schema blueprint
├── HLD.md                                    # High-Level Design specification
├── LLD.md                                    # Low-Level Design specification
└── README.md                                 # Project README & User Handover Guide
```

---

## 2. Android Client Low-Level Design

### 2.1 Dependency Injection (`AppContainer`)
`AppContainer` (initialized in `ClinicApplication`) provides constructor-injected singletons across the app:
* `sessionPreferences: SessionPreferences` — Jetpack DataStore (`clinic_session_prefs`) storing active session role, `clinicId`, `actorId`, theme mode (`SYSTEM`, `LIGHT`, `DARK`), DPDP consent state, and optional unverified patient self-reported health profile.
* `appDatabase: AppDatabase` — Room SQLite database (`clinic_queue_local.db`, schema version `3`) providing `ClinicQueueDao` for offline-capable persistence and pending mutation queueing.
* `apiService: ClinicApiService` — Retrofit 2 interface backed by `OkHttpClient` with:
  * `PersistentSessionCookieJar` (`JSESSIONID` + `XSRF-TOKEN` double-submit CSRF header injection).
  * `DynamicBaseUrlInterceptor` (enforcing HTTPS for non-loopback hosts; defaults to `Constants.PRODUCTION_BASE_URL = "https://api.sehatflow.app/api/v1/"` in release builds and `http://10.0.2.2:8090/api/v1/` in debug emulator builds).
* `firestoreSyncRepository: FirestoreClinicSyncRepository` — Real-time Cloud Firestore repository syncing `/users/{uid}`, `/clinics/{clinicId}`, `/clinics/{clinicId}/tokens/{tokenId}`, and `/subscriptions/{subId}`.
* `authRepository: AuthRepository` & `queueRepository: QueueRepository` — Unified repositories coordinating REST, Firestore, and local Room storage.

### 2.2 Navigation Routes & RBAC (`NavRoutes` & `RoleGuard`)

| Route Constant | Destination Screen | Permitted Roles (`RoleGuard`) | Purpose |
| :--- | :--- | :--- | :--- |
| `NavRoutes.LOGIN` (`"login"`) | `LoginScreen` | Public / Unauthenticated | Unified welcome page: Patient Google Sign-In, Token Status lookup, Clinic Admin 14-day trial onboarding, Staff Invitation Code verification, and Staff Credential login. |
| `NavRoutes.PATIENT_STATUS` (`"patient_status/{tokenCode}"`) | `PatientStatusScreen` | `PATIENT` (or Token Holder) | Live token status card, EWMA wait-time countdown, chamber indicator, prescription upload, doctor discovery, and longitudinal medical history dialog. |
| `NavRoutes.RECEPTION_DASHBOARD` (`"reception_dashboard"`) | `ReceptionDashboardScreen` | `RECEPTIONIST`, `OWNER` | Walk-in token registration, priority/emergency queue management, chamber filtering, interactive doctor assignment/reassignment dialog, and demographic/visit medical history view. |
| `NavRoutes.DOCTOR_CONSOLE` (`"doctor_console"`) | `DoctorConsoleScreen` | `DOCTOR`, `OWNER` | Active consultation workspace, Call Next / Start / Complete / Hold / No-Show controls, full SOAP & prescription composer, and longitudinal clinical timeline (`PatientMedicalHistoryDialog`). |
| `NavRoutes.OWNER_OVERVIEW` (`"owner_overview"`) | `OwnerOverviewScreen` | `OWNER` | Multi-chamber throughput metrics, average wait-time analytics, doctor schedule matrix, staff invitation code generator (`SF-DOC-*`, `SF-REC-*`), and Indian clinical record compliance tracker. |

### 2.3 Longitudinal EMR & Role-Scoped Redaction
`LocalClinicEngine` and `PatientMedicalHistoryDialog` enforce strict clinical privacy boundaries in accordance with the **Digital Personal Data Protection Act (DPDP) 2023** and Indian clinical establishment standards:
* **Immutable Patient Identifier (`patientId`):** Generated in the unambiguous format `PT-YYYYMMDD-XXXX` (excluding visually ambiguous characters `I`, `O`, `0`, `1`).
* **Doctor View (`DOCTOR`):** Full access to longitudinal SOAP clinical notes, presenting complaints, vitals (`BP`, `SpO2`, `Pulse`, `Temp`, `Weight`), diagnoses, prescriptions, lab orders, and versioned clinical addenda (`ClinicalAddendum`).
* **Receptionist & Owner View (`RECEPTIONIST`, `OWNER`):** Restricted to administrative visit metadata, token timestamps, assigned doctor/chamber, and billing/record-request tracking. Clinical notes and diagnoses are redacted at the repository layer before reaching the UI.
* **Patient View (`PATIENT`):** Read-only access strictly isolated to the authenticated patient's own consultations (`patientId` ownership check) plus structured prescription summaries.

---

## 3. Backend API Specification (`/api/v1`)

### 3.1 Authentication & Session Endpoints (`AuthController`)
| Method | Path | Auth | Request Body | Response DTO | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Public (Rate-Limited) | `LoginRequest { clinicSlug, username, password }` | `StaffProfileResponse` | Authenticates staff via BCrypt, rotates HTTP session (`JSESSIONID`), and issues `XSRF-TOKEN` cookie. |
| `POST` | `/api/v1/auth/logout` | Authenticated | None | `204 No Content` | Invalidates active HTTP session and clears session cookie. |
| `GET` | `/api/v1/auth/me` | Authenticated | None | `StaffProfileResponse` | Returns currently authenticated staff principal (`userId`, `clinicId`, `role`, `doctorId`, `displayName`). |

### 3.2 Receptionist Queue Endpoints (`ReceptionQueueController`)
| Method | Path | Auth Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/reception/queue/current` | `RECEPTIONIST`, `DOCTOR`, `OWNER` | Returns active `QueueSnapshotResponse` including waiting tokens, active consultation, and chamber summary metrics. |
| `POST` | `/api/v1/reception/queue/sessions` | `RECEPTIONIST`, `OWNER` | Opens a new daily `QueueSession` for a specific `doctorId` (`OPEN` status). |
| `POST` | `/api/v1/reception/tokens` | `RECEPTIONIST`, `OWNER` | Issues a new `QueueToken` (`NORMAL`, `PRIORITY`, or `EMERGENCY`) with idempotency via `clientRequestId`. |
| `POST` | `/api/v1/reception/tokens/{tokenId}/priority` | `RECEPTIONIST`, `OWNER` | Escalates or adjusts token priority and records audit reason in `token_events`. |
| `POST` | `/api/v1/reception/tokens/{tokenId}/hold` | `RECEPTIONIST`, `OWNER` | Places a `WAITING` token on `HOLD` (e.g., patient stepped out for diagnostics). |
| `POST` | `/api/v1/reception/tokens/{tokenId}/unhold` | `RECEPTIONIST`, `OWNER` | Restores a `HOLD` token back to `WAITING` in priority order. |
| `POST` | `/api/v1/reception/tokens/{tokenId}/cancel` | `RECEPTIONIST`, `OWNER` | Cancels a token (`CANCELLED`) and frees daily session capacity. |
| `POST` | `/api/v1/reception/tokens/{tokenId}/requeue` | `RECEPTIONIST`, `OWNER` | Re-queues a `SKIPPED` or `NO_SHOW` token back into the active waiting list. |

### 3.3 Doctor Session Endpoints (`DoctorSessionController`)
| Method | Path | Auth Role | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/doctor/sessions/{sessionId}/call-next` | `DOCTOR`, `RECEPTIONIST`, `OWNER` | Selects highest-priority `WAITING` token (`priority_weight DESC, queue_order ASC`) and transitions it to `CALLED`. |
| `POST` | `/api/v1/doctor/tokens/{tokenId}/start` | `DOCTOR`, `OWNER` | Transitions `CALLED` token to `IN_CONSULTATION` and stamps `consultation_started_at`. |
| `POST` | `/api/v1/doctor/tokens/{tokenId}/complete` | `DOCTOR`, `OWNER` | Marks consultation `COMPLETED`, stamps `consultation_ended_at`, and updates rolling EWMA consultation duration. |
| `POST` | `/api/v1/doctor/tokens/{tokenId}/skip` | `DOCTOR`, `RECEPTIONIST`, `OWNER` | Marks a `CALLED` token as `SKIPPED` if the patient does not respond at the chamber door. |
| `POST` | `/api/v1/doctor/sessions/{sessionId}/pause` | `DOCTOR`, `RECEPTIONIST`, `OWNER` | Pauses chamber session (`PAUSED`) with a visible reason (e.g., emergency round) and suppresses ETA countdowns. |
| `POST` | `/api/v1/doctor/sessions/{sessionId}/resume` | `DOCTOR`, `RECEPTIONIST`, `OWNER` | Resumes `PAUSED` session back to `OPEN`. |
| `POST` | `/api/v1/doctor/sessions/{sessionId}/close` | `DOCTOR`, `RECEPTIONIST`, `OWNER` | Closes daily session (`CLOSED`) once consultations conclude. |

### 3.4 Public Patient Token & Owner Overview Endpoints
| Method | Path | Auth Role | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/public/tokens/{publicCode}` | Public (Rate-Limited) | Returns privacy-safe `PublicTokenStatusResponse` (masked name, token number, people ahead, EWMA wait range, chamber state). |
| `GET` | `/api/v1/owner/overview` | `OWNER` | Returns clinic-wide daily operational KPIs (`completedCount`, `waitingCount`, `averageWaitMinutes`, `averageConsultationMinutes`). |

---

## 4. Database Schemas

### 4.1 PostgreSQL Relational Schema (`backend/src/main/resources/db/migration/V1__init_schema.sql`)

```mermaid
erDiagram
    CLINICS ||--o{ DOCTORS : "employs"
    CLINICS ||--o{ STAFF_USERS : "authorizes"
    DOCTORS |o--o| STAFF_USERS : "linked_to"
    CLINICS ||--o{ QUEUE_SESSIONS : "hosts"
    DOCTORS ||--o{ QUEUE_SESSIONS : "leads"
    QUEUE_SESSIONS ||--o{ QUEUE_TOKENS : "contains"
    QUEUE_TOKENS ||--o{ TOKEN_EVENTS : "audits"

    CLINICS {
        uuid id PK
        varchar slug UK
        varchar name
        varchar timezone
        int default_consult_minutes
        timestamptz created_at
    }
    DOCTORS {
        uuid id PK
        uuid clinic_id FK
        varchar display_name
        varchar specialty
        varchar room_label
        boolean active
    }
    STAFF_USERS {
        uuid id PK
        uuid clinic_id FK
        varchar username
        varchar password_hash
        varchar role "OWNER | RECEPTIONIST | DOCTOR"
        uuid doctor_id FK
        boolean active
    }
    QUEUE_SESSIONS {
        uuid id PK
        uuid clinic_id FK
        uuid doctor_id FK
        date session_date
        varchar label
        varchar status "OPEN | PAUSED | CLOSED"
        varchar pause_reason
        int next_token_number
        bigint version
    }
    QUEUE_TOKENS {
        uuid id PK
        uuid session_id FK
        uuid clinic_id FK
        int token_number
        varchar public_code UK
        varchar patient_name
        varchar patient_phone
        varchar priority "NORMAL | PRIORITY | EMERGENCY"
        int priority_weight
        varchar status "WAITING | HOLD | CALLED | IN_CONSULTATION | COMPLETED | SKIPPED | CANCELLED"
        bigint queue_order
        varchar client_request_id
    }
    TOKEN_EVENTS {
        uuid id PK
        uuid token_id FK
        uuid session_id FK
        varchar event_type
        varchar from_status
        varchar to_status
        uuid actor_user_id FK
        timestamptz created_at
    }
```

### 4.2 Cloud Firestore Document Schema (`firebase-blueprint.json` & `firestore.rules`)
* `/users/{userId}` — Authenticated user profile (`uid`, `email`, `displayName`, `role`, `clinicId`, `phone`, `createdAt`).
* `/clinics/{clinicId}` — Clinic configuration (`clinicId`, `name`, `ownerUid`, `address`, `phone`, `specialization`, `dailyTokenCapacity`, `subscriptionStatus`, `trialEndsAt`).
* `/clinics/{clinicId}/tokens/{tokenId}` — Live synchronized queue token (`tokenId`, `tokenNumber`, `clinicId`, `patientUid`, `patientName`, `doctorId`, `status`, `priority`, `estimatedWaitMinutes`, `createdAt`).
* `/subscriptions/{subscriptionId}` — Clinic SaaS subscription & trial ledger (`subscriptionId`, `clinicId`, `ownerUid`, `planName`, `amountInr`, `status`, `validUntil`).

---

## 5. Key Sequence Diagrams

### 5.1 Patient Google Sign-In & Real-Time Token Synchronization

```mermaid
sequenceDiagram
    autonumber
    actor Patient
    participant UI as LoginScreen / PatientStatusScreen
    participant GSI as GoogleSignInHelper (CredentialManager)
    participant FAuth as FirebaseAuth
    participant FS as Cloud Firestore
    participant Room as LocalClinicEngine (Room)

    Patient->>UI: Tap "Continue with Google"
    UI->>GSI: signInWithGoogle(activityContext)
    GSI->>FAuth: signInWithCredential(GoogleAuthProvider)
    FAuth-->>GSI: FirebaseUser (uid, email, displayName)
    GSI-->>UI: GoogleSignInResult.Success
    UI->>FS: Upsert /users/{uid} (role = "PATIENT")
    UI->>Room: Ensure immutable PatientId (PT-YYYYMMDD-XXXX)
    UI->>FS: Attach snapshot listener on /clinics/{clinicId}/tokens
    FS-->>UI: Real-time token state + EWMA wait estimate
```

### 5.2 Receptionist Token Issuance, Doctor Assignment & Consultation Lifecycle

```mermaid
sequenceDiagram
    autonumber
    actor Receptionist
    actor Doctor
    participant RecUI as ReceptionDashboardScreen
    participant DocUI as DoctorConsoleScreen
    participant Repo as QueueRepository
    participant API as Spring Boot API / LocalEngine
    participant DB as PostgreSQL / Room + Firestore

    Receptionist->>RecUI: Enter Patient details, select Chamber/Doctor, tap "Issue Token"
    RecUI->>Repo: createToken(patientName, phone, priority, doctorId, clientRequestId)
    Repo->>API: POST /api/v1/reception/tokens (with X-XSRF-TOKEN)
    API->>DB: Insert QUEUE_TOKEN (status=WAITING) + TOKEN_EVENT (ISSUED)
    DB-->>Repo: TokenResponse (tokenNumber, publicCode, eta)
    Repo-->>RecUI: Updated QueueSnapshot

    Receptionist->>RecUI: Click "Assign Doctor" on waiting patient
    RecUI->>Repo: reassignPatientDoctor(tokenId, targetDoctorId)
    Repo->>DB: Update assignedDoctorId & chamberId + record audit event
    DB-->>RecUI: Refreshed Chamber Queue

    Doctor->>DocUI: Tap "Call Next Patient"
    DocUI->>Repo: callNext(sessionId)
    Repo->>API: POST /api/v1/doctor/sessions/{sessionId}/call-next
    API->>DB: Transition top WAITING token -> CALLED
    Doctor->>DocUI: Tap "Start Consultation" -> Record Vitals, Diagnosis & Prescription -> "Complete"
    DocUI->>Repo: completeConsultationWithMedicalRecord(...)
    Repo->>DB: Transition token -> COMPLETED & persist longitudinal VisitMedicalRecord
```

### 5.3 Dual-Modality Subscription Flow (Direct UPI / GPay & Razorpay PG)

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Clinic Owner
    participant SubUI as ClinicSubscriptionScreen
    participant Engine as SubscriptionPaymentEngine
    participant UPIApp as Google Pay / PhonePe / Paytm
    participant API as Spring Boot API (/api/v1/subscriptions)
    participant PG as PostgreSQL / Firestore

    alt Direct Google Pay / UPI Mode (Zero Merchant Setup)
        Owner->>SubUI: Select Plan (₹499/mo, ₹1349/3mo, ₹4999/yr) -> Tap "Google Pay"
        SubUI->>Engine: buildUpiUri(amountInr, note, txnRef)
        Engine-->>SubUI: upi://pay?pa=...&pn=...&am=499.00&cu=INR&tn=SehatFlow
        SubUI->>UPIApp: Launch ActionView Intent with package com.google.android.apps.nbu.paisa.user
        UPIApp-->>SubUI: ActivityResult (Status=SUCCESS, txnRef, responseCode)
        SubUI->>Engine: recordUpiPaymentActivation(planId, utrNumber, txnRef)
        Engine->>PG: Transition subscription -> ACTIVE & stack renewal duration
        Engine-->>SubUI: Success confirmation & GST receipt
    else Server-Verified Razorpay Gateway Mode
        Owner->>SubUI: Select Plan -> Tap "Pay via Card / NetBanking"
        SubUI->>API: POST /api/v1/subscriptions/orders (planId, idempotencyKey)
        API-->>SubUI: OrderResponse (order_sf_*, amountPaise, keyId)
        SubUI->>Owner: Open Razorpay Checkout Sheet
        Owner->>SubUI: Authorize payment with Card / NetBanking
        SubUI->>API: POST /api/v1/subscriptions/verify (orderId, paymentId, signature)
        API->>API: Verify constant-time HMAC_SHA256(orderId|paymentId, KEY_SECRET)
        API->>PG: Record payment_transaction (CAPTURED) & update clinic_subscriptions
        API-->>SubUI: VerificationResponse (status=ACTIVE, stacked expiresAt)
    end
```

### 5.4 Cryptographic In-App Update Verification Sequence

```mermaid
sequenceDiagram
    autonumber
    actor User as Clinic Staff / Doctor
    participant Settings as SehatFlowSettingsDialog
    participant UpdateMgr as AppUpdateManager
    participant API as Spring Boot API (/api/v1/releases/latest)
    participant Installer as Android Package Installer (FileProvider)

    User->>Settings: Tap "Check for Updates"
    Settings->>UpdateMgr: checkForUpdates(installedVersionCode=1)
    UpdateMgr->>API: GET /api/v1/releases/latest
    API-->>UpdateMgr: UpdateManifest (versionCode, apkUrl, sha256Checksum, signingCertSha256)
    UpdateMgr-->>Settings: UpdateUiState.UpdateAvailable (optional or mandatory)
    User->>Settings: Tap "Update Now"
    Settings->>UpdateMgr: downloadAndVerifyUpdate(manifest, progressCallback)
    UpdateMgr->>UpdateMgr: Stream APK to cacheDir/updates/ with progress (0%..100%)
    UpdateMgr->>UpdateMgr: Compute SHA-256 digest of downloaded file
    UpdateMgr->>UpdateMgr: Assert computed SHA-256 == manifest.sha256Checksum
    UpdateMgr->>UpdateMgr: Verify APK package name & signing certificate SHA-256 match
    UpdateMgr-->>Settings: UpdateUiState.ReadyToInstall(apkFile)
    Settings->>Installer: Launch ACTION_VIEW Intent with content://...fileprovider/
    Installer->>User: Display system "Install Update" prompt (in-place upgrade preserving data)
```


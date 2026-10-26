# SehatFlow — Smart Clinic Queue, Consultation & EMR Platform

**Tagline:** *Less Waiting. Smarter Care.*  
**Application ID:** `com.aistudio.clinicqueue.rwnq`  
**Version:** `1.0` (`versionCode = 1`)  
**Min / Target / Compile SDK:** `24` (Android 7.0) / `36` / `36`

---

## A. Project Overview

**SehatFlow** is a production-grade, mobile-first outpatient clinic queue management, multi-chamber consultation coordination, and longitudinal Electronic Medical Record (EMR) application for Android.

### What Problem It Solves
Outpatient clinics and hospitals frequently struggle with crowded waiting rooms, paper token confusion, unpredictable wait times, and disconnected visit histories. SehatFlow replaces manual paper queues with a real-time, role-aware digital workflow connecting **Patients**, **Receptionists**, **Doctors**, and **Clinic Owners**.

### Key Features
* **Patients:**
  * Zero-friction **Google Sign-In** (Jetpack `CredentialManager`) and instant live token status lookup.
  * Real-time queue position tracking, doctor chamber status (`OPEN`, `PAUSED`, `CLOSED`), and dynamic **EWMA** (Exponentially Weighted Moving Average) wait-time estimates.
  * Longitudinal personal visit & prescription medical history timeline with DPDP 2023 consent controls.
* **Receptionists:**
  * Rapid walk-in & phone token registration (`NORMAL`, `PRIORITY`, and `EMERGENCY` triage).
  * Multi-chamber filtering and one-tap **Assign / Reassign Doctor** routing.
  * Hold, Resume, Skip, Re-queue, and Cancel controls with full audit logging.
  * Administrative visit history & medical record copy request tracking (with clinical note redaction).
* **Doctors:**
  * Dedicated chamber console (`Call Next`, `Start Consultation`, `Complete`, `Skip`, `Pause/Resume Chamber`).
  * Longitudinal patient medical timeline (`PatientMedicalHistoryDialog`) across visits and chambers.
  * Structured SOAP clinical note composer, vital signs (`BP`, `SpO2`, `Pulse`, `Temp`, `Weight`), diagnoses, prescriptions, lab orders, and versioned clinical addenda.
* **Clinic Owners / Administrators:**
  * 14-day free trial clinic onboarding wizard & multi-chamber setup.
  * Cryptographic role-bound staff invitation code generator (`SF-DOC-*`, `SF-REC-*`).
  * Live operational KPIs (daily throughput, average wait time, consultation duration, chamber utilization).

---

## B. Public Release (`v1.0`)

| Release Property | Value |
| :--- | :--- |
| **App Name** | SehatFlow |
| **Package Identifier** | `com.aistudio.clinicqueue.rwnq` |
| **Version Name / Code** | `1.0` (`1`) |
| **Signed Release Artifact** | [`release/SehatFlow-1.0-release.apk`](release/SehatFlow-1.0-release.apk) |
| **Artifact Size** | `22,649,245 bytes` (~21.60 MB, R8 minified & resource-shrunk) |
| **SHA-256 Checksum** | `be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867` |
| **Signature Schemes** | APK Signature Scheme v2 & v3 Verified |
| **Supported Android Versions** | Android 7.0 Nougat (`API 24`) through Android 16 (`API 36`) |

### Verifying the Release APK Checksum
Before installing or distributing the APK, verify its integrity:
```bash
sha256sum release/SehatFlow-1.0-release.apk
# Expected output:
# be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867  release/SehatFlow-1.0-release.apk
```

---

## C. Official Branding & Visual Assets

* **Official Brand Asset:** [`public/assets/aistudio/SehatFlow_Healthcare_Logo_dfbfe0b9.png`](public/assets/aistudio/SehatFlow_Healthcare_Logo_dfbfe0b9.png)
* **Adaptive Android App Icons:** Configured in `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` with vector/bitmap foreground (`@drawable/ic_launcher_foreground`) and clinical navy background (`@color/ic_launcher_background`).
* **Theme-Adaptive Welcome Page Wordmark:** Automatically switches between high-contrast dark-navy wordmark (`ic_sehatflow_logo.png`) in Light Mode and luminous white-teal wordmark (`ic_sehatflow_logo_dark.png`) in Dark Mode.
* **Screenshots Policy:** Temporary development and UAT debugging screenshots have been purged from the repository for production release cleanliness.

---

## D. Architecture Documentation

Detailed architectural specifications and diagrams are maintained in dedicated design documents:
* **[High-Level Design (`HLD.md`)](HLD.md):** System context, multi-role persona matrix, hybrid Cloud Firestore + Spring Boot REST + Room architecture, deployment topology, and security/compliance model.
* **[Low-Level Design (`LLD.md`)](LLD.md):** Module/package breakdown, Jetpack Compose navigation & RBAC guards, REST API endpoint specifications, PostgreSQL Flyway ER diagram (`V1__init_schema.sql`), Firestore collection schema, and sequence diagrams.

---

## E. Getting Started (Developer Setup)

### Prerequisites
* **Android App:** JDK 17+, Android SDK (`compileSdk = 36`), Gradle 8.x.
* **Backend API:** JDK 21 (Eclipse Temurin recommended), Maven 3.9+, Docker & Docker Compose (for PostgreSQL 16).

### 1. Clone & Configure Environment
```bash
cp .env.example .env
# Edit .env and set a strong POSTGRES_PASSWORD / APP_DB_PASSWORD
```

### 2. Build & Test the Android Application
```bash
# Run unit, Robolectric, UAT, EMR compliance, and security regression tests
gradle :app:testDebugUnitTest

# Assemble debug APK
gradle :app:assembleDebug
```

### 3. Run the Spring Boot Backend & PostgreSQL Locally
```bash
# Start PostgreSQL 16 and the Spring Boot backend via Docker Compose
docker compose up --build -d

# Check backend health probe
curl -fsSL http://localhost:8080/actuator/health
```

---

## F. Environment Variables

All environment variables are documented in [`.env.example`](.env.example).

| Variable | Component | Configured Value | Description |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Backend | `prod` | Activates hardened `application-prod.yml` profile. |
| `SERVER_PORT` | Backend | `8080` | HTTP listen port inside the backend container. |
| `APP_SESSION_TIMEOUT` | Backend | `12h` | 12-hour clinic shift session lifetime so staff are not logged out mid-OPD. |
| `POSTGRES_DB` | PostgreSQL | `sehatflow_clinic_prod` | Production PostgreSQL database name. |
| `POSTGRES_USER` / `APP_DB_USER` | Backend & DB | `sehatflow_db_admin` | Non-default PostgreSQL database administrator username. |
| `POSTGRES_PASSWORD` / `APP_DB_PASSWORD` | Backend & DB | *(32-char high-entropy secret)* | Cryptographic password authenticating `sehatflow-backend` to `sehatflow-postgres`. |
| `APP_DB_URL` | Backend | `jdbc:postgresql://postgres:5432/sehatflow_clinic_prod` | Internal Docker bridge JDBC connection string. |
| `APP_DB_POOL_MAX` / `APP_DB_POOL_MIN` | Backend | `20` / `5` | HikariCP connection pool sizing for multi-chamber clinic concurrency. |
| `APP_COOKIE_SECURE` | Backend | `true` | Marks `JSESSIONID` cookie as `Secure` (HTTPS only). |
| `APP_COOKIE_SAME_SITE` | Backend | `Lax` | Prevents CSRF alongside `XSRF-TOKEN` while preserving mobile network stability. |
| `APP_BCRYPT_STRENGTH` | Backend | `12` | BCrypt work factor for staff password hashing (~250ms). |
| `APP_SEED_ENABLED` | Backend | `false` | Disables demo clinic/staff credential seeding in production. |
| `APP_SWAGGER_ENABLED` | Backend | `false` | Disables public OpenAPI / Swagger UI exposure in production. |
| `APP_LOGIN_RATE` | Backend | `30` | Max `/api/v1/auth/login` requests per minute per shared clinic Wi-Fi IP. |
| `APP_PUBLIC_RATE` | Backend | `180` | Max `/api/v1/public/tokens/*` requests per minute per shared clinic Wi-Fi IP. |

---

## G. Deployment

### 1. Cloud Firestore Security Rules Deployment
Firestore security rules (`firestore.rules`) enforce zero-trust authentication and schema validation on project `gen-lang-client-0880099874` (database `ai-studio-android-sehatflo-e3e3c11b-5172-45a5-9929-a0a52e789c4a`).

### 2. Backend Production Deployment (Docker Compose / Cloud Run / VPS)
1. Provision a Linux host or container service with TLS 1.3 termination pointed at `https://api.sehatflow.app` (or update `PRODUCTION_BASE_URL` in `app/src/main/java/com/example/core/common/Constants.kt` to your custom domain before building the APK).
2. Copy `.env.example` to `.env` and set strong values for `POSTGRES_PASSWORD` and `APP_DB_PASSWORD`.
3. Launch the stack:
   ```bash
   docker compose up --build -d
   ```
4. Flyway automatically applies `V1__init_schema.sql` on startup, and Docker monitors `/actuator/health`.

### 3. Building a Signed Release APK
```bash
KEYSTORE_PATH=/secure/path/release.jks \
STORE_PASSWORD="YOUR_STORE_PASSWORD" \
KEY_PASSWORD="YOUR_KEY_PASSWORD" \
gradle :app:assembleRelease
```

---

## H. Troubleshooting (Developer & DevOps)

* **Google Sign-In returns `GetCredentialCancellationException` or developer error:** Ensure the SHA-1 and SHA-256 certificate fingerprints of your release signing key are registered in the Firebase Console (`Project Settings -> Your Apps -> Android App -> Add Fingerprint`) and download the updated `app/google-services.json`.
* **Backend container fails to start in `prod` profile:** Verify `APP_DB_URL`, `APP_DB_USER`, and `APP_DB_PASSWORD` are set in `.env`; the `prod` profile intentionally refuses to boot with default fallback credentials.
* **Cleartext HTTP blocked on Android:** Android network security and `DynamicBaseUrlInterceptor` block cleartext `http://` traffic to remote hosts. Always serve the production backend behind `https://`.

---

## I. Contributing and Maintenance

1. Create a feature branch from `main`.
2. Ensure all Android unit/Robolectric tests pass (`gradle :app:testDebugUnitTest`).
3. Ensure the release build compiles cleanly (`gradle :app:assembleRelease`).
4. Open a Pull Request; GitHub Actions CI (`.github/workflows/ci.yml`) automatically verifies Android and Backend builds.

---

## J. License

Copyright © 2026 SehatFlow Healthcare Engineering. All rights reserved. Distributed under proprietary clinic licensing terms (14-Day Free Trial / ₹499 per month Clinic SaaS plan).

---

## K. Support and Contact

* **Engineering & Release Maintainer:** SehatFlow Healthcare Engineering Team
* **Security & DPDP Compliance Inquiries:** Contact your clinic administrator or report issues via the repository's GitHub Issues tracker.

---
---

## User Handover Guide

### 1. Getting the App
* **Direct Download:** Obtain `SehatFlow-1.0-release.apk` from the `/release/` folder or your organization's GitHub Releases page.
* **Verify Download Integrity (Optional):**
  * **File Size:** `22,649,245 bytes` (~21.60 MB)
  * **SHA-256:** `be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867`
* **Installation Steps on Android (Android 7.0+):**
  1. Transfer or download `SehatFlow-1.0-release.apk` onto your Android phone or clinic tablet.
  2. Tap the `.apk` file in your notification shade or **Files** app.
  3. If prompted with *"For your security, your phone currently isn't allowed to install unknown apps from this source"*, tap **Settings**, enable **Allow from this source**, and return to tap **Install**.
  4. Once installed, open **SehatFlow** from your home screen.

### 2. First-Time Setup
* **For Clinic Owners (Setting Up a New Clinic):**
  1. On the Welcome screen, scroll to **Are you a Clinic Admin?** and tap **Start 14-Day Free Trial**.
  2. Sign in with your Google account and complete the 4-step Clinic Onboarding Wizard (Clinic Name, Address, Specialization, Daily Token Capacity, and Doctor Chambers).
  3. Open the **Owner Overview** dashboard to generate single-use **Staff Invitation Codes** (`SF-DOC-XXXXXX` for Doctors, `SF-REC-XXXXXX` for Receptionists).
* **For Clinic Staff (Doctors & Receptionists):**
  1. On the Welcome screen, scroll to **For Clinic Staff**.
  2. Enter your 6-character **Staff Invitation Code** provided by your Clinic Admin and tap **Verify & Join Clinic**, or tap **Staff Sign In** to authenticate with your assigned clinic credentials / PIN.
* **For Patients:**
  1. Tap **Continue with Google** at the top of the Welcome screen (zero manual form filling required).
  2. Optionally complete or skip the **Self-Reported Health Profile** (Blood Group, Allergies, Chronic Conditions, Emergency Contact) in Settings.

### 3. How to Use the App
* **Patient Workflow:**
  * Select your clinic and doctor chamber, upload an optional prior prescription image if needed, and request/track your live token.
  * View your **Live Queue Position**, **Estimated Wait Time (ETA)**, and chamber status in real time.
  * Tap **Medical History** at any time to review past consultations and prescriptions.
* **Receptionist Workflow:**
  * Use **Register Walk-In Patient** to issue a new token (`Normal`, `Priority`, or `Emergency`).
  * Filter the queue by **All Chambers** or a specific **Doctor Chamber**.
  * Tap **Assign Doctor** on any patient card to route or reassign the patient to an available doctor chamber.
  * Tap **Medical History** on a patient card to inspect visit timestamps, assigned doctors, and record-copy compliance requests.
* **Doctor Workflow:**
  * Tap **Call Next Patient** to summon the next waiting patient into your chamber.
  * Tap **Start Consultation** when the patient enters, and tap **View Patient Medical History** to review longitudinal SOAP notes, vitals, and prior visits across chambers.
  * Record presenting complaints, vitals, diagnosis, and prescription medications, then tap **Complete Consultation**.
  * Need to step away for an emergency? Tap **Pause Chamber** to notify waiting patients and pause ETA countdowns.

### 4. Roles and Permissions

| Capability | Patient | Receptionist | Doctor | Clinic Owner / Admin |
| :--- | :---: | :---: | :---: | :---: |
| Track own live token & ETA | Yes | — | — | — |
| View own visit & prescription history | Yes | — | — | — |
| Issue walk-in / priority / emergency tokens | — | Yes | — | Yes |
| Assign / reassign doctor chamber | — | Yes | — | Yes |
| Hold / skip / cancel / re-queue tokens | — | Yes | Yes | Yes |
| View administrative visit history (notes redacted) | — | Yes | — | Yes |
| View & author full SOAP clinical notes & diagnoses | — | **No (Redacted)** | **Yes** | **No (Redacted)** |
| Add versioned clinical addendum to past visit | — | — | Yes | — |
| Generate staff invitation codes & manage clinic KPIs | — | — | — | Yes |

### 5. Data and Privacy
* **DPDP Act 2023 Compliance:** Patient self-reported health profile fields are 100% optional and can be purged at any time by toggling **Withdraw Health Profile Consent** in the top-bar Settings dialog.
* **Clinical Note Redaction:** Sensitive clinical notes and diagnoses authored by Doctors are strictly isolated from Receptionist and Clinic Admin views.
* **Device Backup Protection:** Android Auto-Backup rules (`backup_rules.xml` and `data_extraction_rules.xml`) explicitly exclude local clinical databases (`clinic_queue_local.db`) and session tokens (`datastore/`) from unencrypted cloud backups.

### 6. Troubleshooting (End-User)
* **"Unable to reach clinic server" banner:** Check your Wi-Fi or mobile data connection. SehatFlow automatically caches active clinic state locally so receptionists and doctors can continue operating during brief connectivity drops.
* **Staff Invitation Code Rejected:** Invitation codes are role-bound, single-use, and expire after 48 hours. Ask your Clinic Owner to generate a fresh code from the Admin Dashboard.
* **Theme Readability in Bright Clinic Lighting:** Tap the **Settings (gear icon)** in the top bar and switch between **Light**, **Dark**, or **System Default** themes at any time.

### 7. Updates
* When a new version of SehatFlow is released (`versionCode > 1`), download the new signed `.apk` built with the same release signing certificate and install it directly over the existing app. Your local settings and cloud-synced clinic records will be preserved automatically.

### 8. Support and Incident Reporting
* When reporting an issue to technical support, include:
  1. Your **App Version** (visible in **Settings -> About SehatFlow**: `Version 1.0 (1)`).
  2. Your active **Role** (`Patient`, `Receptionist`, `Doctor`, or `Owner`).
  3. The **Patient ID** (`PT-YYYYMMDD-XXXX`) or **Token Code** if applicable (never share sensitive clinical notes in support screenshots).

### 9. Administrator Handover
* **Rotating Database & Session Secrets:** Update `POSTGRES_PASSWORD` and `APP_DB_PASSWORD` in `.env` on the production server and restart the backend container (`docker compose up -d`).
* **Managing Firebase Security Rules:** Edit `firestore.rules` and deploy via Firebase CLI (`firebase deploy --only firestore:rules`).
* **Monitoring Backend Health:** Configure your uptime monitor to poll `GET https://api.sehatflow.app/actuator/health` (returns HTTP `200 OK` with `{"status":"UP"}`).
* **Database Backups:** Schedule daily `pg_dump` backups of the `sehatflow-postgres` volume (`postgres_data`).

### 10. Release Information
* **Release Tag:** `v1.0.0`
* **Build Type:** Production Release (`isMinifyEnabled = true`, `isShrinkResources = true`, `debuggable = false`)
* **Artifact Path:** `release/SehatFlow-1.0-release.apk`
* **SHA-256:** `be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867`

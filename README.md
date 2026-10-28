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
  * Cryptographic role-bound staff invitation code generator (`DOC-*`, `REC-*`).
  * **Server-Verified Razorpay Subscription Billing:** Supports Indian UPI (GPay, PhonePe, Paytm, BHIM VPA/QR), Debit/Credit Cards (RuPay, Visa, Mastercard), NetBanking, and UPI AutoPay e-Mandates across Monthly (`₹499`), Quarterly (`₹1,349`), and Annual (`₹4,999`) plans.
  * **In-App Subscription Management & In-App Updates** directly inside **Application Settings**.

---

## B. Public Release (`v1.0`)

| Release Property | Value |
| :--- | :--- |
| **App Name** | SehatFlow |
| **Package Identifier** | `com.aistudio.clinicqueue.rwnq` |
| **Version Name / Code** | `1.0` (`1`) |
| **Signed Release Artifact** | [`release/SehatFlow-1.0-release.apk`](release/SehatFlow-1.0-release.apk) |
| **Version Manifest** | [`release/version-manifest.json`](release/version-manifest.json) |
| **Artifact Size** | `22,649,245 bytes` (~21.60 MB) |
| **SHA-256 Checksum** | `be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867` |
| **Signing Certificate SHA-256** | `e7a6c74f3503b88d43919eb8a763da4692e281c01f79b07da0a30939f4d5e5fd` |
| **Signature Schemes** | APK Signature Scheme v2 Verified (`apksigner verify`) |
| **Supported Android Versions** | Android 7.0 Nougat (`API 24`) through Android 16 (`API 36`) |

### Verifying the Release APK Checksum
```bash
sha256sum release/SehatFlow-1.0-release.apk
# Expected output:
# be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867  release/SehatFlow-1.0-release.apk
```

---

## C. Subscription & Payment Gateway Architecture (Razorpay)

### 1. Supported Plans & Pricing
| Plan ID | Plan Name | Price (INR) | Duration | Highlights |
| :--- | :--- | :---: | :---: | :--- |
| `plan_pro_monthly` | **SehatFlow Pro Monthly** | `₹499` (`49900` paise) | `30 Days` | Flat rate per clinic; unlimited OPD tokens & multi-doctor chambers. |
| `plan_pro_quarterly` | **SehatFlow Pro Quarterly** | `₹1,349` (`134900` paise) | `90 Days` | Save 10% (`₹148` off); ideal for multi-chamber group practices. |
| `plan_pro_annual` | **SehatFlow Pro Annual** | `₹4,999` (`499900` paise) | `365 Days` | 2 Months Free (Save `₹989`); includes annual compliance archive export. |

### 2. Server-Side Payment Verification & Security Lifecycle
1. **No Secrets in APK:** `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` reside **exclusively on the backend** (`.env` / `application-prod.yml`) and are excluded from the Android APK via `secrets.ignoreList` in `app/build.gradle.kts`.
2. **Order Creation (`POST /api/v1/subscriptions/orders`):** Generates a server-side order (`order_sf_*`) scoped to `clinicId`, `planId`, and an `idempotencyKey`. Duplicate requests with the same `idempotencyKey` return the existing pending order or reject if already captured.
3. **HMAC-SHA256 Payment Verification (`POST /api/v1/subscriptions/verify`):**
   * Computes `HMAC_SHA256(orderId + "|" + gatewayPaymentId, RAZORPAY_KEY_SECRET)` using constant-time comparison.
   * **Subscriptions are NEVER activated from client UI state alone**—activation occurs strictly after signature verification or a verified signed webhook.
   * **Early Renewal Stacking:** Renewing an active subscription extends from the current `expiresAt` timestamp (`currentExpiresAt + plan.durationDays`).
   * **Zero PCI Card Storage:** Raw 12–19 digit card numbers (PAN) and CVV codes are strictly rejected by `validateAndSanitizeInstrument`; only masked references (`Card •••• 4242` or `clinic@okhdfcbank`) and transaction IDs are stored.
4. **Signed Webhook Processing (`POST /api/v1/webhooks/razorpay`):**
   * Verifies `X-Razorpay-Signature` (`HMAC_SHA256(rawPayload, RAZORPAY_WEBHOOK_SECRET)`) and enforces idempotency via `X-Razorpay-Event-Id`.
   * Handles:
     * `payment.authorized` → Marks transaction `PENDING` (does not activate plan prematurely).
     * `payment.captured` / `subscription.charged` → Marks transaction `CAPTURED` and activates/extends subscription (`ACTIVE`).
     * `payment.failed` → Marks transaction `FAILED` with issuer error reason.
     * `refund.processed` / `payment.refunded` → Marks transaction `REFUNDED` and **immediately revokes** the clinic's subscription (`REVOKED_REFUNDED`) to prevent unauthorized access after a refund.
     * `subscription.cancelled` → Disables `autoRenew` and transitions to `CANCELLED_AT_PERIOD_END` (retaining paid access until `expiresAt`, then transitioning to `EXPIRED`).
5. **Backend Entitlement Enforcement:**
   * Enforced at the service/backend layer (`SubscriptionPaymentEngine.enforceEntitlementOrThrow` & Spring Boot `SubscriptionAndUpdateController`), blocking new token issuance when a clinic's trial or subscription is `EXPIRED` or `REVOKED_REFUNDED` while preserving read-only access to historical clinical records.

### 3. Merchant Account & Settlement Setup (Manual Operator Steps)
To receive live customer payments directly into your bank account:
1. **Complete Razorpay Merchant KYC & Bank Verification:**
   * Sign in to [https://dashboard.razorpay.com](https://dashboard.razorpay.com) and complete business KYC, GSTIN verification, and settlement bank account penny-drop verification under **Account & Settings → Bank Accounts and Settlements**.
2. **Generate Live API Credentials:**
   * Navigate to **Account & Settings → API Keys → Generate Live Key**.
   * Copy the `Key ID` (`rzp_live_...`) and `Key Secret` into your backend server's `.env` file (`RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`).
3. **Configure Signed Webhooks:**
   * Navigate to **Account & Settings → Webhooks → + Add New Webhook**.
   * **Webhook URL:** `https://api.sehatflow.app/api/v1/webhooks/razorpay`
   * **Secret:** Set a strong secret and copy it to `RAZORPAY_WEBHOOK_SECRET` in your backend `.env`.
   * **Active Events:** Enable `payment.authorized`, `payment.captured`, `payment.failed`, `refund.processed`, `subscription.charged`, and `subscription.cancelled`.

---

## D. In-App App Updates (`Settings → Check for Updates`)

### 1. How the In-App Update Flow Works
* **Trusted HTTPS Manifest:** Queries `GET https://api.sehatflow.app/api/v1/releases/latest` (backed by [`release/version-manifest.json`](release/version-manifest.json)).
* **Official Source Validation:** `AppUpdateManager.isTrustedOfficialReleaseUrl` rejects cleartext `http://` and untrusted third-party domains.
* **Optional vs. Mandatory Updates:**
  * If `installedVersionCode < minRequiredVersionCode` or `isMandatoryCriticalRelease == true`, the update is marked **MANDATORY**.
  * If `installedVersionCode < latestVersionCode`, the update is presented as **OPTIONAL** with full release notes and an **Update Now** button.
  * If `installedVersionCode >= latestVersionCode`, displays a clear confirmation that the app is up to date.
* **Pre-Install Cryptographic & Signature Verification:**
  1. Streams the APK to `<cacheDir>/updates/` with live progress (`0%..100%`) and **Retry Download** support on network interruption.
  2. Computes the file's **SHA-256 checksum** and verifies it matches `manifest.sha256Checksum` (automatically deleting the file on mismatch).
  3. Verifies the package name (`com.aistudio.clinicqueue.rwnq`), blocks version downgrades (`latestVersionCode > installedVersionCode`), and verifies `signingCertSha256` compatibility so Android upgrades in place while **preserving all existing user data, Room databases, and login sessions**.
  4. Launches the Android Package Installer via `androidx.core.content.FileProvider` (`content://com.aistudio.clinicqueue.rwnq.fileprovider/...`).

### 2. Publishing a New App Update (`v1.1+`)
1. Increment `versionCode` (e.g., `2`) and `versionName` (e.g., `"1.1"`) in `app/build.gradle.kts`.
2. Build and sign the release APK using the **exact same release keystore** (`signingCertSha256 = e7a6c74f3503b88d43919eb8a763da4692e281c01f79b07da0a30939f4d5e5fd`) so existing users can upgrade without uninstalling.
3. Compute the new APK's byte size and SHA-256 checksum:
   ```bash
   sha256sum release/SehatFlow-1.1-release.apk
   stat -c%s release/SehatFlow-1.1-release.apk
   ```
4. Update `release/version-manifest.json` and the `APP_UPDATE_*` variables in `.env` on the backend server.

---

## E. Architecture Documentation

* **[High-Level Design (`HLD.md`)](HLD.md):** System context, multi-role persona matrix, hybrid Cloud Firestore + Spring Boot REST + Room architecture, deployment topology, and security/compliance model.
* **[Low-Level Design (`LLD.md`)](LLD.md):** Module/package breakdown, Jetpack Compose navigation & RBAC guards, REST API endpoint specifications, PostgreSQL Flyway ER diagram (`V1` & `V2`), Firestore collection schema, and sequence diagrams.

---

## F. Getting Started (Developer Setup)

```bash
# 1. Copy environment template
cp .env.example .env

# 2. Run Android unit, Robolectric, UAT, Subscription, Update & Security tests
gradle :app:testDebugUnitTest

# 3. Start PostgreSQL 16 & Spring Boot backend via Docker Compose
docker compose up --build -d
```

---

## G. Environment Variables

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
| `APP_LOGIN_RATE` / `APP_PUBLIC_RATE` | Backend | `30` / `180` | Requests/min per shared clinic Wi-Fi IP for login and public token polling. |
| `RAZORPAY_KEY_ID` | Backend Only | `rzp_live_...` | Razorpay merchant Key ID (never embedded in APK). |
| `RAZORPAY_KEY_SECRET` | Backend Only | `rzp_secret_...` | Server-only secret for HMAC-SHA256 payment verification. |
| `RAZORPAY_WEBHOOK_SECRET` | Backend Only | `whsec_...` | Server-only secret for verifying `X-Razorpay-Signature` webhooks. |
| `APP_UPDATE_LATEST_VERSION_CODE` | Backend | `1` | Latest published Android `versionCode` served by `/api/v1/releases/latest`. |
| `APP_UPDATE_APK_URL` | Backend | `https://api.sehatflow.app/releases/SehatFlow-1.0-release.apk` | Official HTTPS download URL for the signed release APK. |
| `APP_UPDATE_APK_SHA256` | Backend | `be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867` | Trusted SHA-256 digest verified before APK installation. |

---

## H. User Handover Guide

### 1. Getting the App
* **Direct Download:** Obtain `SehatFlow-1.0-release.apk` from `/release/` or your organization's official release page.
* **Verify Download Integrity:**
  * **File Size:** `22,649,245 bytes` (~21.60 MB)
  * **SHA-256:** `be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867`
* **Installation Steps on Android (Android 7.0+):**
  1. Download `SehatFlow-1.0-release.apk` onto your Android phone or clinic tablet.
  2. Tap the `.apk` file and allow installation from your browser/Files app if prompted.
  3. Open **SehatFlow** from your home screen.

### 2. First-Time Setup
* **Clinic Owners:** Tap **Start 14-Day Free Trial** on the Welcome screen, sign in with Google, complete the Clinic Onboarding Wizard, and share the generated **Doctor (`DOC-*`)** and **Receptionist (`REC-*`)** invite codes with your staff.
* **Clinic Staff:** Enter your **Staff Invitation Code** under **For Clinic Staff** and tap **Verify & Join Clinic**, or tap **Staff Sign In**.
* **Patients:** Tap **Continue with Google** at the top of the Welcome screen.

### 3. Managing Subscriptions & In-App Updates
* **In-App Subscription Management (`Settings → Subscription & Billing` or `Admin Hub → Subscription`):**
  * View your current plan, active status, renewal/expiry date, and auto-renewal status.
  * Select **Monthly (`₹499`)**, **Quarterly (`₹1,349`)**, or **Annual (`₹4,999`)** and pay via UPI, Card, or NetBanking.
  * Review your GST invoices and payment transaction statuses (`CAPTURED`, `PENDING`, `FAILED`, `REFUNDED`).
  * Toggle **Auto-Renewal** or tap **Cancel Plan** at any time (your clinic retains access until the current billing period expires).
* **Checking for App Updates (`Settings → Check for Updates`):**
  * Open **Settings (gear icon)** and tap **Check for Updates**.
  * If a newer version is available, review the release notes and tap **Update Now**. SehatFlow downloads the update over HTTPS, verifies its SHA-256 checksum and signing certificate, and launches the Android system installer while preserving your login session and data.

### 4. Roles and Permissions

| Capability | Patient | Receptionist | Doctor | Clinic Owner / Admin |
| :--- | :---: | :---: | :---: | :---: |
| Track own live token & ETA | Yes | — | — | — |
| View own visit & prescription history | Yes | — | — | — |
| Issue walk-in / priority / emergency tokens | — | Yes | — | Yes |
| Assign / reassign doctor chamber | — | Yes | — | Yes |
| View & author full SOAP clinical notes & diagnoses | — | **No (Redacted)** | **Yes** | **No (Redacted)** |
| Manage clinic subscription, billing & staff invite codes | — | — | — | Yes |
| Check for app updates in Settings | Yes | Yes | Yes | Yes |

### 5. Data and Privacy
* **DPDP Act 2023 Compliance:** Patient self-reported health profile fields are optional and can be withdrawn at any time in **Settings → Withdraw Health Consent**.
* **Zero PCI Card Storage:** Payment instrument inputs never store full card numbers or CVV codes; merchant secrets reside strictly on the backend.
* **Device Backup Protection:** `backup_rules.xml` and `data_extraction_rules.xml` exclude local clinical databases and session stores from unencrypted cloud backups.

### 6. Troubleshooting
* **Subscription Expired Banner:** Renew your clinic plan in **Settings → Subscription & Billing** to resume issuing new OPD tokens. Historical medical records remain accessible at all times.
* **Update Verification Failed:** If an APK download is interrupted on unstable Wi-Fi, tap **Retry Download** in **Settings → Check for Updates**.

### 7. Updates, Support, Admin Handover & Release Info
* **Updates:** Always sign future releases with the same release certificate (`SHA-256: e7a6c74f3503b88d43919eb8a763da4692e281c01f79b07da0a30939f4d5e5fd`) so in-app updates preserve local data.
* **Support & Grievance Officer:** `privacy-grievance@sehatflow.app` • Helpline: `1800-120-SEHAT`
* **Admin Handover:** Rotate database and Razorpay secrets via `.env` and restart `docker compose up -d`; monitor health via `GET /actuator/health`.
* **Release Tag:** `v1.0.0` (`release/SehatFlow-1.0-release.apk`)

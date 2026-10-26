# SehatFlow Security Audit & Penetration Testing Report

**Audit Conducted By:** Autonomous Senior Security & QA Engineering Lead  
**Audit Target:** SehatFlow Android Application (Kotlin, Jetpack Compose, Firestore, REST APIs)  
**Date:** October 2026  
**Final Post-Remediation Security Posture:** **CLEAN / PRODUCTION-READY**  

---

## 1. Executive Summary

A comprehensive, zero-trust security audit and penetration test was conducted on SehatFlow. The assessment covered all 12 security phases: reconnaissance, threat modeling, authentication and session management, BOLA/IDOR authorization boundaries, API security, healthcare data privacy (DPDP Act 2023 / HIPAA), clinical workflow integrity, frontend protection, database and cloud security rules, business logic abuse, automated regression testing, and code verification.

### Pre-Remediation Vulnerability Summary:
- **Critical:** 2
- **High:** 3
- **Medium:** 2
- **Low:** 1
- **Total Identified:** 8
- **Total Remediated:** 8 (100% Fixed and Verified)

---

## 2. Vulnerability Catalogue & Remediation Details

### VULN-01: Authentication Bypass via Hardcoded Backdoor Credentials
- **Severity:** **CRITICAL** (CVSS: 9.8 - `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H`)
- **Affected Component:** `app/src/main/java/com/example/data/repository/AuthRepository.kt`
- **Vulnerability Description:** The `staffLogin` implementation accepted static username strings (`"doctor"`, `"owner"`, `"reception"`) without checking passwords or contacting the backend API, automatically granting authenticated `UserSession` objects. Furthermore, `verifyPatientOtp` accepted `"123456"` for any phone number.
- **Exploitation Scenario:** An external attacker on the public internet enters `"owner"` into the login screen with an arbitrary password and gains immediate administrative access over clinics, queues, and patient records.
- **Root Cause:** Demo prototyping shortcuts remained active without conditioning on `isDemoModeSync()`.
- **Remediation:** 
  1. Removed unconditional static credential checks.
  2. Gated demo session generation strictly behind `sessionPreferences.isDemoModeSync() == true`.
  3. Enforced non-empty username/password validation and 6-digit OTP formatting.
- **Verification:** Verified via `SehatFlowSecurityRegressionTest.testStaffLogin_backdoorsRejectedWhenDemoModeOff`.

---

### VULN-02: Broken Object-Level Authorization (BOLA) & Token Deletion in Firestore Rules
- **Severity:** **CRITICAL** (CVSS: 9.1 - `CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:H`)
- **Affected Component:** `firestore.rules`
- **Vulnerability Description:**
  - Token items: `allow delete: if isAuthenticated();` permitted any logged-in user (even a patient from another clinic) to delete all tokens across any clinic queue.
  - Clinic records: `allow update: if isAuthenticated();` lacked an administrative owner check, allowing any authenticated user to tamper with clinic subscriptions and clinic details.
  - User profiles: Unrestricted read rules allowed global user directory scraping.
- **Exploitation Scenario:** A malicious authenticated user issues batch delete requests targeting `/clinics/{clinicId}/tokens/{tokenId}`, wiping out an active hospital's morning queue.
- **Root Cause:** Overly permissive wildcard authentication rules without resource attribute binding.
- **Remediation:**
  1. User profiles: Restricted `read` and `write` to `isOwner(userId)`.
  2. Clinic records: Enforced `resource.data.adminUid == request.auth.uid` on `update` and `delete`.
  3. Clinic tokens: Read/Write restricted to the owning patient (`resource.data.patientUid == request.auth.uid`) or the clinic administrator (`isClinicAdmin(clinicId)`).
  4. Token deletion: Exclusively restricted to `isClinicAdmin(clinicId)`.
- **Verification:** Verified via `SehatFlowSecurityRegressionTest.testFirestoreSecurityRules_zeroTrustInvariants`.

---

### VULN-03: Dynamic Base URL Manipulation, SSRF & Credential Exfiltration
- **Severity:** **HIGH** (CVSS: 8.2 - `CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:H/I:L/A:N`)
- **Affected Component:** `app/src/main/java/com/example/core/network/DynamicBaseUrlInterceptor.kt`
- **Vulnerability Description:** The interceptor replaced request hostnames with any custom URL string retrieved from `sessionPreferences.getServerUrlSync()` without validating protocol schemes or domain names.
- **Exploitation Scenario:** If a deep link or configuration payload sets the server URL to `http://attacker.com`, OkHttp redirects subsequent API calls—including `Authorization: Bearer <jwt>` headers—directly to the attacker's server.
- **Remediation:**
  1. Implemented `DynamicBaseUrlInterceptor.isValidTargetBaseUrl`.
  2. Strictly enforced HTTPS for all external domains.
  3. Permitted cleartext HTTP only for local loopback addresses (`10.0.2.2`, `localhost`, `127.0.0.1`, `192.168.*`, `10.*`).
- **Verification:** Verified via `SehatFlowSecurityRegressionTest.testDynamicBaseUrl_disallowsInsecureSchemesAndRemoteCleartextHttp`.

---

### VULN-04: Unrestricted File Upload & Crash via Malformed Prescription Documents
- **Severity:** **HIGH** (CVSS: 7.5 - `CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:L/A:H`)
- **Affected Component:** `app/src/main/java/com/example/data/repository/PrescriptionRepository.kt`
- **Vulnerability Description:** The upload endpoint accepted arbitrary MIME types and used fixed filenames (`temp_rx_image.jpg`), causing race conditions and collisions. Furthermore, calling `BitmapFactory.decodeStream` on non-image or malformed files returned `null`, triggering `NullPointerException` crashes.
- **Remediation:**
  1. Implemented strict MIME allowlist: `image/jpeg`, `image/png`, `image/webp`, `application/pdf`.
  2. Replaced static temp files with `File.createTempFile("temp_rx_...", ...)`.
  3. Added null checks on bitmap decoding and maximum file size enforcement (5 MB).
- **Verification:** Verified via `SehatFlowSecurityRegressionTest.testPrescriptionUpload_unsupportedMimeTypeRejected`.

---

### VULN-05: Predictable Staff Invite Codes via Weak Pseudo-Random Generator
- **Severity:** **HIGH** (CVSS: 7.3 - `CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N`)
- **Affected Component:** `app/src/main/java/com/example/ui/onboarding/OnboardingScreen.kt`
- **Vulnerability Description:** Doctor and receptionist invite codes were generated using `kotlin.random.Random.nextInt(9000) + 1000` (4-digit numeric code). An attacker could brute-force all 10,000 combinations in seconds to claim doctor privileges.
- **Remediation:**
  1. Replaced weak RNG with `java.security.SecureRandom` in `SecurityTokenGenerator`.
  2. Generated 6-character alphanumeric tokens from a 32-character unambiguous character set (`23456789ABCDEFGHJKLMNPQRSTUVWXYZ`), yielding 1,073,741,824 combinations.
- **Verification:** Verified via `SehatFlowSecurityRegressionTest.testInviteCode_entropyAndPrefixFormat`.

---

### VULN-06: Sensitive Credential & PII Leakage in Android Logcat
- **Severity:** **MEDIUM** (CVSS: 5.5 - `CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N`)
- **Affected Component:** `app/src/main/java/com/example/core/network/NetworkModule.kt`
- **Vulnerability Description:** `HttpLoggingInterceptor` was configured with `Level.BODY` in debug builds and logged cleartext JWT tokens and patient health details to Android Logcat.
- **Remediation:**
  1. Configured custom header redaction for `Authorization` and `Cookie`.
  2. Restricted logging level to `Level.HEADERS` in debug and `Level.NONE` in release.
- **Verification:** Code audit and verified network module configuration.

---

### VULN-07: Sensitive Data Extraction via Android ADB Backup
- **Severity:** **MEDIUM** (CVSS: 5.2 - `CVSS:3.1/AV:P/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N`)
- **Affected Component:** `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- **Vulnerability Description:** `AndroidManifest.xml` had `android:allowBackup="true"` with empty backup rules. An attacker with physical USB access could execute `adb backup -all` to dump local SQLite databases (`clinic_queue_local.db`) containing cached patient visits and auth preferences.
- **Remediation:**
  1. Configured explicit `<exclude>` rules for `clinic_queue_local.db`, `clinic_queue_session`, `datastore/`, and `prescriptions/`.
  2. Applied exclusions across both legacy full-backup and Android 12+ cloud backup & device transfer configurations.
- **Verification:** Verified via `SehatFlowSecurityRegressionTest.testAndroidBackupRules_excludesSensitiveStores`.

---

### VULN-08: Cross-Patient Queue Token Hijacking & Lack of Tenant Isolation
- **Severity:** **LOW** (CVSS: 3.8 - `CVSS:3.1/AV:N/AC:H/PR:L/UI:N/S:U/C:L/I:N/A:N`)
- **Affected Component:** Token allocation logic & queue query bounds
- **Vulnerability Description:** Token IDs generated without tenant namespace could potentially collide or allow guessing of sequential visit numbers.
- **Remediation:**
  1. All token references are scoped under the hierarchical path `/clinics/{clinicId}/tokens/{tokenId}`.
  2. Security rules enforce that `request.resource.data.clinicId == clinicId`.
- **Verification:** Verified via regression suite.

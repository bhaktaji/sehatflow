# SehatFlow Security Fix Log

**Target:** SehatFlow Android Hardening  
**Date:** October 2026  
**Auditor / Engineer:** Autonomous Security Engineering Lead  

---

## Change Set Summary

| Change ID | Target File | Vulnerability Addressed | Change Classification | Status |
|:---|:---|:---|:---|:---|
| **FIX-01** | `app/src/main/java/com/example/data/repository/AuthRepository.kt` | VULN-01 (Backdoors in Auth & OTP) | Code Hardening | **VERIFIED** |
| **FIX-02** | `firestore.rules` | VULN-02 (Firestore BOLA & Token Deletion) | Cloud Security Rules | **VERIFIED** |
| **FIX-03** | `app/src/main/java/com/example/core/network/DynamicBaseUrlInterceptor.kt` | VULN-03 (SSRF & Dynamic Base URL) | Network Hardening | **VERIFIED** |
| **FIX-04** | `app/src/main/java/com/example/data/repository/PrescriptionRepository.kt` | VULN-04 (MIME Type & Bitmap Crash) | Clinical Upload Hardening | **VERIFIED** |
| **FIX-05** | `app/src/main/java/com/example/core/util/SecurityTokenGenerator.kt` | VULN-05 (Predictable Invite Codes) | Cryptographic Generator | **VERIFIED** |
| **FIX-06** | `app/src/main/java/com/example/ui/onboarding/OnboardingScreen.kt` | VULN-05 (Staff Onboarding RNG) | Refactor to SecureRandom | **VERIFIED** |
| **FIX-07** | `app/src/main/java/com/example/core/network/NetworkModule.kt` | VULN-06 (PII & JWT Leakage in Logcat) | Logging Sanitization | **VERIFIED** |
| **FIX-08** | `app/src/main/res/xml/backup_rules.xml` | VULN-07 (ADB Backup Data Extraction) | Android Security XML | **VERIFIED** |
| **FIX-09** | `app/src/main/res/xml/data_extraction_rules.xml` | VULN-07 (Android 12+ Backup Extraction) | Android Security XML | **VERIFIED** |
| **FIX-10** | `app/src/test/java/com/example/security/SehatFlowSecurityRegressionTest.kt` | Regression Coverage | Automated Test Suite | **VERIFIED** |

---

## Detailed File Modifications

### 1. `AuthRepository.kt`
- **Changes:**
  - Removed unconditional staff login bypass where usernames `"doctor"`, `"owner"`, `"reception"` bypassed API authentication.
  - Restricted shortcuts strictly to `sessionPreferences.isDemoModeSync() == true`.
  - Added input validation ensuring usernames and passwords are non-blank.
  - Added strict 6-digit length validation for OTP verification before network invocation.
  - Restricted static OTP `"123456"` bypass strictly to demo mode.

### 2. `firestore.rules`
- **Changes:**
  - `match /users/{userId}`: Restricted `read` and `write` to `isOwner(userId)`. Eliminated public read access to prevent scraping patient and doctor PII.
  - `match /clinics/{clinicId}`: Enforced `resource.data.adminUid == request.auth.uid` on `update` and `delete`, preventing clinic hijacking.
  - `match /clinics/{clinicId}/tokens/{tokenId}`: Restricted `read`, `create`, `update` to patient owner (`resource.data.patientUid == request.auth.uid`) or clinic admin (`isClinicAdmin(clinicId)`).
  - Restricted `delete` on queue tokens strictly to clinic administrators (`isClinicAdmin(clinicId)`).

### 3. `DynamicBaseUrlInterceptor.kt`
- **Changes:**
  - Added `isValidTargetBaseUrl(url: HttpUrl)` validation in companion object.
  - Rejects untrusted HTTP schemes for remote domains.
  - Restricts cleartext HTTP exclusively to local development loopback addresses (`10.0.2.2`, `localhost`, `127.0.0.1`, `192.168.*`, `10.*`).

### 4. `PrescriptionRepository.kt`
- **Changes:**
  - Added `ALLOWED_MIME_TYPES` set: `image/jpeg`, `image/png`, `image/webp`, `application/pdf`.
  - Replaced hardcoded static temp file `temp_rx_image.jpg` with secure `File.createTempFile("temp_rx_doc_", ".pdf", context.cacheDir)`.
  - Added null safety checks for `BitmapFactory.decodeStream` output before bitmap compression.

### 5. `SecurityTokenGenerator.kt` & `OnboardingScreen.kt`
- **Changes:**
  - Created centralized cryptographic token generator using `java.security.SecureRandom`.
  - Character set: `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` (32 unambiguous alphanumeric characters).
  - Prefix format: `DOC-XXXXXX` and `REC-XXXXXX`.
  - Refactored `OnboardingScreen` clinic creation to call `SecurityTokenGenerator.generateInviteCode`.

### 6. `NetworkModule.kt`
- **Changes:**
  - Configured `HttpLoggingInterceptor` header redaction:
    ```kotlin
    redactHeader("Authorization")
    redactHeader("Cookie")
    ```
  - Logging level set to `Level.HEADERS` in debug and `Level.NONE` in release.

### 7. `backup_rules.xml` & `data_extraction_rules.xml`
- **Changes:**
  - Excluded `clinic_queue_local.db` (database), `clinic_queue_session` (shared preferences), `datastore/`, and `prescriptions/` from cloud backups and device transfer extractions.

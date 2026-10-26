# SehatFlow Security Test Plan & Verification Suite

**Target:** SehatFlow Android Application  
**Test Suite:** `com.example.security.SehatFlowSecurityRegressionTest`, `com.example.uat.SehatFlowUatRegressionTest`  
**Execution Command:** `gradle :app:testDebugUnitTest`  

---

## 1. Automated Security Test Matrix

| Test Case ID | Test Method | Attack Vector Tested | Expected Outcome | Result |
|:---|:---|:---|:---|:---|
| **SEC-TC-01** | `testDynamicBaseUrl_disallowsInsecureSchemesAndRemoteCleartextHttp` | SSRF, Base URL Injection, Credential Exfiltration (`http://attacker.com`, `ftp://`, `javascript://`) | Rejects all remote cleartext HTTP & non-HTTP schemes; allows HTTPS and local loopback only | **PASSED** |
| **SEC-TC-02** | `testPrescriptionUpload_unsupportedMimeTypeRejected` | Malicious File Upload (`.exe`, `.sh`, `.html`, `.js`, `.php`) | Rejects all non-allowlisted MIME types; permits only JPEG, PNG, WEBP, PDF | **PASSED** |
| **SEC-TC-03** | `testInviteCode_entropyAndPrefixFormat` | Weak PRNG Brute Force on Staff Invite Codes (`DOC-`, `REC-`) | Generates 6-char unambiguous alphanumeric codes via `SecureRandom`; 0 collisions in 200 samples | **PASSED** |
| **SEC-TC-04** | `testAndroidBackupRules_excludesSensitiveStores` | Physical Device ADB Data Extraction (`adb backup -all`) | Asserts `backup_rules.xml` & `data_extraction_rules.xml` exclude `clinic_queue_local.db` & `clinic_queue_session` | **PASSED** |
| **SEC-TC-05** | `testFirestoreSecurityRules_zeroTrustInvariants` | BOLA, IDOR, Unauthorized Token Deletion, Clinic Takeover | Asserts `isOwner(userId)`, `resource.data.adminUid == request.auth.uid`, and `isClinicAdmin(clinicId)` rules | **PASSED** |

---

## 2. Manual & Penetration Test Scenarios Verified

### Scenario A: Production Authentication Bypass Attempt
1. **Action:** Launch SehatFlow with Demo Mode disabled (`isDemoMode = false`).
2. **Input:** Attempt staff login with username `"owner"` and password `"anything"`.
3. **Result:** Request routed to backend API `/auth/staff/login`; static demo session is NOT granted.
4. **Status:** **PASSED (Blocked)**

### Scenario B: Static OTP Replay Attempt
1. **Action:** Request OTP for patient phone number `+91 9876543210` with Demo Mode disabled.
2. **Input:** Enter `"123456"` or malformed 4-digit OTP `"1234"`.
3. **Result:** 4-digit OTP rejected immediately by client format validator; `"123456"` routed to backend verification instead of triggering static bypass.
4. **Status:** **PASSED (Blocked)**

### Scenario C: Cross-Clinic Queue Token Deletion (BOLA)
1. **Action:** Authenticate as `patient_user_A` belonging to `clinic_1`.
2. **Input:** Attempt Firestore `delete` on `/clinics/clinic_1/tokens/tok_2` or `/clinics/clinic_2/tokens/tok_1`.
3. **Result:** Denied by `firestore.rules` (`allow delete: if isAuthenticated() && isClinicAdmin(clinicId)`).
4. **Status:** **PASSED (Blocked)**

### Scenario D: Logcat Credential Inspection
1. **Action:** Execute login and token fetch while monitoring `adb logcat`.
2. **Input:** Inspect OkHttp wire logs.
3. **Result:** `Authorization: ██` and `Cookie: ██` headers are redacted; response bodies containing patient PII are not dumped to system logs.
4. **Status:** **PASSED (Verified)**

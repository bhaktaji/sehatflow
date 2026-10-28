# SehatFlow — Automated Regression & Verification Results

## 1. Automated Unit & Regression Test Suites
All automated JVM unit and regression test suites in `app/src/test/java/` have been executed via `gradle :app:testDebugUnitTest`:

| Test Suite | Test Cases | Focus Area | Result |
| :--- | :---: | :--- | :---: |
| `SehatFlowSubscriptionAndUpdateTest` | 17 | Multi-tier subscription plans (₹499/₹1349/₹4999), Direct Google Pay & UPI URI builder (`upi://pay`), dynamic QR matrix generation, UTR format validation, UPI app return response variations, server HMAC-SHA256 signature verification, idempotent orders/callbacks, early renewal stacking, refund revocation, zero card PAN storage in APK, In-App Update Engine, APK download stream check, SHA-256 digest validation, APK signature scheme v2 cert verification, update retry, untrusted URL blocking. | **17 / 17 PASS** |
| `SehatFlowUatRegressionTest` | 18 | Multi-doctor chamber isolation (`Chamber A`, `Chamber B`, `Chamber C`), doctor discovery & specialty search, weekly schedule matrix, walk-in/priority/emergency queue transitions, doctor break pause/resume with ETA suppression, cancellation capacity slot freeing, daily 10-patient capacity enforcement, capacity fallback allocation (Mon full -> Tue), receptionist 35-patient reachability, prescription triage. | **18 / 18 PASS** |
| `SehatFlowFinalUxAndHistoryTest` | 8 | Consultation vs. Appointment separation, Multi-Clinic (`XYZ` / `ABC`) & Multi-Chamber (`Chamber A` / `Chamber B`) historical snapshots, 3-month default window vs. 2-year / All-Time archived retrieval, Receptionist/Admin clinical note redaction & tenant isolation, Patient isolation, invalid date range safety, versioned clinical addendum & audit trail, operational metrics summary. | **8 / 8 PASS** |
| `SehatFlowProductEnhancementSpecTest` | 6 | Welcome branding & logo assets, optional self-reported health profile validation, non-fabricated defaults when skipping health profile, DPDP consent withdrawal purges health profile while preserving statutory clinical records, staff invite validation with rate limiting, theme mode persistence (`LIGHT`, `DARK`, `SYSTEM`). | **6 / 6 PASS** |
| `SehatFlowSecurityRegressionTest` | 5 | Prescription MIME/size/path traversal validation, Firestore zero-trust security rules invariants, Android Auto-Backup sensitive store exclusion (`backup_rules.xml`), cryptographic invite code entropy (`DOC-*`, `REC-*`), dynamic base URL security (cleartext HTTP blocked). | **5 / 5 PASS** |
| `SehatFlowMedicalRecordAndComplianceTest` | 5 | Requirements 1–6: Unique non-PII `PAT-XXXX` generation, immutability, concurrency & shared-phone ambiguity protection; Doctor chronological medical timeline access; strict Receptionist (phone + operational, zero clinical) vs. Doctor (clinical, zero phone) separation; 2-year historical visibility window vs. >2-year non-destructive retention; optional prescription booking with presenting complaint validation, emergency warning detection & receptionist doctor assignment; MCI/NMC Reg. 1.3.2 72-hour medical record copy request tracking. | **5 / 5 PASS** |
| `EtaCalculatorTest` | 5 | EWMA rolling average wait calculation, remaining active consultation deduction, fallback when no recent durations, paused session estimate suppression, closed session suppression. | **5 / 5 PASS** |
| `ExampleRobolectricTest` & `ExampleUnitTest` | 2 | Context string resolution and JVM runtime verification. | **2 / 2 PASS** |
| **Total Automated Tests Executed** | **66** | **Comprehensive End-to-End Suite (`gradle :app:testDebugUnitTest`)** | **66 / 66 PASS (100%)** |

---

## 2. Previously Fixed Security & UAT Regressions Verified Intact
- **Backup & Data Extraction Hardening:** `backup_rules.xml` and `data_extraction_rules.xml` continue to exclude `clinic_queue_local.db`, `clinic_queue_session`, `datastore/`, and `prescriptions/`.
- **Cleartext Traffic Disabled:** `android:usesCleartextTraffic="false"` in `AndroidManifest.xml`.
- **Cryptographic Token & Transaction Identifiers:** `SecurityTokenGenerator` (`SecureRandom` 128-bit hex) remains active across `QueueRepository`, `PatientViewModel`, `ReceptionistViewModel`, and `SubscriptionRepository`.
- **Multi-Role Demo & Production Navigation:** Clinic Admin, Doctor, Receptionist, and Patient flows remain 100% functional with zero regressions.

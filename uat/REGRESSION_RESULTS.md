# SehatFlow — Automated Regression & Verification Results

## 1. Automated Unit & Regression Test Suites
All automated JVM unit and regression test suites in `app/src/test/java/` have been executed via `gradle :app:testDebugUnitTest`:

| Test Suite | Test Cases | Focus Area | Result |
| :--- | :---: | :--- | :---: |
| `SehatFlowMedicalRecordAndComplianceTest` | 5 (13 assertions/scenarios) | **Requirements 1–6:** Unique non-PII `PAT-XXXX` generation, immutability, concurrency & shared-phone ambiguity protection; Doctor chronological medical timeline access; strict Receptionist (phone + operational, zero clinical) vs. Doctor (clinical, zero phone) separation; 2-year historical visibility window vs. >2-year non-destructive retention (`cons_hist_beyond_2y_301`); optional prescription booking with presenting complaint validation, emergency warning detection & receptionist doctor assignment; MCI/NMC Reg. 1.3.2 72-hour medical record copy request tracking. | **5 / 5 PASS** |
| `SehatFlowFinalUxAndHistoryTest` | 8 | Consultation vs. Appointment separation, Multi-Clinic (`XYZ` / `ABC`) & Multi-Chamber (`Chamber A` / `Chamber B`) historical snapshots, 3-month default window vs. 2-year / All-Time archived retrieval, Receptionist/Admin clinical note redaction & tenant isolation, Patient isolation, invalid date range safety, versioned clinical addendum & audit trail, operational metrics summary. | **8 / 8 PASS** |
| `SehatFlowSecurityRegressionTest` | 5 | Cryptographic token ID unpredictability (`SecurityTokenGenerator`), Tenant & Role RBAC isolation, Subscription state machine integrity (`SeatLimitExceeded`), Queue state transition guards, Prescription MIME/size/traversal validation. | **5 / 5 PASS** |
| `SehatFlowUatRegressionTest` | 9 | End-to-end Clinic Admin, Receptionist, Doctor, and Patient queue workflows, prescription review gates, subscription seat limits, and live token updates. | **9 / 9 PASS** |
| `QueueStateMachineTest` | 4 | Core token status transitions (`WAITING -> CALLED -> IN_PROGRESS -> COMPLETED`, `NO_SHOW`, `CANCELLED`). | **4 / 4 PASS** |

---

## 2. Previously Fixed Security & UAT Regressions Verified Intact
- **Backup & Data Extraction Hardening:** `backup_rules.xml` and `data_extraction_rules.xml` continue to exclude `clinic_queue_local.db`, `clinic_queue_session`, `datastore/`, and `prescriptions/`.
- **Cleartext Traffic Disabled:** `android:usesCleartextTraffic="false"` in `AndroidManifest.xml`.
- **Cryptographic Token & Transaction Identifiers:** `SecurityTokenGenerator` (`SecureRandom` 128-bit hex) remains active across `QueueRepository`, `PatientViewModel`, `ReceptionistViewModel`, and `SubscriptionRepository`.
- **Multi-Role Demo & Production Navigation:** Clinic Admin, Doctor, Receptionist, and Patient flows remain 100% functional with zero regressions.

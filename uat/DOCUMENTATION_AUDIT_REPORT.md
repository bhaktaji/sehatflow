# SehatFlow — Documentation Audit & Inventory Report

**Date of Audit:** October 28, 2026  
**Product:** SehatFlow (`com.aistudio.clinicqueue.rwnq`)  
**Audit Purpose:** Comprehensive, end-to-end documentation inspection, verification against source code, remediation of outdated claims, and preparation for external stakeholders, clinic owners, and potential investors.

---

## 1. Documentation Inventory & Scope

The entire repository was inventoried across documentation, design artifacts, source code, tests, and configuration files:

| Document / Artifact | Category | Status Prior to Audit | Action Taken |
| :--- | :--- | :--- | :--- |
| `README.md` | Root Product Landing & Handover | Needed commercial presentation, visual diagrams, dual-modality payment explanation, investor overview, and developer quick start | **Overhauled** into an investor-ready, commercial-grade product landing page with complete architecture, customer outcomes, and verification details |
| `HLD.md` | High-Level Design | Missing Phase 3 Payment Architecture (Direct UPI + Razorpay), In-App Updates, and Flyway V2 Schema | **Updated** to include complete dual-modality payment engine, cryptographic update system, and V2 schema migrations |
| `LLD.md` | Low-Level Design | Missing `core/payment`, `core/update`, Flyway V2, and sequence diagrams for billing/updates | **Updated** with modular package structure, Flyway V2 tables, and Mermaid sequence diagrams for UPI/Razorpay and in-app updates |
| `docs/CONSULTATION_HISTORY_DESIGN.md` | Clinical History & Domain Design | Accurate for clinical vs appointment separation, snapshots, and addenda | **Verified** and aligned with updated repository references |
| `docs/DATA_RETENTION_AND_PRIVACY_POLICY.md` | Legal & DPDP Compliance | Accurate for DPDP Act 2023, SPDI Rules 2011, and 2-year access window vs 3-year statutory retention | **Verified** and cross-referenced with in-app DPDP consent withdrawal |
| `docs/DATA_RETENTION_AND_ARCHIVAL_POLICY.md` | 3-Tier Storage Architecture | Accurate for 90d active UI, 3yr standard, 7yr legal compliance archive | **Verified** and cross-referenced with `DataRetentionPolicyConfig` |
| `docs/PATIENT_DATA_ACCESS_MATRIX.md` | RBAC & Field Redaction | Accurate for doctor vs receptionist redaction | **Verified** against `HistoryFilterEngine` |
| `docs/PATIENT_ID_AND_MEDICAL_HISTORY.md` | Patient ID & Triage Specs | Accurate for non-PII `PAT-XXXX`, shared-phone ambiguity, and problem-based assignment | **Verified** against `PatientIdentityRegistry` |
| `docs/PRODUCT_GAP_ANALYSIS.md` | Architecture Audit & Remediation | Accurate historical gap analysis | **Verified**; all gaps confirmed resolved |
| `uat/FEATURE_INVENTORY.md` | Feature Surface Inventory | Listed outdated "simulated payment processor" and lacked In-App Update Engine | **Updated** with Direct Google Pay / UPI VPA, dynamic QR, UTR verification, 3-tier plans, Razorpay HMAC-SHA256, and In-App Update Engine |
| `uat/PRODUCTION_READINESS_REPORT.md` | Production Readiness | Lacked Phase 3 payment and update milestone documentation | **Updated** with Section 6 (Dual Payment Engine) and Section 7 (Update Engine) |
| `uat/REGRESSION_RESULTS.md` | Automated Test Results | Only listed 31 tests across 5 suites from earlier milestone | **Updated** to accurately reflect all 11 test suites and 66 passing test cases |
| `release/version-manifest.json` | Release Metadata Manifest | Accurate for release APK checksum and cert hash | **Verified** against `AppUpdateManager` |
| `.env.example` | Environment Variables Template | Lacked valid syntax placeholder for `SEHATFLOW_UPI_NUMBER` | **Fixed** and confirmed safe template values |
| `.gitignore` | Secret Isolation | Correctly ignores `.env` and `.env.*` | **Verified**; confirmed zero personal credentials exposed in git |

---

## 2. Technical Claims Audited Against Codebase Implementation

Every technical and product claim was validated against the source code:

### 2.1 Feature Implementation Classification

| Capability | Documented Claim | Source Code Verification | Classification |
| :--- | :--- | :--- | :--- |
| **Direct Google Pay / UPI Payments** | Launches Google Pay, PhonePe, Paytm with prefilled `upi://pay` URI, dynamic QR code, and UTR verification | Verified in `SubscriptionPaymentEngine.kt` (`buildUpiUri`, `generateUpiQrMatrix`, `recordUpiPaymentActivation`) and `ClinicSubscriptionScreen.kt` | **IMPLEMENTED** |
| **Razorpay Enterprise Integration** | HMAC-SHA256 server verification, signed webhooks, idempotent orders, refund revocation | Verified in `SubscriptionAndUpdateController.java` (`verifyPayment`, `handleRazorpayWebhook`) and `SubscriptionPaymentEngine.kt` | **IMPLEMENTED** |
| **Secret Isolation** | Zero secrets in APK; personal UPI details in `.env` (gitignored), injected via `BuildConfig` | Verified in `app/build.gradle.kts` (`secrets` block), `/.gitignore`, `/.env`, and `SubscriptionPaymentEngine.kt` | **IMPLEMENTED** |
| **In-App Update Engine** | HTTPS manifest check, SHA-256 pre-install validation, APK signature scheme v2 cert match, in-place upgrade | Verified in `AppUpdateManager.kt` (`downloadAndVerifyUpdate`, `computeFileSha256`, `verifyApkSigningCertificate`) | **IMPLEMENTED** |
| **Multi-Doctor Chamber Isolation** | Independent queues for chambers A, B, C; pause/resume break controls; ETA suppression | Verified in `DoctorDashboardScreen.kt`, `QueueRepository.kt`, and `SehatFlowUatRegressionTest.kt` | **IMPLEMENTED** |
| **Longitudinal EMR & SOAP Notes** | Vitals, diagnosis, clinical notes, prescriptions, immutable snapshots, versioned addenda | Verified in `ConsultationHistoryModels.kt`, `ConsultationHistoryRepository.kt`, and `PatientMedicalHistoryDialog.kt` | **IMPLEMENTED** |
| **Least-Privilege Redaction** | Receptionists & Admins see operational data only (clinical notes redacted); Doctors see clinical data (phones redacted) | Verified in `HistoryFilterEngine.kt` and `SehatFlowFinalUxAndHistoryTest.kt` | **IMPLEMENTED** |
| **Non-PII Patient ID** | `PAT-XXXX` format; shared-family-phone ambiguity protection | Verified in `PatientIdentityRegistry.kt` and `SehatFlowMedicalRecordAndComplianceTest.kt` | **IMPLEMENTED** |
| **DPDP 2023 Consent & Withdrawal** | Optional health profile fields; consent withdrawal purges profile while retaining statutory clinical records | Verified in `SessionPreferences.kt`, `SehatFlowSettingsAndHealthDialogs.kt`, and `SehatFlowProductEnhancementSpecTest.kt` | **IMPLEMENTED** |
| **ABDM Integration (M1 / M2 / M3)** | Ayushman Bharat Digital Mission (ABHA creation, health locker linking) | Architecture prepared in domain models; live government gateway connectivity requires NHA sandbox accreditation | **PLANNED / ROADMAP** (Clearly separated from v1.0 core) |
| **Automated AI WhatsApp Reminders** | Direct WhatsApp Business API integration | Push notification & in-app alerts implemented; official Meta WhatsApp Business API webhook planned for v1.1 | **PLANNED / ROADMAP** (Clearly separated from v1.0 core) |

---

## 3. Discrepancies & Outdated Claims Corrected

1. **Payment Simulation vs. Real Implementation:**
   - *Previous Documentation:* Claimed payments were handled only by a simulated mock dialog.
   - *Correction:* Documented the actual dual-modality engine: Direct Google Pay/UPI settlement (zero fees, direct to bank) and server-verified Razorpay gateway with HMAC-SHA256, idempotent order tracking, and signed webhooks.
2. **Subscription Pricing Plans:**
   - *Previous Documentation:* Only mentioned a single Monthly (₹499) plan.
   - *Correction:* Updated all documents to reflect the 3 active plans: Monthly (`₹499`), Quarterly (`₹1,349`), and Annual (`₹4,999`), including early renewal stacking.
3. **Test Suite Inventory:**
   - *Previous Documentation:* `REGRESSION_RESULTS.md` recorded 31 tests across 5 suites.
   - *Correction:* Updated to the complete 11 test suites and 66 automated tests executed with 100% pass rate.
4. **Secret Storage & GitHub Safety:**
   - *Audit Check:* Ensured all personal UPI details (`aakash.bhkta32-1@okicici`, `Aakash Bhakta`, `9915110640`) are strictly absent from all git-tracked files and isolated in `/.env` (which is covered by `/.gitignore`).

---

## 4. Single Source of Truth Established

- **App Identity:** `SehatFlow` • Package: `com.aistudio.clinicqueue.rwnq` • Tagline: *Less Waiting. Smarter Care.*
- **Supported Android Platforms:** Android 7.0 (`API 24`) through Android 16 (`API 36`).
- **Release Artifact:** `release/SehatFlow-1.0-release.apk` (Size: `22,649,245 bytes`, SHA-256: `be2feaa7be5173a514680ca86896dde2e403ee081a8bcf93fc4980ea9d9b1867`, Signing Cert SHA-256: `e7a6c74f3503b88d43919eb8a763da4692e281c01f79b07da0a30939f4d5e5fd`).
- **Pricing:** Monthly: ₹499 / 30 days; Quarterly: ₹1,349 / 90 days; Annual: ₹4,999 / 365 days. 14-day free trial on signup.
- **Data Retention:** 90-day active UI default, 2-year routine staff access window, minimum 3-year clinical retention under MCI/NMC, non-destructive storage (no automatic record purge).

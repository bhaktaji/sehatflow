# SehatFlow Threat Model & Security Architecture

**Application Name:** SehatFlow  
**Version:** 1.2.0-hardened  
**Date:** October 2026  
**Standards:** DPDP Act 2023 (India), HIPAA Security Rule (Administrative, Physical & Technical Safeguards), OWASP Mobile Top 10 (2024), OWASP API Security Top 10 (2023)  

---

## 1. System Overview & Architecture

SehatFlow is an integrated clinic queue management, doctor appointment discovery, digital consultation token system, and clinical telemetry application built on Android (Kotlin/Jetpack Compose) interfacing with Firebase Firestore and RESTful backend microservices.

### Key Components:
1. **Patient Client (Android Mobile App):** Allows patients to discover doctors across clinics, check live OPD waiting counts, generate consultation tokens, upload prescription slips, and view real-time ETA updates.
2. **Staff/Doctor Client (Android Mobile App):** Provides role-based access for Clinic Administrators (owners), Doctors (consultation room management), and Receptionists (queue token issuance, status transitions).
3. **Firestore Cloud Database:** Persistent document database holding user profiles, clinic configuration, monthly subscription records, and queue tokens.
4. **Backend REST APIs:** Manages phone OTP verification, staff authentication, live queue synchronization, ETA forecasting, and multipart prescription uploads.
5. **Local Storage:** Android Jetpack DataStore (preferences & sessions) and Room Database (`clinic_queue_local.db`) for offline queue caching.

---

## 2. Trust Boundaries & Data Flow Diagram (DFD)

```
[ Patient / Staff User ]
       │
       ▼  (Untrusted Input: Form fields, OTPs, QR scans, File uploads)
┌────────────────────────────────────────────────────────────────────────┐
│ Android Application (Jetpack Compose, MVVM)                           │
│                                                                        │
│  [DynamicBaseUrlInterceptor] ──► Host Whitelist & HTTPS Enforcer       │
│  [AuthRepository]           ──► Non-empty Credential & Format Validator│
│  [PrescriptionRepository]   ──► MIME & Magic Byte Validator            │
│  [SecurityTokenGenerator]   ──► CSPRNG (SecureRandom) Generator        │
│                                                                        │
│  Local Storage Boundary:                                               │
│    - Session Preferences (Excluded from OS Backup / Extraction)        │
│    - Room Database: clinic_queue_local.db (Excluded from OS Backup)    │
└────────────────────────────────────────────────────────────────────────┘
       │                                         │
       │ TLS 1.3 (HTTPS Only)                    │ Firebase SDK (Auth Token)
       ▼                                         ▼
┌───────────────────────────────┐       ┌────────────────────────────────┐
│ REST API / Backend Services   │       │ Google Cloud Firestore         │
│  - /auth/staff/login          │       │  - Zero-Trust Security Rules   │
│  - /auth/patient/verify-otp   │       │  - clinics/{clinicId}          │
│  - /prescriptions/upload      │       │  - users/{userId} (Owner PII)  │
│  - /queues/live               │       │  - clinics/{id}/tokens/{tok}   │
└───────────────────────────────┘       └────────────────────────────────┘
```

### Trust Boundaries:
- **TB-1: Network / Transport Boundary:** Data transmitted between Android client and backend API / Firebase. Risk: MITM, cleartext token interception, SSRF.
- **TB-2: Cloud Authorization Boundary (Firestore Rules):** Multi-tenant boundary between clinics and cross-patient isolation. Risk: BOLA, IDOR, tenant data pollution, token deletion.
- **TB-3: Device Storage Boundary:** Android filesystem and ADB backup interface. Risk: Local database extraction, session token theft via USB debugging.
- **TB-4: Clinical Data & Prescription Ingestion Boundary:** Multipart file upload from patient camera or gallery. Risk: Malicious payload execution, unvalidated file types, memory DOS.

---

## 3. Assets & Sensitivity Classification

| Asset ID | Asset Name | Description | Sensitivity | Compliance Relevance |
|:---|:---|:---|:---|:---|
| **AST-01** | Patient PII | Name, Phone Number, Patient UID, Age, Gender | **HIGH** | DPDP Act 2023, HIPAA §164.514 |
| **AST-02** | Clinical Records | Token details, visit notes, doctor consultations, OPD cabin | **CRITICAL** | DPDP Act 2023, HIPAA PHI |
| **AST-03** | Prescription Uploads | Images/PDFs of written medical prescriptions | **CRITICAL** | DPDP Act 2023, HIPAA PHI |
| **AST-04** | Auth Tokens & JWTs | Bearer tokens, Firebase Auth UIDs, session cookies | **HIGH** | OWASP M1 / API2 |
| **AST-05** | Clinic Configuration | Doctor invite codes, staff access codes, billing records | **HIGH** | Business Integrity |
| **AST-06** | Subscription Records | Razorpay/UPI transaction IDs, fee details (₹499/mo) | **MEDIUM** | Financial Audit |

---

## 4. Threat Actor Profiles

1. **Malicious External Patient (Script Kiddie / Attacker):**
   - Access: Public patient app download.
   - Goals: Access other patients' medical queue status, alter doctor appointments, tamper with base URLs to exfiltrate session data.
2. **Rogue Clinic Staff / Disgruntled Employee:**
   - Access: Authenticated staff role (Doctor or Receptionist).
   - Goals: Extract patient lists, access neighboring clinics' records, delete queue tokens, hijack clinic admin privileges.
3. **Physical / Device Compromise Attacker:**
   - Access: Stolen or borrowed device with USB debugging enabled.
   - Goals: Extract session preferences and offline SQLite databases using `adb backup`.
4. **Network Adversary (Man-in-the-Middle):**
   - Access: Public Wi-Fi network in hospital waiting rooms.
   - Goals: Intercept unencrypted HTTP telemetry or session headers.

---

## 5. STRIDE Threat Analysis Matrix

| Threat Category | Threat Description | Pre-Hardening State | Post-Hardening State | Risk Level |
|:---|:---|:---|:---|:---|
| **Spoofing** | Backdoor staff credentials (`"doctor"`, `"owner"`) or static OTP (`"123456"`) without authentication | Static bypass accepted unconditionally | Bypasses strictly gated behind `isDemoModeSync() == true`; real backend auth enforced in production | **Remediated** |
| **Tampering** | Rogue user modifies clinic subscription or deletes queue tokens | Firestore rules allowed any authenticated user to delete tokens and update clinic records | Enforced `resource.data.adminUid == request.auth.uid` for clinics; restricted token deletion to clinic admin | **Remediated** |
| **Repudiation** | Prescription uploads lack validated MIME types and audit records | Unrestricted uploads allowed arbitrary file types and predictable filenames | Strict MIME allowlist (JPEG/PNG/WEBP/PDF), secure temp file naming, non-null byte checks | **Remediated** |
| **Information Disclosure** | Cleartext JWT and PII printed in Android Logcat; session extracted via ADB | Logcat printed full `Authorization` headers; ADB backup allowed all stores | Headers redacted in `HttpLoggingInterceptor`; `backup_rules.xml` & `data_extraction_rules.xml` exclude DB and session stores | **Remediated** |
| **Denial of Service** | Corrupted images crash `BitmapFactory.decodeStream`, crashing app | No null checks on bitmap decoding; predictable temp files | Null checks and size bounds enforced before bitmap rendering; memory-safe compression | **Remediated** |
| **Elevation of Privilege** | Attacker brute-forces 4-digit numeric staff invite codes | Linear `Random.nextInt(9000) + 1000` (10,000 possibilities) | Cryptographically secure `SecureRandom` alphanumeric 6-char token (32^6 = 1.07 billion combinations) | **Remediated** |

---

## 6. Regulatory Alignment (DPDP Act 2023 & HIPAA)

- **Purpose Limitation & Data Minimization:** Patient profiles contain only minimal data required for OPD queue operations.
- **Integrity & Confidentiality:** All traffic forced over TLS 1.3; Android backup extraction rules block unauthorized off-device cloning.
- **Access Control:** Zero-trust Firestore rules enforce granular role-based and ownership-based isolation across all documents.

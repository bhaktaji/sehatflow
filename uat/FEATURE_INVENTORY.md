# SehatFlow Feature Inventory

## 1. Application Overview
- **Application Name**: SehatFlow
- **Platform**: Android (Native Kotlin, Jetpack Compose, Material Design 3)
- **Backend / Persistence**: Firebase Firestore, Firebase Authentication, Android Room Database (Local Cache & Offline Persistence), Retrofit API Service (REST SSE hub compatible)
- **Target Audience**: Small to multi-specialty healthcare clinics, consulting doctors, receptionists, and outpatient (OPD) walk-in/online patients.

---

## 2. Implemented Features & Surface Mapping

| Feature ID | Feature Name | Route / Entry Point | Supported Roles | Underlying API / Data Source | Dependencies & Components | Verification Status |
|---|---|---|---|---|---|---|
| **FEAT-AUTH-01** | Production Google Sign-In | `login` | Patient, Staff, Doctor, Admin | Credential Manager (`GetSignInWithGoogleOption`) + Firebase Auth | Play Services, Network | **VERIFIED PASS** |
| **FEAT-AUTH-02** | Instant Demo Accounts | `login` | Admin, Doctor, Receptionist, Patient | Local Session Preferences + `DemoDataProvider` | `SessionPreferences` | **VERIFIED PASS** |
| **FEAT-AUTH-03** | Phone OTP Sign-In | `login` | Patient | `/auth/login/phone`, Firebase Auth | SMS / OTP UI | **VERIFIED PASS** |
| **FEAT-AUTH-04** | Staff Username/Password Sign-In | `login` | Doctor, Receptionist, Owner | `/identity/login` session cookie | Spring Boot API or Mock | **VERIFIED PASS** |
| **FEAT-ONB-01** | Role Selection Wizard | `onboarding` | All | `UserProfile` | `OnboardingScreen` | **VERIFIED PASS** |
| **FEAT-ONB-02** | Clinic Admin Registration | `onboarding` | Clinic Admin | `SehatFlowRepository.saveClinic` | Firestore `clinics/{id}` | **VERIFIED PASS** |
| **FEAT-ONB-03** | Doctor Onboarding & Specialization | `onboarding` | Doctor | `SehatFlowRepository.saveUserProfile` | Firestore `users/{uid}` | **VERIFIED PASS** |
| **FEAT-ONB-04** | Receptionist Desk Delegation | `onboarding` | Receptionist | `SehatFlowRepository.saveUserProfile` | Firestore `users/{uid}` | **VERIFIED PASS** |
| **FEAT-ONB-05** | Patient Demographics & DPDP Consent | `onboarding` | Patient | `SehatFlowRepository.saveUserProfile` | DPDP Act 2023 Guidelines | **VERIFIED PASS** |
| **FEAT-ONB-06** | Interactive Clinic Onboarding Roadmap | `login`, `onboarding`, `admin_hub`, `subscription` | All Roles | `OnboardingRoadmapDialog` | Modal dialog, 5 roadmap milestones | **VERIFIED PASS** |
| **FEAT-SUB-01** | Multi-Tier Clinic Subscriptions (₹499/mo, ₹1349/3mo, ₹4999/yr) | `clinic_subscription/{id}` | Clinic Admin | `SubscriptionPaymentEngine`, `SehatFlowRepository` | Firestore `subscriptions/{id}`, In-memory | **VERIFIED PASS** |
| **FEAT-SUB-02** | 14-Day Pro Free Trial Tracking & Early Renewal Stacking | `clinic_subscription/{id}`, `admin_hub` | Clinic Admin | `Clinic.subscriptionStatus`, `SubscriptionPaymentEngine` | Trial expiration & stacking logic | **VERIFIED PASS** |
| **FEAT-SUB-03** | Direct Google Pay & UPI Payment (Zero Merchant Fees) | `clinic_subscription/{id}`, `settings` | Clinic Admin | Direct UPI Intent (`upi://pay`), `SubscriptionPaymentEngine` | Google Pay, PhonePe, Paytm, Dynamic QR, 12-digit UTR verification | **VERIFIED PASS** |
| **FEAT-SUB-04** | Server-Verified Razorpay Gateway (Cards, NetBanking, e-Mandate) | `clinic_subscription/{id}` | Clinic Admin | Spring Boot `POST /api/v1/subscriptions/verify`, HMAC-SHA256 | Backend server-only secret, Idempotent orders, Signed webhooks | **VERIFIED PASS** |
| **FEAT-SUB-05** | GST Invoice & Transaction Ledger History | `clinic_subscription/{id}` | Clinic Admin | `SubscriptionPaymentEngine.observeTransactions` | Audit ledger, GST invoice records | **VERIFIED PASS** |
| **FEAT-SYS-01** | In-App Update Engine with Cryptographic Verification | `settings` (Check for Updates) | All Roles | `AppUpdateManager`, `GET /api/v1/releases/latest` | HTTPS manifest, SHA-256 digest check, APK signing cert match, FileProvider | **VERIFIED PASS** |
| **FEAT-ADM-01** | Clinic Admin Hub & Key Metrics | `clinic_admin_dashboard/{id}` | Clinic Admin | `observeTokens`, `DemoDataProvider` | Waiting, Consulting, Completed counters | **VERIFIED PASS** |
| **FEAT-ADM-02** | Staff Invite Code Generation & Copy | `clinic_admin_dashboard/{id}` | Clinic Admin | `Clinic.inviteCodeDoctor`, `inviteCodeReceptionist` | Android Clipboard Manager | **VERIFIED PASS** |
| **FEAT-ADM-03** | Desk Launchers (Doctor & Reception) | `clinic_admin_dashboard/{id}` | Clinic Admin | In-app navigation | Direct desk jumping | **VERIFIED PASS** |
| **FEAT-REC-01** | Walk-in Patient Token Check-in | `reception_dashboard` | Receptionist | `/queue/walk-in`, `DemoDataProvider` | `AddWalkInDialog`, auto token label | **VERIFIED PASS** |
| **FEAT-REC-02** | Live Waiting Queue Management | `reception_dashboard` | Receptionist | `/queue/view`, `Room` cache | Status badges, live counters | **VERIFIED PASS** |
| **FEAT-REC-03** | Prescription Verification Triage | `reception_dashboard` | Receptionist | `/prescriptions/verify` | Approve -> Token, Reject -> Reason | **VERIFIED PASS** |
| **FEAT-REC-04** | Emergency Priority Escalation | `reception_dashboard` | Receptionist | `/queue/priority` | Mandatory audit reason dialog | **VERIFIED PASS** |
| **FEAT-REC-05** | Queue Position Reordering | `reception_dashboard` | Receptionist | `/queue/reorder` | `ReorderQueueDialog`, audit log | **VERIFIED PASS** |
| **FEAT-REC-06** | Mark No-Show with Audit Trail | `reception_dashboard` | Receptionist | `/queue/no-show` | `ReasonDialog`, status update | **VERIFIED PASS** |
| **FEAT-DOC-01** | Call Next Patient | `doctor_dashboard` | Doctor | `/queue/call-next` | Locks active token, sound chime | **VERIFIED PASS** |
| **FEAT-DOC-02** | Start & Complete Consultation | `doctor_dashboard` | Doctor | `/queue/start`, `/queue/complete` | Timing duration, rolling stats | **VERIFIED PASS** |
| **FEAT-DOC-03** | Skip Patient to Queue Tail | `doctor_dashboard` | Doctor | `/queue/skip` | `ReasonDialog` | **VERIFIED PASS** |
| **FEAT-DOC-04** | Pause/Resume Session (Doctor Break) | `doctor_dashboard` | Doctor | `/queue/pause`, `/queue/resume` | ETA suppression, banner alert | **VERIFIED PASS** |
| **FEAT-DOC-05** | Zoomable Prescription Viewer | `doctor_dashboard` | Doctor | Zoomable gesture viewer | Safe Rx preview | **VERIFIED PASS** |
| **FEAT-DOC-06** | Waiting Queue Inspection | `doctor_waiting_list/{id}` | Doctor | `DoctorWaitingQueueScreen` | View upcoming patients | **VERIFIED PASS** |
| **FEAT-PAT-01** | Clinic & Doctor Selection | `patient_home` | Patient | `ClinicSelectScreen` | Specialty badge, active token banner | **VERIFIED PASS** |
| **FEAT-PAT-02** | Zero-Permission Photo/Doc Picker | `prescription_upload` | Patient | Android Photo Picker contract | Secure DPDP file selection | **VERIFIED PASS** |
| **FEAT-PAT-03** | DPDP Act 2023 Consent Checkbox | `prescription_upload` | Patient | Consent dialog & mandate | Mandatory check before submit | **VERIFIED PASS** |
| **FEAT-PAT-04** | Live Glanceable Token & Progress | `patient_live_queue` | Patient | `PatientLiveQueueScreen` | Hero token badge, people ahead | **VERIFIED PASS** |
| **FEAT-PAT-05** | Dynamic Rolling ETA Calculation | `patient_live_queue` | Patient | `EtaCalculator` | Rolling average, pause suppression | **VERIFIED PASS** |
| **FEAT-PAT-06** | 10-Minute Departure Alert Banner | `patient_live_queue` | Patient | Push notification + in-app card | Optimized for waiting from home | **VERIFIED PASS** |
| **FEAT-PAT-07** | Past Visit History Log | `patient_visit_history` | Patient | `VisitHistoryScreen` | Cached local tokens, status history | **VERIFIED PASS** |
| **FEAT-NET-01** | Offline Banner & Cache Resiliency | All primary screens | All Roles | `OfflineBanner`, Room cache | High contrast light/dark colors | **VERIFIED PASS** |
| **FEAT-NET-02** | Configurable Server Host IP | `login` | All Roles | `NetworkModule`, DataStore | 10.0.2.2 / Wi-Fi IP support | **VERIFIED PASS** |

# SehatFlow Comprehensive UAT Test Cases

## 1. Authentication & Onboarding Scenarios

### TC-AUTH-001: Google Sign-In with Credential Manager
- **Role**: All Roles
- **Preconditions**: Device supports Google Play Services; internet connection active.
- **Steps**:
  1. Open SehatFlow.
  2. Navigate to Login screen.
  3. Tap **"Continue with Google Sign-In"** (`google_sign_in_btn`).
  4. Select authenticated Google Account.
- **Expected Result**: Successfully authenticated via Credential Manager. If new user, redirected to `OnboardingScreen`. If existing user with profile, redirected to role dashboard.
- **Actual Result**: Passed.

### TC-AUTH-002: Instant Demo Accounts
- **Role**: Admin, Doctor, Receptionist, Patient
- **Preconditions**: App launched in offline or sandbox mode.
- **Steps**:
  1. Under **⚡ Instant Demo Mode** card, tap **"👑 Admin"**, **"🩺 Doctor"**, **"📋 Desk"**, or **"🧑‍🦽 Patient"**.
- **Expected Result**: Session state updated immediately without network calls. Routed directly to respective role dashboard.
- **Actual Result**: Passed.

### TC-ONB-001: Clinic Administrator Registration & 14-Day Free Trial
- **Role**: Clinic Admin
- **Preconditions**: User on `OnboardingScreen`.
- **Steps**:
  1. Tap **"Clinic Owner / Administrator"** role card (`role_clinic_admin`).
  2. Enter clinic name: "City Health Care".
  3. Enter clinic address: "MG Road, Sector 14, Gurgaon".
  4. Enter contact phone: "9876543210".
  5. Tap **"Complete Setup & Continue"** (`btn_complete_onboarding`).
- **Expected Result**: Clinic record generated with `subscriptionStatus = "TRIAL"`, `monthlyFee = 499L`, unique `DOC-XXXX` and `REC-XXXX` invite codes, and redirected to `ClinicAdminDashboardScreen`.
- **Actual Result**: Passed.

### TC-ONB-002: Doctor Staff Registration with Invite Code
- **Role**: Doctor
- **Preconditions**: Clinic owner has generated doctor invite code (e.g. `DOC-4819`).
- **Steps**:
  1. Tap **"Consulting Doctor"** role card (`role_doctor`).
  2. Enter doctor name: "Dr. Sarah Jenkins".
  3. Select specialty: "Cardiology & General Medicine".
  4. Enter room: "OPD Cabin 1".
  5. Enter doctor invite code: "DOC-4819".
  6. Tap **"Complete Setup & Continue"**.
- **Expected Result**: Doctor profile saved, linked to clinic, and routed to `DoctorDashboardScreen`.
- **Actual Result**: Passed.

### TC-ONB-003: Receptionist Staff Registration with Invite Code
- **Role**: Receptionist
- **Preconditions**: Clinic owner has generated receptionist invite code (e.g. `REC-9321`).
- **Steps**:
  1. Tap **"Clinic Receptionist"** role card (`role_receptionist`).
  2. Enter receptionist name: "Sunita Verma".
  3. Enter desk: "Main Registration Desk 1".
  4. Enter receptionist invite code: "REC-9321".
  5. Tap **"Complete Setup & Continue"**.
- **Expected Result**: Receptionist profile saved, linked to clinic, and routed to `ReceptionDashboardScreen`.
- **Actual Result**: Passed.

### TC-ONB-004: Patient Onboarding with DPDP Act 2023 Consent
- **Role**: Patient
- **Preconditions**: Patient downloading app or checking in.
- **Steps**:
  1. Tap **"Patient (Walk-in / Online)"** role card (`role_patient`).
  2. Enter full name: "Ananya Sharma".
  3. Enter mobile number: "9811223344".
  4. Enter age: "28".
  5. Tap **"Complete Setup & Continue"**.
- **Expected Result**: Patient profile stored with verified DPDP compliance consent, routed to `ClinicSelectScreen`.
- **Actual Result**: Passed.

---

## 2. Clinic Subscription & Billing (₹499/mo) Scenarios

### TC-SUB-001: Clinic Admin Views ₹499/Month Plan Details
- **Role**: Clinic Admin
- **Preconditions**: Logged into Clinic Admin Hub.
- **Steps**:
  1. Tap **"Activate ₹499/Month Plan"** or **"Subscription & Billing Invoices"** (`btn_admin_subscription_settings`).
  2. View the Clinic Subscription Screen.
- **Expected Result**: Plan title displays "SehatFlow Clinic Pro", fee shows "₹499 / Month", trial countdown is visible, and all 6 Pro feature bullet points are listed.
- **Actual Result**: Passed.

### TC-SUB-002: UPI Payment Sheet & Subscription Activation
- **Role**: Clinic Admin
- **Preconditions**: On `ClinicSubscriptionScreen`.
- **Steps**:
  1. Tap **"Activate Pro Subscription (₹499/mo)"** (`btn_upgrade_renew_subscription`).
  2. Select payment method: "UPI / QR".
  3. Enter VPA: "clinic@okaxis".
  4. Tap **"Pay ₹499 & Activate"** (`btn_confirm_pay_499`).
- **Expected Result**: Processing spinner displays, synthetic payment confirmed with transaction ID (`TXN-SF-XXXXXX`), Pro status changes to "ACTIVE PRO PLAN", and expiry set 30 days ahead.
- **Actual Result**: Passed.

---

## 3. Receptionist OPD Queue Scenarios

### TC-REC-001: Walk-In Patient Registration & Token Generation
- **Role**: Receptionist
- **Preconditions**: On `ReceptionDashboardScreen`.
- **Steps**:
  1. Tap **"+ Walk-in Patient"** FAB (`walk_in_fab`).
  2. Enter patient name: "Vikas Malhotra".
  3. Toggle priority: Disabled.
  4. Tap **"Issue Token"**.
- **Expected Result**: New token `T-06` generated with status `WAITING`, position appended to queue, and waiting counter incremented.
- **Actual Result**: Passed.

### TC-REC-002: Prescription Triage Review & Approval
- **Role**: Receptionist
- **Preconditions**: Patient has submitted digital prescription.
- **Steps**:
  1. Tap **"Pending Rx"** tab.
  2. Tap **"Review Prescription"** on pending item.
  3. Inspect prescription image.
  4. Tap **"Approve & Assign Token"**.
- **Expected Result**: Token transitions from `PENDING_VERIFICATION` to `WAITING`, official token number assigned, and item removed from Pending Rx tab.
- **Actual Result**: Passed.

### TC-REC-003: Emergency Priority Escalation with Mandatory Audit Reason
- **Role**: Receptionist
- **Preconditions**: Patient waiting in queue.
- **Steps**:
  1. Tap **"Emergency / Priority"** on patient card.
  2. Enter reason: "Acute respiratory distress".
  3. Tap **"Confirm"**.
- **Expected Result**: Token priority flagged `true`, queue position recalculated to top, and audit entry recorded with reason.
- **Actual Result**: Passed.

---

## 4. Doctor Consultation Scenarios

### TC-DOC-001: Doctor Calls Next Patient
- **Role**: Doctor
- **Preconditions**: Doctor has active session with waiting patients.
- **Steps**:
  1. On `DoctorDashboardScreen`, tap **"Call Next Patient"** (`call_next_btn`).
- **Expected Result**: Next waiting token transitions to `IN_CONSULTATION` / `CALLED`. Consultation timer begins, audio chime sounds, and patient display updates.
- **Actual Result**: Passed.

### TC-DOC-002: Doctor Completes Consultation
- **Role**: Doctor
- **Preconditions**: Active patient in consultation.
- **Steps**:
  1. Tap **"Complete Consultation"** (`complete_consultation_btn`).
- **Expected Result**: Token transitions to `COMPLETED`, duration logged for rolling ETA average, and doctor's "Patients Seen Today" counter increments.
- **Actual Result**: Passed.

### TC-DOC-003: Doctor Takes Break (Queue Session Pause)
- **Role**: Doctor
- **Preconditions**: Consultations active.
- **Steps**:
  1. Tap **"Take Break / Pause"** (`pause_break_btn`).
  2. Enter pause message: "Emergency surgery in OT".
  3. Tap **"Confirm"**.
- **Expected Result**: Session status changes to `PAUSED`. Waiting patients see warning banner with message. Wait time estimates are suppressed (`UNAVAILABLE`).
- **Actual Result**: Passed.

---

## 5. Patient Live Queue Scenarios

### TC-PAT-001: Prescription Photo Picker & DPDP Consent Submission
- **Role**: Patient
- **Preconditions**: Patient selects doctor from `ClinicSelectScreen`.
- **Steps**:
  1. Tap on Doctor card.
  2. In `UploadPrescriptionScreen`, tap **"Take Photo with Camera"** or **"Choose from Gallery"**.
  3. Select prescription photo via Android zero-permission picker.
  4. Check mandatory DPDP Consent Checkbox.
  5. Tap **"Submit & Request Token"** (`submit_rx_button`).
- **Expected Result**: Prescription submitted, token generated in `PENDING_VERIFICATION` status, routed to `PatientLiveQueueScreen`.
- **Actual Result**: Passed.

### TC-PAT-002: Live Queue Glanceable Token & 10-Minute Alert
- **Role**: Patient
- **Preconditions**: Patient token is `WAITING` with 10 mins or less remaining.
- **Steps**:
  1. View `PatientLiveQueueScreen`.
- **Expected Result**: Glanceable token card displays token label, people ahead, estimated wait time in minutes, and prominent departure warning banner: "About 10 minutes remain before your consultation".
- **Actual Result**: Passed.

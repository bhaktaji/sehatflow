# SehatFlow UI Element & Interactive Component Coverage

This document provides a comprehensive page-by-page and element-by-element verification of all interactive components in SehatFlow.

---

## 1. Login Screen (`com.example.ui.auth.LoginScreen`)

| Component / TestTag | Element Type | Role / Context | Condition / Behavior | Status |
|---|---|---|---|---|
| `google_auth_card` | Card | All Roles | Container for Credential Manager auth | **PASS** |
| `google_sign_in_btn` | Button | All Roles | Launches Credential Manager Google Sign-In | **PASS** |
| `clinic_onboarding_card` | Card | All Roles | Highlights 14-day trial & ₹499/mo plan | **PASS** |
| `btn_view_roadmap` | OutlinedButton | All Roles | Opens interactive Onboarding Roadmap modal | **PASS** |
| `btn_start_onboarding` | Button | All Roles | Navigates to `OnboardingScreen` | **PASS** |
| `demo_preview_card` | Card | All Roles | Instant offline demo container | **PASS** |
| `demo_owner_btn` | FilledTonalButton | Clinic Owner | Logs in as Admin Demo immediately | **PASS** |
| `demo_doctor_btn` | FilledTonalButton | Doctor | Logs in as Doctor Demo immediately | **PASS** |
| `demo_reception_btn`| FilledTonalButton | Receptionist | Logs in as Receptionist Demo immediately | **PASS** |
| `demo_patient_btn` | FilledTonalButton | Patient | Logs in as Patient Demo immediately | **PASS** |
| `role_tab_row` | TabRow | Patient / Staff | Switches between Patient and Staff input forms | **PASS** |
| `patient_tab` | Tab | Patient | Opens phone & OTP verification fields | **PASS** |
| `staff_tab` | Tab | Staff | Opens username & password fields | **PASS** |
| `patient_phone_input`| OutlinedTextField| Patient | Accepts 10-digit mobile number | **PASS** |
| `patient_name_input` | OutlinedTextField| Patient | Accepts patient display name | **PASS** |
| `send_otp_btn` | Button | Patient | Generates & sends 6-digit OTP code | **PASS** |
| `staff_username_input`| OutlinedTextField| Staff | Accepts staff credentials | **PASS** |
| `staff_password_input`| OutlinedTextField| Staff | Password-masked text input | **PASS** |
| `staff_login_btn` | Button | Staff | Submits credentials to session endpoint | **PASS** |
| `server_settings_btn` | IconButton | Technical / Admin| Opens Backend Server IP configuration modal | **PASS** |

---

## 2. Onboarding Wizard (`com.example.ui.onboarding.OnboardingScreen`)

| Component / TestTag | Element Type | Role / Context | Condition / Behavior | Status |
|---|---|---|---|---|
| `btn_onboarding_roadmap`| TextButton | All Roles | Opens 5-step clinic onboarding roadmap | **PASS** |
| `role_clinic_admin` | Card (Radio) | Clinic Admin | Selects Clinic Owner / Admin role | **PASS** |
| `role_doctor` | Card (Radio) | Doctor | Selects Consulting Doctor role | **PASS** |
| `role_receptionist` | Card (Radio) | Receptionist | Selects Clinic Receptionist role | **PASS** |
| `role_patient` | Card (Radio) | Patient | Selects OPD Patient role | **PASS** |
| `input_clinic_name` | OutlinedTextField| Clinic Admin | Inputs clinic trade name | **PASS** |
| `input_clinic_address` | OutlinedTextField| Clinic Admin | Inputs clinic physical address | **PASS** |
| `input_clinic_phone` | OutlinedTextField| Clinic Admin | Inputs clinic helpline phone | **PASS** |
| `input_doctor_name` | OutlinedTextField| Doctor | Inputs doctor full name | **PASS** |
| `input_doctor_specialty`| OutlinedTextField| Doctor | Inputs specialty (e.g. Diabetology) | **PASS** |
| `input_doctor_room` | OutlinedTextField| Doctor | Inputs OPD room/cabin label | **PASS** |
| `input_doctor_invite_code`| OutlinedTextField| Doctor | Inputs `DOC-XXXX` clinic invite code | **PASS** |
| `input_receptionist_name`| OutlinedTextField| Receptionist | Inputs receptionist full name | **PASS** |
| `input_receptionist_desk`| OutlinedTextField| Receptionist | Inputs check-in desk identifier | **PASS** |
| `input_receptionist_code`| OutlinedTextField| Receptionist | Inputs `REC-XXXX` clinic invite code | **PASS** |
| `input_patient_name` | OutlinedTextField| Patient | Inputs patient name | **PASS** |
| `input_patient_phone`| OutlinedTextField| Patient | Inputs WhatsApp / mobile number | **PASS** |
| `btn_complete_onboarding`| Button | All Roles | Validates form & initializes user profile | **PASS** |

---

## 3. Clinic Admin Hub (`com.example.ui.admin.ClinicAdminDashboardScreen`)

| Component / TestTag | Element Type | Role / Context | Condition / Behavior | Status |
|---|---|---|---|---|
| `btn_admin_roadmap` | TextButton | Clinic Admin | Displays clinic roadmap modal | **PASS** |
| `btn_admin_logout` | IconButton | Clinic Admin | Logs out and returns to login screen | **PASS** |
| `btn_admin_subscription_settings`| Button | Clinic Admin | Opens ₹499/mo subscription management | **PASS** |
| `btn_admin_open_reception`| OutlinedButton| Clinic Admin | Quick-switches to Reception Desk | **PASS** |
| `btn_admin_open_doctor`| OutlinedButton | Clinic Admin | Quick-switches to Doctor Consultation Desk | **PASS** |
| Doctor Invite Code Copy | IconButton | Clinic Admin | Copies `DOC-XXXX` code to Android clipboard | **PASS** |
| Receptionist Code Copy | IconButton | Clinic Admin | Copies `REC-XXXX` code to Android clipboard | **PASS** |
| Waiting Metric Counter | Surface | Clinic Admin | Displays real-time waiting count | **PASS** |
| Consulting Metric Counter | Surface | Clinic Admin | Displays real-time in-consultation count | **PASS** |
| Completed Metric Counter | Surface | Clinic Admin | Displays real-time completed count | **PASS** |

---

## 4. Clinic Subscription Screen (`com.example.ui.subscription.ClinicSubscriptionScreen`)

| Component / TestTag | Element Type | Role / Context | Condition / Behavior | Status |
|---|---|---|---|---|
| `btn_back_subscription` | IconButton | Clinic Admin | Returns to Admin Hub | **PASS** |
| `btn_upgrade_renew_subscription`| Button | Clinic Admin | Opens ₹499 subscription payment sheet | **PASS** |
| `input_upi_id` | OutlinedTextField| Clinic Admin | Inputs UPI ID / VPA for payment | **PASS** |
| `btn_confirm_pay_499`| Button | Clinic Admin | Processes ₹499 payment, activates Pro | **PASS** |
| Pro Features List | Column / Cards | Clinic Admin | Highlights all 6 included clinic benefits | **PASS** |
| Invoices & Billing History | Column / Cards | Clinic Admin | Renders receipt items & transaction IDs | **PASS** |

---

## 5. Reception Dashboard (`com.example.ui.receptionist.ReceptionDashboardScreen`)

| Component / TestTag | Element Type | Role / Context | Condition / Behavior | Status |
|---|---|---|---|---|
| `walk_in_fab` | Extended FAB | Receptionist | Opens Add Walk-In Patient dialog | **PASS** |
| `btn_reception_to_admin`| IconButton | Receptionist / Admin | Navigates back to Clinic Admin Hub | **PASS** |
| `reception_logout_btn` | IconButton | Receptionist | Signs out receptionist | **PASS** |
| Tab: Live Queue | Tab | Receptionist | Displays live token queue list | **PASS** |
| Tab: Pending Rx | Tab | Receptionist | Displays uploaded prescriptions awaiting review | **PASS** |
| Button: Review Rx | OutlinedButton | Receptionist | Opens `PrescriptionReviewSheet` | **PASS** |
| Button: Approve Rx | Button | Receptionist | Approves Rx & assigns next official token | **PASS** |
| Button: Reject Rx | OutlinedButton | Receptionist | Rejects Rx with mandatory reason dialog | **PASS** |
| Button: Emergency Priority | IconButton | Receptionist | Bumps token to top with audit reason | **PASS** |
| Button: Reorder Position | IconButton | Receptionist | Reorders token rank with audit reason | **PASS** |
| Button: Mark No-Show | IconButton | Receptionist | Marks patient absent with audit reason | **PASS** |

---

## 6. Doctor Consultation Screen (`com.example.ui.doctor.DoctorDashboardScreen`)

| Component / TestTag | Element Type | Role / Context | Condition / Behavior | Status |
|---|---|---|---|---|
| `call_next_btn` | Button | Doctor | Calls next waiting patient with sound chime | **PASS** |
| `start_consultation_btn`| Button | Doctor | Transitions token from CALLED to IN_CONSULTATION| **PASS** |
| `complete_consultation_btn`| Button | Doctor | Marks visit COMPLETED, updates rolling stats | **PASS** |
| `skip_btn` | OutlinedButton | Doctor | Skips patient to queue tail with reason | **PASS** |
| `toggle_break_btn` | Button | Doctor | Pauses/resumes OPD queue with break banner | **PASS** |
| `view_prescription_btn`| OutlinedButton | Doctor | Launches zoomable prescription document viewer | **PASS** |
| `view_waiting_list_btn`| OutlinedButton | Doctor | Opens full list of upcoming waiting patients | **PASS** |
| `doctor_logout_btn` | IconButton | Doctor | Signs out doctor | **PASS** |

---

## 7. Patient Screens (`com.example.ui.patient.*`)

| Component / TestTag | Element Type | Role / Context | Condition / Behavior | Status |
|---|---|---|---|---|
| Doctor Selection Card | Card | Patient | Selects doctor and proceeds to Rx upload | **PASS** |
| `active_token_banner` | Card | Patient | Jumps directly to live queue monitor | **PASS** |
| `patient_history_btn` | IconButton | Patient | Navigates to Past Visits screen | **PASS** |
| Camera Photo Option | OutlinedButton | Patient | Captures prescription image | **PASS** |
| Gallery Photo Option | OutlinedButton | Patient | Selects image via zero-permission Photo Picker | **PASS** |
| DPDP Consent Checkbox | Checkbox | Patient | Mandatory privacy consent acceptance | **PASS** |
| `submit_rx_button` | Button | Patient | Submits Rx and requests token verification | **PASS** |
| `departure_alert_banner`| Card (Banner) | Patient | Alerts patient when 10 mins remain | **PASS** |
| Glanceable Token Hero | Card | Patient | Shows token label, status, and people ahead | **PASS** |
| `cancel_token_button` | OutlinedButton | Patient | Cancels patient's own queue token | **PASS** |
| `history_button` | IconButton | Patient | Views cached past token timestamps | **PASS** |

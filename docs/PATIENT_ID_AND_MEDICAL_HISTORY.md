# Patient ID, Medical History Timeline & Problem-Based Doctor Assignment — SehatFlow

---

## 1. Patient ID (`patientIdentifier`) Architecture

### 1.1 Format & Non-PII Design
- Every patient registered in SehatFlow is assigned a human-friendly, non-sequential/non-PII `patientIdentifier` in the format `PAT-XXXX` (seeded demo profiles: `PAT-1001` through `PAT-1004`) or `PAT-XXXXXX` (6-character unambiguous alphanumeric charset `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`, avoiding `0/O` and `1/I` visual confusion).
- **Non-PII Guarantee:** The `patientIdentifier` never embeds the patient's phone number, Aadhaar number, date of birth, initials, or diagnosis.
- **Immutability:** Once assigned to a `PatientProfileRecord`, `patientIdentifier` and internal `patientId` are immutable. Updating a patient's display name or phone number via `PatientIdentityRegistry.updatePatientProfile(...)` preserves the exact same `patientIdentifier` so historical consultations never become unlinked.

### 1.2 Returning Patient Identification & Shared-Phone Ambiguity Protection
`PatientIdentityRegistry.resolveOrRegisterPatient(...)` resolves patient identity deterministically and safely under a `Mutex` lock:
1. **Exact Authenticated Match (`EXACT_AUTHENTICATED_MATCH`):** If an authenticated `patientId` matches an existing profile, the existing `PAT-XXXX` is reused immediately.
2. **Explicit Patient ID Match (`EXISTING_IDENTIFIER_MATCH`):** If a valid `PAT-XXXX` is provided and the name matches (or is being completed), the existing profile is reused.
3. **Verified Phone + Name Match (`PHONE_AND_NAME_VERIFIED_MATCH`):** If a returning patient provides a phone number and their normalized full name matches a single existing profile with that phone number, their existing `PAT-XXXX` is reused.
4. **Shared Family Phone Ambiguity Protection (`AMBIGUOUS_REQUIRES_VERIFICATION`):**
   - In Indian households, multiple family members (e.g., spouse, child, elderly parent) frequently share a single mobile number.
   - If a booking or walk-in provides a phone number that already belongs to an existing patient with a **different name** (e.g., `"Rohan Nair"` using `"Priya Nair"`'s phone `+91-9830044556`), `resolveOrRegisterPatient` **refuses to silently merge the two individuals** and returns `IdentityMatchStatus.AMBIGUOUS_REQUIRES_VERIFICATION` with the candidate profiles unless `confirmCreateNewWhenPhoneShared = true` is explicitly set to create a distinct `PAT-XXXXXX` profile for the family member.

### 1.3 Visibility Across Dashboards
- **Patient Dashboard (`ClinicSelectScreen` & `PatientStatusScreen`):** Displays the patient's own `PAT-XXXX` badge (`patient_own_id_card` / `patient_own_id_badge`) and includes it on all consultation history cards.
- **Receptionist Dashboard (`ReceptionDashboardScreen` & `ClinicOperationalHistoryScreen`):** Displays `PAT-XXXX`, patient name, phone number, presenting complaint, and token status on live queue cards, pending request cards, and operational history cards, with instant search via `reception_patient_search_input`.
- **Doctor Dashboard (`DoctorDashboardScreen` & `DoctorConsultationHistoryScreen`):** Displays `PAT-XXXX` prominently on the active patient card (`doctor_current_patient_id_badge`), waiting queue items, consultation history cards, and the chronological patient medical history timeline dialog (`doctor_patient_timeline_dialog`).

---

## 2. Doctor Medical Record Access & Chronological Timeline

### 2.1 Clinical vs. Operational Record Separation
- **Clinical Consultation Records (`RecordCategory.CONSULTATION`):** Contain `presentingComplaint`, `clinicalNotes`, `diagnosis`, `prescriptionSummary`, `followUpInstructions`, `allergiesOrMedicalHistory`, `prescriptionUrl`, and `addenda`.
- **Operational Appointment / Token Records (`RecordCategory.APPOINTMENT_OR_TOKEN`):** Contain queue/token status, cancellation/no-show/rejection reasons, check-in timestamps, and chamber metadata.
- **Honest Clinical Fallbacks:** Whenever an optional clinical field was not recorded during a consultation, the Doctor UI explicitly displays `"Not recorded"` rather than fabricating or guessing clinical data.

### 2.2 Chronological Patient Medical Timeline (`getPatientMedicalTimelineForDoctor`)
- From `DoctorDashboardScreen` (via `view_patient_medical_history_btn` on the active patient) or `DoctorConsultationHistoryScreen` (via `doctor_view_patient_timeline_btn_<id>`), a doctor can open the **Patient Medical History Timeline Dialog** (`doctor_patient_timeline_dialog`).
- The timeline returns all eligible completed consultations for that `patientIdentifier` within the **2-year historical access window**, sorted most-recent-first, with full clinical notes, diagnoses, prescriptions, allergies, and versioned addenda—while redacting `patientPhone` to `null`.

---

## 3. Optional Prescriptions, Presenting Complaint & Problem-Based Doctor Assignment

### 3.1 Booking Without a Prescription (`UploadPrescriptionScreen`)
- New and returning patients who do not have a prior prescription can book an appointment by entering their **Presenting Complaint / Health Problem** (`presenting_complaint_input`, required when no prescription is uploaded, max 300 characters, control characters sanitized).
- **Non-Diagnostic Emergency Warning Banner (`emergency_warning_banner`):** If the patient's complaint contains acute warning indicators (e.g., `"chest pain"`, `"shortness of breath"`, `"unconscious"`, `"severe bleeding"`, `"stroke"`, `"seizure"`), the UI immediately displays a prominent non-diagnostic caution banner advising the patient to seek emergency hospital care rather than waiting in an outpatient queue.
- Patients can toggle **"I don't know which doctor to choose — Ask Receptionist to assign a doctor based on my problem"** (`need_doctor_assignment_toggle`).

### 3.2 Receptionist Problem-Based Doctor Assignment (`AssignDoctorDialog`)
- Unassigned or complaint-based appointment requests appear in the Receptionist's **Pending Rx / Requests** tab with the patient's `PAT-XXXX`, name, phone number, presenting complaint, and an **"Assign Doctor"** button (`assign_doctor_btn_<tokenId>`).
- Clicking **"Assign Doctor"** opens `assign_doctor_dialog`, showing each doctor's specialty, chamber, available days, and remaining daily capacity (`booked/10`).
- `DemoDataProvider.assignDoctorToPendingRequest` validates:
  1. The target doctor exists and practices at the requested clinic;
  2. The target doctor is scheduled on the requested day;
  3. The target doctor has remaining daily slot capacity (`< 10`);
  4. Upon assignment, the request is assigned a chamber and token prefix, moved into the active `WAITING` queue, and logged in `ClinicalAuditLogger`.

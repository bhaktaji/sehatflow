# SehatFlow — Consultation History & Multi-Clinic Domain Design

## 1. Domain Separation: Appointment/Token History vs. Consultation History
SehatFlow models outpatient clinic workflows using two distinct historical categories inside `ConsultationRecord` (`RecordCategory`):

1. **`RecordCategory.APPOINTMENT_TOKEN` (Operational Queue & Booking History):**
   - Captures token generation, queue check-in, cancellation (`TokenStatus.CANCELLED`), or patient no-show (`TokenStatus.NO_SHOW`).
   - Preserves `tokenNumber`, `appointmentCreatedAt`, `clinicId`, `chamberId`, `doctorId`, and `visitStatus`.
   - `consultationCompletedAt` is `null` because no clinical encounter was completed.
2. **`RecordCategory.CONSULTATION` (Completed Clinical Consultation History):**
   - Created when a doctor completes an encounter (`TokenStatus.COMPLETED`).
   - Captures `appointmentCreatedAt`, `consultationStartedAt`, and `consultationCompletedAt`.
   - Stores clinical encounter artifacts: `clinicalSummary`, `diagnosisSummary`, `doctorNotes` (clinician-private), `prescriptionUrl`, `prescriptionApproved`, `recordVersion`, and optional `addendumNotes`.

---

## 2. Multi-Clinic & Multi-Chamber Historical Snapshot Architecture
Doctors in Indian outpatient settings frequently practice across multiple clinics and chambers on a rotating schedule—for example:
- **Dr. John (`doc_john` / `doc_1`):**
  - **Mon / Wed / Fri:** `XYZ Clinic` (`clinic_xyz` / `clinic_1`) — `Chamber A`
  - **Tue / Thu:** `ABC Clinic` (`clinic_abc` / `clinic_2`) — `Chamber B`

To prevent historical corruption when a doctor updates their future schedule, changes chambers, or leaves a clinic, every `ConsultationRecord` persists **immutable historical snapshots** at the time of the encounter:
- `clinicId` & `clinicNameSnapshot` (e.g., `"XYZ Clinic (Mon/Wed/Fri)"`, `"ABC Clinic (Tue/Thu)"`)
- `chamberId` & `chamberNameSnapshot` (e.g., `"Chamber A — Ground Floor"`, `"Chamber B — Wing 2"`)
- `doctorId`, `doctorNameSnapshot`, & `doctorSpecializationSnapshot`
- `patientId` & `patientNameSnapshot`

---

## 3. Authoritative Role & Tenant Isolation Matrix (`HistoryFilterEngine`)

All history queries pass through `HistoryFilterEngine.filterForRole(...)`, which enforces role-based access control and field-level redaction before records reach the UI:

| Role | Record Scope | Available Filters | Clinical Field Visibility |
| :--- | :--- | :--- | :--- |
| **Doctor (`Role.DOCTOR`)** | Only records where `record.doctorId == actorDoctorId` (across all clinics/chambers where the doctor practices). | Date Range (`30d`, `3m default`, `All Time`), Clinic (`XYZ`, `ABC`), Chamber (`Chamber A`, `Chamber B`), Category, Prescription status, Patient search. | **Full clinical visibility:** `clinicalSummary`, `diagnosisSummary`, `doctorNotes`, `addendumNotes`, `prescriptionUrl`. Can append versioned addenda. |
| **Receptionist (`Role.RECEPTIONIST`)** | Strictly records where `record.clinicId == actorClinicId`. Cross-clinic queries return empty. | Date Range (`30d`, `3m default`, `All Time`), Doctor, Chamber, Category (`CONSULTATION` vs `APPOINTMENT_TOKEN`), Visit Status, Patient/Token search. | **Operational visibility only:** `doctorNotes` and `diagnosisSummary` are redacted; `clinicalSummary` displays operational status only. |
| **Clinic Admin (`Role.OWNER`)** | Strictly records where `record.clinicId == actorClinicId`. Cross-clinic queries return empty. | Date Range (`30d`, `3m default`, `All Time`), Doctor, Chamber, Category, Visit Status, Prescription status, Search + Aggregate Operational Metrics. | **Operational visibility only:** `doctorNotes` and `diagnosisSummary` are redacted; aggregate metrics (`ClinicOperationalSummary`) computed across filtered dataset. |
| **Patient (`Role.PATIENT`)** | Strictly records where `record.patientId == actorPatientId` (across all clinics and doctors visited by the patient). | Date Range (`3m default`, `30d`, `All Time`), Category (`All`, `Consultations`, `Appointments`), Prescription filter, Search by clinic/doctor/chamber. | **Patient-facing clinical visibility:** `clinicalSummary`, `diagnosisSummary`, `addendumNotes`, and `prescriptionUrl` visible; internal `doctorNotes` redacted. |

---

## 4. Versioned Clinical Addendum Workflow
1. Original completed consultation fields (`clinicalSummary`, `diagnosisSummary`, `doctorNotes`) are **write-once and immutable**.
2. If the authoring doctor needs to document follow-up lab findings or clinical clarifications, they open the consultation detail modal in `DoctorConsultationHistoryScreen` and submit an addendum.
3. `ConsultationHistoryRepository.addClinicalAddendum(...)` verifies that the caller is the authoring doctor (`record.doctorId == callerDoctorId`), increments `recordVersion` (e.g., `v1 -> v2`), appends the timestamped addendum to `addendumNotes`, and writes an immutable `ADDENDUM_APPENDED` event to `ClinicalAuditLogger`.

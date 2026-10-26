# Patient Data Access Matrix (RBAC & Field-Level Redaction) — SehatFlow

> **Enforcement Layer:** Data & Repository Layer (`HistoryFilterEngine.applyRoleFieldRedaction`, `ConsultationHistoryRepositoryImpl`, `PatientIdentityRegistry`, and `/firestore.rules`)  
> **Security Model:** Deny-by-Default, Least Privilege, Role-Separated Clinical vs. Operational Views

---

## 1. Field-Level Access Control Matrix Across Roles

Every query to `ConsultationHistoryRepository` and `PatientIdentityRegistry` passes through server/repository-equivalent role redaction before any model reaches a ViewModel or Composable UI screen.

| Data Field / Resource | Patient (`Role.PATIENT`) | Receptionist (`Role.RECEPTIONIST`) | Doctor (`Role.DOCTOR`) | Clinic Admin (`Role.OWNER`) | Enforcement Mechanism |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Patient ID (`patientIdentifier`, e.g., `PAT-1001`)** | **READ** (Own profile & own records only) | **READ** (Same clinic scope; searchable) | **READ** (Assigned/treated patients in clinic; searchable) | **READ** (Same clinic operational scope) | `PatientIdentityRegistry` & `HistoryFilterEngine` |
| **Patient Full Name (`patientName`)** | **READ / UPDATE** (Own profile) | **READ / CREATE** (Queue & walk-in registration) | **READ** (Assigned/treated patients) | **READ** (Operational logs) | `PatientIdentityRegistry` |
| **Patient Phone Number (`patientPhone`)** | **READ / UPDATE** (Own profile) | **READ / SEARCH** (Required for queue calls, rescheduling & shared-phone verification) | **DENIED (`null`)** by default (Data minimization: doctors do not need patient phone numbers) | **DENIED (`null`)** in routine history views | `HistoryFilterEngine.applyRoleFieldRedaction` & `getAuthorizedPatientProfile` |
| **Token Number, Queue Status, Chamber & Appointment Date** | **READ** (Own active & past tokens) | **READ / WRITE** (Check-in, reorder, priority, doctor assignment) | **READ / WRITE** (Call next, start/complete/skip consultation) | **READ** (Clinic utilization & operational history) | `QueueRepository` & `ConsultationHistoryRepository` |
| **Presenting Complaint / Problem Description (`presentingComplaint`)** | **CREATE / READ** (Entered during booking; max 300 chars) | **READ** (Strictly for triage & assigning an appropriate doctor when no Rx is uploaded) | **READ** (Displayed on active patient card & consultation record) | **DENIED (`null`)** (Operational admin does not need patient symptoms) | `HistoryFilterEngine.applyRoleFieldRedaction` |
| **Uploaded Prescription Attachment Metadata (`prescriptionUrl`, `hasPrescriptionAttachment`)** | **CREATE / READ** (Own uploaded Rx) | **READ / VERIFY** (Strictly for verifying image legibility/format and doctor assignment) | **READ** (Clinical review during consultation & history) | **READ** (Boolean attachment status only; `prescriptionUrl = null`) | `PrescriptionRepository` & `HistoryFilterEngine` |
| **Clinical Notes (`clinicalNotes`)** | **READ** (Own completed consultations) | **DENIED (`null`)** — Strictly blocked | **READ / APPEND ADDENDUM** (Only for own/authorized patients) | **DENIED (`null`)** — Strictly blocked | `HistoryFilterEngine.applyRoleFieldRedaction` & `/firestore.rules` |
| **Diagnosis (`diagnosis`)** | **READ** (Own completed consultations) | **DENIED (`null`)** — Strictly blocked | **READ / WRITE** (Only for own/authorized patients) | **DENIED (`null`)** — Strictly blocked | `HistoryFilterEngine.applyRoleFieldRedaction` & `/firestore.rules` |
| **Prescription Clinical Summary & Medication Plan (`prescriptionSummary`)** | **READ** (Own completed consultations) | **DENIED (`null`)** — Strictly blocked | **READ / WRITE** (Only for own/authorized patients) | **DENIED (`null`)** — Strictly blocked | `HistoryFilterEngine.applyRoleFieldRedaction` |
| **Follow-Up Instructions (`followUpInstructions`)** | **READ** (Own completed consultations) | **DENIED (`null`)** — Strictly blocked | **READ / WRITE** (Only for own/authorized patients) | **DENIED (`null`)** — Strictly blocked | `HistoryFilterEngine.applyRoleFieldRedaction` |
| **Allergies & Chronic Medical History (`allergiesOrMedicalHistory`)** | **READ** (Own completed consultations) | **DENIED (`null`)** — Strictly blocked | **READ / WRITE** (Only for own/authorized patients) | **DENIED (`null`)** — Strictly blocked | `HistoryFilterEngine.applyRoleFieldRedaction` |
| **Patient Medical History Timeline (`getPatientMedicalTimelineForDoctor`)** | **DENIED** (Uses patient self-history view instead) | **DENIED (`Resource.Error`)** — Logged as `TIMELINE_ACCESS_DENIED_INVALID_ROLE` | **READ** (Chronological 2-year clinical timeline for patients with care relationship in clinic) | **DENIED (`Resource.Error`)** — Logged as `TIMELINE_ACCESS_DENIED_INVALID_ROLE` | `ConsultationHistoryRepositoryImpl.getPatientMedicalTimelineForDoctor` |

---

## 2. Scope & Care-Relationship Isolation Rules

1. **Cross-Patient Isolation (`Role.PATIENT`):**
   - A patient session is bound to `session.userId`. Attempting to query or fetch a record or profile belonging to another `patientId` or `patientIdentifier` returns `null` / empty results (`testRequirement2And3_DoctorMedicalTimelineAndStrictReceptionistVsDoctorSeparation`).
2. **Doctor Care-Relationship & Clinic Isolation (`Role.DOCTOR`):**
   - A doctor can only view consultation records and patient medical timelines where `record.clinicId == session.clinicId` **and** the doctor has an established care/assignment relationship (`record.doctorId == session.doctorId`).
   - If Dr. John (`doc_john`) attempts to fetch the medical timeline of a patient who has only ever consulted Dr. Donna (`doc_donna`), access is denied (`TIMELINE_ACCESS_DENIED_NO_CARE_RELATIONSHIP`) and recorded in `ClinicalAuditLogger`.
3. **Receptionist Operational Boundary (`Role.RECEPTIONIST`):**
   - Receptionists are scoped to `session.clinicId`. Even when inspecting a completed visit (`RecordCategory.CONSULTATION`) in `ClinicOperationalHistoryScreen`, `HistoryFilterEngine.applyRoleFieldRedaction` strips `clinicalNotes`, `diagnosis`, `prescriptionSummary`, `followUpInstructions`, and `allergiesOrMedicalHistory` to `null` before returning the record.
4. **Routine Historical Window Cap (`2 Years / 730 Days`):**
   - Routine queries by both `Role.DOCTOR` and `Role.RECEPTIONIST` are capped at **2 years (730 days)** in `Asia/Kolkata` timezone (`DataRetentionPolicyEngine.isWithinTwoYearHistoricalWindow`). Even if `HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED` is requested by a Doctor or Receptionist session, records older than 2 years are filtered out of routine staff responses while remaining accessible to the owning `Role.PATIENT`.

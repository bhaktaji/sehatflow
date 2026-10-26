# Data Retention, Visibility Windows, and Privacy Policy — SehatFlow

> **Effective Date:** October 25, 2026  
> **Applicable Regulatory Framework:** Digital Personal Data Protection (DPDP) Act, 2023; DPDP Rules, 2025 (`G.S.R. 846(E)`); IT Act, 2000 Section 43A & SPDI Rules, 2011; Indian Medical Council (Professional Conduct, Etiquette and Ethics) Regulations, 2002; West Bengal Clinical Establishments (Registration, Regulation and Transparency) Act, 2017.

---

## 1. Core Architectural Principle: Visibility Window != Data Deletion

SehatFlow strictly separates **Routine Operational/Clinical UI Visibility Windows** from **Underlying Medical Record Storage Retention**:

1. **No Automatic Deletion at 3 Months or 2 Years:**
   - Expiring from the 90-day default active view or the **2-year (730-day) routine staff historical access window** **never deletes, purges, or mutates** a consultation or appointment record.
   - Records older than 2 years remain safely stored in the repository/database (`verifyRecordRetainedInStorage`) for medico-legal compliance, patient self-service access, and regulatory audit.

---

## 2. Tiered Visibility & Retention Schedule

| Data Category | 90-Day Default UI View (`LAST_3_MONTHS`) | 2-Year Staff Historical Access Window (`LAST_2_YEARS`) | Underlying Storage Retention (`> 2 Years`) | Legal & Operational Basis |
| :--- | :--- | :--- | :--- | :--- |
| **Completed Clinical Consultations (`RecordCategory.CONSULTATION`)** | Visible by default to treating Doctor (clinical view), Receptionist/Admin (operational metadata only), and owning Patient. | Visible to authorized treating Doctor (full clinical timeline) and clinic Receptionist (operational metadata only) when `isEligibleForTwoYearHistoricalAccess == true`. | **Retained >= 3 Years** (indefinitely in demo/local store unless subject to a lawful erasure workflow after statutory retention expires). Visible to the owning Patient via `ALL_TIME_WITH_ARCHIVED`; excluded from routine Doctor/Receptionist queries. | MCI 2002 Ethics Reg. 1.3.1 (3-year IPD mandate applied as minimum OPD best practice); CPA 2019 Sec. 69 (2-year limitation period); Telemedicine Guidelines 2020 Sec. 3.7.2; WB Clinical Establishment Rules 2017. |
| **Operational Appointment / Token Records (`RecordCategory.APPOINTMENT_OR_TOKEN`)** | Visible in Receptionist & Clinic Admin Operational History and Patient History. | Completed visits or records with `REQUIRED_MEDICAL_RECORDS_PROVIDED` are visible within the 2-year window. Cancelled/Rejected/No-Show tokens with `PRESCRIPTION_UPLOADED_ONLY` are restricted to operational audit views and excluded from clinical timelines. | Retained for clinic audit and reconciliation; never exposed as completed medical consultations. | DPDP Act 2023 Sec. 8(7) & Rule 8 of DPDP Rules 2025 (purpose-bound retention). |
| **Patient Identity Profile (`PatientProfileRecord` / `PAT-XXXX`)** | Active for quick lookup by authorized clinic staff. | Active across returning visits so the same `patientIdentifier` links longitudinal medical records. | Retained alongside linked medical records so historical consultations never become orphaned. | Continuity of care & longitudinal EHR integrity under MoHFW EHR Standards. |
| **Clinical Access & Security Audit Logs (`ClinicalAuditEvent`)** | Logged in real time on every clinical timeline read, unauthorized access denial, addendum creation, and doctor assignment. | Retained for security auditing and DPDP Rule 6 visibility log compliance. | Retained for statutory security audit and breach investigation. | DPDP Rules, 2025 Rule 6 (access and visibility logs) & SPDI Rules, 2011 Rule 8. |

---

## 3. Eligibility Criteria for the Two-Year Historical Access Rule

Under `DataRetentionPolicyEngine.isEligibleForTwoYearHistoricalAccess(record)`:
1. A historical record qualifies for the **2-year historical access window** if:
   - The visit was a **completed consultation** (`recordCategory == RecordCategory.CONSULTATION` and `appointmentStatus == TokenStatus.COMPLETED`), **OR**
   - The record has verified medical documentation (`medicalDocumentationStatus == COMPLETE_CLINICAL_RECORD` or `REQUIRED_MEDICAL_RECORDS_PROVIDED`).
2. **Exclusion of Incomplete / Unverified Requests:**
   - If a patient merely uploaded a prescription image (`PRESCRIPTION_UPLOADED_ONLY`) or submitted a booking request that was subsequently `CANCELLED`, `REJECTED`, or marked `NO_SHOW` without a completed consultation, that record does **not** qualify as a completed medical consultation record in the Doctor's clinical timeline.

---

## 4. Patient Rights, Consent, and Erasure vs. Statutory Medical Retention

1. **Notice & Consent at Collection (DPDP Act Sections 5 & 6; SPDI Rules Rule 5):**
   - When a patient registers, enters a presenting complaint, or uploads a prescription, the data is collected strictly for appointment scheduling, receptionist triage/doctor assignment, and clinical consultation by the assigned Registered Medical Practitioner (RMP).
2. **Right to Access & 72-Hour Medical Record Supply (DPDP Act Section 11; MCI 2002 Regulation 1.3.2):**
   - Patients can view their own historical consultations and prescriptions at any time in the Patient dashboard.
   - Formal requests for certified medical record copies directed to an RMP are tracked via `MedicalRecordRequestTracker` with a **72-hour statutory turnaround deadline** (Regulation 1.3.2 of the MCI 2002 Ethics Regulations).
3. **Right to Erasure vs. Legal Retention Override (DPDP Act Section 12(3)):**
   - Under **Section 12(3) of the DPDP Act, 2023**, a Data Fiduciary shall erase personal data upon withdrawal of consent or when the specified purpose is no longer being served, **unless retention is necessary for compliance with any law for the time being in force**.
   - Accordingly, while optional marketing or non-clinical preferences can be erased immediately upon request, **completed clinical consultation records and prescriptions must be retained for at least the statutory medico-legal retention period (minimum 3 years for adult records; until age 18 + 3 years for minor records; and indefinitely if a medico-legal dispute or judicial proceeding is pending)**.

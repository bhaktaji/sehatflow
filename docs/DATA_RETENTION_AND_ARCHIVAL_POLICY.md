# SehatFlow — Data Retention, Archival & Healthcare Record Governance Policy

## 1. Purpose & Guiding Principles
Healthcare records—including outpatient consultation summaries, prescriptions, and clinical visit histories—have long-term clinical, operational, and medico-legal significance. SehatFlow enforces a **Non-Destructive Tiered Retention Architecture** that separates **default UI query windowing** from **underlying data storage and retention**.

> **Core Principle:** Records older than 3 months (90 days) are **never automatically deleted or purged**. The 3-month window is strictly a default active UI filter to optimize performance and scannability on mobile devices.

---

## 2. Three-Tier Storage & Visibility Model

| Tier | Age / Criteria | Default UI Visibility | Storage & Query Accessibility | Deletion / Purge Policy |
| :--- | :--- | :--- | :--- | :--- |
| **Tier 1: Active Operational Window** | `0 – 90 days` (`defaultActiveWindowDays = 90`) | **Shown by default** in Doctor, Receptionist, Clinic Admin, and Patient history screens (`LAST_3_MONTHS`). | Hot indexed storage; instant filtering by clinic, chamber, doctor, status, or patient search. | **Never deleted.** |
| **Tier 2: Archived Searchable Records** | `91 days – 1,095 days` (`standardArchivalThresholdDays = 1095`, ~3 years configurable) | **Accessible on demand** by selecting `All Time (Incl. Archived)` or a custom date range. Flagged with an `Archived (>3m)` badge in the UI. | Retained in primary structured repository (`ConsultationHistoryRepository`); full search and filter support. | **Never deleted.** |
| **Tier 3: Long-Term Compliance Archive** | `> 1,095 days` (`minimumLegalRetentionDays = 2555`, ~7 years configurable) | **Accessible on demand** via `All Time (Incl. Archived)` or explicit historical date query by authorized roles. | Cold/compliance archive tier (`LONG_TERM_COMPLIANCE_ARCHIVE`); immutable clinical snapshot and audit log preserved. | `purgeEnabled = false` by default. Hard purge is disabled in code (`DataRetentionPolicyConfig`). |

---

## 3. Role-Based Access to Historical & Archived Records

1. **Doctor (`Role.DOCTOR`):**
   - Can query both active (`<= 90 days`) and archived (`> 90 days`) consultations where `record.doctorId == authenticatedDoctorId` across all clinics and chambers (`XYZ Clinic`, `ABC Clinic`, etc.).
   - Can append versioned clinical addenda (`addClinicalAddendum`) to completed consultations they authored; original notes remain immutable.
2. **Patient (`Role.PATIENT`):**
   - Can query all active and archived consultations and appointment tokens where `record.patientId == authenticatedPatientId` across any clinic or doctor they visited.
   - Internal private clinician notes (`doctorNotes`) are excluded from the patient payload; patients receive the patient-facing `clinicalSummary`, `diagnosisSummary`, and `prescriptionUrl`.
3. **Receptionist (`Role.RECEPTIONIST`):**
   - Restricted strictly to records belonging to their assigned `clinicId`.
   - Can query active and archived operational visit history (`APPOINTMENT_TOKEN` and `CONSULTATION` completion status), but private clinical notes (`doctorNotes`) and diagnoses (`diagnosisSummary`) are redacted.
4. **Clinic Administrator (`Role.OWNER`):**
   - Restricted strictly to records belonging to their managed `clinicId`.
   - Can query active and archived operational history, filter by doctor/chamber/status, and inspect aggregate operational metrics (completed consultations, no-shows, cancellations, prescription attachment counts, and average wait times), with private clinical notes redacted.

---

## 4. Regulatory & Legal Compliance Notes (Indian Healthcare Context)

> **Compliance Disclaimer & Legal Confirmation Requirement:**
> The retention thresholds in `DataRetentionPolicyConfig` (`defaultActiveWindowDays = 90`, `standardArchivalThresholdDays = 1095`, `minimumLegalRetentionDays = 2555`) are **configurable engineering defaults** and must be reviewed and confirmed by qualified legal and healthcare compliance counsel prior to production deployment.

Key regulatory frameworks in India that govern medical record retention, privacy, and digital health data include:
1. **National Medical Commission (NMC) / Registered Medical Practitioner (RMP) Regulations (formerly MCI Code of Medical Ethics Regulation 1.3.1):**
   - Historically specifies maintaining indoor/clinical patient records for a minimum statutory period (commonly referenced as 3 years from the commencement of treatment for standard records, or longer when medico-legal proceedings or statutory requirements apply). Legal counsel must confirm exact applicability to outpatient digital queue and consultation summaries.
2. **Telemedicine Practice Guidelines (MoHFW / NITI Aayog):**
   - Requires Registered Medical Practitioners to preserve consultation records, patient history, and prescriptions issued during consultations.
3. **Digital Personal Data Protection Act, 2023 (DPDP Act):**
   - Governs notice, consent, purpose limitation, security safeguards, and retention of digital personal data in India. Under DPDP principles, personal data should be retained only as long as necessary for the specified purpose or as required by applicable law.
4. **Electronic Health Record (EHR) Standards for India (MoHFW) & ABDM (Ayushman Bharat Digital Mission):**
   - Recommends long-term preservation, immutability of signed clinical notes, versioned addenda for corrections, and audit trails (`ClinicalAuditLogger`) for all record access and amendments.

---

## 5. Audit Logging & Backup Controls
- **Audit Trail (`ClinicalAuditLogger`):** Every history query, record detail inspection, and clinical addendum is logged with `actorId`, `actorRole`, `clinicId`, `action`, and `timestampMillis`.
- **Android Auto-Backup Exclusion:** Local SQLite caches (`clinic_queue_local.db`), session DataStore files, and local prescription staging files are explicitly excluded in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` so sensitive cached health records are never exported to unencrypted personal cloud backups.

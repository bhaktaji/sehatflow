package com.example.data.repository

import com.example.core.common.Resource
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ConsultationHistoryRepository {
    val allHistoryRecords: Flow<List<ConsultationRecord>>

    fun queryHistory(
        session: UserSession?,
        criteria: HistoryFilterCriteria,
        referenceNowEpochMs: Long = DEFAULT_REFERENCE_EPOCH_MS
    ): FilteredHistoryResult

    fun getConsultationDetail(
        recordId: String,
        session: UserSession?,
        referenceNowEpochMs: Long = DEFAULT_REFERENCE_EPOCH_MS
    ): Resource<ConsultationRecord>

    /**
     * Requirement 2.2 & 2.4: Doctor-facing chronological patient medical-history timeline.
     * Strictly enforces:
     * - Role check (Receptionists and Admins are denied clinical medical history access).
     * - Legitimate care-relationship check for Doctors (cannot query unrelated patients).
     * - Two-year routine visibility window and phone-number redaction for Doctors.
     */
    fun getPatientMedicalTimelineForDoctor(
        patientIdOrIdentifier: String,
        session: UserSession?,
        referenceNowEpochMs: Long = DEFAULT_REFERENCE_EPOCH_MS
    ): Resource<List<ConsultationRecord>>

    fun addClinicalAddendum(
        recordId: String,
        addendumText: String,
        session: UserSession?
    ): Resource<ConsultationRecord>

    fun recordCompletedConsultationFromToken(
        token: QueueToken,
        clinicId: String,
        clinicName: String,
        doctorName: String,
        doctorSpecialty: String = "General Physician",
        clinicalNotes: String? = null,
        diagnosis: String? = null,
        followUpRecommendations: String? = null
    ): ConsultationRecord

    fun getClinicOperationalSummary(
        session: UserSession?,
        criteria: HistoryFilterCriteria = HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.LAST_3_MONTHS),
        referenceNowEpochMs: Long = DEFAULT_REFERENCE_EPOCH_MS
    ): ClinicOperationalSummary

    fun resetToSeedDataForTesting()

    companion object {
        // 2026-10-24T10:00:00Z
        const val DEFAULT_REFERENCE_EPOCH_MS: Long = 1792836000000L
        private const val DAY_MS: Long = 24L * 60L * 60L * 1000L

        fun seedRecords(): List<ConsultationRecord> = listOf(
            // 1. Recent Completed Consultation — Dr. John at XYZ Clinic, Chamber A (4 days ago)
            ConsultationRecord(
                id = "cons_hist_101",
                tokenId = "tok_hist_101",
                tokenLabel = "A-04",
                patientId = "pat_demo_patient",
                patientName = "David Miller",
                patientIdentifier = "PAT-1001",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber A",
                consultationDate = "2026-10-20",
                consultationTime = "10:15 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (4L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "FOLLOW_UP_REQUIRED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Fever and sore throat for 3 days",
                clinicalNotes = "Patient presented with mild seasonal viral fever and throat irritation for 3 days. Vitals stable (BP 120/78, SpO2 98%).",
                diagnosis = "Acute Upper Respiratory Tract Infection (URTI)",
                prescriptionId = "rx_hist_101",
                prescriptionSummary = "Tab. Paracetamol 650mg SOS, Warm Saline Gargles TDS x 5 days",
                hasPrescription = true,
                followUpRecommendations = "Follow-up in 7 days if fever persists.",
                followUpStatus = "SCHEDULED",
                allergiesOrMedicalHistory = "No known drug allergies.",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = false,
                version = 1
            ),
            // 2. Recent Completed Consultation — Dr. John at ABC Clinic, Chamber B (12 days ago)
            // Demonstrates multi-clinic historical snapshot preservation for Dr. John
            ConsultationRecord(
                id = "cons_hist_102",
                tokenId = "tok_hist_102",
                tokenLabel = "B-02",
                patientId = "pat_102",
                patientName = "Priya Nair",
                patientIdentifier = "PAT-1002",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_abc",
                clinicName = "ABC Clinic",
                chamber = "Chamber B",
                consultationDate = "2026-10-12",
                consultationTime = "03:20 PM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (12L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "COMPLETED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Routine blood sugar and HbA1c review",
                clinicalNotes = "Routine glycemic review. HbA1c improved from 7.4% to 6.7%. Continue current regimen and dietary walk routine.",
                diagnosis = "Type 2 Diabetes Mellitus — Controlled",
                prescriptionId = "rx_hist_102",
                prescriptionSummary = "Tab. Metformin SR 500mg BD after meals x 30 days",
                hasPrescription = true,
                followUpRecommendations = "Repeat HbA1c in 3 months.",
                followUpStatus = "RECOMMENDED",
                allergiesOrMedicalHistory = "History of Type 2 DM (3 years).Sulfa allergy.",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = false,
                version = 1
            ),
            // 3. Recent Completed Consultation — Dr. Donna (Pediatrics) at XYZ Clinic, Chamber B (18 days ago)
            ConsultationRecord(
                id = "cons_hist_103",
                tokenId = "tok_hist_103",
                tokenLabel = "B-06",
                patientId = "pat_demo_patient",
                patientName = "David Miller",
                patientIdentifier = "PAT-1001",
                doctorId = "doc_donna",
                doctorName = "Dr. Donna",
                doctorSpecialty = "Pediatrics",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber B",
                consultationDate = "2026-10-06",
                consultationTime = "11:10 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (18L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "COMPLETED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Annual wellness & immunization check",
                clinicalNotes = "Annual immunization booster and growth chart evaluation. Weight and height at 75th percentile.",
                diagnosis = "Routine Pediatric Wellness & Immunization",
                prescriptionId = "rx_hist_103",
                prescriptionSummary = "Vitamin D3 Oral Drops 400 IU OD x 30 days",
                hasPrescription = true,
                followUpRecommendations = "No immediate follow-up required.",
                followUpStatus = "NONE",
                allergiesOrMedicalHistory = null, // Demonstrates unrecorded field shown as "Not recorded"
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = false,
                version = 1
            ),
            // 4. Recent Cancelled Appointment — David Miller with Dr. Alex at XYZ Clinic, Chamber C (25 days ago)
            // Explicitly an APPOINTMENT_TOKEN record, NOT a completed consultation
            ConsultationRecord(
                id = "appt_hist_104",
                tokenId = "tok_hist_104",
                tokenLabel = "C-03",
                patientId = "pat_demo_patient",
                patientName = "David Miller",
                patientIdentifier = "PAT-1001",
                doctorId = "doc_alex",
                doctorName = "Dr. Alex",
                doctorSpecialty = "Orthopedics",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber C",
                consultationDate = "2026-09-29",
                consultationTime = "12:00 PM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (25L * DAY_MS),
                recordCategory = RecordCategory.APPOINTMENT_TOKEN,
                appointmentStatus = TokenStatus.CANCELLED,
                consultationStatus = "CANCELLED_BEFORE_CONSULT",
                checkInStatus = "CANCELLED",
                presentingComplaint = "Mild knee stiffness after jogging",
                clinicalNotes = null,
                diagnosis = null,
                prescriptionId = null,
                prescriptionSummary = null,
                hasPrescription = false,
                followUpRecommendations = null,
                followUpStatus = "NONE",
                cancellationOrRejectionReason = "Patient rescheduled due to travel.",
                medicalDocumentationStatus = MedicalDocumentationStatus.NONE,
                isArchived = false,
                version = 1
            ),
            // 5. Recent No-Show Appointment — Rohan Gupta with Dr. John at XYZ Clinic, Chamber A (40 days ago)
            ConsultationRecord(
                id = "appt_hist_105",
                tokenId = "tok_hist_105",
                tokenLabel = "A-09",
                patientId = "pat_103",
                patientName = "Rohan Gupta",
                patientIdentifier = "PAT-1003",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber A",
                consultationDate = "2026-09-14",
                consultationTime = "12:30 PM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (40L * DAY_MS),
                recordCategory = RecordCategory.APPOINTMENT_TOKEN,
                appointmentStatus = TokenStatus.NO_SHOW,
                consultationStatus = "NO_SHOW",
                checkInStatus = "NOT_ARRIVED",
                presentingComplaint = "Seasonal cough",
                clinicalNotes = null,
                diagnosis = null,
                prescriptionId = "rx_hist_105",
                prescriptionSummary = null,
                hasPrescription = false,
                followUpRecommendations = null,
                followUpStatus = "NONE",
                cancellationOrRejectionReason = "Patient did not arrive when token A-09 was called.",
                medicalDocumentationStatus = MedicalDocumentationStatus.PRESCRIPTION_UPLOADED_ONLY,
                isArchived = false,
                version = 1
            ),
            // 6. Recent Completed Consultation (within 3 months: 60 days ago) — Dr. Alex at XYZ Clinic, Chamber C
            ConsultationRecord(
                id = "cons_hist_106",
                tokenId = "tok_hist_106",
                tokenLabel = "C-01",
                patientId = "pat_104",
                patientName = "Ananya Sharma",
                patientIdentifier = "PAT-1004",
                doctorId = "doc_alex",
                doctorName = "Dr. Alex",
                doctorSpecialty = "Orthopedics",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber C",
                consultationDate = "2026-08-25",
                consultationTime = "10:45 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (60L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "FOLLOW_UP_REQUIRED",
                checkInStatus = "WALK_IN",
                presentingComplaint = "Right ankle pain after twisting foot during badminton",
                clinicalNotes = "Right ankle sprain Grade I following sports activity. No fracture on AP/Lateral radiograph.",
                diagnosis = "Grade I Lateral Ankle Ligament Sprain",
                prescriptionId = "rx_hist_106",
                prescriptionSummary = "RICE protocol, Crepe bandage immobilization, Tab. Aceclofenac 100mg BD x 5 days",
                hasPrescription = true,
                followUpRecommendations = "Physiotherapy assessment in 2 weeks.",
                followUpStatus = "COMPLETED",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = false,
                version = 1
            ),
            // 7. Recent Rejected Booking Request (within 3 months: 15 days ago) — XYZ Clinic
            ConsultationRecord(
                id = "appt_hist_107",
                tokenId = "tok_hist_107",
                tokenLabel = "REJECTED",
                patientId = "pat_105",
                patientName = "Vikram Singh",
                patientIdentifier = "PAT-1005",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber A",
                consultationDate = "2026-10-09",
                consultationTime = "09:40 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (15L * DAY_MS),
                recordCategory = RecordCategory.APPOINTMENT_TOKEN,
                appointmentStatus = TokenStatus.REJECTED,
                consultationStatus = "NOT_STARTED",
                checkInStatus = "NOT_ARRIVED",
                presentingComplaint = "Follow-up prescription verification",
                clinicalNotes = null,
                diagnosis = null,
                prescriptionId = "rx_hist_107",
                prescriptionSummary = null,
                hasPrescription = false,
                followUpRecommendations = null,
                followUpStatus = "NONE",
                cancellationOrRejectionReason = "Uploaded image was blurred and lacked patient name.",
                medicalDocumentationStatus = MedicalDocumentationStatus.PRESCRIPTION_UPLOADED_ONLY,
                isArchived = false,
                version = 1
            ),
            // 8. ARCHIVED Completed Consultation (140 days / ~4.6 months ago, within 2-year window) — David Miller with Dr. John at XYZ Clinic
            ConsultationRecord(
                id = "cons_hist_archived_201",
                tokenId = "tok_hist_201",
                tokenLabel = "A-02",
                patientId = "pat_demo_patient",
                patientName = "David Miller",
                patientIdentifier = "PAT-1001",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber A",
                consultationDate = "2026-06-06",
                consultationTime = "09:30 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (140L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "COMPLETED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Annual lipid profile and blood pressure checkup",
                clinicalNotes = "Pre-monsoon health checkup and lipid profile review. LDL borderline elevated (138 mg/dL).",
                diagnosis = "Borderline Dyslipidemia",
                prescriptionId = "rx_hist_201",
                prescriptionSummary = "Dietary modification, brisk walking 30 mins/day, Tab. Rosuvastatin 5mg HS x 60 days",
                hasPrescription = true,
                followUpRecommendations = "Repeat fasting lipid profile after 3 months.",
                followUpStatus = "COMPLETED",
                allergiesOrMedicalHistory = "No known drug allergies. Borderline LDL.",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = true,
                version = 1
            ),
            // 9. ARCHIVED Completed Consultation (210 days / ~7 months ago, within 2-year window) — Dr. John at ABC Clinic, Chamber B
            ConsultationRecord(
                id = "cons_hist_archived_202",
                tokenId = "tok_hist_202",
                tokenLabel = "B-08",
                patientId = "pat_102",
                patientName = "Priya Nair",
                patientIdentifier = "PAT-1002",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_abc",
                clinicName = "ABC Clinic",
                chamber = "Chamber B",
                consultationDate = "2026-03-28",
                consultationTime = "04:15 PM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (210L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "COMPLETED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Fasting blood glucose elevation and fatigue",
                clinicalNotes = "Initial glycemic baseline assessment at ABC Clinic Chamber B.",
                diagnosis = "Newly Diagnosed Type 2 Diabetes Mellitus",
                prescriptionId = "rx_hist_202",
                prescriptionSummary = "Tab. Metformin 500mg OD x 30 days",
                hasPrescription = true,
                followUpRecommendations = "Review fasting blood glucose in 4 weeks.",
                followUpStatus = "COMPLETED",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = true,
                version = 1
            ),
            // 10. LONG-TERM RETAINED Record BEYOND 2-Year Routine Access Window (800 days / ~2.2 years ago)
            // Requirement 4.2 & 4.5: Retained in storage (never deleted), but excluded from routine 2-year
            // Doctor and Receptionist historical views unless accessed via explicit legal/compliance override.
            ConsultationRecord(
                id = "cons_hist_beyond_2y_301",
                tokenId = "tok_hist_301",
                tokenLabel = "A-01",
                patientId = "pat_demo_patient",
                patientName = "David Miller",
                patientIdentifier = "PAT-1001",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber A",
                consultationDate = "2024-08-15",
                consultationTime = "10:00 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (800L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "COMPLETED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Routine physical examination (2024)",
                clinicalNotes = "Historical 2024 consultation preserved in long-term compliance archive.",
                diagnosis = "General Health Check — Normal",
                prescriptionId = "rx_hist_301",
                prescriptionSummary = "Multivitamin OD x 15 days",
                hasPrescription = true,
                followUpRecommendations = "Annual review.",
                followUpStatus = "COMPLETED",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = true,
                version = 1
            ),
            // 11. Recent Completed Consultation — Amitabh Sen (PAT-1006, current A-01 patient) with Dr. John at XYZ Clinic, Chamber A (10 days ago)
            ConsultationRecord(
                id = "cons_hist_108",
                tokenId = "tok_hist_108",
                tokenLabel = "A-03",
                patientId = "pat_1",
                patientName = "Amitabh Sen",
                patientIdentifier = "PAT-1006",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber A",
                consultationDate = "2026-10-14",
                consultationTime = "10:05 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (10L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "FOLLOW_UP_REQUIRED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Blood pressure fluctuation and mild morning headache",
                clinicalNotes = "BP 134/84 mmHg (improved from 148/92). ECG normal sinus rhythm. Advised low-sodium diet and continued Amlodipine 5mg OD.",
                diagnosis = "Stage 1 Essential Hypertension — Follow-up",
                prescriptionId = "rx_hist_108",
                prescriptionSummary = "Tab. Amlodipine 5mg OD x 30 days, Home BP log",
                hasPrescription = true,
                followUpRecommendations = "Review home BP log in 2 weeks.",
                followUpStatus = "SCHEDULED",
                allergiesOrMedicalHistory = "Known hypertensive (2 years). No known drug allergies.",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = false,
                version = 1
            ),
            // 12. Archived Completed Consultation — Amitabh Sen (PAT-1006) with Dr. John at XYZ Clinic, Chamber A (115 days ago, within 2-year window)
            ConsultationRecord(
                id = "cons_hist_archived_203",
                tokenId = "tok_hist_203",
                tokenLabel = "A-06",
                patientId = "pat_1",
                patientName = "Amitabh Sen",
                patientIdentifier = "PAT-1006",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber A",
                consultationDate = "2026-07-01",
                consultationTime = "11:20 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (115L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "COMPLETED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Elevated home blood pressure readings and fatigue",
                clinicalNotes = "Initial hypertension workup at Chamber A. Renal function and serum electrolytes within normal limits.",
                diagnosis = "Essential Hypertension — Initial Assessment",
                prescriptionId = "rx_hist_203",
                prescriptionSummary = "Tab. Amlodipine 5mg OD x 30 days, Salt restriction",
                hasPrescription = true,
                followUpRecommendations = "Follow-up blood pressure check in 3 months.",
                followUpStatus = "COMPLETED",
                allergiesOrMedicalHistory = "No known drug allergies.",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = true,
                version = 1
            ),
            // 13. Recent Completed Consultation — Alice Smith (PAT-1007, waiting A-02 patient) with Dr. John at XYZ Clinic, Chamber A (22 days ago)
            ConsultationRecord(
                id = "cons_hist_109",
                tokenId = "tok_hist_109",
                tokenLabel = "A-05",
                patientId = "pat_2",
                patientName = "Alice Smith",
                patientIdentifier = "PAT-1007",
                doctorId = "doc_john",
                doctorName = "Dr. John",
                doctorSpecialty = "General Physician",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                chamber = "Chamber A",
                consultationDate = "2026-10-02",
                consultationTime = "11:40 AM",
                consultationEpochMs = DEFAULT_REFERENCE_EPOCH_MS - (22L * DAY_MS),
                recordCategory = RecordCategory.CONSULTATION,
                appointmentStatus = TokenStatus.COMPLETED,
                consultationStatus = "COMPLETED",
                checkInStatus = "CHECKED_IN",
                presentingComplaint = "Episodic throbbing headache with photophobia",
                clinicalNotes = "Neurological exam normal. Triggered by sleep deprivation and prolonged screen exposure. Advised hydration and sleep hygiene.",
                diagnosis = "Episodic Migraine without Aura",
                prescriptionId = "rx_hist_109",
                prescriptionSummary = "Tab. Naproxen 250mg SOS for acute migraine attack",
                hasPrescription = true,
                followUpRecommendations = "Return if headache frequency exceeds 2 episodes/week.",
                followUpStatus = "RECOMMENDED",
                allergiesOrMedicalHistory = "Penicillin allergy (mild rash).",
                medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
                isArchived = false,
                version = 1
            )
        )
    }
}

class ConsultationHistoryRepositoryImpl : ConsultationHistoryRepository {

    private val _recordsState = MutableStateFlow(ConsultationHistoryRepository.seedRecords())
    override val allHistoryRecords: Flow<List<ConsultationRecord>> = _recordsState.asStateFlow()

    override fun queryHistory(
        session: UserSession?,
        criteria: HistoryFilterCriteria,
        referenceNowEpochMs: Long
    ): FilteredHistoryResult {
        val result = HistoryFilterEngine.filterRecordsForSession(
            allRecords = _recordsState.value,
            session = session,
            criteria = criteria,
            referenceNowEpochMs = referenceNowEpochMs
        )
        if (session != null) {
            ClinicalAuditLogger.recordEvent(
                actorId = session.userId,
                actorRole = session.role,
                action = "QUERY_HISTORY",
                resourceId = "preset=${criteria.dateRangePreset.name},clinic=${criteria.clinicIdFilter},chamber=${criteria.chamberFilter}",
                clinicId = session.clinicId,
                outcome = if (result.errorMessage == null) "SUCCESS" else "DENIED",
                contextNote = "Matched ${result.totalMatchedCount} records (page ${result.page})"
            )
        }
        return result
    }

    override fun getConsultationDetail(
        recordId: String,
        session: UserSession?,
        referenceNowEpochMs: Long
    ): Resource<ConsultationRecord> {
        if (session == null) {
            return Resource.Error("Authentication required to view consultation record.")
        }
        val record = _recordsState.value.find { it.id == recordId }
            ?: return Resource.Error("Consultation record not found.")

        // Check role-based, tenant/care-relationship, and 2-year routine window authorization
        val filtered = HistoryFilterEngine.filterRecordsForSession(
            allRecords = listOf(record),
            session = session,
            criteria = HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED),
            referenceNowEpochMs = referenceNowEpochMs
        )
        val authorizedRecord = filtered.records.firstOrNull()
        if (authorizedRecord == null) {
            ClinicalAuditLogger.recordEvent(
                actorId = session.userId,
                actorRole = session.role,
                action = "VIEW_CONSULTATION_DETAIL",
                resourceId = recordId,
                clinicId = session.clinicId,
                outcome = "DENIED",
                contextNote = "Unauthorized attempt to access record $recordId outside role, clinic, or 2-year routine window."
            )
            return Resource.Error("Access restricted: You are not authorized to view this clinical record.")
        }

        ClinicalAuditLogger.recordEvent(
            actorId = session.userId,
            actorRole = session.role,
            action = "VIEW_CONSULTATION_DETAIL",
            resourceId = recordId,
            clinicId = session.clinicId,
            outcome = "SUCCESS",
            contextNote = "Authorized detail view of record $recordId (v${authorizedRecord.version})"
        )
        return Resource.Success(authorizedRecord)
    }

    override fun getPatientMedicalTimelineForDoctor(
        patientIdOrIdentifier: String,
        session: UserSession?,
        referenceNowEpochMs: Long
    ): Resource<List<ConsultationRecord>> {
        if (session == null) {
            return Resource.Error("Authentication required to view patient medical records.")
        }

        // Requirement 3.1 & 3.3: Receptionists and Clinic Admins must NOT access clinical medical records
        if (session.role == Role.RECEPTIONIST || session.role == Role.OWNER) {
            ClinicalAuditLogger.recordEvent(
                actorId = session.userId,
                actorRole = session.role,
                action = "VIEW_PATIENT_MEDICAL_TIMELINE",
                resourceId = patientIdOrIdentifier,
                clinicId = session.clinicId,
                outcome = "DENIED",
                contextNote = "Role ${session.role} is prohibited from accessing patient clinical medical records."
            )
            return Resource.Error("Access denied: Your role (${session.role}) is not authorized to access patient medical records.")
        }

        val cleanTarget = patientIdOrIdentifier.trim()
        val patientRecords = _recordsState.value.filter {
            it.patientId.equals(cleanTarget, ignoreCase = true) ||
                it.patientIdentifier.equals(cleanTarget, ignoreCase = true)
        }

        if (patientRecords.isEmpty()) {
            val authorizedEmptyProfile = PatientIdentityRegistry.getAuthorizedPatientProfile(cleanTarget, session)
            if (authorizedEmptyProfile != null) {
                ClinicalAuditLogger.recordEvent(
                    actorId = session.userId,
                    actorRole = session.role,
                    action = "VIEW_PATIENT_MEDICAL_TIMELINE",
                    resourceId = cleanTarget,
                    clinicId = session.clinicId,
                    outcome = "SUCCESS",
                    contextNote = "Authorized timeline check for $cleanTarget (0 prior completed consultations)."
                )
                return Resource.Success(emptyList())
            }
            return Resource.Error("No medical records found for patient $cleanTarget.")
        }

        if (session.role == Role.PATIENT) {
            val ownsRecord = patientRecords.all { it.patientId == session.userId }
            if (!ownsRecord) {
                ClinicalAuditLogger.recordEvent(
                    actorId = session.userId,
                    actorRole = session.role,
                    action = "VIEW_PATIENT_MEDICAL_TIMELINE",
                    resourceId = cleanTarget,
                    clinicId = session.clinicId,
                    outcome = "DENIED",
                    contextNote = "Patient attempted to access another patient's medical timeline."
                )
                return Resource.Error("Access denied: You can only view your own medical records.")
            }
            return Resource.Success(patientRecords.sortedByDescending { it.consultationEpochMs })
        }

        // Role.DOCTOR: Verify legitimate care relationship (doctor has treated or is assigned to this patient)
        val docId = session.doctorId ?: "doc_john"
        val effectiveDocIds = if (docId == "doc_1" || docId == "doc_john") {
            setOf("doc_1", "doc_john")
        } else {
            setOf(docId)
        }

        val targetPatientId = patientRecords.first().patientId
        val authorizedProfile = PatientIdentityRegistry.getAuthorizedPatientProfile(targetPatientId, session)
        val hasDirectConsultation = patientRecords.any { it.doctorId in effectiveDocIds }

        if (authorizedProfile == null && !hasDirectConsultation) {
            ClinicalAuditLogger.recordEvent(
                actorId = session.userId,
                actorRole = session.role,
                action = "VIEW_PATIENT_MEDICAL_TIMELINE",
                resourceId = cleanTarget,
                clinicId = session.clinicId,
                outcome = "DENIED",
                contextNote = "Doctor $docId attempted to view medical history of unrelated patient $cleanTarget."
            )
            return Resource.Error("Access denied: No established care relationship with patient $cleanTarget.")
        }

        // Filter to records within the 2-year historical access window where the doctor is authorized
        // (treating doctor or within the doctor's authorized clinic care context for this patient)
        val allowedClinicsForDoctor = when {
            "doc_john" in effectiveDocIds -> setOf("clinic_xyz", "clinic_abc", "clinic_1")
            session.clinicId != null -> setOf(session.clinicId)
            else -> setOf("clinic_xyz")
        }

        val timeline = patientRecords
            .filter { rec ->
                rec.recordCategory == RecordCategory.CONSULTATION &&
                    DataRetentionPolicyEngine.isEligibleForTwoYearHistoricalAccess(rec) &&
                    (rec.doctorId in effectiveDocIds || rec.clinicId in allowedClinicsForDoctor) &&
                    DataRetentionPolicyEngine.isWithinTwoYearHistoricalWindow(rec.consultationEpochMs, referenceNowEpochMs)
            }
            .map { rec ->
                // Redact patient phone number by default for Doctor role (Requirement 3.2)
                HistoryFilterEngine.applyRoleFieldRedaction(rec, session, includeAuthorizedDoctorContact = false)
            }
            .sortedByDescending { it.consultationEpochMs }

        ClinicalAuditLogger.recordEvent(
            actorId = session.userId,
            actorRole = session.role,
            action = "VIEW_PATIENT_MEDICAL_TIMELINE",
            resourceId = cleanTarget,
            clinicId = session.clinicId,
            outcome = "SUCCESS",
            contextNote = "Doctor $docId retrieved ${timeline.size} medical timeline records for $cleanTarget."
        )

        return Resource.Success(timeline)
    }

    override fun addClinicalAddendum(
        recordId: String,
        addendumText: String,
        session: UserSession?
    ): Resource<ConsultationRecord> {
        if (session == null) {
            return Resource.Error("Authentication required.")
        }
        val trimmed = addendumText.trim()
        if (trimmed.isBlank()) {
            return Resource.Error("Addendum note cannot be empty.")
        }
        if (session.role != Role.DOCTOR) {
            ClinicalAuditLogger.recordEvent(
                actorId = session.userId,
                actorRole = session.role,
                action = "ADD_CLINICAL_ADDENDUM",
                resourceId = recordId,
                clinicId = session.clinicId,
                outcome = "DENIED",
                contextNote = "Non-doctor role (${session.role}) attempted to modify clinical record."
            )
            return Resource.Error("Only the treating doctor is authorized to add a clinical addendum.")
        }

        val currentList = _recordsState.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == recordId }
        if (index == -1) {
            return Resource.Error("Consultation record not found.")
        }

        val existing = currentList[index]
        val allowedDocIds = if (session.doctorId == "doc_1" || session.doctorId == "doc_john" || session.doctorId == null) {
            setOf("doc_1", "doc_john")
        } else {
            setOf(session.doctorId)
        }
        if (existing.doctorId !in allowedDocIds) {
            ClinicalAuditLogger.recordEvent(
                actorId = session.userId,
                actorRole = session.role,
                action = "ADD_CLINICAL_ADDENDUM",
                resourceId = recordId,
                clinicId = session.clinicId,
                outcome = "DENIED",
                contextNote = "Doctor ${session.userId} attempted to edit consultation owned by ${existing.doctorId}."
            )
            return Resource.Error("Access restricted: Only the original treating doctor may amend this consultation.")
        }

        val newVersion = existing.version + 1
        val preservedNotes = if (existing.clinicalNotes.isNullOrBlank()) {
            "[Addendum v$newVersion]: $trimmed"
        } else {
            "${existing.clinicalNotes}\n[Addendum v$newVersion]: $trimmed"
        }

        val updated = existing.copy(
            clinicalNotes = preservedNotes,
            version = newVersion,
            lastUpdatedNote = "Amended by ${session.displayName} (v$newVersion)"
        )
        currentList[index] = updated
        _recordsState.value = currentList

        ClinicalAuditLogger.recordEvent(
            actorId = session.userId,
            actorRole = session.role,
            action = "ADD_CLINICAL_ADDENDUM",
            resourceId = recordId,
            clinicId = existing.clinicId,
            outcome = "SUCCESS",
            contextNote = "Appended clinical addendum; incremented version from ${existing.version} to $newVersion."
        )
        return Resource.Success(updated)
    }

    override fun recordCompletedConsultationFromToken(
        token: QueueToken,
        clinicId: String,
        clinicName: String,
        doctorName: String,
        doctorSpecialty: String,
        clinicalNotes: String?,
        diagnosis: String?,
        followUpRecommendations: String?
    ): ConsultationRecord {
        val currentList = _recordsState.value.toMutableList()
        val existingIdx = currentList.indexOfFirst { it.tokenId == token.id }
        val resolvedPatientId = token.patientId ?: "pat_demo_patient"
        val resolvedIdentifier = token.patientIdentifier
            ?: PatientIdentityRegistry.getIdentifierForPatientId(resolvedPatientId)

        val newRecord = ConsultationRecord(
            id = if (existingIdx != -1) currentList[existingIdx].id else "cons_${token.id}",
            tokenId = token.id,
            tokenLabel = token.label,
            patientId = resolvedPatientId,
            patientName = token.displayName ?: "Patient",
            patientIdentifier = resolvedIdentifier,
            doctorId = token.doctorId ?: "doc_john",
            doctorName = doctorName,
            doctorSpecialty = doctorSpecialty,
            clinicId = clinicId,
            clinicName = clinicName,
            chamber = token.chamber ?: "Chamber A",
            consultationDate = "2026-10-24",
            consultationTime = token.calledAt ?: "10:30 AM",
            consultationEpochMs = ConsultationHistoryRepository.DEFAULT_REFERENCE_EPOCH_MS,
            recordCategory = RecordCategory.CONSULTATION,
            appointmentStatus = TokenStatus.COMPLETED,
            consultationStatus = if (!followUpRecommendations.isNullOrBlank()) "FOLLOW_UP_REQUIRED" else "COMPLETED",
            checkInStatus = "CHECKED_IN",
            presentingComplaint = token.presentingComplaint,
            clinicalNotes = clinicalNotes,
            diagnosis = diagnosis,
            prescriptionId = token.prescriptionId,
            prescriptionSummary = if (token.prescriptionId != null) "Verified Prescription (${token.prescriptionId})" else null,
            hasPrescription = token.prescriptionId != null,
            followUpRecommendations = followUpRecommendations,
            followUpStatus = if (!followUpRecommendations.isNullOrBlank()) "RECOMMENDED" else "NONE",
            medicalDocumentationStatus = MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD,
            isArchived = false,
            version = 1
        )
        if (existingIdx != -1) {
            currentList[existingIdx] = newRecord
        } else {
            currentList.add(0, newRecord)
        }
        _recordsState.value = currentList
        return newRecord
    }

    override fun getClinicOperationalSummary(
        session: UserSession?,
        criteria: HistoryFilterCriteria,
        referenceNowEpochMs: Long
    ): ClinicOperationalSummary {
        val wideCriteria = criteria.copy(page = 1, pageSize = 100)
        val filtered = HistoryFilterEngine.filterRecordsForSession(
            allRecords = _recordsState.value,
            session = session,
            criteria = wideCriteria,
            referenceNowEpochMs = referenceNowEpochMs
        )
        return HistoryFilterEngine.computeClinicOperationalSummary(filtered.records)
    }

    override fun resetToSeedDataForTesting() {
        PatientIdentityRegistry.resetToSeedPatients()
        _recordsState.value = ConsultationHistoryRepository.seedRecords()
        ClinicalAuditLogger.clearForTest()
        MedicalRecordRequestTracker.clearForTest()
    }
}

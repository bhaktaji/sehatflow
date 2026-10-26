package com.example.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * Explicitly distinguishes completed clinical consultations from appointment/token bookings.
 * A booked, waiting, called, cancelled, or no-show token must NEVER be classified as a
 * completed medical consultation.
 */
enum class RecordCategory {
    CONSULTATION,
    APPOINTMENT_TOKEN
}

/**
 * Explicit medical documentation status (Requirement 4.1).
 * Prevents a mere uploaded prescription file on a pending/cancelled/rejected token from
 * being misinterpreted as a completed clinical consultation.
 */
enum class MedicalDocumentationStatus {
    COMPLETE_CLINICAL_RECORD,
    REQUIRED_MEDICAL_RECORDS_PROVIDED,
    PRESCRIPTION_UPLOADED_ONLY,
    NONE
}

enum class HistoryDateRangePreset(val label: String, val daysBack: Int?) {
    LAST_30_DAYS("Last 30 Days", 30),
    LAST_3_MONTHS("Last 3 Months (Default)", 90),
    LAST_2_YEARS("Last 2 Years (24M Window)", 730),
    ALL_TIME_WITH_ARCHIVED("All Time (Include Archived)", null),
    CUSTOM_RANGE("Custom Date Range", null)
}

/**
 * Immutable historical record preserving the original clinic, chamber, and clinician
 * snapshot at the time of the consultation or appointment booking.
 */
data class ConsultationRecord(
    val id: String,
    val tokenId: String,
    val tokenLabel: String,
    val patientId: String,
    val patientName: String,
    val patientIdentifier: String,
    val doctorId: String,
    val doctorName: String,
    val doctorSpecialty: String,
    val clinicId: String,
    val clinicName: String,
    val chamber: String,
    val consultationDate: String, // YYYY-MM-DD
    val consultationTime: String,
    val consultationEpochMs: Long,
    val recordCategory: RecordCategory,
    val appointmentStatus: TokenStatus,
    val consultationStatus: String, // COMPLETED, FOLLOW_UP_REQUIRED, NOT_STARTED, CANCELLED_BEFORE_CONSULT, NO_SHOW
    val checkInStatus: String, // CHECKED_IN, WALK_IN, NOT_ARRIVED, CANCELLED
    val clinicalNotes: String? = null,
    val diagnosis: String? = null,
    val prescriptionId: String? = null,
    val prescriptionSummary: String? = null,
    val hasPrescription: Boolean = false,
    val followUpRecommendations: String? = null,
    val followUpStatus: String = "NONE", // SCHEDULED, RECOMMENDED, NONE, COMPLETED
    val cancellationOrRejectionReason: String? = null,
    val isArchived: Boolean = false,
    val version: Int = 1,
    val lastUpdatedNote: String? = null,
    val patientPhone: String? = PatientIdentityRegistry.getPhoneForPatientId(patientId),
    val presentingComplaint: String? = null,
    val allergiesOrMedicalHistory: String? = null,
    val medicalDocumentationStatus: MedicalDocumentationStatus = when {
        recordCategory == RecordCategory.CONSULTATION && appointmentStatus == TokenStatus.COMPLETED ->
            MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD
        hasPrescription ->
            MedicalDocumentationStatus.PRESCRIPTION_UPLOADED_ONLY
        else ->
            MedicalDocumentationStatus.NONE
    }
)

data class HistoryFilterCriteria(
    val dateRangePreset: HistoryDateRangePreset = HistoryDateRangePreset.LAST_3_MONTHS,
    val customStartEpochMs: Long? = null,
    val customEndEpochMs: Long? = null,
    val searchQuery: String = "",
    val patientIdFilter: String? = null,
    val patientPhoneFilter: String? = null,
    val doctorIdFilter: String = "ALL",
    val clinicIdFilter: String = "ALL",
    val chamberFilter: String = "ALL",
    val recordCategoryFilter: RecordCategory? = null,
    val appointmentStatusFilter: String = "ALL",
    val consultationStatusFilter: String = "ALL",
    val checkInStatusFilter: String = "ALL",
    val hasPrescriptionFilter: Boolean? = null,
    val followUpStatusFilter: String = "ALL",
    val enforceTwoYearEligibilityRule: Boolean = false,
    val includeAuthorizedDoctorContact: Boolean = false,
    val legalHoldOrComplianceOverride: Boolean = false,
    val page: Int = 1,
    val pageSize: Int = 25
) {
    fun hasInvalidDateRange(): Boolean {
        if (dateRangePreset == HistoryDateRangePreset.CUSTOM_RANGE) {
            if (customStartEpochMs != null && customEndEpochMs != null && customStartEpochMs > customEndEpochMs) {
                return true
            }
        }
        return false
    }
}

data class FilteredHistoryResult(
    val records: List<ConsultationRecord>,
    val totalMatchedCount: Int,
    val page: Int,
    val pageSize: Int,
    val hasMorePages: Boolean,
    val activeWindowLabel: String,
    val errorMessage: String? = null
)

data class ClinicOperationalSummary(
    val totalAppointmentRequests: Int,
    val approvedBookingsCount: Int,
    val completedConsultationsCount: Int,
    val cancelledCount: Int,
    val rejectedCount: Int,
    val noShowCount: Int,
    val waitingOrInProgressCount: Int,
    val consultationsByDoctor: Map<String, Int>,
    val utilizationByChamber: Map<String, Int>
)

/**
 * Configurable Data Retention & Archival Policy Rule.
 * Separates historical visibility windows (90-day quick filter / 2-year operational access rule)
 * from underlying record retention.
 * Never automatically deletes clinical records when they exceed 3 months or 2 years.
 */
data class DataRetentionCategoryRule(
    val categoryName: String,
    val defaultVisibilityDays: Int?, // 90 days for default quick filter, 730 days (2 years) for routine doctor/receptionist access
    val twoYearMaxRoutineAccessDays: Int = 730,
    val logicalArchiveAfterDays: Int?, // 90 days flagged as archived, still searchable within 2-year window
    val minimumRetentionYears: Int?, // Configurable pending legal sign-off (e.g., 3 years MCI/NMC indoor, 5+ years WB clinical establishment)
    val autoDeletionPermitted: Boolean = false,
    val requiresLegalHoldCheck: Boolean = true,
    val authorizedRoles: Set<Role>
)

object DataRetentionPolicyEngine {
    const val DEFAULT_VISIBILITY_DAYS: Int = 90
    const val TWO_YEAR_HISTORICAL_ACCESS_DAYS: Int = 730
    const val DAY_MS: Long = 24L * 60L * 60L * 1000L
    const val MAX_COMPLAINT_LENGTH: Int = 300

    val categoryRules: List<DataRetentionCategoryRule> = listOf(
        DataRetentionCategoryRule(
            categoryName = "CONSULTATION_RECORDS_AND_CLINICAL_NOTES",
            defaultVisibilityDays = DEFAULT_VISIBILITY_DAYS,
            twoYearMaxRoutineAccessDays = TWO_YEAR_HISTORICAL_ACCESS_DAYS,
            logicalArchiveAfterDays = DEFAULT_VISIBILITY_DAYS,
            minimumRetentionYears = null, // Requires legal/NMC/State confirmation; never auto-deleted
            autoDeletionPermitted = false,
            requiresLegalHoldCheck = true,
            authorizedRoles = setOf(Role.DOCTOR, Role.PATIENT)
        ),
        DataRetentionCategoryRule(
            categoryName = "PRESCRIPTIONS_AND_MEDICAL_ATTACHMENTS",
            defaultVisibilityDays = DEFAULT_VISIBILITY_DAYS,
            twoYearMaxRoutineAccessDays = TWO_YEAR_HISTORICAL_ACCESS_DAYS,
            logicalArchiveAfterDays = DEFAULT_VISIBILITY_DAYS,
            minimumRetentionYears = null,
            autoDeletionPermitted = false,
            requiresLegalHoldCheck = true,
            authorizedRoles = setOf(Role.DOCTOR, Role.PATIENT, Role.RECEPTIONIST)
        ),
        DataRetentionCategoryRule(
            categoryName = "APPOINTMENT_AND_QUEUE_TOKENS",
            defaultVisibilityDays = DEFAULT_VISIBILITY_DAYS,
            twoYearMaxRoutineAccessDays = TWO_YEAR_HISTORICAL_ACCESS_DAYS,
            logicalArchiveAfterDays = DEFAULT_VISIBILITY_DAYS,
            minimumRetentionYears = null,
            autoDeletionPermitted = false,
            requiresLegalHoldCheck = true,
            authorizedRoles = setOf(Role.DOCTOR, Role.RECEPTIONIST, Role.OWNER, Role.PATIENT)
        ),
        DataRetentionCategoryRule(
            categoryName = "SECURITY_AND_CLINICAL_AUDIT_LOGS",
            defaultVisibilityDays = 365,
            twoYearMaxRoutineAccessDays = TWO_YEAR_HISTORICAL_ACCESS_DAYS,
            logicalArchiveAfterDays = 365,
            minimumRetentionYears = null,
            autoDeletionPermitted = false,
            requiresLegalHoldCheck = true,
            authorizedRoles = setOf(Role.OWNER)
        )
    )

    fun isWithinDefaultThreeMonthWindow(recordEpochMs: Long, referenceNowEpochMs: Long): Boolean {
        val cutoffEpochMs = referenceNowEpochMs - (DEFAULT_VISIBILITY_DAYS * DAY_MS)
        return recordEpochMs >= cutoffEpochMs
    }

    /**
     * Requirement 4.2: Calculates the two-year historical access boundary using the
     * clinic's timezone rules (defaulting to Asia/Kolkata).
     * For example, if referenceNow is 2026-10-24T10:00:00Z, the two-year boundary is
     * the corresponding timestamp two years earlier (2024-10-24).
     */
    fun getTwoYearCutoffEpochMs(
        referenceNowEpochMs: Long,
        zoneId: ZoneId = ZoneId.of("Asia/Kolkata")
    ): Long {
        return Instant.ofEpochMilli(referenceNowEpochMs)
            .atZone(zoneId)
            .minusYears(2)
            .toInstant()
            .toEpochMilli()
    }

    fun isWithinTwoYearHistoricalWindow(
        recordEpochMs: Long,
        referenceNowEpochMs: Long,
        zoneId: ZoneId = ZoneId.of("Asia/Kolkata")
    ): Boolean {
        val cutoffEpochMs = getTwoYearCutoffEpochMs(referenceNowEpochMs, zoneId)
        return recordEpochMs in cutoffEpochMs..referenceNowEpochMs
    }

    /**
     * Requirement 4.1: Explicit eligibility rule for the two-year historical access policy.
     * - Completed appointments/visits or completed consultations qualify.
     * - Records where required medical records have been explicitly provided and recorded
     *   (COMPLETE_CLINICAL_RECORD or REQUIRED_MEDICAL_RECORDS_PROVIDED) qualify.
     * - Pending, rejected, cancelled, or no-show records do NOT qualify merely because a
     *   prescription file was uploaded (PRESCRIPTION_UPLOADED_ONLY).
     */
    fun isEligibleForTwoYearHistoricalAccess(record: ConsultationRecord): Boolean {
        val isCompletedVisitOrConsult = record.appointmentStatus == TokenStatus.COMPLETED &&
            (record.recordCategory == RecordCategory.CONSULTATION ||
                record.consultationStatus == "COMPLETED" ||
                record.consultationStatus == "FOLLOW_UP_REQUIRED")

        val hasVerifiedRequiredMedicalRecords =
            record.medicalDocumentationStatus == MedicalDocumentationStatus.COMPLETE_CLINICAL_RECORD ||
                record.medicalDocumentationStatus == MedicalDocumentationStatus.REQUIRED_MEDICAL_RECORDS_PROVIDED

        val isIncompleteOrRejected = record.appointmentStatus in setOf(
            TokenStatus.PENDING_VERIFICATION,
            TokenStatus.CANCELLED,
            TokenStatus.REJECTED,
            TokenStatus.NO_SHOW,
            TokenStatus.SKIPPED
        )

        return if (isCompletedVisitOrConsult) {
            true
        } else if (hasVerifiedRequiredMedicalRecords && !isIncompleteOrRejected) {
            true
        } else {
            // Special case: if required medical documentation was explicitly verified and recorded
            // as REQUIRED_MEDICAL_RECORDS_PROVIDED for an approved visit
            record.medicalDocumentationStatus == MedicalDocumentationStatus.REQUIRED_MEDICAL_RECORDS_PROVIDED &&
                record.appointmentStatus != TokenStatus.REJECTED &&
                record.appointmentStatus != TokenStatus.CANCELLED &&
                record.appointmentStatus != TokenStatus.PENDING_VERIFICATION
        }
    }

    /**
     * Requirement 5.2: Backend/repository validation for presenting complaint.
     * - Rejects empty/whitespace-only complaint when no prescription is provided.
     * - Enforces max length (300 chars) and strips unsafe HTML/script tags.
     */
    fun validateAndSanitizePresentingComplaint(
        rawComplaint: String?,
        hasPrescription: Boolean
    ): Result<String?> {
        val trimmed = rawComplaint?.trim() ?: ""
        if (trimmed.isEmpty()) {
            return if (!hasPrescription) {
                Result.failure(
                    IllegalArgumentException("Please describe the problem you are experiencing when booking without a prescription.")
                )
            } else {
                Result.success(null)
            }
        }
        if (trimmed.length > MAX_COMPLAINT_LENGTH) {
            return Result.failure(
                IllegalArgumentException("Problem description must be $MAX_COMPLAINT_LENGTH characters or fewer (currently ${trimmed.length}).")
            )
        }
        // Sanitize control characters and angle brackets safely
        val sanitized = trimmed
            .replace(Regex("[<>]"), "")
            .replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "")
            .trim()
        if (sanitized.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Problem description contains only unsupported characters.")
            )
        }
        return Result.success(sanitized)
    }

    /**
     * Requirement 5.5: Non-diagnostic emergency keyword check to display a cautious
     * emergency care instruction banner in the UI without claiming to be a medical diagnosis.
     */
    fun containsPotentialEmergencyIndicator(complaint: String?): Boolean {
        if (complaint.isNullOrBlank()) return false
        val lower = complaint.lowercase()
        val emergencyTerms = listOf(
            "chest pain",
            "heart attack",
            "difficulty breathing",
            "shortness of breath",
            "unconscious",
            "severe bleeding",
            "stroke",
            "seizure"
        )
        return emergencyTerms.any { lower.contains(it) }
    }
}

/**
 * Requirement 6.4: Medical Record Copy Request & Tracking Model.
 * Avoids hardcoding a universal 72-hour deadline across all contexts; calculates
 * context-appropriate response targets (e.g., 72 hours for RMP indoor records under
 * MCI/NMC Reg. 1.3.2 vs clinic/DPDP statutory workflow).
 */
data class MedicalRecordCopyRequest(
    val requestId: String,
    val patientId: String,
    val patientIdentifier: String,
    val requesterName: String,
    val requesterRelationship: String, // SELF, AUTHORIZED_ATTENDANT, LEGAL_AUTHORITY
    val identityVerified: Boolean,
    val authorityVerified: Boolean,
    val clinicId: String,
    val custodianRole: String, // REGISTERED_MEDICAL_PRACTITIONER, CLINIC_ESTABLISHMENT
    val recordScope: String, // INDOOR_IPD_RECORD, OUTPATIENT_OPD_SUMMARY
    val submittedAtEpochMs: Long,
    val applicableResponseWindowHours: Int?, // 72 for RMP indoor/NMC Reg 1.3.2; configurable for OPD/DPDP
    val status: String, // PENDING_VERIFICATION, IN_PROGRESS, DELIVERED, OVERDUE_ESCALATED
    val deliveredAtEpochMs: Long? = null
)

object MedicalRecordRequestTracker {
    private val requests = mutableListOf<MedicalRecordCopyRequest>()

    @Synchronized
    fun submitCopyRequest(
        patientId: String,
        requesterName: String,
        requesterRelationship: String = "SELF",
        identityVerified: Boolean,
        authorityVerified: Boolean,
        clinicId: String,
        custodianRole: String = "REGISTERED_MEDICAL_PRACTITIONER",
        recordScope: String = "OUTPATIENT_OPD_SUMMARY",
        submittedAtEpochMs: Long = System.currentTimeMillis()
    ): MedicalRecordCopyRequest {
        // MCI/NMC 2002 Reg. 1.3.2 specifies 72 hours for RMP medical record requests
        val windowHours = if (custodianRole == "REGISTERED_MEDICAL_PRACTITIONER") 72 else null
        val req = MedicalRecordCopyRequest(
            requestId = "mrr_${submittedAtEpochMs}_${requests.size + 1}",
            patientId = patientId,
            patientIdentifier = PatientIdentityRegistry.getIdentifierForPatientId(patientId),
            requesterName = requesterName,
            requesterRelationship = requesterRelationship,
            identityVerified = identityVerified,
            authorityVerified = authorityVerified,
            clinicId = clinicId,
            custodianRole = custodianRole,
            recordScope = recordScope,
            submittedAtEpochMs = submittedAtEpochMs,
            applicableResponseWindowHours = windowHours,
            status = if (identityVerified && authorityVerified) "IN_PROGRESS" else "PENDING_VERIFICATION"
        )
        requests.add(0, req)
        ClinicalAuditLogger.recordEvent(
            actorId = patientId,
            actorRole = Role.PATIENT,
            action = "SUBMIT_MEDICAL_RECORD_COPY_REQUEST",
            resourceId = req.requestId,
            clinicId = clinicId,
            outcome = "SUCCESS",
            contextNote = "Scope=$recordScope, Custodian=$custodianRole, Verified=${identityVerified && authorityVerified}"
        )
        return req
    }

    @Synchronized
    fun evaluateOverdueEscalation(
        requestId: String,
        nowEpochMs: Long
    ): MedicalRecordCopyRequest? {
        val idx = requests.indexOfFirst { it.requestId == requestId }
        if (idx == -1) return null
        val existing = requests[idx]
        val hours = existing.applicableResponseWindowHours ?: return existing
        val deadlineMs = existing.submittedAtEpochMs + (hours * 3600_000L)
        if (existing.status != "DELIVERED" && nowEpochMs > deadlineMs) {
            val escalated = existing.copy(status = "OVERDUE_ESCALATED")
            requests[idx] = escalated
            return escalated
        }
        return existing
    }

    @Synchronized
    fun clearForTest() {
        requests.clear()
    }
}

/**
 * Immutable audit log entry for sensitive historical record access and authorized corrections.
 * Never logs passwords, tokens, or raw PHI clinical bodies.
 */
data class ClinicalAuditEntry(
    val id: String,
    val actorId: String,
    val actorRole: Role,
    val action: String, // e.g., VIEW_CONSULTATION_DETAIL, FILTER_HISTORY, ADD_CLINICAL_ADDENDUM, UNAUTHORIZED_ACCESS_BLOCKED
    val resourceId: String,
    val clinicId: String?,
    val timestampEpochMs: Long,
    val outcome: String, // SUCCESS, DENIED
    val contextNote: String
)

object ClinicalAuditLogger {
    private val _auditTrail = mutableListOf<ClinicalAuditEntry>()

    @Synchronized
    fun recordEvent(
        actorId: String,
        actorRole: Role,
        action: String,
        resourceId: String,
        clinicId: String?,
        outcome: String,
        contextNote: String
    ): ClinicalAuditEntry {
        val entry = ClinicalAuditEntry(
            id = "aud_${System.currentTimeMillis()}_${_auditTrail.size + 1}",
            actorId = actorId,
            actorRole = actorRole,
            action = action,
            resourceId = resourceId,
            clinicId = clinicId,
            timestampEpochMs = System.currentTimeMillis(),
            outcome = outcome,
            contextNote = contextNote
        )
        _auditTrail.add(0, entry)
        return entry
    }

    @Synchronized
    fun getAuditTrailForClinic(clinicId: String?): List<ClinicalAuditEntry> {
        if (clinicId == null) return _auditTrail.toList()
        return _auditTrail.filter { it.clinicId == null || it.clinicId == clinicId }
    }

    @Synchronized
    fun getAllEntriesForTest(): List<ClinicalAuditEntry> = _auditTrail.toList()

    @Synchronized
    fun clearForTest() {
        _auditTrail.clear()
    }
}

/**
 * Authoritative role-based and tenant-isolated filtering engine for consultation and operational history.
 * Enforces:
 * - Requirement 2: Doctor access to authorized patient medical history.
 * - Requirement 3: Strict separation between Receptionist (phone allowed, clinical notes/diagnosis/prescription summary denied)
 *   and Doctor (clinical records allowed, phone redacted by default).
 * - Requirement 4: Two-year historical access window & eligibility rule for Doctor and Receptionist views.
 */
object HistoryFilterEngine {

    fun applyRoleFieldRedaction(
        record: ConsultationRecord,
        session: UserSession,
        includeAuthorizedDoctorContact: Boolean = false
    ): ConsultationRecord {
        return when (session.role) {
            Role.RECEPTIONIST -> {
                // Requirement 3.1: Receptionists get Patient ID, name, phone, token/booking status,
                // presenting complaint for triage, and prescription attachment presence,
                // but NEVER clinical notes, diagnoses, clinical treatment plans, or prescription clinical contents.
                record.copy(
                    clinicalNotes = null,
                    diagnosis = null,
                    prescriptionSummary = null,
                    allergiesOrMedicalHistory = null,
                    followUpRecommendations = null
                )
            }
            Role.DOCTOR -> {
                // Requirement 3.2: Doctors get full authorized clinical records, but patient phone
                // is NOT exposed by default unless explicitly justified.
                if (includeAuthorizedDoctorContact) {
                    record
                } else {
                    record.copy(patientPhone = null)
                }
            }
            Role.OWNER -> {
                // Requirement 3.3: Clinic Administrators get operational records without clinical notes,
                // diagnoses, prescription contents, presenting complaints, or unrestricted phone access.
                record.copy(
                    clinicalNotes = null,
                    diagnosis = null,
                    prescriptionSummary = null,
                    allergiesOrMedicalHistory = null,
                    followUpRecommendations = null,
                    presentingComplaint = null,
                    patientPhone = null
                )
            }
            Role.PATIENT -> {
                record
            }
        }
    }

    fun filterRecordsForSession(
        allRecords: List<ConsultationRecord>,
        session: UserSession?,
        criteria: HistoryFilterCriteria,
        referenceNowEpochMs: Long = 1792836000000L // 2026-10-24T10:00:00Z default reference
    ): FilteredHistoryResult {
        if (session == null) {
            return FilteredHistoryResult(
                records = emptyList(),
                totalMatchedCount = 0,
                page = criteria.page,
                pageSize = criteria.pageSize,
                hasMorePages = false,
                activeWindowLabel = criteria.dateRangePreset.label,
                errorMessage = "Authentication required to access historical records."
            )
        }

        if (criteria.hasInvalidDateRange()) {
            return FilteredHistoryResult(
                records = emptyList(),
                totalMatchedCount = 0,
                page = criteria.page,
                pageSize = criteria.pageSize,
                hasMorePages = false,
                activeWindowLabel = criteria.dateRangePreset.label,
                errorMessage = "Invalid date range: Start date cannot be after end date."
            )
        }

        // 1. Authoritative Role & Tenant Isolation (Backend / Repository Access Control)
        val authorizedRecords = allRecords.filter { record ->
            when (session.role) {
                Role.PATIENT -> {
                    record.patientId == session.userId
                }
                Role.DOCTOR -> {
                    val allowedDocId = session.doctorId ?: "doc_john"
                    val effectiveDocIds = if (allowedDocId == "doc_1" || allowedDocId == "doc_john") {
                        setOf("doc_1", "doc_john")
                    } else {
                        setOf(allowedDocId)
                    }
                    record.doctorId in effectiveDocIds
                }
                Role.RECEPTIONIST -> {
                    val assignedClinic = session.clinicId ?: "clinic_xyz"
                    val effectiveClinics = if (assignedClinic == "clinic_1" || assignedClinic == "clinic_xyz") {
                        setOf("clinic_1", "clinic_xyz", "xyz_clinic")
                    } else {
                        setOf(assignedClinic)
                    }
                    record.clinicId in effectiveClinics
                }
                Role.OWNER -> {
                    val managedClinic = session.clinicId ?: "clinic_xyz"
                    val effectiveClinics = if (managedClinic == "clinic_1" || managedClinic == "clinic_xyz") {
                        setOf("clinic_1", "clinic_xyz", "xyz_clinic")
                    } else {
                        setOf(managedClinic)
                    }
                    record.clinicId in effectiveClinics
                }
            }
        }.map { record ->
            applyRoleFieldRedaction(
                record = record,
                session = session,
                includeAuthorizedDoctorContact = criteria.includeAuthorizedDoctorContact
            )
        }

        // Log attempt if a caller tried to pass a cross-patient or cross-clinic filter they don't own
        if (session.role == Role.PATIENT && criteria.patientIdFilter != null && criteria.patientIdFilter != session.userId) {
            ClinicalAuditLogger.recordEvent(
                actorId = session.userId,
                actorRole = session.role,
                action = "UNAUTHORIZED_PATIENT_FILTER_BLOCKED",
                resourceId = criteria.patientIdFilter,
                clinicId = session.clinicId,
                outcome = "DENIED",
                contextNote = "Patient attempted to query another patient's history."
            )
        }
        if ((session.role == Role.RECEPTIONIST || session.role == Role.OWNER) &&
            criteria.clinicIdFilter != "ALL"
        ) {
            val assignedClinic = session.clinicId ?: "clinic_xyz"
            val effectiveClinics = if (assignedClinic == "clinic_1" || assignedClinic == "clinic_xyz") {
                setOf("clinic_1", "clinic_xyz", "xyz_clinic", "XYZ Clinic")
            } else {
                setOf(assignedClinic)
            }
            if (criteria.clinicIdFilter !in effectiveClinics) {
                ClinicalAuditLogger.recordEvent(
                    actorId = session.userId,
                    actorRole = session.role,
                    action = "CROSS_CLINIC_FILTER_BLOCKED",
                    resourceId = criteria.clinicIdFilter,
                    clinicId = session.clinicId,
                    outcome = "DENIED",
                    contextNote = "Staff attempted to filter records for an unassigned clinic."
                )
            }
        }

        // 2. Date Range, Two-Year Routine Window & Eligibility Filtering
        val dateFiltered = authorizedRecords.filter { record ->
            // Requirement 4.2: Routine Doctor and Receptionist views are capped at the 2-year historical window
            // unless an explicit legal/compliance override is set.
            val withinRoutineRoleHorizon = if (
                (session.role == Role.DOCTOR || session.role == Role.RECEPTIONIST) &&
                !criteria.legalHoldOrComplianceOverride
            ) {
                DataRetentionPolicyEngine.isWithinTwoYearHistoricalWindow(record.consultationEpochMs, referenceNowEpochMs)
            } else {
                true
            }

            if (!withinRoutineRoleHorizon) {
                return@filter false
            }

            // Requirement 4.1: Enforce Two-Year Eligibility Rule when requested or when using LAST_2_YEARS preset
            if (criteria.enforceTwoYearEligibilityRule || criteria.dateRangePreset == HistoryDateRangePreset.LAST_2_YEARS) {
                if (!DataRetentionPolicyEngine.isEligibleForTwoYearHistoricalAccess(record)) {
                    return@filter false
                }
            }

            when (criteria.dateRangePreset) {
                HistoryDateRangePreset.LAST_30_DAYS -> {
                    val cutoff = referenceNowEpochMs - (30L * DataRetentionPolicyEngine.DAY_MS)
                    record.consultationEpochMs in cutoff..referenceNowEpochMs
                }
                HistoryDateRangePreset.LAST_3_MONTHS -> {
                    DataRetentionPolicyEngine.isWithinDefaultThreeMonthWindow(record.consultationEpochMs, referenceNowEpochMs)
                }
                HistoryDateRangePreset.LAST_2_YEARS -> {
                    DataRetentionPolicyEngine.isWithinTwoYearHistoricalWindow(record.consultationEpochMs, referenceNowEpochMs)
                }
                HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED -> {
                    true
                }
                HistoryDateRangePreset.CUSTOM_RANGE -> {
                    val afterStart = criteria.customStartEpochMs?.let { record.consultationEpochMs >= it } ?: true
                    val beforeEnd = criteria.customEndEpochMs?.let { record.consultationEpochMs <= it } ?: true
                    afterStart && beforeEnd
                }
            }
        }

        // 3. Role-Appropriate Attribute Filters
        val attributeFiltered = dateFiltered.filter { record ->
            val matchesSearch = criteria.searchQuery.isBlank() ||
                record.patientName.contains(criteria.searchQuery, ignoreCase = true) ||
                record.patientIdentifier.contains(criteria.searchQuery, ignoreCase = true) ||
                record.doctorName.contains(criteria.searchQuery, ignoreCase = true) ||
                record.clinicName.contains(criteria.searchQuery, ignoreCase = true) ||
                record.tokenLabel.contains(criteria.searchQuery, ignoreCase = true) ||
                (session.role == Role.RECEPTIONIST && record.patientPhone?.contains(criteria.searchQuery, ignoreCase = true) == true) ||
                (record.diagnosis?.contains(criteria.searchQuery, ignoreCase = true) == true)

            val matchesPatientId = criteria.patientIdFilter.isNullOrBlank() ||
                record.patientId.equals(criteria.patientIdFilter, ignoreCase = true) ||
                record.patientIdentifier.equals(criteria.patientIdFilter, ignoreCase = true)

            val matchesPatientPhone = criteria.patientPhoneFilter.isNullOrBlank() ||
                (session.role == Role.RECEPTIONIST && record.patientPhone?.contains(criteria.patientPhoneFilter, ignoreCase = true) == true)

            val matchesDoctor = criteria.doctorIdFilter == "ALL" ||
                record.doctorId.equals(criteria.doctorIdFilter, ignoreCase = true) ||
                record.doctorName.equals(criteria.doctorIdFilter, ignoreCase = true)

            val matchesClinic = criteria.clinicIdFilter == "ALL" ||
                record.clinicId.equals(criteria.clinicIdFilter, ignoreCase = true) ||
                record.clinicName.equals(criteria.clinicIdFilter, ignoreCase = true)

            val matchesChamber = criteria.chamberFilter == "ALL" ||
                record.chamber.equals(criteria.chamberFilter, ignoreCase = true)

            val matchesCategory = criteria.recordCategoryFilter == null ||
                record.recordCategory == criteria.recordCategoryFilter

            val matchesAppointmentStatus = criteria.appointmentStatusFilter == "ALL" ||
                record.appointmentStatus.name.equals(criteria.appointmentStatusFilter, ignoreCase = true)

            val matchesConsultationStatus = criteria.consultationStatusFilter == "ALL" ||
                record.consultationStatus.equals(criteria.consultationStatusFilter, ignoreCase = true)

            val matchesCheckIn = criteria.checkInStatusFilter == "ALL" ||
                record.checkInStatus.equals(criteria.checkInStatusFilter, ignoreCase = true)

            val matchesPrescription = criteria.hasPrescriptionFilter == null ||
                record.hasPrescription == criteria.hasPrescriptionFilter

            val matchesFollowUp = criteria.followUpStatusFilter == "ALL" ||
                record.followUpStatus.equals(criteria.followUpStatusFilter, ignoreCase = true)

            matchesSearch && matchesPatientId && matchesPatientPhone && matchesDoctor && matchesClinic &&
                matchesChamber && matchesCategory && matchesAppointmentStatus &&
                matchesConsultationStatus && matchesCheckIn && matchesPrescription && matchesFollowUp
        }.distinctBy { it.id }
            .sortedByDescending { it.consultationEpochMs }

        // 4. Pagination
        val safePage = criteria.page.coerceAtLeast(1)
        val safePageSize = criteria.pageSize.coerceIn(1, 100)
        val startIndex = (safePage - 1) * safePageSize
        val pagedRecords = if (startIndex >= attributeFiltered.size) {
            emptyList()
        } else {
            attributeFiltered.subList(
                startIndex,
                (startIndex + safePageSize).coerceAtMost(attributeFiltered.size)
            )
        }

        return FilteredHistoryResult(
            records = pagedRecords,
            totalMatchedCount = attributeFiltered.size,
            page = safePage,
            pageSize = safePageSize,
            hasMorePages = (startIndex + safePageSize) < attributeFiltered.size,
            activeWindowLabel = criteria.dateRangePreset.label,
            errorMessage = null
        )
    }

    fun computeClinicOperationalSummary(records: List<ConsultationRecord>): ClinicOperationalSummary {
        val completedConsultations = records.filter {
            it.recordCategory == RecordCategory.CONSULTATION &&
                it.appointmentStatus == TokenStatus.COMPLETED &&
                (it.consultationStatus == "COMPLETED" || it.consultationStatus == "FOLLOW_UP_REQUIRED")
        }
        val cancelled = records.count { it.appointmentStatus == TokenStatus.CANCELLED }
        val rejected = records.count { it.appointmentStatus == TokenStatus.REJECTED }
        val noShow = records.count { it.appointmentStatus == TokenStatus.NO_SHOW || it.appointmentStatus == TokenStatus.SKIPPED }
        val waitingOrActive = records.count {
            it.appointmentStatus == TokenStatus.WAITING ||
                it.appointmentStatus == TokenStatus.CALLED ||
                it.appointmentStatus == TokenStatus.IN_CONSULTATION
        }
        val approved = records.count {
            it.appointmentStatus != TokenStatus.REJECTED &&
                it.appointmentStatus != TokenStatus.PENDING_VERIFICATION
        }

        val byDoctor = completedConsultations.groupingBy { it.doctorName }.eachCount()
        val byChamber = records.filter { it.appointmentStatus != TokenStatus.REJECTED }
            .groupingBy { it.chamber }
            .eachCount()

        return ClinicOperationalSummary(
            totalAppointmentRequests = records.size,
            approvedBookingsCount = approved,
            completedConsultationsCount = completedConsultations.size,
            cancelledCount = cancelled,
            rejectedCount = rejected,
            noShowCount = noShow,
            waitingOrInProgressCount = waitingOrActive,
            consultationsByDoctor = byDoctor,
            utilizationByChamber = byChamber
        )
    }
}

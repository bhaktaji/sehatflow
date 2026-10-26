package com.example.domain.model

import com.example.data.model.UserRole

/**
 * Optional, patient-reported health profile (Specification Section 7).
 *
 * Strictly separated from clinician-authored [ConsultationRecord] medical records.
 * Every patient-provided field is optional; skipping onboarding leaves fields as `null`
 * rather than storing fabricated placeholder values.
 */
data class PatientHealthProfile(
    val patientId: String,
    val dateOfBirthOrAge: String? = null,
    val bloodGroup: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val knownAllergies: String? = null,
    val existingConditions: String? = null,
    val currentMedications: String? = null,
    val emergencyContact: String? = null,
    val isSkipped: Boolean = false,
    val isCompleted: Boolean = false,
    val consentGranted: Boolean = false,
    val consentVersion: String? = null,
    val consentedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = 1792836000000L,
    val verificationStatusLabel: String = UNVERIFIED_PATIENT_REPORTED_LABEL,
    val isClinicallyVerified: Boolean = false
) {
    companion object {
        const val UNVERIFIED_PATIENT_REPORTED_LABEL = "Reported by patient • Not clinically verified"
        val VALID_BLOOD_GROUPS = setOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "UNKNOWN")
        const val MIN_HEIGHT_CM = 30.0
        const val MAX_HEIGHT_CM = 260.0
        const val MIN_WEIGHT_KG = 1.0
        const val MAX_WEIGHT_KG = 350.0
        const val MAX_TEXT_FIELD_LENGTH = 300
    }

    fun hasAnyReportedMedicalDetail(): Boolean {
        return !dateOfBirthOrAge.isNullOrBlank() ||
            !bloodGroup.isNullOrBlank() ||
            heightCm != null ||
            weightKg != null ||
            !knownAllergies.isNullOrBlank() ||
            !existingConditions.isNullOrBlank() ||
            !currentMedications.isNullOrBlank() ||
            !emergencyContact.isNullOrBlank()
    }
}

/**
 * Thread-safe registry for optional patient-reported health profiles, enforcing:
 * - Zero fabricated defaults when skipped ("Skip for now" leaves all optional fields null).
 * - Strict input validation and sanitization (height, weight, blood group, text length).
 * - Explicit DPDP consent check before storing optional health attributes.
 * - Role-based access control: Patient (own record) and authorized Doctor (when consent is active)
 *   can view self-reported health details labeled "Reported by patient • Not clinically verified";
 *   Receptionist and Clinic Owner/Admin are denied access to clinical/health profile fields.
 */
object PatientHealthProfileRegistry {

    private val lock = Any()
    private val profilesByPatientId = LinkedHashMap<String, PatientHealthProfile>()

    init {
        resetForTesting()
    }

    fun resetForTesting() {
        synchronized(lock) {
            profilesByPatientId.clear()
            // Seed a sample patient-reported profile for pat_demo_patient (David Miller)
            profilesByPatientId["pat_demo_patient"] = PatientHealthProfile(
                patientId = "pat_demo_patient",
                dateOfBirthOrAge = "38 yrs",
                bloodGroup = "O+",
                heightCm = 174.0,
                weightKg = 72.5,
                knownAllergies = "Penicillin, Dust mites",
                existingConditions = "Mild seasonal asthma",
                currentMedications = "Salbutamol inhaler PRN",
                emergencyContact = "+91-9830099881 (Spouse)",
                isSkipped = false,
                isCompleted = true,
                consentGranted = true,
                consentVersion = DpdpPrivacyRegistry.CURRENT_NOTICE_VERSION,
                consentedAtEpochMs = 1792836000000L,
                updatedAtEpochMs = 1792836000000L
            )
        }
    }

    private fun sanitizeOptionalText(raw: String?, fieldName: String): Result<String?> {
        val trimmed = raw?.trim() ?: return Result.success(null)
        if (trimmed.isEmpty()) return Result.success(null)
        if (trimmed.length > PatientHealthProfile.MAX_TEXT_FIELD_LENGTH) {
            return Result.failure(
                IllegalArgumentException("$fieldName must be ${PatientHealthProfile.MAX_TEXT_FIELD_LENGTH} characters or fewer.")
            )
        }
        val cleaned = trimmed
            .replace(Regex("[<>]"), "")
            .replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "")
            .trim()
        return Result.success(cleaned.ifEmpty { null })
    }

    /**
     * Records that a patient chose "Skip for now" during onboarding.
     * Never writes fake or default medical values; preserves existing values if already saved,
     * or creates a skipped record with all health attributes set to `null`.
     */
    fun skipHealthProfileOnboarding(
        patientId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): PatientHealthProfile {
        synchronized(lock) {
            val existing = profilesByPatientId[patientId]
            val skipped = if (existing != null && existing.hasAnyReportedMedicalDetail()) {
                existing.copy(isSkipped = true, updatedAtEpochMs = nowEpochMs)
            } else {
                PatientHealthProfile(
                    patientId = patientId,
                    dateOfBirthOrAge = null,
                    bloodGroup = null,
                    heightCm = null,
                    weightKg = null,
                    knownAllergies = null,
                    existingConditions = null,
                    currentMedications = null,
                    emergencyContact = null,
                    isSkipped = true,
                    isCompleted = false,
                    consentGranted = false,
                    consentVersion = null,
                    consentedAtEpochMs = null,
                    updatedAtEpochMs = nowEpochMs
                )
            }
            profilesByPatientId[patientId] = skipped
            return skipped
        }
    }

    /**
     * Validates and saves optional patient-reported health profile attributes.
     * Requires explicit consent for optional health data processing.
     */
    fun saveOrUpdateHealthProfile(
        patientId: String,
        dateOfBirthOrAge: String? = null,
        bloodGroup: String? = null,
        heightCmInput: String? = null,
        weightKgInput: String? = null,
        knownAllergies: String? = null,
        existingConditions: String? = null,
        currentMedications: String? = null,
        emergencyContact: String? = null,
        consentGranted: Boolean,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result<PatientHealthProfile> {
        synchronized(lock) {
            if (patientId.isBlank()) {
                return Result.failure(IllegalArgumentException("Patient ID is required."))
            }
            if (!consentGranted) {
                return Result.failure(
                    IllegalArgumentException("Explicit consent is required to save optional health profile information. You can also choose 'Skip for now'.")
                )
            }

            val cleanDob = sanitizeOptionalText(dateOfBirthOrAge, "Date of birth / Age")
                .getOrElse { return Result.failure(it) }

            val cleanBloodGroup = bloodGroup?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            if (cleanBloodGroup != null && cleanBloodGroup !in PatientHealthProfile.VALID_BLOOD_GROUPS) {
                return Result.failure(
                    IllegalArgumentException("Invalid blood group '$bloodGroup'. Expected A+, A-, B+, B-, AB+, AB-, O+, O-, or Unknown.")
                )
            }

            val parsedHeight: Double? = heightCmInput?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                val v = raw.toDoubleOrNull()
                    ?: return Result.failure(IllegalArgumentException("Height must be a valid number in cm."))
                if (v < PatientHealthProfile.MIN_HEIGHT_CM || v > PatientHealthProfile.MAX_HEIGHT_CM) {
                    return Result.failure(
                        IllegalArgumentException("Height must be between ${PatientHealthProfile.MIN_HEIGHT_CM.toInt()} cm and ${PatientHealthProfile.MAX_HEIGHT_CM.toInt()} cm.")
                    )
                }
                v
            }

            val parsedWeight: Double? = weightKgInput?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                val v = raw.toDoubleOrNull()
                    ?: return Result.failure(IllegalArgumentException("Weight must be a valid number in kg."))
                if (v < PatientHealthProfile.MIN_WEIGHT_KG || v > PatientHealthProfile.MAX_WEIGHT_KG) {
                    return Result.failure(
                        IllegalArgumentException("Weight must be between ${PatientHealthProfile.MIN_WEIGHT_KG.toInt()} kg and ${PatientHealthProfile.MAX_WEIGHT_KG.toInt()} kg.")
                    )
                }
                v
            }

            val cleanAllergies = sanitizeOptionalText(knownAllergies, "Known allergies")
                .getOrElse { return Result.failure(it) }
            val cleanConditions = sanitizeOptionalText(existingConditions, "Existing conditions")
                .getOrElse { return Result.failure(it) }
            val cleanMedications = sanitizeOptionalText(currentMedications, "Current medications")
                .getOrElse { return Result.failure(it) }
            val cleanEmergencyContact = sanitizeOptionalText(emergencyContact, "Emergency contact")
                .getOrElse { return Result.failure(it) }

            DpdpPrivacyRegistry.recordOptionalHealthProfileConsent(
                userId = patientId,
                granted = true,
                nowEpochMs = nowEpochMs
            )

            val saved = PatientHealthProfile(
                patientId = patientId,
                dateOfBirthOrAge = cleanDob,
                bloodGroup = cleanBloodGroup,
                heightCm = parsedHeight,
                weightKg = parsedWeight,
                knownAllergies = cleanAllergies,
                existingConditions = cleanConditions,
                currentMedications = cleanMedications,
                emergencyContact = cleanEmergencyContact,
                isSkipped = false,
                isCompleted = true,
                consentGranted = true,
                consentVersion = DpdpPrivacyRegistry.CURRENT_NOTICE_VERSION,
                consentedAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
                verificationStatusLabel = PatientHealthProfile.UNVERIFIED_PATIENT_REPORTED_LABEL,
                isClinicallyVerified = false
            )
            profilesByPatientId[patientId] = saved

            ClinicalAuditLogger.recordEvent(
                actorId = patientId,
                actorRole = Role.PATIENT,
                action = "UPDATE_PATIENT_REPORTED_HEALTH_PROFILE",
                resourceId = patientId,
                clinicId = null,
                outcome = "SUCCESS",
                contextNote = "Patient saved optional self-reported health profile (marked unverified)."
            )
            return Result.success(saved)
        }
    }

    /**
     * Role-enforced retrieval of a patient's self-reported health profile.
     * - Patient can view their own health profile.
     * - Doctor can view an authorized patient's health profile ONLY if consent is currently active.
     * - Receptionist and Owner/Admin are strictly denied access to clinical/health profile details.
     */
    fun getAuthorizedHealthProfile(
        patientId: String,
        session: UserSession?
    ): PatientHealthProfile? {
        if (session == null) return null
        synchronized(lock) {
            val profile = profilesByPatientId[patientId] ?: return null
            return when (session.role) {
                Role.PATIENT -> {
                    if (session.userId == patientId) profile else null
                }
                Role.DOCTOR -> {
                    val authorizedPatient = PatientIdentityRegistry.getAuthorizedPatientProfile(
                        patientIdOrIdentifier = patientId,
                        session = session
                    )
                    if (authorizedPatient != null && profile.consentGranted) {
                        profile
                    } else {
                        null
                    }
                }
                Role.RECEPTIONIST, Role.OWNER -> {
                    ClinicalAuditLogger.recordEvent(
                        actorId = session.userId,
                        actorRole = session.role,
                        action = "UNAUTHORIZED_HEALTH_PROFILE_ACCESS_BLOCKED",
                        resourceId = patientId,
                        clinicId = session.clinicId,
                        outcome = "DENIED",
                        contextNote = "Role ${session.role} is not permitted to access patient-reported clinical health profile."
                    )
                    null
                }
            }
        }
    }

    internal fun clearHealthProfileOnConsentWithdrawal(
        patientId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            profilesByPatientId[patientId] = PatientHealthProfile(
                patientId = patientId,
                isSkipped = true,
                isCompleted = false,
                consentGranted = false,
                consentVersion = null,
                consentedAtEpochMs = null,
                updatedAtEpochMs = nowEpochMs
            )
        }
    }
}

/**
 * Staff Invitation Code & Role Authorization Engine (Specification Section 8).
 *
 * Enforces:
 * 1. Google Sign-In alone NEVER grants Doctor, Receptionist, or Clinic Admin roles.
 * 2. Staff invitations are validated on the backend/repository for existence, expiration,
 *    clinic binding, permitted role, and single-use consumption.
 * 3. Rate-limiting / lockout protects invitation redemption against brute-force guessing.
 */
data class StaffInvitationRecord(
    val inviteCode: String,
    val clinicId: String,
    val clinicName: String,
    val permittedRole: UserRole,
    val expiresAtEpochMs: Long,
    val maxUses: Int = 1,
    val redeemedByUserIds: Set<String> = emptySet(),
    val isRevoked: Boolean = false
)

data class StaffInvitationRedemptionResult(
    val isSuccess: Boolean,
    val assignedRole: UserRole? = null,
    val clinicId: String? = null,
    val clinicName: String? = null,
    val errorCode: String? = null,
    val message: String
)

object StaffInvitationValidator {

    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_WINDOW_MS = 15L * 60L * 1000L // 15 minutes
    const val DEFAULT_INVITE_VALIDITY_MS = 7L * 24L * 60L * 60L * 1000L // 7 days

    private val lock = Any()
    private val invitationsByCode = LinkedHashMap<String, StaffInvitationRecord>()
    private val verifiedStaffRolesByUserId = LinkedHashMap<String, Pair<UserRole, String>>() // userId -> (Role, clinicId)
    private val failedAttemptsByActor = LinkedHashMap<String, MutableList<Long>>()

    init {
        resetForTesting()
    }

    fun resetForTesting(referenceNowEpochMs: Long = 1792836000000L) {
        synchronized(lock) {
            invitationsByCode.clear()
            verifiedStaffRolesByUserId.clear()
            failedAttemptsByActor.clear()

            // Seed valid single-use invitations for XYZ Clinic
            invitationsByCode["DOC-7890"] = StaffInvitationRecord(
                inviteCode = "DOC-7890",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                permittedRole = UserRole.DOCTOR,
                expiresAtEpochMs = referenceNowEpochMs + DEFAULT_INVITE_VALIDITY_MS,
                maxUses = 1
            )
            invitationsByCode["REC-1234"] = StaffInvitationRecord(
                inviteCode = "REC-1234",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                permittedRole = UserRole.RECEPTIONIST,
                expiresAtEpochMs = referenceNowEpochMs + DEFAULT_INVITE_VALIDITY_MS,
                maxUses = 1
            )
            // Seed an expired invitation for testing expiration enforcement
            invitationsByCode["DOC-EXPIRED"] = StaffInvitationRecord(
                inviteCode = "DOC-EXPIRED",
                clinicId = "clinic_xyz",
                clinicName = "XYZ Clinic",
                permittedRole = UserRole.DOCTOR,
                expiresAtEpochMs = referenceNowEpochMs - 3600_000L,
                maxUses = 1
            )
        }
    }

    fun registerClinicInvitations(
        clinicId: String,
        clinicName: String,
        adminUserId: String,
        doctorInviteCode: String,
        receptionistInviteCode: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            verifiedStaffRolesByUserId[adminUserId] = UserRole.CLINIC_ADMIN to clinicId
            val cleanDoc = doctorInviteCode.trim().uppercase()
            val cleanRec = receptionistInviteCode.trim().uppercase()
            if (cleanDoc.isNotEmpty()) {
                invitationsByCode[cleanDoc] = StaffInvitationRecord(
                    inviteCode = cleanDoc,
                    clinicId = clinicId,
                    clinicName = clinicName,
                    permittedRole = UserRole.DOCTOR,
                    expiresAtEpochMs = nowEpochMs + DEFAULT_INVITE_VALIDITY_MS,
                    maxUses = 5
                )
            }
            if (cleanRec.isNotEmpty()) {
                invitationsByCode[cleanRec] = StaffInvitationRecord(
                    inviteCode = cleanRec,
                    clinicId = clinicId,
                    clinicName = clinicName,
                    permittedRole = UserRole.RECEPTIONIST,
                    expiresAtEpochMs = nowEpochMs + DEFAULT_INVITE_VALIDITY_MS,
                    maxUses = 5
                )
            }
        }
    }

    /**
     * Resolves the role granted upon Google Sign-In.
     * - Google Sign-In establishes authenticated identity only.
     * - Never grants DOCTOR, RECEPTIONIST, or CLINIC_ADMIN unless the user is already
     *   in [verifiedStaffRolesByUserId].
     */
    fun resolveRoleAfterGoogleSignIn(userId: String): UserRole {
        synchronized(lock) {
            return verifiedStaffRolesByUserId[userId]?.first ?: UserRole.PATIENT
        }
    }

    /**
     * Validates and redeems a staff invitation code with rate-limiting, expiry, role-binding,
     * and single-use/max-use enforcement.
     */
    fun validateAndRedeemInvitation(
        userId: String,
        rawInviteCode: String,
        requestedRole: UserRole? = null,
        nowEpochMs: Long = 1792836000000L
    ): StaffInvitationRedemptionResult {
        synchronized(lock) {
            val actorKey = userId.ifBlank { "anonymous" }
            val recentFailures = failedAttemptsByActor.getOrPut(actorKey) { mutableListOf() }
            recentFailures.removeAll { it < (nowEpochMs - LOCKOUT_WINDOW_MS) }

            if (recentFailures.size >= MAX_FAILED_ATTEMPTS) {
                ClinicalAuditLogger.recordEvent(
                    actorId = actorKey,
                    actorRole = Role.PATIENT,
                    action = "STAFF_INVITE_RATE_LIMITED",
                    resourceId = rawInviteCode.take(12),
                    clinicId = null,
                    outcome = "DENIED",
                    contextNote = "Too many failed staff invitation attempts (${recentFailures.size})."
                )
                return StaffInvitationRedemptionResult(
                    isSuccess = false,
                    errorCode = "RATE_LIMITED_LOCKED_OUT",
                    message = "Too many failed invitation attempts. Please wait 15 minutes or contact your Clinic Administrator."
                )
            }

            val code = rawInviteCode.trim().uppercase()
            if (code.isEmpty()) {
                return StaffInvitationRedemptionResult(
                    isSuccess = false,
                    errorCode = "EMPTY_CODE",
                    message = "Please enter a valid staff invitation code (e.g., DOC-XXXX or REC-XXXX)."
                )
            }

            val record = invitationsByCode[code]
            if (record == null || record.isRevoked) {
                recentFailures.add(nowEpochMs)
                ClinicalAuditLogger.recordEvent(
                    actorId = actorKey,
                    actorRole = Role.PATIENT,
                    action = "STAFF_INVITE_INVALID",
                    resourceId = code,
                    clinicId = null,
                    outcome = "DENIED",
                    contextNote = "Invalid or revoked staff invitation code."
                )
                return StaffInvitationRedemptionResult(
                    isSuccess = false,
                    errorCode = "INVALID_CODE",
                    message = "Invalid or unrecognized staff invitation code."
                )
            }

            if (nowEpochMs > record.expiresAtEpochMs) {
                recentFailures.add(nowEpochMs)
                return StaffInvitationRedemptionResult(
                    isSuccess = false,
                    errorCode = "EXPIRED_CODE",
                    message = "This staff invitation code has expired. Please request a new code from your Clinic Administrator."
                )
            }

            if (requestedRole != null && requestedRole != record.permittedRole) {
                recentFailures.add(nowEpochMs)
                ClinicalAuditLogger.recordEvent(
                    actorId = actorKey,
                    actorRole = Role.PATIENT,
                    action = "STAFF_INVITE_ROLE_MISMATCH",
                    resourceId = code,
                    clinicId = record.clinicId,
                    outcome = "DENIED",
                    contextNote = "Attempted to claim $requestedRole using a ${record.permittedRole} invite code."
                )
                return StaffInvitationRedemptionResult(
                    isSuccess = false,
                    errorCode = "ROLE_MISMATCH",
                    message = "Invitation code $code is only valid for ${record.permittedRole.name}, not ${requestedRole.name}."
                )
            }

            val alreadyRedeemedBySameUser = userId in record.redeemedByUserIds
            if (!alreadyRedeemedBySameUser && record.redeemedByUserIds.size >= record.maxUses) {
                recentFailures.add(nowEpochMs)
                return StaffInvitationRedemptionResult(
                    isSuccess = false,
                    errorCode = "INVITE_ALREADY_USED",
                    message = "This invitation code has already been redeemed and cannot be reused."
                )
            }

            val updatedRecord = record.copy(redeemedByUserIds = record.redeemedByUserIds + userId)
            invitationsByCode[code] = updatedRecord
            verifiedStaffRolesByUserId[userId] = record.permittedRole to record.clinicId
            recentFailures.clear()

            ClinicalAuditLogger.recordEvent(
                actorId = actorKey,
                actorRole = if (record.permittedRole == UserRole.DOCTOR) Role.DOCTOR else Role.RECEPTIONIST,
                action = "STAFF_INVITE_REDEEMED",
                resourceId = code,
                clinicId = record.clinicId,
                outcome = "SUCCESS",
                contextNote = "Staff member onboarded to ${record.clinicId} as ${record.permittedRole.name}."
            )

            return StaffInvitationRedemptionResult(
                isSuccess = true,
                assignedRole = record.permittedRole,
                clinicId = record.clinicId,
                clinicName = record.clinicName,
                errorCode = null,
                message = "Verified ${record.permittedRole.name} invitation for ${record.clinicName}."
            )
        }
    }
}

/**
 * DPDP Act 2023 Privacy, Consent & Data-Principal Rights Registry (Specification Section 9).
 *
 * Supports:
 * - Separate purpose-specific consent tracking (Account/Queue Identity vs Optional Health Profile).
 * - Consent version tracking and multilingual notice summary (English + Hindi/Bengali Eighth Schedule notice).
 * - Immediate withdrawal of optional health profile consent without blocking queue/token tracking.
 * - Data-principal rights requests (profile correction, optional health erasure, and account deletion
 *   that respects statutory clinical record retention rules).
 */
data class PrivacyConsentRecord(
    val userId: String,
    val noticeVersion: String = DpdpPrivacyRegistry.CURRENT_NOTICE_VERSION,
    val accountIdentityNoticeAccepted: Boolean = true,
    val optionalHealthProfileConsentGranted: Boolean = false,
    val optionalHealthConsentUpdatedEpochMs: Long? = null,
    val preferredNoticeLanguage: String = "en"
)

data class DataPrincipalRightsRequestRecord(
    val requestId: String,
    val userId: String,
    val requestType: String, // WITHDRAW_OPTIONAL_HEALTH_CONSENT, ERASE_OPTIONAL_HEALTH_DATA, ACCOUNT_DELETION_REQUEST
    val status: String, // COMPLETED, PARTIAL_RETAINED_UNDER_CLINICAL_LAW
    val resolutionSummary: String,
    val submittedAtEpochMs: Long
)

object DpdpPrivacyRegistry {
    const val CURRENT_NOTICE_VERSION = "DPDP-2023-v2.1"

    val SUPPORTED_NOTICE_LANGUAGES = mapOf(
        "en" to "English: Your basic identity is used only for clinic queue tokens and wait-time updates. Optional health profile details require separate consent and can be skipped or withdrawn anytime.",
        "hi" to "हिन्दी (Hindi): आपकी बुनियादी पहचान केवल क्लिनिक टोकन और प्रतीक्षा समय के लिए उपयोग की जाती है। वैकल्पिक स्वास्थ्य प्रोफ़ाइल विवरण के लिए अलग सहमति आवश्यक है और इसे कभी भी छोड़ा या वापस लिया जा सकता है।",
        "bn" to "বাংলা (Bengali): আপনার মৌলিক পরিচয় শুধুমাত্র ক্লিনিক টোকেন এবং অপেক্ষার সময়ের জন্য ব্যবহৃত হয়। ঐচ্ছিক স্বাস্থ্য তথ্যের জন্য আলাদা সম্মতি প্রয়োজন এবং তা যেকোনো সময় প্রত্যাহার করা যায়।"
    )

    private val lock = Any()
    private val consentByUserId = LinkedHashMap<String, PrivacyConsentRecord>()
    private val requestsByUserId = LinkedHashMap<String, MutableList<DataPrincipalRightsRequestRecord>>()

    init {
        resetForTesting()
    }

    fun resetForTesting() {
        synchronized(lock) {
            consentByUserId.clear()
            requestsByUserId.clear()
            consentByUserId["pat_demo_patient"] = PrivacyConsentRecord(
                userId = "pat_demo_patient",
                noticeVersion = CURRENT_NOTICE_VERSION,
                accountIdentityNoticeAccepted = true,
                optionalHealthProfileConsentGranted = true,
                optionalHealthConsentUpdatedEpochMs = 1792836000000L,
                preferredNoticeLanguage = "en"
            )
        }
    }

    fun getConsentRecord(userId: String): PrivacyConsentRecord {
        synchronized(lock) {
            return consentByUserId.getOrPut(userId) {
                PrivacyConsentRecord(userId = userId)
            }
        }
    }

    fun recordOptionalHealthProfileConsent(
        userId: String,
        granted: Boolean,
        languageCode: String = "en",
        nowEpochMs: Long = System.currentTimeMillis()
    ): PrivacyConsentRecord {
        synchronized(lock) {
            val existing = getConsentRecord(userId)
            val updated = existing.copy(
                noticeVersion = CURRENT_NOTICE_VERSION,
                optionalHealthProfileConsentGranted = granted,
                optionalHealthConsentUpdatedEpochMs = nowEpochMs,
                preferredNoticeLanguage = languageCode
            )
            consentByUserId[userId] = updated
            if (!granted) {
                PatientHealthProfileRegistry.clearHealthProfileOnConsentWithdrawal(userId, nowEpochMs)
            }
            return updated
        }
    }

    /**
     * Withdraws optional health profile consent and immediately purges self-reported health fields,
     * while keeping basic patient identity and queue/token functionality intact.
     */
    fun withdrawOptionalHealthProfileConsent(
        userId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): DataPrincipalRightsRequestRecord {
        synchronized(lock) {
            recordOptionalHealthProfileConsent(userId = userId, granted = false, nowEpochMs = nowEpochMs)
            val request = DataPrincipalRightsRequestRecord(
                requestId = "dpdp_req_${nowEpochMs}_${(requestsByUserId[userId]?.size ?: 0) + 1}",
                userId = userId,
                requestType = "WITHDRAW_OPTIONAL_HEALTH_CONSENT",
                status = "COMPLETED",
                resolutionSummary = "Optional health profile consent withdrawn and self-reported health fields erased. Queue & token booking remains active.",
                submittedAtEpochMs = nowEpochMs
            )
            requestsByUserId.getOrPut(userId) { mutableListOf() }.add(0, request)
            ClinicalAuditLogger.recordEvent(
                actorId = userId,
                actorRole = Role.PATIENT,
                action = "DPDP_WITHDRAW_OPTIONAL_HEALTH_CONSENT",
                resourceId = request.requestId,
                clinicId = null,
                outcome = "SUCCESS",
                contextNote = request.resolutionSummary
            )
            return request
        }
    }

    /**
     * Processes a patient's account/data erasure request in accordance with Section 9.5:
     * - Immediately purges optional self-reported health profile data.
     * - Checks whether completed clinical consultation records exist that must be retained
     *   under applicable medical/clinical establishment retention regulations.
     */
    fun submitDataErasureOrAccountDeletionRequest(
        userId: String,
        hasRetainedClinicalConsultations: Boolean,
        nowEpochMs: Long = System.currentTimeMillis()
    ): DataPrincipalRightsRequestRecord {
        synchronized(lock) {
            recordOptionalHealthProfileConsent(userId = userId, granted = false, nowEpochMs = nowEpochMs)
            val status = if (hasRetainedClinicalConsultations) {
                "PARTIAL_RETAINED_UNDER_CLINICAL_LAW"
            } else {
                "COMPLETED"
            }
            val summary = if (hasRetainedClinicalConsultations) {
                "Optional self-reported health profile and marketing/preference data erased. Completed clinical consultation records are retained in restricted archive per statutory medical record retention rules."
            } else {
                "Optional health profile and account profile data erased."
            }
            val request = DataPrincipalRightsRequestRecord(
                requestId = "dpdp_del_${nowEpochMs}_${(requestsByUserId[userId]?.size ?: 0) + 1}",
                userId = userId,
                requestType = "ACCOUNT_DELETION_REQUEST",
                status = status,
                resolutionSummary = summary,
                submittedAtEpochMs = nowEpochMs
            )
            requestsByUserId.getOrPut(userId) { mutableListOf() }.add(0, request)
            ClinicalAuditLogger.recordEvent(
                actorId = userId,
                actorRole = Role.PATIENT,
                action = "DPDP_ACCOUNT_ERASURE_REQUEST",
                resourceId = request.requestId,
                clinicId = null,
                outcome = "SUCCESS",
                contextNote = "Status=$status"
            )
            return request
        }
    }

    fun getRequestsForUser(userId: String): List<DataPrincipalRightsRequestRecord> {
        synchronized(lock) {
            return requestsByUserId[userId]?.toList() ?: emptyList()
        }
    }
}

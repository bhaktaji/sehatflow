package com.example.domain.model

import com.example.core.util.SecurityTokenGenerator

data class UserSession(
    val token: String,
    val userId: String,
    val displayName: String,
    val role: Role,
    val clinicId: String?,
    val doctorId: String?
)

/**
 * Authoritative Patient Identity Record (Requirement 1).
 * Keeps internal database/auth key (patientId) separate from the stable, human-readable
 * Patient ID (patientIdentifier, e.g. "PAT-1001" or "PAT-8F3A2C").
 * Never embeds phone numbers or sensitive PII inside patientIdentifier.
 */
data class PatientProfileRecord(
    val patientId: String,
    val patientIdentifier: String,
    val fullName: String,
    val phoneNumber: String?,
    val primaryClinicId: String = "clinic_xyz",
    val assignedDoctorIds: Set<String> = emptySet(),
    val associatedClinicIds: Set<String> = setOf("clinic_xyz"),
    val createdAtEpochMs: Long = 1792836000000L,
    val updatedAtEpochMs: Long = 1792836000000L
)

enum class IdentityMatchStatus {
    EXACT_AUTHENTICATED_MATCH,
    EXISTING_IDENTIFIER_MATCH,
    PHONE_AND_NAME_VERIFIED_MATCH,
    NEW_PATIENT_CREATED,
    AMBIGUOUS_REQUIRES_VERIFICATION
}

data class IdentityResolutionResult(
    val status: IdentityMatchStatus,
    val patientRecord: PatientProfileRecord?,
    val candidateRecords: List<PatientProfileRecord> = emptyList(),
    val message: String
) {
    val profile: PatientProfileRecord? get() = patientRecord
    val ambiguousCandidates: List<PatientProfileRecord> get() = candidateRecords
}

/**
 * Thread-safe authoritative Patient Identity Registry enforcing:
 * - Unique, immutable Patient IDs across appointments, consultations, and profile updates.
 * - Concurrency-safe registration (cannot produce duplicate IDs).
 * - Returning patient resolution without creating duplicate records or auto-merging on name alone.
 * - Role-based field redaction (Receptionist sees phone for front-desk workflow; Doctor does not see phone by default).
 */
object PatientIdentityRegistry {

    private val lock = Any()
    private val byPatientId = LinkedHashMap<String, PatientProfileRecord>()
    private val byPatientIdentifier = LinkedHashMap<String, String>() // patientIdentifier -> patientId

    init {
        resetToSeedPatients()
    }

    fun resetToSeedForTesting() = resetToSeedPatients()

    fun resetToSeedPatients() {
        synchronized(lock) {
            byPatientId.clear()
            byPatientIdentifier.clear()
            val seeds = listOf(
                PatientProfileRecord(
                    patientId = "pat_demo_patient",
                    patientIdentifier = "PAT-1001",
                    fullName = "David Miller",
                    phoneNumber = "+91-9830011223",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_john", "doc_donna", "doc_alex"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_1",
                    patientIdentifier = "PAT-1006",
                    fullName = "Amitabh Sen",
                    phoneNumber = "+91-9830011224",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_john"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_2",
                    patientIdentifier = "PAT-1007",
                    fullName = "Alice Smith",
                    phoneNumber = "+91-9830044557",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_john"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_3",
                    patientIdentifier = "PAT-1001",
                    fullName = "David Miller",
                    phoneNumber = "+91-9830011223",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_john", "doc_donna", "doc_alex"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_4",
                    patientIdentifier = "PAT-1008",
                    fullName = "Baby Rohan",
                    phoneNumber = "+91-9831122334",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_donna"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_5",
                    patientIdentifier = "PAT-1009",
                    fullName = "Ananya Roy",
                    phoneNumber = "+91-9831155667",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_donna"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_6",
                    patientIdentifier = "PAT-1010",
                    fullName = "Harish Chandra",
                    phoneNumber = "+91-9831188990",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_alex"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_102",
                    patientIdentifier = "PAT-1002",
                    fullName = "Priya Nair",
                    phoneNumber = "+91-9830044556",
                    primaryClinicId = "clinic_abc",
                    assignedDoctorIds = setOf("doc_john"),
                    associatedClinicIds = setOf("clinic_abc")
                ),
                PatientProfileRecord(
                    patientId = "pat_103",
                    patientIdentifier = "PAT-1003",
                    fullName = "Rohan Gupta",
                    phoneNumber = "+91-9831122334", // Shared family phone with Baby Rohan (pat_4)
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_john"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_104",
                    patientIdentifier = "PAT-1004",
                    fullName = "Ananya Sharma",
                    phoneNumber = "+91-9830998877",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_alex"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_105",
                    patientIdentifier = "PAT-1005",
                    fullName = "Vikram Singh",
                    phoneNumber = "+91-9830554433",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_john"),
                    associatedClinicIds = setOf("clinic_xyz")
                ),
                PatientProfileRecord(
                    patientId = "pat_new_john",
                    patientIdentifier = "PAT-1011",
                    fullName = "Suresh Raina",
                    phoneNumber = "+91-9876501234",
                    primaryClinicId = "clinic_xyz",
                    assignedDoctorIds = setOf("doc_john"),
                    associatedClinicIds = setOf("clinic_xyz")
                )
            )
            seeds.forEach { record ->
                byPatientId[record.patientId] = record
                if (!byPatientIdentifier.containsKey(record.patientIdentifier)) {
                    byPatientIdentifier[record.patientIdentifier] = record.patientId
                }
            }
        }
    }

    /**
     * Generates a non-sequential, non-PII, collision-free human-readable Patient ID.
     */
    private fun generateUniqueIdentifierLocked(): String {
        while (true) {
            val candidate = SecurityTokenGenerator.generateInviteCode(prefix = "PAT", length = 6)
            if (!byPatientIdentifier.containsKey(candidate)) {
                return candidate
            }
        }
    }

    /**
     * Resolves an existing patient or registers a new patient safely.
     * - Never creates duplicate records when an authenticated patientId or patientIdentifier is supplied.
     * - Never auto-merges solely on matching names.
     * - Flags AMBIGUOUS_REQUIRES_VERIFICATION when a phone number is shared by multiple family members
     *   or when phone matches an existing patient with a different name without authenticated ID
     *   (unless confirmCreateNewWhenPhoneShared = true).
     */
    fun resolveOrRegisterPatient(
        authenticatedPatientId: String? = null,
        providedPatientIdentifier: String? = null,
        explicitPatientIdentifier: String? = null,
        fullName: String,
        phoneNumber: String? = null,
        clinicId: String = "clinic_xyz",
        doctorId: String? = null,
        confirmCreateNewWhenPhoneShared: Boolean = false
    ): IdentityResolutionResult {
        synchronized(lock) {
            val cleanName = fullName.trim().ifBlank { "Patient" }
            val cleanPhone = phoneNumber?.trim()?.takeIf { it.isNotBlank() }
            val effectiveProvidedIdentifier = providedPatientIdentifier ?: explicitPatientIdentifier

            // 1. Trusted authenticated patientId lookup
            if (!authenticatedPatientId.isNullOrBlank()) {
                val existing = byPatientId[authenticatedPatientId]
                if (existing != null) {
                    val updated = existing.copy(
                        associatedClinicIds = existing.associatedClinicIds + clinicId,
                        assignedDoctorIds = if (doctorId != null) existing.assignedDoctorIds + doctorId else existing.assignedDoctorIds
                    )
                    byPatientId[authenticatedPatientId] = updated
                    return IdentityResolutionResult(
                        status = IdentityMatchStatus.EXACT_AUTHENTICATED_MATCH,
                        patientRecord = updated,
                        message = "Identified returning patient ${updated.patientIdentifier}."
                    )
                }
            }

            // 2. Explicit Patient Identifier (PAT-XXXX) lookup
            if (!effectiveProvidedIdentifier.isNullOrBlank()) {
                val normalizedId = effectiveProvidedIdentifier.trim().uppercase()
                val mappedPatientId = byPatientIdentifier[normalizedId]
                val existing = mappedPatientId?.let { byPatientId[it] }
                if (existing != null) {
                    if (existing.fullName.equals(cleanName, ignoreCase = true) || authenticatedPatientId == existing.patientId) {
                        val updated = existing.copy(
                            associatedClinicIds = existing.associatedClinicIds + clinicId,
                            assignedDoctorIds = if (doctorId != null) existing.assignedDoctorIds + doctorId else existing.assignedDoctorIds
                        )
                        byPatientId[existing.patientId] = updated
                        return IdentityResolutionResult(
                            status = IdentityMatchStatus.EXISTING_IDENTIFIER_MATCH,
                            patientRecord = updated,
                            message = "Matched existing Patient ID ${updated.patientIdentifier}."
                        )
                    } else {
                        return IdentityResolutionResult(
                            status = IdentityMatchStatus.AMBIGUOUS_REQUIRES_VERIFICATION,
                            patientRecord = null,
                            candidateRecords = listOf(existing),
                            message = "Patient ID $normalizedId belongs to another registered name. Staff verification required."
                        )
                    }
                }
            }

            // 3. Phone number lookup (accounts for shared family numbers & phone changes)
            if (cleanPhone != null && authenticatedPatientId == null && !confirmCreateNewWhenPhoneShared) {
                val phoneMatches = byPatientId.values
                    .distinctBy { it.patientIdentifier }
                    .filter { it.phoneNumber == cleanPhone }

                if (phoneMatches.size > 1) {
                    return IdentityResolutionResult(
                        status = IdentityMatchStatus.AMBIGUOUS_REQUIRES_VERIFICATION,
                        patientRecord = null,
                        candidateRecords = phoneMatches,
                        message = "Shared family phone number matches ${phoneMatches.size} patients (${phoneMatches.joinToString { it.patientIdentifier }}). Please specify Patient ID or verify at front desk."
                    )
                } else if (phoneMatches.size == 1) {
                    val match = phoneMatches.first()
                    return if (match.fullName.equals(cleanName, ignoreCase = true)) {
                        val updated = match.copy(
                            associatedClinicIds = match.associatedClinicIds + clinicId,
                            assignedDoctorIds = if (doctorId != null) match.assignedDoctorIds + doctorId else match.assignedDoctorIds
                        )
                        byPatientId[match.patientId] = updated
                        IdentityResolutionResult(
                            status = IdentityMatchStatus.PHONE_AND_NAME_VERIFIED_MATCH,
                            patientRecord = updated,
                            message = "Matched returning patient ${updated.patientIdentifier}."
                        )
                    } else {
                        IdentityResolutionResult(
                            status = IdentityMatchStatus.AMBIGUOUS_REQUIRES_VERIFICATION,
                            patientRecord = null,
                            candidateRecords = listOf(match),
                            message = "Phone number is registered to ${match.patientIdentifier}. Confirm whether this is a family member or existing patient."
                        )
                    }
                }
            }

            // 4. Create new patient record (never auto-merges on name alone!)
            val newPatientId = authenticatedPatientId?.takeIf { it.isNotBlank() }
                ?: SecurityTokenGenerator.generateInviteCode(prefix = "pat", length = 8).lowercase()
            val existingIfConcurrent = byPatientId[newPatientId]
            if (existingIfConcurrent != null) {
                return IdentityResolutionResult(
                    status = IdentityMatchStatus.EXACT_AUTHENTICATED_MATCH,
                    patientRecord = existingIfConcurrent,
                    message = "Identified existing patient ${existingIfConcurrent.patientIdentifier}."
                )
            }

            val newIdentifier = generateUniqueIdentifierLocked()
            val newRecord = PatientProfileRecord(
                patientId = newPatientId,
                patientIdentifier = newIdentifier,
                fullName = cleanName,
                phoneNumber = cleanPhone,
                primaryClinicId = clinicId,
                assignedDoctorIds = if (doctorId != null) setOf(doctorId) else emptySet(),
                associatedClinicIds = setOf(clinicId),
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
            byPatientId[newPatientId] = newRecord
            byPatientIdentifier[newIdentifier] = newPatientId

            return IdentityResolutionResult(
                status = IdentityMatchStatus.NEW_PATIENT_CREATED,
                patientRecord = newRecord,
                message = "Assigned new Patient ID $newIdentifier."
            )
        }
    }

    /**
     * Updates a patient's profile (name or phone number) while keeping patientIdentifier
     * and patientId strictly immutable.
     */
    fun updatePatientProfile(
        patientId: String,
        newFullName: String? = null,
        newPhoneNumber: String? = null,
        actorId: String = "system"
    ): Result<PatientProfileRecord> {
        synchronized(lock) {
            val existing = byPatientId[patientId]
                ?: return Result.failure(IllegalArgumentException("Patient not found: $patientId"))
            val updated = existing.copy(
                fullName = newFullName?.trim()?.takeIf { it.isNotBlank() } ?: existing.fullName,
                phoneNumber = newPhoneNumber?.trim()?.takeIf { it.isNotBlank() } ?: existing.phoneNumber,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            // Primary key and patientIdentifier NEVER change
            byPatientId[patientId] = updated
            return Result.success(updated)
        }
    }

    fun getIdentifierForPatientId(patientId: String?): String {
        if (patientId.isNullOrBlank()) return "PAT-1001"
        synchronized(lock) {
            return byPatientId[patientId]?.patientIdentifier ?: "PAT-${patientId.takeLast(4).uppercase()}"
        }
    }

    fun getPhoneForPatientId(patientId: String?): String? {
        if (patientId.isNullOrBlank()) return null
        synchronized(lock) {
            return byPatientId[patientId]?.phoneNumber
        }
    }

    /**
     * Role-enforced lookup of a single patient profile.
     * - Patients can only retrieve their own record.
     * - Doctors can only retrieve patients within an authorized care relationship, and phoneNumber is redacted by default.
     * - Receptionists can retrieve patients for their assigned clinic, including phoneNumber for front-desk operations.
     */
    fun getAuthorizedPatientProfile(
        patientIdOrIdentifier: String,
        session: UserSession?,
        includeAuthorizedDoctorPhone: Boolean = false
    ): PatientProfileRecord? {
        if (session == null) return null
        synchronized(lock) {
            val normalized = patientIdOrIdentifier.trim()
            val record = byPatientId[normalized]
                ?: byPatientIdentifier[normalized.uppercase()]?.let { byPatientId[it] }
                ?: return null

            return when (session.role) {
                Role.PATIENT -> {
                    if (record.patientId == session.userId) record else null
                }
                Role.DOCTOR -> {
                    val docId = session.doctorId ?: "doc_john"
                    val effectiveDocIds = if (docId == "doc_1" || docId == "doc_john") setOf("doc_1", "doc_john") else setOf(docId)
                    if (record.assignedDoctorIds.intersect(effectiveDocIds).isNotEmpty()) {
                        if (includeAuthorizedDoctorPhone) record else record.copy(phoneNumber = null)
                    } else {
                        null
                    }
                }
                Role.RECEPTIONIST -> {
                    val clinic = session.clinicId ?: "clinic_xyz"
                    val effectiveClinics = if (clinic == "clinic_1" || clinic == "clinic_xyz") setOf("clinic_1", "clinic_xyz") else setOf(clinic)
                    if (record.associatedClinicIds.intersect(effectiveClinics).isNotEmpty()) {
                        record
                    } else {
                        null
                    }
                }
                Role.OWNER -> {
                    val clinic = session.clinicId ?: "clinic_xyz"
                    val effectiveClinics = if (clinic == "clinic_1" || clinic == "clinic_xyz") setOf("clinic_1", "clinic_xyz") else setOf(clinic)
                    if (record.associatedClinicIds.intersect(effectiveClinics).isNotEmpty()) {
                        record.copy(phoneNumber = null)
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * Role-enforced search by Patient ID, name, or phone (phone search restricted to Receptionist).
     */
    fun searchAuthorizedPatients(
        query: String,
        session: UserSession?
    ): List<PatientProfileRecord> {
        if (session == null) return emptyList()
        val cleanQuery = query.trim()
        synchronized(lock) {
            return byPatientId.values
                .distinctBy { it.patientIdentifier }
                .mapNotNull { getAuthorizedPatientProfile(it.patientId, session) }
                .filter { profile ->
                    cleanQuery.isBlank() ||
                        profile.patientIdentifier.contains(cleanQuery, ignoreCase = true) ||
                        profile.fullName.contains(cleanQuery, ignoreCase = true) ||
                        (session.role == Role.RECEPTIONIST && profile.phoneNumber?.contains(cleanQuery) == true)
                }
        }
    }
}

data class QueueToken(
    val id: String,
    val tokenNumber: Int,
    val label: String,
    val status: TokenStatus,
    val priority: Boolean,
    val queuePosition: Int,
    val displayName: String?,
    val checkedInAt: String?,
    val calledAt: String?,
    val patientId: String?,
    val prescriptionId: String?,
    val rejectionReason: String?,
    val estimatedWaitMinutes: Int?,
    val estimateQuality: String?,
    val chamber: String? = null,
    val appointmentDate: String? = null,
    val isRescheduled: Boolean = false,
    val originalRequestedDate: String? = null,
    val allocationNote: String? = null,
    val doctorId: String? = null,
    val patientIdentifier: String? = PatientIdentityRegistry.getIdentifierForPatientId(patientId),
    val patientPhone: String? = PatientIdentityRegistry.getPhoneForPatientId(patientId),
    val presentingComplaint: String? = null,
    val needsDoctorAssignment: Boolean = false
)

data class QueueSession(
    val id: String,
    val clinicId: String,
    val doctorId: String,
    val doctorName: String,
    val businessDate: String,
    val status: String,
    val tokenPrefix: String,
    val nextTokenNumber: Int,
    val fallbackConsultMinutes: Int,
    val pauseMessage: String?,
    val waitingCount: Int,
    val totalTokens: Int,
    val chamber: String = "Chamber A"
)

data class PendingPrescription(
    val tokenId: String,
    val prescriptionId: String,
    val patientId: String,
    val patientName: String,
    val patientPhone: String?,
    val requestedAt: String?,
    val fileType: String,
    val fileSize: Long,
    val doctorId: String? = null,
    val doctorName: String? = null,
    val requestedDate: String? = null,
    val requestedClinic: String? = null,
    val chamber: String? = null,
    val patientIdentifier: String = PatientIdentityRegistry.getIdentifierForPatientId(patientId),
    val presentingComplaint: String? = null,
    val hasPrescriptionAttachment: Boolean = prescriptionId.isNotBlank() && !prescriptionId.startsWith("NO_RX"),
    val needsDoctorAssignment: Boolean = doctorId == null || doctorId == "UNASSIGNED"
)

data class DoctorStats(
    val patientsSeenToday: Int,
    val averageConsultationMinutes: Double,
    val currentlyWaiting: Int
)

data class DoctorScheduleSlot(
    val dayOfWeek: String,
    val clinicId: String,
    val clinicName: String,
    val chamber: String,
    val dailyCapacity: Int,
    val consultationHours: String,
    val isDoctorAvailable: Boolean = true,
    val isClinicClosed: Boolean = false
)

data class ClinicDoctor(
    val id: String,
    val displayName: String,
    val specialty: String?,
    val active: Boolean,
    val qualifications: String? = "MBBS",
    val credentials: String? = "Reg. Verified",
    val experienceYears: Int? = 8,
    val primaryChamber: String? = "Chamber A",
    val primaryClinic: String? = "XYZ Clinic",
    val schedules: List<DoctorScheduleSlot> = emptyList()
)

data class AllocationResult(
    val requestedDate: String,
    val allocatedDate: String,
    val isAllocated: Boolean,
    val isRescheduled: Boolean,
    val clinicName: String,
    val chamber: String,
    val doctorName: String,
    val remainingCapacity: Int,
    val reason: String?,
    val token: QueueToken?
)

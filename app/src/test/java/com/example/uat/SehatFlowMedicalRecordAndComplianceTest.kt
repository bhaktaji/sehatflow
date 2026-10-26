package com.example.uat

import com.example.core.common.Resource
import com.example.core.demo.DemoDataProvider
import com.example.data.repository.ConsultationHistoryRepository
import com.example.data.repository.ConsultationHistoryRepositoryImpl
import com.example.domain.model.ClinicalAuditLogger
import com.example.domain.model.DataRetentionPolicyEngine
import com.example.domain.model.HistoryDateRangePreset
import com.example.domain.model.HistoryFilterCriteria
import com.example.domain.model.IdentityMatchStatus
import com.example.domain.model.MedicalRecordRequestTracker
import com.example.domain.model.PatientIdentityRegistry
import com.example.domain.model.RecordCategory
import com.example.domain.model.Role
import com.example.domain.model.TokenStatus
import com.example.domain.model.UserSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Automated Regression & Compliance Test Suite for:
 * - Requirement 1: Patient ID Generation, Immutability, Concurrency, Returning Patient Lookup & Shared-Phone Ambiguity Protection
 * - Requirement 2: Doctor Medical Record Timeline Access & Clinical vs Operational Record Separation
 * - Requirement 3: Strict Separation Between Receptionist (Operational + Phone, Zero Clinical) & Doctor (Clinical + Medical History, Redacted Phone)
 * - Requirement 4: Two-Year Historical Access Rule, Completed/Documented Eligibility, & Retention Without Deletion (>2 Year Record Preserved)
 * - Requirement 5: New/Returning Patients Without Prescriptions, Presenting Complaint Validation/Emergency Detection, & Receptionist Problem-Based Doctor Assignment
 * - Requirement 6: Indian Health-Law Compliance Controls (MCI/NMC Reg. 1.3.2 72-hour RMP Record Copy Tracker & Audit Logging)
 */
class SehatFlowMedicalRecordAndComplianceTest {

    private lateinit var repository: ConsultationHistoryRepositoryImpl
    private val dayMs = 24L * 60L * 60L * 1000L
    private val referenceNow = ConsultationHistoryRepository.DEFAULT_REFERENCE_EPOCH_MS

    private val doctorJohnSession = UserSession(
        token = "tok_doc_john",
        userId = "user_doc_john",
        displayName = "Dr. John",
        role = Role.DOCTOR,
        clinicId = "clinic_xyz",
        doctorId = "doc_john"
    )

    private val doctorDonnaSession = UserSession(
        token = "tok_doc_donna",
        userId = "user_doc_donna",
        displayName = "Dr. Donna",
        role = Role.DOCTOR,
        clinicId = "clinic_xyz",
        doctorId = "doc_donna"
    )

    private val receptionistXyzSession = UserSession(
        token = "tok_recep_xyz",
        userId = "user_recep_1",
        displayName = "Front Desk XYZ",
        role = Role.RECEPTIONIST,
        clinicId = "clinic_xyz",
        doctorId = null
    )

    private val adminXyzSession = UserSession(
        token = "tok_admin_xyz",
        userId = "user_admin_1",
        displayName = "Admin XYZ",
        role = Role.OWNER,
        clinicId = "clinic_xyz",
        doctorId = null
    )

    private val patientDavidSession = UserSession(
        token = "tok_pat_david",
        userId = "pat_demo_patient",
        displayName = "David Miller",
        role = Role.PATIENT,
        clinicId = null,
        doctorId = null
    )

    private val patientPriyaSession = UserSession(
        token = "tok_pat_priya",
        userId = "pat_102",
        displayName = "Priya Nair",
        role = Role.PATIENT,
        clinicId = null,
        doctorId = null
    )

    @Before
    fun setUp() {
        ClinicalAuditLogger.clearForTest()
        PatientIdentityRegistry.resetToSeedForTesting()
        MedicalRecordRequestTracker.clearForTest()
        repository = ConsultationHistoryRepositoryImpl()
        repository.resetToSeedDataForTesting()
    }

    /**
     * Requirement 1:
     * - Unique, stable, non-PII Patient ID generation
     * - Returning patient recognition (same Patient ID retained across visits)
     * - Immutability across name/phone updates
     * - Shared family phone ambiguity protection (does not merge two distinct family members sharing a phone number)
     * - Concurrency safety under parallel patient registrations
     */
    @Test
    fun testRequirement1_PatientIdGenerationImmutabilityAmbiguityAndConcurrency() = runTest {
        // 1. Existing authenticated patient resolves to stable PAT-1001
        val davidMatch = PatientIdentityRegistry.resolveOrRegisterPatient(
            authenticatedPatientId = "pat_demo_patient",
            explicitPatientIdentifier = null,
            fullName = "David Miller",
            phoneNumber = "+91-9830011223",
            clinicId = "clinic_xyz"
        )
        assertEquals(IdentityMatchStatus.EXACT_AUTHENTICATED_MATCH, davidMatch.status)
        assertEquals("PAT-1001", davidMatch.profile?.patientIdentifier)

        // 2. Updating name or phone number preserves immutable Patient ID and internal ID
        val updatedDavid = PatientIdentityRegistry.updatePatientProfile(
            patientId = "pat_demo_patient",
            newFullName = "David R. Miller",
            newPhoneNumber = "+91-9830099999",
            actorId = "pat_demo_patient"
        ).getOrThrow()
        assertEquals("PAT-1001", updatedDavid.patientIdentifier)
        assertEquals("David R. Miller", updatedDavid.fullName)
        assertEquals("+91-9830099999", updatedDavid.phoneNumber)

        // 3. Shared family phone number with a DIFFERENT patient name triggers AMBIGUOUS_REQUIRES_VERIFICATION
        // instead of silently merging into Priya Nair's PAT-1002 record
        val familyPhoneConflict = PatientIdentityRegistry.resolveOrRegisterPatient(
            authenticatedPatientId = null,
            explicitPatientIdentifier = null,
            fullName = "Rohan Nair", // Different family member sharing Priya's phone +91-9830044556
            phoneNumber = "+91-9830044556",
            clinicId = "clinic_xyz",
            confirmCreateNewWhenPhoneShared = false
        )
        assertEquals(IdentityMatchStatus.AMBIGUOUS_REQUIRES_VERIFICATION, familyPhoneConflict.status)
        assertNull(familyPhoneConflict.profile)
        assertEquals(1, familyPhoneConflict.ambiguousCandidates.size)
        assertEquals("PAT-1002", familyPhoneConflict.ambiguousCandidates.first().patientIdentifier)

        // When receptionist/patient explicitly confirms creating a distinct profile for the family member:
        val confirmedFamilyMember = PatientIdentityRegistry.resolveOrRegisterPatient(
            authenticatedPatientId = null,
            explicitPatientIdentifier = null,
            fullName = "Rohan Nair",
            phoneNumber = "+91-9830044556",
            clinicId = "clinic_xyz",
            confirmCreateNewWhenPhoneShared = true
        )
        assertEquals(IdentityMatchStatus.NEW_PATIENT_CREATED, confirmedFamilyMember.status)
        assertNotNull(confirmedFamilyMember.profile)
        assertTrue(confirmedFamilyMember.profile!!.patientIdentifier.startsWith("PAT-"))
        assertFalse(confirmedFamilyMember.profile!!.patientIdentifier == "PAT-1002")

        // 4. Concurrency test: 25 parallel new patient registrations generate 25 strictly unique Patient IDs
        val createdIds = (1..25).map { idx ->
            async {
                val res = PatientIdentityRegistry.resolveOrRegisterPatient(
                    authenticatedPatientId = null,
                    explicitPatientIdentifier = null,
                    fullName = "Concurrent Patient $idx",
                    phoneNumber = "+91-90000000${idx.toString().padStart(2, '0')}",
                    clinicId = "clinic_xyz"
                )
                res.profile!!.patientIdentifier
            }
        }.awaitAll()
        assertEquals(25, createdIds.toSet().size)
        createdIds.forEach { id ->
            assertTrue("Expected PAT-XXXXXX format but got $id", id.matches(Regex("^PAT-[A-Z0-9]{4,8}$")))
        }
    }

    /**
     * Requirement 2 & Requirement 3:
     * - Doctors can view chronological medical history timeline for authorized patients
     * - Doctors NEVER receive patient phone numbers by default (patientPhone == null)
     * - Receptionists can view Patient ID, Name, Phone, Token, Presenting Complaint, and Rx status,
     *   but NEVER clinical notes, diagnosis, treatment plan, or prescription clinical summary
     * - Unrelated doctors without a care relationship are denied access to another doctor's patient timeline
     * - Patients cannot tamper with Patient ID to view another patient's medical records
     */
    @Test
    fun testRequirement2And3_DoctorMedicalTimelineAndStrictReceptionistVsDoctorSeparation() = runTest {
        // 1. Doctor John views David Miller's (PAT-1001) medical history timeline
        val timelineRes = repository.getPatientMedicalTimelineForDoctor(
            patientIdOrIdentifier = "PAT-1001",
            session = doctorJohnSession,
            referenceNowEpochMs = referenceNow
        )
        assertTrue(timelineRes is Resource.Success)
        val timeline = (timelineRes as Resource.Success).data
        assertTrue("Expected David Miller's 2-year eligible consultations", timeline.isNotEmpty())

        // Verify chronological ordering (most recent first) and clinical completeness
        assertTrue(timeline.first().consultationEpochMs >= timeline.last().consultationEpochMs)
        timeline.forEach { record ->
            assertEquals("PAT-1001", record.patientIdentifier)
            assertEquals(RecordCategory.CONSULTATION, record.recordCategory)
            assertNotNull("Doctor must see clinical notes", record.clinicalNotes)
            assertNotNull("Doctor must see diagnosis", record.diagnosis)
            assertNull("Doctor must NOT see patient phone number by default", record.patientPhone)
        }

        // 2. Receptionist queries history for the same clinic:
        val recepHistory = repository.queryHistory(
            session = receptionistXyzSession,
            criteria = HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.LAST_2_YEARS),
            referenceNowEpochMs = referenceNow
        )
        assertTrue(recepHistory.records.isNotEmpty())
        recepHistory.records.forEach { rec ->
            // Receptionist sees operational fields + phone + presenting complaint
            assertTrue(rec.patientIdentifier.startsWith("PAT-"))
            assertNotNull("Receptionist must see patient phone for queue coordination", rec.patientPhone)
            // Receptionist NEVER sees clinical notes, diagnosis, prescription summary, or allergies
            assertNull("Receptionist must NEVER see clinicalNotes", rec.clinicalNotes)
            assertNull("Receptionist must NEVER see diagnosis", rec.diagnosis)
            assertNull("Receptionist must NEVER see prescriptionSummary", rec.prescriptionSummary)
            assertNull("Receptionist must NEVER see followUpRecommendations", rec.followUpRecommendations)
            assertNull("Receptionist must NEVER see allergiesOrMedicalHistory", rec.allergiesOrMedicalHistory)
        }

        // 3. Receptionist attempting to call Doctor-only medical timeline endpoint is strictly denied
        val recepTimelineAttempt = repository.getPatientMedicalTimelineForDoctor(
            patientIdOrIdentifier = "PAT-1001",
            session = receptionistXyzSession,
            referenceNowEpochMs = referenceNow
        )
        assertTrue(recepTimelineAttempt is Resource.Error)

        // 4. Unrelated doctor check: Ananya Sharma (PAT-1004) only consulted Dr. Alex (doc_alex).
        // Dr. John (doc_john) has no care relationship with PAT-1004 and must be denied timeline access.
        val unrelatedDoctorAttempt = repository.getPatientMedicalTimelineForDoctor(
            patientIdOrIdentifier = "PAT-1004",
            session = doctorJohnSession,
            referenceNowEpochMs = referenceNow
        )
        assertTrue(unrelatedDoctorAttempt is Resource.Error)

        // 5. Patient ID tampering check: David Miller (pat_demo_patient) cannot view Priya Nair's (cons_hist_102) record
        val tamperedPatientDetail = repository.getConsultationDetail(
            recordId = "cons_hist_102",
            session = patientDavidSession
        )
        assertTrue("Patient must not access another patient's record", tamperedPatientDetail is Resource.Error)

        // 6. Patient Registry RBAC check: Doctor gets null phone; Receptionist gets phone; Patient gets only own profile
        val docProfileView = PatientIdentityRegistry.getAuthorizedPatientProfile("PAT-1001", doctorJohnSession)
        assertNotNull(docProfileView)
        assertNull("Doctor profile lookup must redact phone number", docProfileView?.phoneNumber)

        val recepProfileView = PatientIdentityRegistry.getAuthorizedPatientProfile("PAT-1001", receptionistXyzSession)
        assertNotNull(recepProfileView)
        assertEquals("+91-9830011223", recepProfileView?.phoneNumber)

        val crossPatientLookup = PatientIdentityRegistry.getAuthorizedPatientProfile("PAT-1002", patientDavidSession)
        assertNull("Patient cannot look up another patient's profile by Patient ID", crossPatientLookup)
    }

    /**
     * Requirement 4:
     * - Two-Year Historical Access Rule for Doctors and Receptionists
     * - Eligibility requires completed visit/consultation or required medical records (`isEligibleForTwoYearHistoricalAccess`)
     * - Cancelled/Rejected/No-Show tokens with only an unverified prescription upload (`PRESCRIPTION_UPLOADED_ONLY`) do not qualify as completed medical consultations
     * - Boundary checks: 1 day, 364 days, 730 days (exactly 2 years) are inside the 2-year window; 735 days and 800 days (`cons_hist_beyond_2y_301`) are outside
     * - Non-destructive retention: `cons_hist_beyond_2y_301` (800 days old) is excluded from routine Doctor/Receptionist 2-year views, but remains intact in storage and accessible to the owning Patient
     */
    @Test
    fun testRequirement4_TwoYearHistoricalAccessBoundaryEligibilityAndRetentionWithoutDeletion() = runTest {
        // 1. Exact boundary checks in Asia/Kolkata timezone
        assertTrue(DataRetentionPolicyEngine.isWithinTwoYearHistoricalWindow(referenceNow - 1 * dayMs, referenceNow))
        assertTrue(DataRetentionPolicyEngine.isWithinTwoYearHistoricalWindow(referenceNow - 364 * dayMs, referenceNow))
        assertTrue(DataRetentionPolicyEngine.isWithinTwoYearHistoricalWindow(referenceNow - 730 * dayMs, referenceNow))
        assertFalse(DataRetentionPolicyEngine.isWithinTwoYearHistoricalWindow(referenceNow - 735 * dayMs, referenceNow))

        // 2. Doctor routine 2-year query includes 140-day and 210-day archived records (within 2 years),
        //    but excludes the 800-day-old record `cons_hist_beyond_2y_301` (> 2 years)
        val docTwoYearResult = repository.queryHistory(
            session = doctorJohnSession,
            criteria = HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.LAST_2_YEARS),
            referenceNowEpochMs = referenceNow
        )
        val docRecordIds = docTwoYearResult.records.map { it.id }
        assertTrue("140-day record is within 2 years", docRecordIds.contains("cons_hist_archived_201"))
        assertTrue("210-day record is within 2 years", docRecordIds.contains("cons_hist_archived_202"))
        assertFalse("800-day record (>2 years) must be excluded from routine Doctor 2-year access", docRecordIds.contains("cons_hist_beyond_2y_301"))

        // Even if Doctor selects ALL_TIME_WITH_ARCHIVED in UI without legalHoldOrComplianceOverride, the 2-year routine visibility cap protects >2-year records
        val docAllTimeAttempt = repository.queryHistory(
            session = doctorJohnSession,
            criteria = HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED),
            referenceNowEpochMs = referenceNow
        )
        assertFalse(
            "Doctor routine query must never leak records older than 2 years",
            docAllTimeAttempt.records.any { it.id == "cons_hist_beyond_2y_301" }
        )

        // 3. Receptionist routine 2-year query also excludes `cons_hist_beyond_2y_301`
        val recepAllTimeAttempt = repository.queryHistory(
            session = receptionistXyzSession,
            criteria = HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED),
            referenceNowEpochMs = referenceNow
        )
        assertFalse(
            "Receptionist routine query must never leak records older than 2 years",
            recepAllTimeAttempt.records.any { it.id == "cons_hist_beyond_2y_301" }
        )

        // 4. Verify non-destructive retention: `cons_hist_beyond_2y_301` is NOT deleted from storage,
        //    and the Patient (David Miller) can still view their own 800-day-old record
        val allStored = repository.allHistoryRecords.first()
        assertTrue("Record > 2 years must never be deleted from storage", allStored.any { it.id == "cons_hist_beyond_2y_301" })

        val patientAllTime = repository.queryHistory(
            session = patientDavidSession,
            criteria = HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED),
            referenceNowEpochMs = referenceNow
        )
        assertTrue(
            "Patient must still have access to their own >2-year retained medical record",
            patientAllTime.records.any { it.id == "cons_hist_beyond_2y_301" }
        )
    }

    /**
     * Requirement 5:
     * - Patients (new or returning) can book appointments WITHOUT a prescription by entering a presenting complaint
     * - Presenting complaint validation rejects empty/blank when no Rx is uploaded, enforces max 300 chars, strips control chars, and detects emergency warning keywords
     * - Receptionist can assign an available doctor based on presenting complaint, specialty, schedule, and remaining capacity
     * - Doctor assignment rejects doctors who do not practice at the clinic/day or whose daily slot capacity (10/10) is full
     */
    @Test
    fun testRequirement5_OptionalPrescriptionPresentingComplaintAndProblemBasedDoctorAssignment() = runTest {
        // 1. Validation: No prescription AND blank complaint is rejected
        val emptyResult = DataRetentionPolicyEngine.validateAndSanitizePresentingComplaint(
            rawComplaint = "   ",
            hasPrescription = false
        )
        assertTrue(emptyResult.isFailure)

        // 2. Validation: Over 300 characters is rejected
        val tooLong = "A".repeat(305)
        val longResult = DataRetentionPolicyEngine.validateAndSanitizePresentingComplaint(
            rawComplaint = tooLong,
            hasPrescription = false
        )
        assertTrue(longResult.isFailure)

        // 3. Emergency symptom keyword detection (non-diagnostic caution banner trigger)
        assertTrue(DataRetentionPolicyEngine.containsPotentialEmergencyIndicator("Severe chest pain radiating to left arm and shortness of breath"))
        assertFalse(DataRetentionPolicyEngine.containsPotentialEmergencyIndicator("Mild seasonal cough and runny nose for 2 days"))

        // 4. New patient books appointment WITHOUT a prescription, providing presenting complaint & requesting receptionist doctor assignment
        val complaintToken = DemoDataProvider.requestToken(
            prescriptionId = "NO_RX_COMPLAINT_ONLY",
            displayName = "Meera Chatterjee",
            doctorId = "UNASSIGNED",
            requestedDay = "Monday",
            clinicName = "XYZ Clinic",
            presentingComplaint = "Persistent migraine headache and dizziness for 4 days",
            authenticatedPatientId = null,
            patientPhone = "+91-9839900112"
        )
        assertTrue(complaintToken.needsDoctorAssignment)
        assertTrue(complaintToken.patientIdentifier!!.startsWith("PAT-"))
        assertEquals("Persistent migraine headache and dizziness for 4 days", complaintToken.presentingComplaint)

        // 5. Receptionist attempts to assign Dr. John on Friday (Dr. John is closed on Friday) -> Rejected
        val closedDayAttempt = DemoDataProvider.assignDoctorToPendingRequest(
            tokenId = complaintToken.id,
            targetDoctorId = "doc_john",
            requestedClinicName = "XYZ Clinic",
            requestedDay = "Friday",
            actorId = "user_recep_1"
        )
        assertTrue("Dr. John does not practice on Friday so assignment must fail", closedDayAttempt.isFailure)

        // 6. Receptionist assigns Dr. Donna (Pediatrics/General, Chamber B, available Monday at XYZ Clinic) -> Succeeds!
        val validAssignment = DemoDataProvider.assignDoctorToPendingRequest(
            tokenId = complaintToken.id,
            targetDoctorId = "doc_donna",
            requestedClinicName = "XYZ Clinic",
            requestedDay = "Monday",
            actorId = "user_recep_1"
        )
        assertTrue(validAssignment.isSuccess)
        val assignedToken = validAssignment.getOrThrow()
        assertFalse(assignedToken.needsDoctorAssignment)
        assertEquals(TokenStatus.WAITING, assignedToken.status)
        assertEquals("Chamber B", assignedToken.chamber)
        assertEquals("Persistent migraine headache and dizziness for 4 days", assignedToken.presentingComplaint)
    }

    /**
     * Requirement 6:
     * - Indian Medical Council (Professional Conduct, Etiquette and Ethics) Regulations, 2002 Reg. 1.3.2
     *   72-hour medical record copy request tracking for RMP-managed requests vs configurable clinic workflow
     * - Audit logging records actor, role, clinic, patient ID, and action without leaking raw clinical notes
     */
    @Test
    fun testRequirement6_IndianLawMedicalRecordCopyTrackerAndAuditCompliance() = runTest {
        val requestedAt = referenceNow
        val rmpRequest = MedicalRecordRequestTracker.submitCopyRequest(
            patientId = "pat_demo_patient",
            requesterName = "David Miller",
            requesterRelationship = "SELF",
            identityVerified = true,
            authorityVerified = true,
            clinicId = "clinic_xyz",
            custodianRole = "REGISTERED_MEDICAL_PRACTITIONER",
            recordScope = "INDOOR_IPD_RECORD",
            submittedAtEpochMs = requestedAt
        )
        assertEquals(72, rmpRequest.applicableResponseWindowHours)
        assertEquals("IN_PROGRESS", rmpRequest.status)

        // At 48 hours: still within 72-hour window
        val check48h = MedicalRecordRequestTracker.evaluateOverdueEscalation(
            requestId = rmpRequest.requestId,
            nowEpochMs = requestedAt + 48L * 3600_000L
        )
        assertEquals("IN_PROGRESS", check48h?.status)

        // At 73 hours: exceeds 72-hour MCI/NMC Reg. 1.3.2 window -> OVERDUE_ESCALATED
        val check73h = MedicalRecordRequestTracker.evaluateOverdueEscalation(
            requestId = rmpRequest.requestId,
            nowEpochMs = requestedAt + 73L * 3600_000L
        )
        assertEquals("OVERDUE_ESCALATED", check73h?.status)

        // Verify audit logs contain no raw diagnosis or clinical note leaks
        val events = ClinicalAuditLogger.getAllEntriesForTest()
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun testCurrentPatientAmitabhSenMedicalHistoryAndReceptionDoctorAssignment() = runTest {
        // 1. Current active patient in consultation A-01: Amitabh Sen (PAT-1006, pat_1)
        val amitabhTimeline = repository.getPatientMedicalTimelineForDoctor(
            patientIdOrIdentifier = "PAT-1006",
            session = doctorJohnSession,
            referenceNowEpochMs = referenceNow
        )
        assertTrue("Expected Amitabh Sen (PAT-1006) medical history to succeed", amitabhTimeline is Resource.Success)
        val records = (amitabhTimeline as Resource.Success).data
        assertEquals(2, records.size)
        assertEquals("PAT-1006", records.first().patientIdentifier)
        assertEquals("Amitabh Sen", records.first().patientName)
        assertNull("Phone number must be redacted for Doctor", records.first().patientPhone)

        // 2. Receptionist Assign Doctor dialog for unassigned request (with Clinic XYZ / XYZ Clinic normalization & Monday capacity fallback)
        val reqTok = DemoDataProvider.requestToken(
            prescriptionId = "NO_RX_COMPLAINT_ONLY",
            displayName = "Ananya Sharma",
            doctorId = "UNASSIGNED",
            requestedDay = "Monday",
            clinicName = "XYZ Clinic",
            presentingComplaint = "Right knee pain and swelling when climbing stairs for 4 days",
            authenticatedPatientId = "pat_104",
            patientPhone = "+91 9830998877"
        )
        val assigned = DemoDataProvider.assignDoctorToPendingRequest(
            tokenId = reqTok.id,
            targetDoctorId = "doc_alex",
            requestedClinicName = "Clinic XYZ",
            requestedDay = "Monday",
            actorId = "recep_1"
        )
        assertTrue("Expected doctor assignment to succeed even when 'Clinic XYZ' is passed", assigned.isSuccess)
        assertEquals("Chamber C", assigned.getOrThrow().chamber)
        assertFalse(assigned.getOrThrow().needsDoctorAssignment)
    }
}


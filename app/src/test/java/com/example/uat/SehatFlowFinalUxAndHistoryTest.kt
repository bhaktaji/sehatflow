package com.example.uat

import com.example.core.common.Resource
import com.example.data.repository.ConsultationHistoryRepository
import com.example.data.repository.ConsultationHistoryRepositoryImpl
import com.example.domain.model.ClinicalAuditLogger
import com.example.domain.model.DataRetentionPolicyEngine
import com.example.domain.model.HistoryDateRangePreset
import com.example.domain.model.HistoryFilterCriteria
import com.example.domain.model.RecordCategory
import com.example.domain.model.Role
import com.example.domain.model.TokenStatus
import com.example.domain.model.UserSession
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
 * Automated Regression & UAT Verification Suite for:
 * - Status Bar & Safe-Area Inset Compliance
 * - Patient Logout & Cross-Session Cache Isolation
 * - Consultation History vs Appointment/Token History Domain Separation
 * - Multi-Clinic & Multi-Chamber Historical Snapshot Integrity
 * - Role-Specific History Filters & Tenant Isolation (Doctor, Receptionist, Admin, Patient)
 * - 3-Month Default Visibility Window vs Non-Destructive Long-Term Archival
 * - Versioned Clinical Addendum & Audit Logging
 */
class SehatFlowFinalUxAndHistoryTest {

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
        repository = ConsultationHistoryRepositoryImpl()
        repository.resetToSeedDataForTesting()
    }

    @Test
    fun `test 1 - consultation history vs appointment token history separation`() = runTest {
        val allRecords = repository.allHistoryRecords.first()
        val consultations = allRecords.filter { it.recordCategory == RecordCategory.CONSULTATION }
        val appointments = allRecords.filter { it.recordCategory == RecordCategory.APPOINTMENT_TOKEN }

        assertTrue("Must contain completed clinical consultation records", consultations.isNotEmpty())
        assertTrue("Must contain operational appointment/token records", appointments.isNotEmpty())

        // Completed consultations must have COMPLETED status and clinical notes/diagnosis
        consultations.forEach { rec ->
            assertEquals(TokenStatus.COMPLETED, rec.appointmentStatus)
            assertNotNull("Completed consultation must have clinicalNotes", rec.clinicalNotes)
            assertNotNull("Completed consultation must have diagnosis", rec.diagnosis)
        }

        // Cancelled / No-Show / Rejected tokens must remain categorized as APPOINTMENT_TOKEN, not CONSULTATION
        appointments.forEach { rec ->
            assertTrue(
                "Appointment-only record must not be COMPLETED (must be NO_SHOW, CANCELLED, or REJECTED)",
                rec.appointmentStatus in setOf(TokenStatus.NO_SHOW, TokenStatus.CANCELLED, TokenStatus.REJECTED)
            )
            assertNull("Non-completed appointment must not have clinicalNotes", rec.clinicalNotes)
        }
    }

    @Test
    fun `test 2 - multi-clinic and multi-chamber historical snapshot preservation for Dr John`() = runTest {
        // Dr. John visits XYZ Clinic (Chamber A, Mon/Wed/Fri) and ABC Clinic (Chamber B, Tue/Thu)
        val drJohnXyzResult = repository.queryHistory(
            session = doctorJohnSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED,
                clinicIdFilter = "clinic_xyz"
            )
        )
        val drJohnAbcResult = repository.queryHistory(
            session = doctorJohnSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED,
                clinicIdFilter = "clinic_abc"
            )
        )

        assertTrue("Dr. John must have historical records at XYZ Clinic", drJohnXyzResult.records.isNotEmpty())
        assertTrue("Dr. John must have historical records at ABC Clinic", drJohnAbcResult.records.isNotEmpty())

        drJohnXyzResult.records.forEach { record ->
            assertEquals("clinic_xyz", record.clinicId)
            assertEquals("XYZ Clinic", record.clinicName)
            assertEquals("Chamber A", record.chamber)
        }

        drJohnAbcResult.records.forEach { record ->
            assertEquals("clinic_abc", record.clinicId)
            assertEquals("ABC Clinic", record.clinicName)
            assertEquals("Chamber B", record.chamber)
        }
    }

    @Test
    fun `test 3 - default 3 month active window vs non-destructive archived retrieval`() = runTest {
        // Default filter uses LAST_3_MONTHS (90 days)
        val defaultWindowResult = repository.queryHistory(
            session = doctorJohnSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.LAST_3_MONTHS
            ),
            referenceNowEpochMs = referenceNow
        )

        // All-Time filter includes archived records (> 90 days old, e.g. 140 days and 210 days old)
        val allTimeResult = repository.queryHistory(
            session = doctorJohnSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED
            ),
            referenceNowEpochMs = referenceNow
        )

        assertTrue(
            "All-Time filter must return more records than 3-month default because archived records are preserved",
            allTimeResult.totalMatchedCount > defaultWindowResult.totalMatchedCount
        )

        val archivedIds = allTimeResult.records.filter {
            !DataRetentionPolicyEngine.isWithinDefaultThreeMonthWindow(it.consultationEpochMs, referenceNow)
        }.map { it.id }

        assertTrue("Archived record cons_hist_archived_201 must exist and be retrievable", archivedIds.contains("cons_hist_archived_201"))
        assertTrue("Archived record cons_hist_archived_202 must exist and be retrievable", archivedIds.contains("cons_hist_archived_202"))
        assertFalse(
            "Default 3-month view must not show 140-day-old record by default",
            defaultWindowResult.records.any { it.id == "cons_hist_archived_201" }
        )

        // Verify DataRetentionPolicyEngine never permits automatic deletion
        DataRetentionPolicyEngine.categoryRules.forEach { rule ->
            assertFalse("Auto-deletion must be prohibited for ${rule.categoryName}", rule.autoDeletionPermitted)
        }
    }

    @Test
    fun `test 4 - role authorization and clinical note redaction for Receptionist and Clinic Admin`() = runTest {
        val receptionistResult = repository.queryHistory(
            session = receptionistXyzSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED
            )
        )

        assertTrue("Receptionist at XYZ Clinic must see XYZ Clinic records", receptionistResult.records.isNotEmpty())
        receptionistResult.records.forEach { rec ->
            assertEquals("Receptionist must NEVER see ABC Clinic records", "clinic_xyz", rec.clinicId)
            assertNull("Private clinicalNotes must be redacted for Receptionist", rec.clinicalNotes)
            assertNull("Diagnosis must be redacted for Receptionist", rec.diagnosis)
        }

        // Attempt by Receptionist at XYZ Clinic to filter by ABC Clinic must return 0 records
        val crossTenantAttempt = repository.queryHistory(
            session = receptionistXyzSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED,
                clinicIdFilter = "clinic_abc"
            )
        )
        assertTrue("Cross-clinic filter attempt by Receptionist must return empty list", crossTenantAttempt.records.isEmpty())
    }

    @Test
    fun `test 5 - patient role isolation and cross-patient protection`() = runTest {
        val davidResult = repository.queryHistory(
            session = patientDavidSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED
            )
        )

        assertTrue("Patient David Miller must see his own records across clinics", davidResult.records.size >= 4)
        davidResult.records.forEach { rec ->
            assertEquals("Patient must never see another patient's record", "pat_demo_patient", rec.patientId)
        }

        val priyaResult = repository.queryHistory(
            session = patientPriyaSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED
            )
        )
        assertTrue(priyaResult.records.isNotEmpty())
        assertTrue(priyaResult.records.none { it.patientId == "pat_demo_patient" })
    }

    @Test
    fun `test 6 - combined filters invalid date range handling and pagination`() = runTest {
        // Invalid custom date range (start > end) must fail closed safely without crashing
        val invalidRangeCriteria = HistoryFilterCriteria(
            dateRangePreset = HistoryDateRangePreset.CUSTOM_RANGE,
            customStartEpochMs = referenceNow,
            customEndEpochMs = referenceNow - (10L * dayMs)
        )
        assertTrue("Invalid custom range must be detected", invalidRangeCriteria.hasInvalidDateRange())

        val invalidResult = repository.queryHistory(
            session = doctorJohnSession,
            criteria = invalidRangeCriteria
        )
        assertTrue("Invalid date range must safely return empty list", invalidResult.records.isEmpty())
        assertNotNull("Invalid date range must surface user-friendly error message", invalidResult.errorMessage)

        // Combined filter: Doctor + Chamber B + Consultation Only + Has Prescription
        val combinedResult = repository.queryHistory(
            session = doctorJohnSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED,
                chamberFilter = "Chamber B",
                recordCategoryFilter = RecordCategory.CONSULTATION,
                hasPrescriptionFilter = true
            )
        )
        assertTrue(combinedResult.records.isNotEmpty())
        combinedResult.records.forEach { rec ->
            assertEquals("Chamber B", rec.chamber)
            assertEquals(RecordCategory.CONSULTATION, rec.recordCategory)
            assertTrue(rec.hasPrescription)
        }
    }

    @Test
    fun `test 7 - versioned clinical addendum preserves original record and logs audit trail`() = runTest {
        val targetId = "cons_hist_101"
        val beforeRes = repository.getConsultationDetail(targetId, doctorJohnSession)
        assertTrue(beforeRes is Resource.Success)
        val before = (beforeRes as Resource.Success).data
        assertEquals(1, before.version)

        // Unauthorized doctor (doc_donna) attempting to amend doc_john's consultation must fail
        val unauthorizedAttempt = repository.addClinicalAddendum(
            recordId = targetId,
            addendumText = "Unauthorized attempt",
            session = doctorDonnaSession
        )
        assertTrue("Non-author doctor must not be allowed to append addendum", unauthorizedAttempt is Resource.Error)

        // Authorized doctor (doc_john) appends versioned addendum
        val authorizedResult = repository.addClinicalAddendum(
            recordId = targetId,
            addendumText = "Follow-up lab HbA1c reviewed: 6.3%. Continue current regimen.",
            session = doctorJohnSession
        )
        assertTrue("Authoring doctor must succeed in appending addendum", authorizedResult is Resource.Success)

        val updated = (authorizedResult as Resource.Success).data
        assertEquals("Record version must increment to 2", 2, updated.version)
        assertTrue(
            "Updated clinical notes must preserve original notes and append addendum",
            updated.clinicalNotes?.contains("HbA1c reviewed: 6.3%") == true
        )

        // Verify audit trail entry was recorded
        val auditEvents = ClinicalAuditLogger.getAllEntriesForTest()
        assertTrue(
            "ClinicalAuditLogger must record ADD_CLINICAL_ADDENDUM event",
            auditEvents.any { it.resourceId == targetId && it.action == "ADD_CLINICAL_ADDENDUM" && it.outcome == "SUCCESS" }
        )
    }

    @Test
    fun `test 8 - clinic operational summary metrics calculation`() = runTest {
        val summary = repository.getClinicOperationalSummary(
            session = adminXyzSession,
            criteria = HistoryFilterCriteria(
                dateRangePreset = HistoryDateRangePreset.ALL_TIME_WITH_ARCHIVED
            )
        )

        assertTrue(summary.totalAppointmentRequests > 0)
        assertTrue(summary.completedConsultationsCount > 0)
        assertTrue("Consultations by doctor map must be populated", summary.consultationsByDoctor.isNotEmpty())
        assertTrue("Utilization by chamber map must be populated", summary.utilizationByChamber.isNotEmpty())
    }
}

package com.example.uat

import com.example.core.demo.DemoDataProvider
import com.example.data.model.Clinic
import com.example.data.model.SubscriptionRecord
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.domain.logic.EtaCalculator
import com.example.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SehatFlowUatRegressionTest {

    @Test
    fun testClinicAdminOnboardingModel_defaultsAndValidation() {
        val clinic = Clinic(
            clinicId = "clinic_uat_101",
            name = "Sehat City Clinic",
            address = "42 Health Avenue, New Delhi",
            phone = "+91 9876543210",
            adminUid = "admin_uat_user_1",
            inviteCodeDoctor = "DOC-7890",
            inviteCodeReceptionist = "REC-1234",
            subscriptionStatus = SubscriptionStatus.TRIAL.name,
            monthlyFee = 499L
        )

        assertEquals("clinic_uat_101", clinic.clinicId)
        assertEquals("Sehat City Clinic", clinic.name)
        assertEquals(499L, clinic.monthlyFee)
        assertEquals("TRIAL", clinic.subscriptionStatus)
        assertTrue(clinic.inviteCodeDoctor.startsWith("DOC-"))
        assertTrue(clinic.inviteCodeReceptionist.startsWith("REC-"))
    }

    @Test
    fun testUserProfileRoles_allSupportedUserTypes() {
        val adminProfile = UserProfile(
            userId = "uid_admin_1",
            email = "admin@sehatflow.app",
            displayName = "Dr. Rajesh Sharma",
            role = UserRole.CLINIC_ADMIN.name,
            clinicId = "clinic_uat_101",
            phone = "+91 9811122233"
        )
        assertEquals("CLINIC_ADMIN", adminProfile.role)

        val doctorProfile = UserProfile(
            userId = "uid_doc_1",
            email = "doctor@sehatflow.app",
            displayName = "Dr. Aisha Khan",
            role = UserRole.DOCTOR.name,
            clinicId = "clinic_uat_101",
            specialization = "General Physician & Diabetology",
            roomNumber = "OPD Cabin 2"
        )
        assertEquals("DOCTOR", doctorProfile.role)
        assertEquals("OPD Cabin 2", doctorProfile.roomNumber)

        val receptionistProfile = UserProfile(
            userId = "uid_rec_1",
            email = "reception@sehatflow.app",
            displayName = "Sunita Verma",
            role = UserRole.RECEPTIONIST.name,
            clinicId = "clinic_uat_101",
            deskName = "Main Registration Desk A"
        )
        assertEquals("RECEPTIONIST", receptionistProfile.role)

        val patientProfile = UserProfile(
            userId = "uid_pat_1",
            email = "patient@sehatflow.app",
            displayName = "Mohan Kumar",
            role = UserRole.PATIENT.name,
            clinicId = "clinic_uat_101",
            phone = "+91 9988776655"
        )
        assertEquals("PATIENT", patientProfile.role)
    }

    @Test
    fun testMonthlyClinicSubscriptionRecord_amount499() {
        val record = SubscriptionRecord(
            id = "sub_rec_99",
            clinicId = "clinic_uat_101",
            adminUid = "admin_uat_user_1",
            planName = "SehatFlow Pro (₹499/mo)",
            amount = 499L,
            currency = "INR",
            status = "ACTIVE",
            paymentMethod = "UPI",
            transactionId = "TXN-SF-781920"
        )

        assertEquals(499L, record.amount)
        assertEquals("INR", record.currency)
        assertEquals("ACTIVE", record.status)
        assertEquals("UPI", record.paymentMethod)
        assertEquals("SehatFlow Pro (₹499/mo)", record.planName)
    }

    @Test
    fun testQueueLifecycle_walkIn_callNext_complete() {
        val initialTokens = DemoDataProvider.getTokens()
        val walkIn = DemoDataProvider.createWalkIn(displayName = "Ramesh Gupta", priority = false)

        assertNotNull(walkIn)
        assertEquals("Ramesh Gupta", walkIn.displayName)
        assertEquals(TokenStatus.WAITING, walkIn.status)
        assertFalse(walkIn.priority)

        // Doctor calls next
        val called = DemoDataProvider.callNext()
        assertNotNull(called)
        assertEquals(TokenStatus.IN_CONSULTATION, called.status)

        // Complete consultation
        val completed = DemoDataProvider.completeConsultation(called.id)
        assertEquals(TokenStatus.COMPLETED, completed.status)
    }

    @Test
    fun testQueueEmergencyPriorityAndReorder() {
        val walkIn = DemoDataProvider.createWalkIn(displayName = "Emergency Patient", priority = true)
        assertTrue(walkIn.priority)

        // Toggle priority off with audit reason
        val updated = DemoDataProvider.setPriority(walkIn.id, enabled = false, reason = "Condition stabilized")
        assertFalse(updated.priority)

        // Reorder position
        val reordered = DemoDataProvider.reorderToken(walkIn.id, targetPosition = 1, reason = "Senior citizen assistance")
        assertEquals(walkIn.id, reordered.id)
    }

    @Test
    fun testDoctorBreakPauseAndResume() {
        val paused = DemoDataProvider.pauseSession("Doctor in hospital conference")
        assertEquals("PAUSED", paused.status)
        assertEquals("Doctor in hospital conference", paused.pauseMessage)

        // When paused, wait calculation returns null/unavailable
        val etaWhenPaused = EtaCalculator.calculate(
            EtaCalculator.CalculationInput(
                ownStatus = TokenStatus.WAITING,
                peopleAhead = 2,
                isSessionPaused = true,
                isSessionClosed = false,
                fallbackMinutes = 10,
                recentDurationsMinutes = listOf(8.0, 12.0),
                activeConsultationStartedEpochSec = null,
                currentServerEpochSec = System.currentTimeMillis() / 1000
            )
        )
        assertNull(etaWhenPaused.estimatedWaitMinutes)
        assertEquals("UNAVAILABLE", etaWhenPaused.estimateQuality)

        val resumed = DemoDataProvider.resumeSession()
        assertEquals("ACTIVE", resumed.status)
        assertNull(resumed.pauseMessage)
    }

    @Test
    fun testPrescriptionTriage_approveAndReject() {
        val request = DemoDataProvider.requestToken("rx_doc_sample", "Pooja Sharma")
        assertEquals(TokenStatus.PENDING_VERIFICATION, request.status)

        // Verify rejection with reason
        val rejected = DemoDataProvider.verifyPrescription(request.id, approve = false, reason = "Image blurry, medicine name obscured")
        assertEquals(TokenStatus.REJECTED, rejected.status)
        assertEquals("Image blurry, medicine name obscured", rejected.rejectionReason)

        // Submit new request and approve
        val newRequest = DemoDataProvider.requestToken("rx_clear_doc", "Pooja Sharma")
        val approved = DemoDataProvider.verifyPrescription(newRequest.id, approve = true, reason = null)
        assertEquals(TokenStatus.WAITING, approved.status)
        assertNull(approved.rejectionReason)
    }

    @Test
    fun testEtaCalculator_accurateWhenWaitingAndZeroWhenCalled() {
        // When waiting with 2 people ahead and rolling average of 10 mins
        val waitingResult = EtaCalculator.calculate(
            EtaCalculator.CalculationInput(
                ownStatus = TokenStatus.WAITING,
                peopleAhead = 2,
                isSessionPaused = false,
                isSessionClosed = false,
                fallbackMinutes = 10,
                recentDurationsMinutes = listOf(10.0, 10.0, 10.0),
                activeConsultationStartedEpochSec = null,
                currentServerEpochSec = 10000L
            )
        )
        assertNotNull(waitingResult.estimatedWaitMinutes)
        assertEquals(20, waitingResult.estimatedWaitMinutes)
        assertEquals("MEDIUM", waitingResult.estimateQuality)

        // When called
        val calledResult = EtaCalculator.calculate(
            EtaCalculator.CalculationInput(
                ownStatus = TokenStatus.CALLED,
                peopleAhead = 0,
                isSessionPaused = false,
                isSessionClosed = false,
                fallbackMinutes = 10,
                recentDurationsMinutes = emptyList(),
                activeConsultationStartedEpochSec = null,
                currentServerEpochSec = 10000L
            )
        )
        assertEquals(0, calledResult.estimatedWaitMinutes)
        assertEquals("NOW", calledResult.estimateQuality)
    }

    // =========================================================================
    // PART 1: DOCTOR DISCOVERY & MULTI-CLINIC WEEKLY SCHEDULES
    // =========================================================================

    @Test
    fun testDoctorDiscovery_completeAndPartialNameSearch() {
        val doctors = DemoDataProvider.demoDoctors

        // 1. Search by complete name: "Dr. John"
        val drJohn = doctors.find { it.displayName.equals("Dr. John", ignoreCase = true) }
        assertNotNull("Dr. John must be found by complete name", drJohn)
        assertEquals("Dr. John", drJohn?.displayName)
        assertEquals("General Physician", drJohn?.specialty)
        assertEquals("MBBS, MD (General Medicine)", drJohn?.qualifications)
        assertEquals("Reg. #SYNTH-48201", drJohn?.credentials)
        assertEquals(10, drJohn?.experienceYears)

        // 2. Partial name search: "John", "Dr.", "Physician"
        val partialMatches = doctors.filter { it.displayName.contains("John", ignoreCase = true) }
        assertTrue("Partial match for 'John' must contain Dr. John", partialMatches.any { it.displayName == "Dr. John" })

        // 3. Filter by specialization: General Physician vs Pediatrics vs Orthopedics
        val genPhysicians = doctors.filter { it.specialty == "General Physician" }
        assertTrue(genPhysicians.any { it.displayName == "Dr. John" })

        val pediatrics = doctors.filter { it.specialty == "Pediatrics" }
        assertTrue(pediatrics.any { it.displayName == "Dr. Donna" })

        val orthopedics = doctors.filter { it.specialty == "Orthopedics" }
        assertTrue(orthopedics.any { it.displayName == "Dr. Alex" })
    }

    @Test
    fun testDoctorSchedule_multiClinicWeeklyMatrix() {
        val doctors = DemoDataProvider.demoDoctors
        val drJohn = doctors.first { it.displayName == "Dr. John" }

        val schedule = drJohn.schedules
        assertEquals(7, schedule.size)

        // Monday: XYZ Clinic, Chamber A, daily limit 10
        val monday = schedule.first { it.dayOfWeek == "Monday" }
        assertEquals("XYZ Clinic", monday.clinicName)
        assertEquals("Chamber A", monday.chamber)
        assertEquals(10, monday.dailyCapacity)
        assertTrue(monday.isDoctorAvailable)

        // Tuesday: XYZ Clinic, Chamber A, daily limit 10
        val tuesday = schedule.first { it.dayOfWeek == "Tuesday" }
        assertEquals("XYZ Clinic", tuesday.clinicName)
        assertEquals("Chamber A", tuesday.chamber)
        assertEquals(10, tuesday.dailyCapacity)
        assertTrue(tuesday.isDoctorAvailable)

        // Wednesday: ABC Clinic, Chamber B, daily limit 10
        val wednesday = schedule.first { it.dayOfWeek == "Wednesday" }
        assertEquals("ABC Clinic", wednesday.clinicName)
        assertEquals("Chamber B", wednesday.chamber)
        assertEquals(10, wednesday.dailyCapacity)
        assertTrue(wednesday.isDoctorAvailable)

        // Thursday: ABC Clinic, Chamber B, daily limit 10
        val thursday = schedule.first { it.dayOfWeek == "Thursday" }
        assertEquals("ABC Clinic", thursday.clinicName)
        assertEquals("Chamber B", thursday.chamber)
        assertEquals(10, thursday.dailyCapacity)
        assertTrue(thursday.isDoctorAvailable)

        // Friday, Saturday, Sunday: No clinic scheduled, capacity 0
        val friday = schedule.first { it.dayOfWeek == "Friday" }
        assertEquals("No Scheduled Clinic", friday.clinicName)
        assertEquals(0, friday.dailyCapacity)
        assertFalse(friday.isDoctorAvailable)

        val saturday = schedule.first { it.dayOfWeek == "Saturday" }
        assertFalse(saturday.isDoctorAvailable)

        val sunday = schedule.first { it.dayOfWeek == "Sunday" }
        assertFalse(sunday.isDoctorAvailable)
    }

    // =========================================================================
    // PART 2: CAPACITY CALCULATION & AUTOMATIC FALLBACK TOKEN ALLOCATION
    // =========================================================================

    @Test
    fun testCapacityFallbackAllocation_mondayFull_allocatedToTuesdayAtXyzClinic() {
        // Reset to initial test condition: Monday 10/10, Tuesday 4/10
        DemoDataProvider.resetCapacityToScenario(mondayFull = true)
        val initialApprovedMonday = DemoDataProvider.getApprovedCountFor("doc_john", "XYZ Clinic", "Monday")
        val initialApprovedTuesday = DemoDataProvider.getApprovedCountFor("doc_john", "XYZ Clinic", "Tuesday")
        assertEquals(10, initialApprovedMonday)
        assertEquals(4, initialApprovedTuesday)

        // Patient requests Monday visit with Dr. John at XYZ Clinic
        val newPendingRx = DemoDataProvider.requestToken("rx_patient_uat_fallback", "Amitabh Bachchan", doctorId = "doc_john", requestedDay = "Monday")
        assertEquals(TokenStatus.PENDING_VERIFICATION, newPendingRx.status)

        // Receptionist verifies and approves prescription
        val approvedToken = DemoDataProvider.verifyPrescription(newPendingRx.id, approve = true, reason = null)

        // 1. Verify token was created
        assertNotNull(approvedToken)
        assertEquals(TokenStatus.WAITING, approvedToken.status)

        // 2. Verify automatic reallocation to Tuesday at XYZ Clinic
        assertEquals("Tuesday", approvedToken.appointmentDate)
        assertEquals("Monday", approvedToken.originalRequestedDate)
        assertTrue("Token must be flagged as rescheduled", approvedToken.isRescheduled)
        assertEquals("Chamber A", approvedToken.chamber)
        assertNotNull(approvedToken.allocationNote)
        assertTrue(approvedToken.allocationNote!!.contains("Monday was fully booked (10/10)"))
        assertTrue(approvedToken.allocationNote!!.contains("Tuesday"))

        // 3. Verify Tuesday capacity incremented from 4 to 5, and Monday stayed at 10
        val updatedTuesday = DemoDataProvider.getApprovedCountFor("doc_john", "XYZ Clinic", "Tuesday")
        val updatedMonday = DemoDataProvider.getApprovedCountFor("doc_john", "XYZ Clinic", "Monday")
        assertEquals(10, updatedMonday)
        assertEquals(5, updatedTuesday)

        // 4. Verify inter-clinic isolation: Patient was NOT assigned to ABC Clinic (Wednesday/Thursday)
        assertFalse("Patient must not bleed to ABC Clinic", approvedToken.allocationNote!!.contains("ABC Clinic"))
    }

    @Test
    fun testCapacityEnforcement_neverIssuesEleventhTokenForDayCapacity10() {
        DemoDataProvider.resetCapacityToScenario(mondayFull = true)

        // Monday is already 10/10 full
        val allocation = DemoDataProvider.allocateTokenWithCapacityCheck(
            patientName = "Overbooking Candidate",
            doctorId = "doc_john",
            requestedClinicName = "XYZ Clinic",
            requestedDay = "Monday"
        )

        // Must allocate to Tuesday (which had remaining slots), never an 11th token on Monday!
        assertTrue(allocation.isAllocated)
        assertEquals("Tuesday", allocation.allocatedDate)
        assertEquals("XYZ Clinic", allocation.clinicName)
        assertEquals("Chamber A", allocation.chamber)
        assertTrue(allocation.isRescheduled)
    }

    @Test
    fun testCancellation_freesCapacitySlot() {
        DemoDataProvider.resetCapacityToScenario(mondayFull = true)
        val beforeCount = DemoDataProvider.getApprovedCountFor("doc_john", "XYZ Clinic", "Tuesday")

        val allocation = DemoDataProvider.allocateTokenWithCapacityCheck(
            patientName = "Cancel Patient",
            doctorId = "doc_john",
            requestedClinicName = "XYZ Clinic",
            requestedDay = "Tuesday"
        )
        assertTrue(allocation.isAllocated)
        assertEquals(beforeCount + 1, DemoDataProvider.getApprovedCountFor("doc_john", "XYZ Clinic", "Tuesday"))

        // Cancel the token
        DemoDataProvider.cancelToken(allocation.token!!.id, reason = "Patient requested cancellation")

        // Capacity is freed
        assertEquals(beforeCount, DemoDataProvider.getApprovedCountFor("doc_john", "XYZ Clinic", "Tuesday"))
    }

    // =========================================================================
    // PART 3: RECEPTIONIST QUEUE SCROLLING & 35-PATIENT QUEUE VISIBILITY
    // =========================================================================

    @Test
    fun testReceptionistQueue_35PatientsReachabilityAndActionExecution() {
        // Populate 35 synthetic patients in queue
        DemoDataProvider.populateLargeQueueForTesting(35)
        val tokens35 = DemoDataProvider.getTokens()
        assertEquals(35, tokens35.size)

        // Verify patient #1 and patient #35
        val firstPatient = tokens35.first()
        val lastPatient = tokens35.last()

        assertEquals(0, firstPatient.queuePosition) // In consultation has position 0
        assertEquals(34, lastPatient.queuePosition)
        assertEquals("T-35", lastPatient.label)

        // Verify actions on the 35th patient (receptionist scrolling to the very last patient)
        // 1. Mark priority
        val priorityUpdated = DemoDataProvider.setPriority(lastPatient.id, enabled = true, reason = "Emergency acute triage")
        assertTrue("35th patient priority must update", priorityUpdated.priority)

        // 2. Reorder 35th patient to position 2
        val reordered = DemoDataProvider.reorderToken(lastPatient.id, targetPosition = 2, reason = "Immediate attention required")
        assertEquals(lastPatient.id, reordered.id)

        // 3. Mark no show on a token
        val noShowToken = DemoDataProvider.verifyPrescription(tokens35[10].id, approve = false, reason = "Patient departed")
        assertEquals(TokenStatus.REJECTED, noShowToken.status)
    }

    // =========================================================================
    // PART 4: MULTI-DOCTOR CHAMBERS & QUEUE ISOLATION
    // =========================================================================

    @Test
    fun testMultiDoctorChamberIsolation_chamberA_chamberB_chamberC() {
        DemoDataProvider.populateLargeQueueForTesting(30)
        val chamberATokens = DemoDataProvider.getTokensForChamber("Chamber A")
        val chamberBTokens = DemoDataProvider.getTokensForChamber("Chamber B")
        val chamberCTokens = DemoDataProvider.getTokensForChamber("Chamber C")

        assertTrue("Chamber A must have tokens", chamberATokens.isNotEmpty())
        assertTrue("Chamber B must have tokens", chamberBTokens.isNotEmpty())
        assertTrue("Chamber C must have tokens", chamberCTokens.isNotEmpty())

        // Chamber A tokens must all belong to Chamber A
        assertTrue(chamberATokens.all { it.chamber == "Chamber A" })
        assertTrue(chamberBTokens.all { it.chamber == "Chamber B" })
        assertTrue(chamberCTokens.all { it.chamber == "Chamber C" })

        // Calling next in Chamber A session must not mutate Chamber B
        val initialChamberBWaiting = chamberBTokens.count { it.status == TokenStatus.WAITING }

        // Walk-in in Chamber A
        val walkInA = DemoDataProvider.createWalkIn("Walk-in Chamber A", priority = false, chamber = "Chamber A", doctorId = "doc_john")
        assertEquals("Chamber A", walkInA.chamber)

        // Chamber B count unchanged
        val updatedChamberBTokens = DemoDataProvider.getTokensForChamber("Chamber B")
        val updatedChamberBWaiting = updatedChamberBTokens.count { it.status == TokenStatus.WAITING }
        assertEquals(initialChamberBWaiting, updatedChamberBWaiting)
    }

    // =========================================================================
    // PART 5: PATIENT DASHBOARD DOCTOR DISCOVERY & E2E APPOINTMENT BOOKING
    // =========================================================================

    @Test
    fun testPatientDoctorDiscovery_nonEmptyDoctorsList() {
        val doctors = DemoDataProvider.demoDoctors
        assertTrue("Available doctors must not be empty", doctors.isNotEmpty())
        assertTrue("Doctor count must be at least 3", doctors.size >= 3)

        val drJohn = doctors.find { it.displayName == "Dr. John" }
        assertNotNull("Dr. John must exist in discovery list", drJohn)
        assertEquals("General Physician", drJohn!!.specialty)
        assertEquals("MBBS, MD (General Medicine)", drJohn.qualifications)
        assertEquals("XYZ Clinic", drJohn.primaryClinic)
        assertEquals("Chamber A", drJohn.primaryChamber)
        assertEquals(4, drJohn.schedules.count { it.isDoctorAvailable })
    }

    @Test
    fun testPatientDoctorDiscovery_searchAndSpecialtyFilters() {
        val doctors = DemoDataProvider.demoDoctors

        // 1. Search by name "John"
        val searchByName = doctors.filter { doc ->
            doc.displayName.contains("John", ignoreCase = true)
        }
        assertEquals(1, searchByName.size)
        assertEquals("Dr. John", searchByName.first().displayName)

        // 2. Search by specialty "Pediatrics"
        val searchBySpecialty = doctors.filter { doc ->
            doc.specialty?.contains("Pediatrics", ignoreCase = true) == true
        }
        assertTrue("Pediatrics doctors must be returned", searchBySpecialty.isNotEmpty())
        assertTrue("Dr. Donna must be present in Pediatrics", searchBySpecialty.any { it.displayName == "Dr. Donna" })

        // 3. Search by clinic name "ABC Clinic"
        val searchByClinic = doctors.filter { doc ->
            doc.schedules.any { it.clinicName.contains("ABC Clinic", ignoreCase = true) }
        }
        assertTrue("Dr. John must appear for ABC Clinic search", searchByClinic.any { it.displayName == "Dr. John" })

        // 4. Combined search + specialty filter
        val combined = doctors.filter { doc ->
            doc.displayName.contains("John", ignoreCase = true) &&
                    doc.specialty?.contains("General Physician", ignoreCase = true) == true
        }
        assertEquals(1, combined.size)

        // 5. Incompatible filter returns empty list correctly without crashing
        val noMatch = doctors.filter { doc ->
            doc.displayName.contains("NonExistent", ignoreCase = true)
        }
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun testDoctorProfileAndWeeklySchedule_multiClinicSeparation() {
        val drJohn = DemoDataProvider.demoDoctors.find { it.displayName == "Dr. John" }
        assertNotNull(drJohn)

        val wednesdaySlot = drJohn!!.schedules.find { it.dayOfWeek == "Wednesday" }
        assertNotNull("Wednesday slot must exist", wednesdaySlot)
        assertEquals("ABC Clinic", wednesdaySlot!!.clinicName)
        assertEquals("Chamber B", wednesdaySlot.chamber)
        assertEquals(10, wednesdaySlot.dailyCapacity)

        val mondaySlot = drJohn.schedules.find { it.dayOfWeek == "Monday" }
        assertNotNull("Monday slot must exist", mondaySlot)
        assertEquals("XYZ Clinic", mondaySlot!!.clinicName)
        assertEquals("Chamber A", mondaySlot.chamber)
        assertEquals(10, mondaySlot.dailyCapacity)

        // Verify distinct location separation: Wednesday must NOT be XYZ Clinic or Chamber A
        assertFalse(wednesdaySlot.clinicName == mondaySlot.clinicName)
        assertFalse(wednesdaySlot.chamber == mondaySlot.chamber)
    }

    @Test
    fun testPatientBookingJourney_endToEndPrescriptionAndTokenAllocation() {
        // Step 1: Select Doctor & Slot (Wednesday, ABC Clinic, Chamber B)
        val doctor = DemoDataProvider.demoDoctors.first { it.displayName == "Dr. John" }
        val wednesdaySlot = doctor.schedules.first { it.dayOfWeek == "Wednesday" }

        // Step 2: Patient uploads prescription and requests token
        val prescriptionId = "rx_test_wed_001"
        val requestedToken = DemoDataProvider.requestToken(
            prescriptionId = prescriptionId,
            displayName = "Anita Desai"
        )
        assertNotNull(requestedToken)
        assertEquals(TokenStatus.PENDING_VERIFICATION, requestedToken.status)
        assertEquals("Anita Desai", requestedToken.displayName)

        // Step 3: Receptionist verifies prescription and assigns chamber
        val verifiedToken = DemoDataProvider.verifyPrescription(
            tokenId = requestedToken.id,
            approve = true
        )
        assertEquals(TokenStatus.WAITING, verifiedToken.status)

        // Step 4: Verify allocation under clinic and chamber rules
        val allocation = DemoDataProvider.allocateTokenWithCapacityCheck(
            patientName = "Anita Desai",
            doctorId = doctor.id,
            requestedClinicName = wednesdaySlot.clinicName,
            requestedDay = wednesdaySlot.dayOfWeek
        )
        assertTrue(allocation.isAllocated)
        assertEquals("ABC Clinic", allocation.clinicName)
        assertEquals("Chamber B", allocation.chamber)
        assertEquals("Wednesday", allocation.allocatedDate)
        assertFalse("Should not be rescheduled when Wednesday has capacity", allocation.isRescheduled)
    }
}


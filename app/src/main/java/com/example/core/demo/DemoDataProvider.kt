package com.example.core.demo

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DemoDataProvider {

    val demoDoctors: List<ClinicDoctor> = listOf(
        ClinicDoctor(
            id = "doc_john",
            displayName = "Dr. John",
            specialty = "General Physician",
            active = true,
            qualifications = "MBBS, MD (General Medicine)",
            credentials = "Reg. #SYNTH-48201",
            experienceYears = 10,
            primaryChamber = "Chamber A",
            primaryClinic = "XYZ Clinic",
            schedules = listOf(
                DoctorScheduleSlot(
                    dayOfWeek = "Monday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber A",
                    dailyCapacity = 10,
                    consultationHours = "09:00 AM - 01:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Tuesday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber A",
                    dailyCapacity = 10,
                    consultationHours = "09:00 AM - 01:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Wednesday",
                    clinicId = "clinic_abc",
                    clinicName = "ABC Clinic",
                    chamber = "Chamber B",
                    dailyCapacity = 10,
                    consultationHours = "02:00 PM - 06:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Thursday",
                    clinicId = "clinic_abc",
                    clinicName = "ABC Clinic",
                    chamber = "Chamber B",
                    dailyCapacity = 10,
                    consultationHours = "02:00 PM - 06:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Friday",
                    clinicId = "none",
                    clinicName = "No Scheduled Clinic",
                    chamber = "—",
                    dailyCapacity = 0,
                    consultationHours = "Closed",
                    isDoctorAvailable = false
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Saturday",
                    clinicId = "none",
                    clinicName = "No Scheduled Clinic",
                    chamber = "—",
                    dailyCapacity = 0,
                    consultationHours = "Closed",
                    isDoctorAvailable = false
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Sunday",
                    clinicId = "none",
                    clinicName = "No Scheduled Clinic",
                    chamber = "—",
                    dailyCapacity = 0,
                    consultationHours = "Closed",
                    isDoctorAvailable = false
                )
            )
        ),
        ClinicDoctor(
            id = "doc_donna",
            displayName = "Dr. Donna",
            specialty = "Pediatrics",
            active = true,
            qualifications = "MBBS, DCH (Pediatrics)",
            credentials = "Reg. #SYNTH-51920",
            experienceYears = 8,
            primaryChamber = "Chamber B",
            primaryClinic = "XYZ Clinic",
            schedules = listOf(
                DoctorScheduleSlot(
                    dayOfWeek = "Monday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber B",
                    dailyCapacity = 15,
                    consultationHours = "09:00 AM - 02:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Tuesday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber B",
                    dailyCapacity = 15,
                    consultationHours = "09:00 AM - 02:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Wednesday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber B",
                    dailyCapacity = 15,
                    consultationHours = "09:00 AM - 02:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Thursday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber B",
                    dailyCapacity = 15,
                    consultationHours = "09:00 AM - 02:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Friday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber B",
                    dailyCapacity = 15,
                    consultationHours = "09:00 AM - 02:00 PM"
                )
            )
        ),
        ClinicDoctor(
            id = "doc_alex",
            displayName = "Dr. Alex",
            specialty = "Orthopedics",
            active = true,
            qualifications = "MBBS, MS (Orthopedics)",
            credentials = "Reg. #SYNTH-62381",
            experienceYears = 12,
            primaryChamber = "Chamber C",
            primaryClinic = "XYZ Clinic",
            schedules = listOf(
                DoctorScheduleSlot(
                    dayOfWeek = "Monday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber C",
                    dailyCapacity = 12,
                    consultationHours = "10:00 AM - 03:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Tuesday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber C",
                    dailyCapacity = 12,
                    consultationHours = "10:00 AM - 03:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Wednesday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber C",
                    dailyCapacity = 12,
                    consultationHours = "10:00 AM - 03:00 PM"
                ),
                DoctorScheduleSlot(
                    dayOfWeek = "Thursday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber C",
                    dailyCapacity = 12,
                    consultationHours = "10:00 AM - 03:00 PM"
                )
            )
        ),
        ClinicDoctor(
            id = "doc_1",
            displayName = "Dr. Sarah Jenkins",
            specialty = "Cardiology & General Medicine",
            active = true,
            qualifications = "MBBS, MD (Cardiology)",
            credentials = "Reg. #SYNTH-10291",
            experienceYears = 15,
            primaryChamber = "Chamber A",
            primaryClinic = "XYZ Clinic",
            schedules = listOf(
                DoctorScheduleSlot(
                    dayOfWeek = "Monday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber A",
                    dailyCapacity = 15,
                    consultationHours = "09:00 AM - 01:00 PM"
                )
            )
        ),
        ClinicDoctor(
            id = "doc_2",
            displayName = "Dr. Robert Chen",
            specialty = "Pediatrics",
            active = true,
            qualifications = "MBBS, MD (Pediatrics)",
            credentials = "Reg. #SYNTH-20394",
            experienceYears = 9,
            primaryChamber = "Chamber B",
            primaryClinic = "XYZ Clinic",
            schedules = listOf(
                DoctorScheduleSlot(
                    dayOfWeek = "Monday",
                    clinicId = "clinic_xyz",
                    clinicName = "XYZ Clinic",
                    chamber = "Chamber B",
                    dailyCapacity = 15,
                    consultationHours = "10:00 AM - 02:00 PM"
                )
            )
        )
    )

    private val nowFormatted: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    // Tracks capacity: key = "$doctorId|$clinicName|$dayOfWeek" -> approved count
    private val _dailyApprovedCount = mutableMapOf<String, Int>(
        "doc_john|XYZ Clinic|Monday" to 10, // Monday is 10/10 FULL
        "doc_john|XYZ Clinic|Tuesday" to 4, // Tuesday has 4/10 approved, 6 slots available
        "doc_john|ABC Clinic|Wednesday" to 2,
        "doc_john|ABC Clinic|Thursday" to 1,
        "doc_donna|XYZ Clinic|Monday" to 3,
        "doc_alex|XYZ Clinic|Monday" to 5
    )

    private val _sessionState = MutableStateFlow(
        QueueSession(
            id = "sess_demo_1",
            clinicId = "clinic_xyz",
            doctorId = "doc_john",
            doctorName = "Dr. John",
            businessDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            status = "ACTIVE",
            tokenPrefix = "A",
            nextTokenNumber = 10,
            fallbackConsultMinutes = 10,
            pauseMessage = null,
            waitingCount = 4,
            totalTokens = 10,
            chamber = "Chamber A"
        )
    )

    // Initial multi-chamber synthetic tokens
    private val _tokensState = MutableStateFlow(
        listOf(
            QueueToken(
                id = "tok_1",
                tokenNumber = 1,
                label = "A-01",
                status = TokenStatus.IN_CONSULTATION,
                priority = false,
                queuePosition = 0,
                displayName = "Amitabh Sen",
                checkedInAt = "09:00",
                calledAt = "09:20",
                patientId = "pat_1",
                prescriptionId = "rx_1",
                rejectionReason = null,
                estimatedWaitMinutes = 0,
                estimateQuality = "REAL_TIME",
                chamber = "Chamber A",
                appointmentDate = "Monday",
                doctorId = "doc_john"
            ),
            QueueToken(
                id = "tok_2",
                tokenNumber = 2,
                label = "A-02",
                status = TokenStatus.WAITING,
                priority = false,
                queuePosition = 1,
                displayName = "Alice Smith",
                checkedInAt = "09:05",
                calledAt = null,
                patientId = "pat_2",
                prescriptionId = "rx_2",
                rejectionReason = null,
                estimatedWaitMinutes = 6,
                estimateQuality = "REAL_TIME",
                chamber = "Chamber A",
                appointmentDate = "Monday",
                doctorId = "doc_john"
            ),
            QueueToken(
                id = "tok_3",
                tokenNumber = 3,
                label = "A-03",
                status = TokenStatus.WAITING,
                priority = false,
                queuePosition = 2,
                displayName = "David Miller",
                checkedInAt = "09:10",
                calledAt = null,
                patientId = "pat_3",
                prescriptionId = "rx_3",
                rejectionReason = null,
                estimatedWaitMinutes = 16,
                estimateQuality = "REAL_TIME",
                chamber = "Chamber A",
                appointmentDate = "Monday",
                doctorId = "doc_john"
            ),
            QueueToken(
                id = "tok_4",
                tokenNumber = 1,
                label = "B-01",
                status = TokenStatus.IN_CONSULTATION,
                priority = false,
                queuePosition = 0,
                displayName = "Baby Rohan (Donna)",
                checkedInAt = "09:15",
                calledAt = "09:25",
                patientId = "pat_4",
                prescriptionId = "rx_4",
                rejectionReason = null,
                estimatedWaitMinutes = 0,
                estimateQuality = "REAL_TIME",
                chamber = "Chamber B",
                appointmentDate = "Monday",
                doctorId = "doc_donna"
            ),
            QueueToken(
                id = "tok_5",
                tokenNumber = 2,
                label = "B-02",
                status = TokenStatus.WAITING,
                priority = false,
                queuePosition = 1,
                displayName = "Ananya Roy (Donna)",
                checkedInAt = "09:20",
                calledAt = null,
                patientId = "pat_5",
                prescriptionId = "rx_5",
                rejectionReason = null,
                estimatedWaitMinutes = 10,
                estimateQuality = "REAL_TIME",
                chamber = "Chamber B",
                appointmentDate = "Monday",
                doctorId = "doc_donna"
            ),
            QueueToken(
                id = "tok_6",
                tokenNumber = 1,
                label = "C-01",
                status = TokenStatus.WAITING,
                priority = false,
                queuePosition = 1,
                displayName = "Harish Chandra (Alex)",
                checkedInAt = "09:30",
                calledAt = null,
                patientId = "pat_6",
                prescriptionId = "rx_6",
                rejectionReason = null,
                estimatedWaitMinutes = 12,
                estimateQuality = "REAL_TIME",
                chamber = "Chamber C",
                appointmentDate = "Monday",
                doctorId = "doc_alex"
            )
        )
    )

    private val _pendingRxState = MutableStateFlow(
        listOf(
            PendingPrescription(
                tokenId = "tok_pending_mon_john",
                prescriptionId = "rx_mon_john",
                patientId = "pat_new_john",
                patientName = "Suresh Raina",
                patientPhone = "+91 9876501234",
                requestedAt = "09:40",
                fileType = "image/jpeg",
                fileSize = 1048576L,
                doctorId = "doc_john",
                doctorName = "Dr. John",
                requestedDate = "Monday",
                requestedClinic = "XYZ Clinic",
                chamber = "Chamber A",
                patientIdentifier = "PAT-1011",
                presentingComplaint = "Follow-up fever and blood test review",
                hasPrescriptionAttachment = true,
                needsDoctorAssignment = false
            ),
            PendingPrescription(
                tokenId = "tok_pending_unassigned_1",
                prescriptionId = "NO_RX_COMPLAINT_ONLY",
                patientId = "pat_104",
                patientName = "Ananya Sharma",
                patientPhone = "+91 9830998877",
                requestedAt = "09:50",
                fileType = "text/plain",
                fileSize = 0L,
                doctorId = "UNASSIGNED",
                doctorName = "Unassigned (Needs Triage)",
                requestedDate = "Monday",
                requestedClinic = "XYZ Clinic",
                chamber = "Pending Assignment",
                patientIdentifier = "PAT-1004",
                presentingComplaint = "Right knee pain and swelling when climbing stairs for 4 days",
                hasPrescriptionAttachment = false,
                needsDoctorAssignment = true
            )
        )
    )

    private val _doctorStatsState = MutableStateFlow(
        DoctorStats(
            patientsSeenToday = 7,
            averageConsultationMinutes = 9.5,
            currentlyWaiting = 4
        )
    )

    fun observeTokens(sessionId: String): Flow<List<QueueToken>> = _tokensState
    fun observeToken(tokenId: String): Flow<QueueToken?> = _tokensState.map { list -> list.find { it.id == tokenId } }
    fun observePatientHistory(patientId: String): Flow<List<QueueToken>> = _tokensState.map { list ->
        list.filter { it.patientId == patientId }
    }

    fun observeTokensForChamber(chamber: String?): Flow<List<QueueToken>> = _tokensState.map { list ->
        if (chamber == null || chamber == "All") list else list.filter { it.chamber == chamber }
    }

    fun observeTokensForDoctor(doctorId: String?): Flow<List<QueueToken>> = _tokensState.map { list ->
        if (doctorId == null || doctorId == "All") list else list.filter { it.doctorId == doctorId }
    }

    fun getSession(sessionId: String = "sess_demo_1"): QueueSession = _sessionState.value
    fun getTokens(): List<QueueToken> = _tokensState.value
    fun getTokensForChamber(chamber: String): List<QueueToken> = _tokensState.value.filter { it.chamber == chamber }
    fun getPendingPrescriptions(): List<PendingPrescription> = _pendingRxState.value
    fun getDoctorStats(): DoctorStats = _doctorStatsState.value

    fun getApprovedCountFor(doctorId: String, clinicName: String, dayOfWeek: String): Int {
        val key = "$doctorId|$clinicName|$dayOfWeek"
        return _dailyApprovedCount[key] ?: 0
    }

    fun getCapacityFor(doctorId: String, clinicName: String, dayOfWeek: String): Int {
        val doctor = demoDoctors.find { it.id == doctorId } ?: return 0
        val slot = doctor.schedules.find {
            it.dayOfWeek.equals(dayOfWeek, ignoreCase = true) &&
                    it.clinicName.equals(clinicName, ignoreCase = true)
        }
        return slot?.dailyCapacity ?: 0
    }

    fun resetCapacityToScenario(mondayFull: Boolean = true) {
        if (mondayFull) {
            _dailyApprovedCount["doc_john|XYZ Clinic|Monday"] = 10
            _dailyApprovedCount["doc_john|XYZ Clinic|Tuesday"] = 4
        } else {
            _dailyApprovedCount["doc_john|XYZ Clinic|Monday"] = 8
            _dailyApprovedCount["doc_john|XYZ Clinic|Tuesday"] = 4
        }
    }

    /**
     * Capacity check and next-eligible-day allocation engine.
     * Enforces:
     * 1. Never issues an 11th token when limit is 10.
     * 2. Automatically finds the NEXT ELIGIBLE clinic day for this doctor at the SAME clinic.
     * 3. Never silently assigns to a different clinic.
     * 4. Returns detailed AllocationResult with full date, chamber, and rescheduling explanation.
     */
    fun allocateTokenWithCapacityCheck(
        patientName: String,
        doctorId: String,
        requestedClinicName: String,
        requestedDay: String,
        prescriptionId: String? = null
    ): AllocationResult {
        val doctor = demoDoctors.find { it.id == doctorId } ?: demoDoctors.first()
        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        val requestedSlot = doctor.schedules.find {
            it.dayOfWeek.equals(requestedDay, ignoreCase = true) &&
                    it.clinicName.equals(requestedClinicName, ignoreCase = true)
        }

        val reqKey = "$doctorId|$requestedClinicName|$requestedDay"
        val currentApproved = _dailyApprovedCount[reqKey] ?: 0
        val capacity = requestedSlot?.dailyCapacity ?: 0

        // Case A: Fits on requested day
        if (requestedSlot != null &&
            capacity > 0 &&
            currentApproved < capacity &&
            requestedSlot.isDoctorAvailable &&
            !requestedSlot.isClinicClosed
        ) {
            val newCount = currentApproved + 1
            _dailyApprovedCount[reqKey] = newCount

            val tokenNum = newCount
            val chamberPrefix = requestedSlot.chamber.substringAfter("Chamber ").take(1).ifBlank { "T" }
            val label = "$chamberPrefix-${tokenNum.toString().padStart(2, '0')}"
            val tokenId = "tok_${System.currentTimeMillis()}"

            val newToken = QueueToken(
                id = tokenId,
                tokenNumber = tokenNum,
                label = label,
                status = TokenStatus.WAITING,
                priority = false,
                queuePosition = 0,
                displayName = patientName,
                checkedInAt = nowFormatted,
                calledAt = null,
                patientId = "pat_${System.currentTimeMillis().toString().takeLast(6)}",
                prescriptionId = prescriptionId,
                rejectionReason = null,
                estimatedWaitMinutes = (tokenNum * 10) - 4,
                estimateQuality = "REAL_TIME",
                chamber = requestedSlot.chamber,
                appointmentDate = requestedDay,
                isRescheduled = false,
                originalRequestedDate = requestedDay,
                doctorId = doctorId
            )

            val currentTokens = _tokensState.value.toMutableList()
            currentTokens.add(newToken)
            recalculateQueue(currentTokens)
            _tokensState.value = currentTokens

            return AllocationResult(
                requestedDate = requestedDay,
                allocatedDate = requestedDay,
                isAllocated = true,
                isRescheduled = false,
                clinicName = requestedClinicName,
                chamber = requestedSlot.chamber,
                doctorName = doctor.displayName,
                remainingCapacity = capacity - newCount,
                reason = "Confirmed for $requestedDay at $requestedClinicName (${requestedSlot.chamber}).",
                token = newToken
            )
        }

        // Case B: Requested day is FULL (or closed/unavailable).
        // Find NEXT ELIGIBLE clinic day for this doctor at the SAME requestedClinicName.
        val startIdx = daysOfWeek.indexOfFirst { it.equals(requestedDay, ignoreCase = true) }
        var candidateSlot: DoctorScheduleSlot? = null
        var candidateDay: String? = null

        // Search subsequent days
        for (i in 1..6) {
            val nextDayIdx = (startIdx + i) % 7
            val dayName = daysOfWeek[nextDayIdx]
            val slot = doctor.schedules.find {
                it.dayOfWeek.equals(dayName, ignoreCase = true) &&
                        it.clinicName.equals(requestedClinicName, ignoreCase = true)
            }
            if (slot != null && slot.dailyCapacity > 0 && slot.isDoctorAvailable && !slot.isClinicClosed) {
                val candKey = "$doctorId|$requestedClinicName|$dayName"
                val candApproved = _dailyApprovedCount[candKey] ?: 0
                if (candApproved < slot.dailyCapacity) {
                    candidateSlot = slot
                    candidateDay = dayName
                    break
                }
            }
        }

        if (candidateSlot != null && candidateDay != null) {
            val candKey = "$doctorId|$requestedClinicName|$candidateDay"
            val candApproved = _dailyApprovedCount[candKey] ?: 0
            val newCandCount = candApproved + 1
            _dailyApprovedCount[candKey] = newCandCount

            val tokenNum = newCandCount
            val chamberPrefix = candidateSlot.chamber.substringAfter("Chamber ").take(1).ifBlank { "T" }
            val label = "$chamberPrefix-${tokenNum.toString().padStart(2, '0')}"
            val tokenId = "tok_${System.currentTimeMillis()}"

            val newToken = QueueToken(
                id = tokenId,
                tokenNumber = tokenNum,
                label = label,
                status = TokenStatus.WAITING,
                priority = false,
                queuePosition = 0,
                displayName = patientName,
                checkedInAt = nowFormatted,
                calledAt = null,
                patientId = "pat_${System.currentTimeMillis().toString().takeLast(6)}",
                prescriptionId = prescriptionId,
                rejectionReason = null,
                estimatedWaitMinutes = (tokenNum * 10) - 4,
                estimateQuality = "SCHEDULED",
                chamber = candidateSlot.chamber,
                appointmentDate = candidateDay,
                isRescheduled = true,
                originalRequestedDate = requestedDay,
                allocationNote = "Requested day $requestedDay was fully booked ($capacity/$capacity). Automatically assigned to next available day: $candidateDay.",
                doctorId = doctorId
            )

            val currentTokens = _tokensState.value.toMutableList()
            currentTokens.add(newToken)
            recalculateQueue(currentTokens)
            _tokensState.value = currentTokens

            return AllocationResult(
                requestedDate = requestedDay,
                allocatedDate = candidateDay,
                isAllocated = true,
                isRescheduled = true,
                clinicName = requestedClinicName,
                chamber = candidateSlot.chamber,
                doctorName = doctor.displayName,
                remainingCapacity = candidateSlot.dailyCapacity - newCandCount,
                reason = "Requested day $requestedDay is full ($capacity/$capacity). Automatically allocated to next eligible clinic day: $candidateDay at $requestedClinicName (${candidateSlot.chamber}).",
                token = newToken
            )
        }

        // Case C: No remaining capacity on ANY eligible clinic day at the requested clinic
        return AllocationResult(
            requestedDate = requestedDay,
            allocatedDate = "",
            isAllocated = false,
            isRescheduled = false,
            clinicName = requestedClinicName,
            chamber = "",
            doctorName = doctor.displayName,
            remainingCapacity = 0,
            reason = "No available capacity on $requestedDay or subsequent scheduled clinic days at $requestedClinicName within the search horizon.",
            token = null
        )
    }

    fun callNext(): QueueToken {
        val tokens = _tokensState.value.toMutableList()
        val currentIdx = tokens.indexOfFirst { it.status == TokenStatus.IN_CONSULTATION }
        if (currentIdx != -1) {
            tokens[currentIdx] = tokens[currentIdx].copy(status = TokenStatus.COMPLETED)
            val stats = _doctorStatsState.value
            _doctorStatsState.value = stats.copy(patientsSeenToday = stats.patientsSeenToday + 1)
        }

        val nextIdx = tokens.indexOfFirst { it.status == TokenStatus.WAITING }
        if (nextIdx != -1) {
            val nextToken = tokens[nextIdx].copy(
                status = TokenStatus.IN_CONSULTATION,
                calledAt = nowFormatted,
                queuePosition = 0,
                estimatedWaitMinutes = 0
            )
            tokens[nextIdx] = nextToken
            recalculateQueue(tokens)
            _tokensState.value = tokens
            return nextToken
        }

        recalculateQueue(tokens)
        _tokensState.value = tokens
        return tokens.firstOrNull { it.status == TokenStatus.IN_CONSULTATION }
            ?: throw IllegalStateException("No patients currently waiting")
    }

    fun completeConsultation(tokenId: String): QueueToken {
        val tokens = _tokensState.value.toMutableList()
        val idx = tokens.indexOfFirst { it.id == tokenId }
        if (idx != -1) {
            val completed = tokens[idx].copy(status = TokenStatus.COMPLETED)
            tokens[idx] = completed
            recalculateQueue(tokens)
            _tokensState.value = tokens
            val stats = _doctorStatsState.value
            _doctorStatsState.value = stats.copy(patientsSeenToday = stats.patientsSeenToday + 1)
            return completed
        }
        throw IllegalArgumentException("Token not found")
    }

    fun skipToken(tokenId: String, reason: String): QueueToken {
        val tokens = _tokensState.value.toMutableList()
        val idx = tokens.indexOfFirst { it.id == tokenId }
        if (idx != -1) {
            val skipped = tokens[idx].copy(
                status = TokenStatus.SKIPPED,
                rejectionReason = reason
            )
            tokens[idx] = skipped
            recalculateQueue(tokens)
            _tokensState.value = tokens
            return skipped
        }
        throw IllegalArgumentException("Token not found")
    }

    fun setPriority(tokenId: String, enabled: Boolean, reason: String): QueueToken {
        val tokens = _tokensState.value.toMutableList()
        val idx = tokens.indexOfFirst { it.id == tokenId }
        if (idx != -1) {
            val updated = tokens[idx].copy(priority = enabled)
            tokens[idx] = updated
            recalculateQueue(tokens)
            _tokensState.value = tokens
            return updated
        }
        throw IllegalArgumentException("Token not found")
    }

    fun reorderToken(tokenId: String, targetPosition: Int, reason: String): QueueToken {
        val tokens = _tokensState.value.toMutableList()
        val token = tokens.find { it.id == tokenId } ?: throw IllegalArgumentException("Token not found")
        tokens.remove(token)
        val waitingTokens = tokens.filter { it.status == TokenStatus.WAITING }.toMutableList()
        val insertIdx = (targetPosition - 1).coerceIn(0, waitingTokens.size)
        waitingTokens.add(insertIdx, token)

        val nonWaiting = tokens.filter { it.status != TokenStatus.WAITING }
        val merged = (nonWaiting + waitingTokens).toMutableList()
        recalculateQueue(merged)
        _tokensState.value = merged
        return token
    }

    fun createWalkIn(
        displayName: String,
        priority: Boolean,
        chamber: String = "Chamber A",
        doctorId: String = "doc_john",
        clinicId: String = "clinic_xyz"
    ): QueueToken {
        com.example.core.payment.SubscriptionPaymentEngine.enforceEntitlementOrThrow(clinicId)
        val tokens = _tokensState.value.toMutableList()
        val session = _sessionState.value
        val num = session.nextTokenNumber
        val chamberPrefix = chamber.substringAfter("Chamber ").take(1).ifBlank { "T" }
        val newTok = QueueToken(
            id = "tok_walkin_$num",
            tokenNumber = num,
            label = "$chamberPrefix-${num.toString().padStart(2, '0')}",
            status = TokenStatus.WAITING,
            priority = priority,
            queuePosition = 0,
            displayName = displayName,
            checkedInAt = nowFormatted,
            calledAt = null,
            patientId = "pat_walkin_$num",
            prescriptionId = null,
            rejectionReason = null,
            estimatedWaitMinutes = 0,
            estimateQuality = "REAL_TIME",
            chamber = chamber,
            appointmentDate = "Today",
            doctorId = doctorId
        )
        tokens.add(newTok)
        _sessionState.value = session.copy(
            nextTokenNumber = num + 1,
            totalTokens = session.totalTokens + 1
        )
        recalculateQueue(tokens)
        _tokensState.value = tokens
        return newTok
    }

    fun verifyPrescription(tokenId: String, approve: Boolean, reason: String? = null): QueueToken {
        val tokens = _tokensState.value.toMutableList()
        val pendingList = _pendingRxState.value.toMutableList()
        val pending = pendingList.find { it.tokenId == tokenId }

        if (approve && pending != null) {
            val result = allocateTokenWithCapacityCheck(
                patientName = pending.patientName,
                doctorId = pending.doctorId ?: "doc_john",
                requestedClinicName = pending.requestedClinic ?: "XYZ Clinic",
                requestedDay = pending.requestedDate ?: "Monday",
                prescriptionId = pending.prescriptionId
            )
            pendingList.removeAll { it.tokenId == tokenId }
            _pendingRxState.value = pendingList

            if (result.isAllocated && result.token != null) {
                return result.token
            }
        }

        val idx = tokens.indexOfFirst { it.id == tokenId }
        if (idx != -1) {
            val session = _sessionState.value
            val num = session.nextTokenNumber
            val updated = if (approve) {
                tokens[idx].copy(
                    tokenNumber = num,
                    label = "A-${num.toString().padStart(2, '0')}",
                    status = TokenStatus.WAITING,
                    rejectionReason = null
                )
            } else {
                tokens[idx].copy(
                    status = TokenStatus.REJECTED,
                    rejectionReason = reason ?: "Prescription unreadable or invalid"
                )
            }
            tokens[idx] = updated

            pendingList.removeAll { it.tokenId == tokenId }
            _pendingRxState.value = pendingList

            if (approve) {
                _sessionState.value = session.copy(
                    nextTokenNumber = num + 1,
                    totalTokens = session.totalTokens + 1
                )
            }
            recalculateQueue(tokens)
            _tokensState.value = tokens
            return updated
        }

        // If not in tokens yet, create rejected token
        val rejectedToken = QueueToken(
            id = tokenId,
            tokenNumber = 0,
            label = "REJECTED",
            status = TokenStatus.REJECTED,
            priority = false,
            queuePosition = 0,
            displayName = pending?.patientName ?: "Patient",
            checkedInAt = nowFormatted,
            calledAt = null,
            patientId = pending?.patientId,
            prescriptionId = pending?.prescriptionId,
            rejectionReason = reason ?: "Prescription unreadable or invalid",
            estimatedWaitMinutes = null,
            estimateQuality = "REJECTED"
        )
        pendingList.removeAll { it.tokenId == tokenId }
        _pendingRxState.value = pendingList
        return rejectedToken
    }

    fun pauseSession(message: String?): QueueSession {
        val updated = _sessionState.value.copy(
            status = "PAUSED",
            pauseMessage = message ?: "Doctor stepped out for emergency"
        )
        _sessionState.value = updated
        return updated
    }

    fun resumeSession(): QueueSession {
        val updated = _sessionState.value.copy(
            status = "ACTIVE",
            pauseMessage = null
        )
        _sessionState.value = updated
        return updated
    }

    fun requestToken(
        prescriptionId: String,
        displayName: String?,
        doctorId: String = "doc_john",
        requestedDay: String = "Monday",
        clinicName: String = "XYZ Clinic",
        presentingComplaint: String? = null,
        authenticatedPatientId: String? = "pat_demo_patient",
        patientPhone: String? = "+91 9876543210"
    ): QueueToken {
        val tokens = _tokensState.value.toMutableList()
        val pendingList = _pendingRxState.value.toMutableList()
        val id = "tok_${System.currentTimeMillis()}"
        val patientName = displayName?.takeIf { it.isNotBlank() } ?: "David Miller"

        val clinicId = if (clinicName.contains("ABC", ignoreCase = true)) "clinic_abc" else "clinic_xyz"
        val effectiveDocId = doctorId.takeIf { it.isNotBlank() && it != "UNASSIGNED" }
        val resolution = PatientIdentityRegistry.resolveOrRegisterPatient(
            authenticatedPatientId = authenticatedPatientId,
            fullName = patientName,
            phoneNumber = patientPhone,
            clinicId = clinicId,
            doctorId = effectiveDocId
        )
        val resolvedPatientId = resolution.patientRecord?.patientId ?: (authenticatedPatientId ?: "pat_demo_patient")
        val resolvedIdentifier = resolution.patientRecord?.patientIdentifier
            ?: PatientIdentityRegistry.getIdentifierForPatientId(resolvedPatientId)
        val resolvedPhone = resolution.patientRecord?.phoneNumber ?: patientPhone

        val hasRx = prescriptionId.isNotBlank() && !prescriptionId.startsWith("NO_RX")
        val needsAssignment = effectiveDocId == null
        val matchedDoctor = effectiveDocId?.let { dId -> demoDoctors.find { it.id == dId } }
        val resolvedChamber = matchedDoctor?.primaryChamber ?: "Pending Assignment"

        val tok = QueueToken(
            id = id,
            tokenNumber = 0,
            label = if (needsAssignment) "ASSIGN" else "PENDING",
            status = TokenStatus.PENDING_VERIFICATION,
            priority = false,
            queuePosition = 0,
            displayName = patientName,
            checkedInAt = nowFormatted,
            calledAt = null,
            patientId = resolvedPatientId,
            prescriptionId = if (hasRx) prescriptionId else null,
            rejectionReason = null,
            estimatedWaitMinutes = null,
            estimateQuality = "PENDING",
            chamber = resolvedChamber,
            appointmentDate = requestedDay,
            doctorId = effectiveDocId ?: "UNASSIGNED",
            patientIdentifier = resolvedIdentifier,
            patientPhone = resolvedPhone,
            presentingComplaint = presentingComplaint,
            needsDoctorAssignment = needsAssignment
        )

        tokens.add(tok)
        pendingList.add(
            PendingPrescription(
                tokenId = id,
                prescriptionId = if (hasRx) prescriptionId else "NO_RX_COMPLAINT_ONLY",
                patientId = resolvedPatientId,
                patientName = patientName,
                patientPhone = resolvedPhone,
                requestedAt = nowFormatted,
                fileType = if (hasRx) "image/jpeg" else "text/plain",
                fileSize = if (hasRx) 1048576L else 0L,
                doctorId = effectiveDocId ?: "UNASSIGNED",
                doctorName = matchedDoctor?.displayName ?: "Unassigned (Needs Triage)",
                requestedDate = requestedDay,
                requestedClinic = clinicName,
                chamber = resolvedChamber,
                patientIdentifier = resolvedIdentifier,
                presentingComplaint = presentingComplaint,
                hasPrescriptionAttachment = hasRx,
                needsDoctorAssignment = needsAssignment
            )
        )

        _tokensState.value = tokens
        _pendingRxState.value = pendingList
        return tok
    }

    /**
     * Requirement 5.4: Receptionist problem-based doctor assignment workflow.
     * Validates:
     * - Doctor exists and is active.
     * - Doctor is scheduled and available at the requested clinic and day.
     * - Session has remaining capacity on the requested day (does not overbook).
     * - Updates token and pending request, and records an audit trail entry.
     */
    fun assignDoctorToPendingRequest(
        tokenId: String,
        targetDoctorId: String,
        requestedClinicName: String = "XYZ Clinic",
        requestedDay: String = "Monday",
        actorId: String = "user_recep_1"
    ): Result<QueueToken> {
        val doctor = demoDoctors.find { it.id == targetDoctorId && it.active }
            ?: return Result.failure(IllegalArgumentException("Selected doctor ($targetDoctorId) is not found or inactive."))

        val normalizedClinic = when {
            requestedClinicName.contains("ABC", ignoreCase = true) -> "ABC Clinic"
            else -> "XYZ Clinic"
        }

        val slot = doctor.schedules.find {
            it.dayOfWeek.equals(requestedDay, ignoreCase = true) &&
                it.clinicName.equals(normalizedClinic, ignoreCase = true)
        }
        if (slot == null || !slot.isDoctorAvailable || slot.isClinicClosed || slot.dailyCapacity <= 0) {
            ClinicalAuditLogger.recordEvent(
                actorId = actorId,
                actorRole = Role.RECEPTIONIST,
                action = "ASSIGN_DOCTOR_TO_REQUEST",
                resourceId = tokenId,
                clinicId = if (normalizedClinic.contains("ABC", true)) "clinic_abc" else "clinic_xyz",
                outcome = "DENIED",
                contextNote = "Ineligible assignment: ${doctor.displayName} is not scheduled at $normalizedClinic on $requestedDay."
            )
            return Result.failure(
                IllegalStateException("${doctor.displayName} is not scheduled at $normalizedClinic on $requestedDay.")
            )
        }

        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val reqKey = "$targetDoctorId|$normalizedClinic|$requestedDay"
        val currentApproved = _dailyApprovedCount[reqKey] ?: 0

        var effectiveSlot: DoctorScheduleSlot = slot
        var effectiveDay: String = requestedDay
        var effectiveKey: String = reqKey
        var effectiveCurrentApproved: Int = currentApproved
        var isRescheduledNextDay = false

        if (currentApproved >= slot.dailyCapacity) {
            // Find next eligible clinic day for this doctor at the SAME clinic
            val startIdx = daysOfWeek.indexOfFirst { it.equals(requestedDay, ignoreCase = true) }.coerceAtLeast(0)
            var foundSlot: DoctorScheduleSlot? = null
            var foundDay: String? = null
            for (i in 1..6) {
                val nextDayName = daysOfWeek[(startIdx + i) % 7]
                val candSlot = doctor.schedules.find {
                    it.dayOfWeek.equals(nextDayName, ignoreCase = true) &&
                        it.clinicName.equals(normalizedClinic, ignoreCase = true)
                }
                if (candSlot != null && candSlot.dailyCapacity > 0 && candSlot.isDoctorAvailable && !candSlot.isClinicClosed) {
                    val candKey = "$targetDoctorId|$normalizedClinic|$nextDayName"
                    val candApproved = _dailyApprovedCount[candKey] ?: 0
                    if (candApproved < candSlot.dailyCapacity) {
                        foundSlot = candSlot
                        foundDay = nextDayName
                        break
                    }
                }
            }
            if (foundSlot != null && foundDay != null) {
                effectiveSlot = foundSlot
                effectiveDay = foundDay
                effectiveKey = "$targetDoctorId|$normalizedClinic|$foundDay"
                effectiveCurrentApproved = _dailyApprovedCount[effectiveKey] ?: 0
                isRescheduledNextDay = true
            } else {
                ClinicalAuditLogger.recordEvent(
                    actorId = actorId,
                    actorRole = Role.RECEPTIONIST,
                    action = "ASSIGN_DOCTOR_TO_REQUEST",
                    resourceId = tokenId,
                    clinicId = slot.clinicId,
                    outcome = "DENIED",
                    contextNote = "Session full ($currentApproved/${slot.dailyCapacity}) for ${doctor.displayName} on $requestedDay."
                )
                return Result.failure(
                    IllegalStateException("Session is full ($currentApproved/${slot.dailyCapacity}) for ${doctor.displayName} at $normalizedClinic (${slot.chamber}) on $requestedDay. Cannot overbook.")
                )
            }
        }

        val pendingList = _pendingRxState.value.toMutableList()
        val pending = pendingList.find { it.tokenId == tokenId }
        val tokens = _tokensState.value.toMutableList()
        val existingTok = tokens.find { it.id == tokenId }

        val patientName = pending?.patientName ?: existingTok?.displayName ?: "Patient"
        val patientId = pending?.patientId ?: existingTok?.patientId ?: "pat_demo_patient"
        val patientIdentifier = pending?.patientIdentifier
            ?: existingTok?.patientIdentifier
            ?: PatientIdentityRegistry.getIdentifierForPatientId(patientId)
        val complaint = pending?.presentingComplaint ?: existingTok?.presentingComplaint
        val rxId = pending?.prescriptionId?.takeIf { !it.startsWith("NO_RX") } ?: existingTok?.prescriptionId

        // Increment approved capacity count and issue active queue token in doctor's chamber
        val newCount = effectiveCurrentApproved + 1
        _dailyApprovedCount[effectiveKey] = newCount
        val chamberPrefix = effectiveSlot.chamber.substringAfter("Chamber ").take(1).ifBlank { "T" }
        val label = "$chamberPrefix-${newCount.toString().padStart(2, '0')}"

        // Ensure patient record reflects assigned doctor
        PatientIdentityRegistry.resolveOrRegisterPatient(
            authenticatedPatientId = patientId,
            providedPatientIdentifier = patientIdentifier,
            fullName = patientName,
            phoneNumber = pending?.patientPhone ?: existingTok?.patientPhone,
            clinicId = effectiveSlot.clinicId,
            doctorId = targetDoctorId
        )

        val assignedToken = QueueToken(
            id = tokenId,
            tokenNumber = newCount,
            label = label,
            status = TokenStatus.WAITING,
            priority = false,
            queuePosition = 0,
            displayName = patientName,
            checkedInAt = nowFormatted,
            calledAt = null,
            patientId = patientId,
            prescriptionId = rxId,
            rejectionReason = null,
            estimatedWaitMinutes = (newCount * 10) - 4,
            estimateQuality = if (isRescheduledNextDay) "SCHEDULED" else "REAL_TIME",
            chamber = effectiveSlot.chamber,
            appointmentDate = effectiveDay,
            isRescheduled = isRescheduledNextDay,
            originalRequestedDate = requestedDay,
            allocationNote = if (isRescheduledNextDay) {
                "Requested day $requestedDay was full ($currentApproved/${slot.dailyCapacity}). Assigned to ${doctor.displayName} (${effectiveSlot.chamber}) for next eligible day: $effectiveDay."
            } else {
                "Assigned to ${doctor.displayName} (${effectiveSlot.chamber}) by receptionist."
            },
            doctorId = targetDoctorId,
            patientIdentifier = patientIdentifier,
            patientPhone = pending?.patientPhone ?: existingTok?.patientPhone,
            presentingComplaint = complaint,
            needsDoctorAssignment = false
        )

        val tokIdx = tokens.indexOfFirst { it.id == tokenId }
        if (tokIdx != -1) {
            tokens[tokIdx] = assignedToken
        } else {
            tokens.add(assignedToken)
        }
        recalculateQueue(tokens)
        _tokensState.value = tokens

        pendingList.removeAll { it.tokenId == tokenId }
        _pendingRxState.value = pendingList

        ClinicalAuditLogger.recordEvent(
            actorId = actorId,
            actorRole = Role.RECEPTIONIST,
            action = "ASSIGN_DOCTOR_TO_REQUEST",
            resourceId = tokenId,
            clinicId = effectiveSlot.clinicId,
            outcome = "SUCCESS",
            contextNote = "Assigned patient $patientIdentifier to ${doctor.displayName} (${effectiveSlot.chamber}) on $effectiveDay."
        )

        return Result.success(assignedToken)
    }

    fun cancelToken(tokenId: String, reason: String): QueueToken {
        val tokens = _tokensState.value.toMutableList()
        val idx = tokens.indexOfFirst { it.id == tokenId }
        if (idx != -1) {
            val cancelled = tokens[idx].copy(status = TokenStatus.CANCELLED, rejectionReason = reason)
            tokens[idx] = cancelled

            // Free capacity slot
            val docId = cancelled.doctorId ?: "doc_john"
            val clinic = "XYZ Clinic"
            val day = cancelled.appointmentDate ?: "Monday"
            val key = "$docId|$clinic|$day"
            val count = _dailyApprovedCount[key] ?: 0
            if (count > 0) {
                _dailyApprovedCount[key] = count - 1
            }

            recalculateQueue(tokens)
            _tokensState.value = tokens
            return cancelled
        }
        throw IllegalArgumentException("Token not found")
    }

    /**
     * Seeds 35+ synthetic patients into the queue for UAT scrolling and visibility testing.
     */
    fun populateLargeQueueForTesting(totalCount: Int = 35) {
        val currentTokens = mutableListOf<QueueToken>()
        for (i in 1..totalCount) {
            val chamber = when (i % 3) {
                0 -> "Chamber A"
                1 -> "Chamber B"
                else -> "Chamber C"
            }
            val status = when (i) {
                1 -> TokenStatus.IN_CONSULTATION
                else -> TokenStatus.WAITING
            }
            currentTokens.add(
                QueueToken(
                    id = "tok_large_$i",
                    tokenNumber = i,
                    label = "T-${i.toString().padStart(2, '0')}",
                    status = status,
                    priority = (i == 2),
                    queuePosition = if (status == TokenStatus.WAITING) i - 1 else 0,
                    displayName = "Synthetic Patient $i",
                    checkedInAt = "09:${(i % 60).toString().padStart(2, '0')}",
                    calledAt = if (status == TokenStatus.IN_CONSULTATION) "09:15" else null,
                    patientId = "pat_large_$i",
                    prescriptionId = "rx_large_$i",
                    rejectionReason = null,
                    estimatedWaitMinutes = (i * 8),
                    estimateQuality = "REAL_TIME",
                    chamber = chamber,
                    appointmentDate = "Monday",
                    doctorId = if (chamber == "Chamber A") "doc_john" else if (chamber == "Chamber B") "doc_donna" else "doc_alex"
                )
            )
        }
        recalculateQueue(currentTokens)
        _tokensState.value = currentTokens
    }

    private fun recalculateQueue(tokens: MutableList<QueueToken>) {
        val waiting = tokens.filter { it.status == TokenStatus.WAITING }
            .sortedWith(compareByDescending<QueueToken> { it.priority }.thenBy { it.tokenNumber })

        tokens.forEachIndexed { i, tok ->
            if (tok.status == TokenStatus.WAITING) {
                val rank = waiting.indexOfFirst { it.id == tok.id } + 1
                tokens[i] = tok.copy(
                    queuePosition = rank,
                    estimatedWaitMinutes = (rank * 10) - 4
                )
            }
        }

        val session = _sessionState.value
        _sessionState.value = session.copy(waitingCount = waiting.size)
        val stats = _doctorStatsState.value
        _doctorStatsState.value = stats.copy(currentlyWaiting = waiting.size)
    }
}

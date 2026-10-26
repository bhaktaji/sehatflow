package com.example.data.repository

import com.example.core.common.Resource
import com.example.core.database.AppDatabase
import com.example.core.database.entity.CachedQueueSession
import com.example.core.database.entity.CachedQueueToken
import com.example.core.datastore.SessionPreferences
import com.example.core.demo.DemoDataProvider
import com.example.core.network.ApiService
import com.example.core.network.dto.*
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

interface QueueRepository {
    fun observeTokensForSession(sessionId: String): Flow<List<QueueToken>>
    fun observeTokenById(tokenId: String): Flow<QueueToken?>
    fun observePatientHistory(patientId: String): Flow<List<QueueToken>>
    suspend fun fetchDoctors(): Resource<List<ClinicDoctor>>
    suspend fun fetchSessions(date: String? = null): Resource<List<QueueSession>>
    suspend fun refreshQueueView(sessionId: String): Resource<Pair<QueueSession, List<QueueToken>>>
    suspend fun requestToken(sessionId: String, prescriptionId: String, displayName: String?): Resource<QueueToken>
    suspend fun cancelToken(tokenId: String, reason: String): Resource<QueueToken>
    suspend fun createWalkInToken(sessionId: String, displayName: String, priority: Boolean): Resource<QueueToken>
    suspend fun callNext(sessionId: String): Resource<QueueToken>
    suspend fun startConsultation(tokenId: String): Resource<QueueToken>
    suspend fun completeConsultation(tokenId: String): Resource<QueueToken>
    suspend fun skipToken(tokenId: String, reason: String): Resource<QueueToken>
    suspend fun setPriority(tokenId: String, enabled: Boolean, reason: String): Resource<QueueToken>
    suspend fun reorderToken(tokenId: String, targetPosition: Int, reason: String): Resource<QueueToken>
    suspend fun pauseSession(sessionId: String, message: String?): Resource<QueueSession>
    suspend fun resumeSession(sessionId: String): Resource<QueueSession>
    suspend fun fetchDoctorStats(sessionId: String): Resource<DoctorStats>
    suspend fun fetchPendingPrescriptions(sessionId: String): Resource<List<PendingPrescription>>
    suspend fun verifyPrescription(tokenId: String, approve: Boolean, reason: String?): Resource<QueueToken>
    suspend fun requestAppointment(
        sessionId: String,
        prescriptionId: String?,
        displayName: String?,
        presentingComplaint: String?,
        doctorId: String? = "doc_john",
        requestedDay: String = "Monday",
        clinicName: String = "XYZ Clinic",
        authenticatedPatientId: String? = "pat_demo_patient",
        patientPhone: String? = "+91 9876543210"
    ): Resource<QueueToken>
    suspend fun assignDoctorToRequest(
        tokenId: String,
        targetDoctorId: String,
        requestedClinicName: String = "XYZ Clinic",
        requestedDay: String = "Monday",
        actorId: String = "user_recep_1"
    ): Resource<QueueToken>
}

class QueueRepositoryImpl(
    private val apiService: ApiService,
    private val database: AppDatabase,
    private val sessionPreferences: SessionPreferences
) : QueueRepository {

    private val tokenDao = database.queueTokenDao()
    private val sessionDao = database.queueSessionDao()

    override fun observeTokensForSession(sessionId: String): Flow<List<QueueToken>> {
        if (sessionPreferences.isDemoModeSync()) {
            return DemoDataProvider.observeTokens(sessionId)
        }
        return tokenDao.getTokensForSession(sessionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeTokenById(tokenId: String): Flow<QueueToken?> {
        if (sessionPreferences.isDemoModeSync()) {
            return DemoDataProvider.observeToken(tokenId)
        }
        return tokenDao.getTokenById(tokenId).map { it?.toDomain() }
    }

    override fun observePatientHistory(patientId: String): Flow<List<QueueToken>> {
        if (sessionPreferences.isDemoModeSync()) {
            return DemoDataProvider.observePatientHistory(patientId)
        }
        return tokenDao.getTokensForPatient(patientId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun fetchDoctors(): Resource<List<ClinicDoctor>> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.demoDoctors)
        }
        return try {
            val response = apiService.getDoctors()
            val list = response.map { dto ->
                val matched = DemoDataProvider.demoDoctors.find { it.id == dto.id }
                ClinicDoctor(
                    id = dto.id,
                    displayName = dto.displayName,
                    specialty = dto.specialty ?: matched?.specialty,
                    active = dto.active,
                    credentials = matched?.credentials ?: "Board Certified",
                    qualifications = matched?.qualifications ?: "MBBS, MD",
                    experienceYears = matched?.experienceYears ?: 10,
                    primaryClinic = matched?.primaryClinic ?: "XYZ Clinic",
                    primaryChamber = matched?.primaryChamber ?: "Chamber A",
                    schedules = matched?.schedules ?: DemoDataProvider.demoDoctors.first().schedules
                )
            }
            if (list.isNotEmpty()) {
                Resource.Success(list)
            } else {
                Resource.Success(DemoDataProvider.demoDoctors)
            }
        } catch (e: Exception) {
            Resource.Success(DemoDataProvider.demoDoctors)
        }
    }

    override suspend fun fetchSessions(date: String?): Resource<List<QueueSession>> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(listOf(DemoDataProvider.getSession("")))
        }
        return try {
            val response = apiService.getSessions(date)
            val domainList = response.map { it.toDomain() }
            sessionDao.insertSessions(response.map { it.toEntity() })
            if (domainList.isNotEmpty()) {
                Resource.Success(domainList)
            } else {
                Resource.Success(listOf(DemoDataProvider.getSession("")))
            }
        } catch (e: Exception) {
            Resource.Success(listOf(DemoDataProvider.getSession("")))
        }
    }

    override suspend fun refreshQueueView(sessionId: String): Resource<Pair<QueueSession, List<QueueToken>>> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(Pair(DemoDataProvider.getSession(sessionId), DemoDataProvider.getTokens()))
        }
        return try {
            val response = apiService.getQueueView(sessionId)
            val session = response.session.toDomain()
            val tokens = response.tokens.map { it.toDomain() }

            sessionDao.insertSession(response.session.toEntity())
            tokenDao.insertTokens(response.tokens.map { it.toEntity(sessionId) })

            Resource.Success(Pair(session, tokens))
        } catch (e: Exception) {
            Resource.Success(Pair(DemoDataProvider.getSession(sessionId), DemoDataProvider.getTokens()))
        }
    }

    override suspend fun requestToken(sessionId: String, prescriptionId: String, displayName: String?): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.requestToken(prescriptionId, displayName))
        }
        return try {
            val response = apiService.requestToken(sessionId, RequestTokenRequestDto(prescriptionId, displayName))
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(sessionId))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Success(DemoDataProvider.requestToken(prescriptionId, displayName))
        }
    }

    override suspend fun cancelToken(tokenId: String, reason: String): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.cancelToken(tokenId, reason))
        }
        return try {
            val response = apiService.cancelToken(tokenId, TokenActionRequestDto(reason))
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(""))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun createWalkInToken(sessionId: String, displayName: String, priority: Boolean): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.createWalkIn(displayName, priority))
        }
        return try {
            val response = apiService.createWalkInToken(sessionId, CreateWalkInRequestDto(displayName, priority))
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(sessionId))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun callNext(sessionId: String): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.callNext())
        }
        return try {
            val response = apiService.callNext(sessionId)
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(sessionId))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun startConsultation(tokenId: String): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            val tok = DemoDataProvider.getTokens().firstOrNull { it.id == tokenId }
                ?: DemoDataProvider.getTokens().first { it.status == TokenStatus.IN_CONSULTATION }
            return Resource.Success(tok)
        }
        return try {
            val response = apiService.startConsultation(tokenId)
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(""))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun completeConsultation(tokenId: String): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.completeConsultation(tokenId))
        }
        return try {
            val response = apiService.completeConsultation(tokenId)
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(""))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun skipToken(tokenId: String, reason: String): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.skipToken(tokenId, reason))
        }
        return try {
            val response = apiService.skipToken(tokenId, TokenActionRequestDto(reason))
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(""))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun setPriority(tokenId: String, enabled: Boolean, reason: String): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.setPriority(tokenId, enabled, reason))
        }
        return try {
            val response = apiService.setTokenPriority(tokenId, PriorityRequestDto(enabled, reason))
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(""))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun reorderToken(tokenId: String, targetPosition: Int, reason: String): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.reorderToken(tokenId, targetPosition, reason))
        }
        return try {
            val response = apiService.reorderToken(tokenId, ReorderRequestDto(targetPosition, reason))
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(""))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun pauseSession(sessionId: String, message: String?): Resource<QueueSession> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.pauseSession(message))
        }
        return try {
            val response = apiService.pauseSession(sessionId, PauseRequestDto(message))
            sessionDao.insertSession(response.toEntity())
            Resource.Success(response.toDomain())
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun resumeSession(sessionId: String): Resource<QueueSession> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.resumeSession())
        }
        return try {
            val response = apiService.resumeSession(sessionId)
            sessionDao.insertSession(response.toEntity())
            Resource.Success(response.toDomain())
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun fetchDoctorStats(sessionId: String): Resource<DoctorStats> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.getDoctorStats())
        }
        return try {
            val response = apiService.getDoctorStats(sessionId)
            Resource.Success(DoctorStats(
                response.patientsSeenToday,
                response.averageConsultationMinutes,
                response.currentlyWaiting
            ))
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun fetchPendingPrescriptions(sessionId: String): Resource<List<PendingPrescription>> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.getPendingPrescriptions())
        }
        return try {
            val response = apiService.getPendingPrescriptions(sessionId)
            val domainList = response.map {
                PendingPrescription(
                    tokenId = it.tokenId,
                    prescriptionId = it.prescriptionId,
                    patientId = it.patientId,
                    patientName = it.patientName,
                    patientPhone = it.patientPhone,
                    requestedAt = it.requestedAt,
                    fileType = it.fileType,
                    fileSize = it.fileSize
                )
            }
            Resource.Success(domainList)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun verifyPrescription(tokenId: String, approve: Boolean, reason: String?): Resource<QueueToken> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success(DemoDataProvider.verifyPrescription(tokenId, approve, reason))
        }
        return try {
            val response = apiService.verifyPrescription(tokenId, VerifyPrescriptionRequestDto(approve, reason))
            val domain = response.toDomain()
            tokenDao.insertToken(response.toEntity(""))
            Resource.Success(domain)
        } catch (e: Exception) {
            Resource.Error(formatError(e), e)
        }
    }

    override suspend fun requestAppointment(
        sessionId: String,
        prescriptionId: String?,
        displayName: String?,
        presentingComplaint: String?,
        doctorId: String?,
        requestedDay: String,
        clinicName: String,
        authenticatedPatientId: String?,
        patientPhone: String?
    ): Resource<QueueToken> {
        val hasRx = !prescriptionId.isNullOrBlank() && !prescriptionId.startsWith("NO_RX")
        val validatedComplaint = DataRetentionPolicyEngine.validateAndSanitizePresentingComplaint(
            rawComplaint = presentingComplaint,
            hasPrescription = hasRx
        ).getOrElse { err ->
            return Resource.Error(err.message ?: "Invalid presenting complaint.")
        }

        val tok = DemoDataProvider.requestToken(
            prescriptionId = prescriptionId ?: "NO_RX_COMPLAINT_ONLY",
            displayName = displayName,
            doctorId = doctorId ?: "UNASSIGNED",
            requestedDay = requestedDay,
            clinicName = clinicName,
            presentingComplaint = validatedComplaint,
            authenticatedPatientId = authenticatedPatientId,
            patientPhone = patientPhone
        )
        return Resource.Success(tok)
    }

    override suspend fun assignDoctorToRequest(
        tokenId: String,
        targetDoctorId: String,
        requestedClinicName: String,
        requestedDay: String,
        actorId: String
    ): Resource<QueueToken> {
        return DemoDataProvider.assignDoctorToPendingRequest(
            tokenId = tokenId,
            targetDoctorId = targetDoctorId,
            requestedClinicName = requestedClinicName,
            requestedDay = requestedDay,
            actorId = actorId
        ).fold(
            onSuccess = { Resource.Success(it) },
            onFailure = { Resource.Error(it.message ?: "Failed to assign doctor.") }
        )
    }

    private fun formatError(e: Exception): String {
        return if (e is ConnectException || e is SocketTimeoutException || e is UnknownHostException ||
            e.message?.contains("Failed to connect", ignoreCase = true) == true ||
            e.message?.contains("ECONNREFUSED", ignoreCase = true) == true
        ) {
            val url = sessionPreferences.getServerUrlSync()
            "Cannot connect to clinic server at $url. Please check your internet connection or verify the server endpoint in Settings."
        } else {
            e.message ?: "Operation failed"
        }
    }

    // Mapper Helpers
    private fun TokenResponseDto.toDomain() = QueueToken(
        id = id,
        tokenNumber = tokenNumber,
        label = label,
        status = try { TokenStatus.valueOf(status) } catch (_: Exception) { TokenStatus.WAITING },
        priority = priority,
        queuePosition = queuePosition,
        displayName = displayName,
        checkedInAt = checkedInAt,
        calledAt = calledAt,
        patientId = patientId,
        prescriptionId = prescriptionId,
        rejectionReason = rejectionReason,
        estimatedWaitMinutes = estimatedWaitMinutes,
        estimateQuality = estimateQuality
    )

    private fun SessionResponseDto.toDomain() = QueueSession(
        id = id,
        clinicId = clinicId,
        doctorId = doctorId,
        doctorName = doctorName,
        businessDate = businessDate,
        status = status,
        tokenPrefix = tokenPrefix,
        nextTokenNumber = nextTokenNumber,
        fallbackConsultMinutes = fallbackConsultMinutes,
        pauseMessage = pauseMessage,
        waitingCount = waitingCount,
        totalTokens = totalTokens
    )

    private fun TokenResponseDto.toEntity(fallbackSessionId: String) = CachedQueueToken(
        id = id,
        tokenNumber = tokenNumber,
        label = label,
        status = status,
        priority = priority,
        queuePosition = queuePosition,
        displayName = displayName,
        checkedInAt = checkedInAt,
        calledAt = calledAt,
        patientId = patientId,
        prescriptionId = prescriptionId,
        rejectionReason = rejectionReason,
        estimatedWaitMinutes = estimatedWaitMinutes,
        estimateQuality = estimateQuality,
        sessionId = fallbackSessionId
    )

    private fun SessionResponseDto.toEntity() = CachedQueueSession(
        id = id,
        clinicId = clinicId,
        doctorId = doctorId,
        doctorName = doctorName,
        businessDate = businessDate,
        status = status,
        tokenPrefix = tokenPrefix,
        nextTokenNumber = nextTokenNumber,
        fallbackConsultMinutes = fallbackConsultMinutes,
        pauseMessage = pauseMessage,
        waitingCount = waitingCount,
        totalTokens = totalTokens
    )

    private fun CachedQueueToken.toDomain() = QueueToken(
        id = id,
        tokenNumber = tokenNumber,
        label = label,
        status = try { TokenStatus.valueOf(status) } catch (_: Exception) { TokenStatus.WAITING },
        priority = priority,
        queuePosition = queuePosition,
        displayName = displayName,
        checkedInAt = checkedInAt,
        calledAt = calledAt,
        patientId = patientId,
        prescriptionId = prescriptionId,
        rejectionReason = rejectionReason,
        estimatedWaitMinutes = estimatedWaitMinutes,
        estimateQuality = estimateQuality
    )
}

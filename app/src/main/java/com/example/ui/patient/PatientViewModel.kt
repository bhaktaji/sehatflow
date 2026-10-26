package com.example.ui.patient

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.Resource
import com.example.core.datastore.SessionPreferences
import com.example.data.di.ServiceLocator
import com.example.data.repository.PrescriptionRepository
import com.example.data.repository.QueueRepository
import com.example.domain.logic.EtaCalculator
import com.example.domain.model.ClinicDoctor
import com.example.domain.model.QueueSession
import com.example.domain.model.QueueToken
import com.example.domain.model.TokenStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.core.demo.DemoDataProvider
import com.example.data.repository.ConsultationHistoryRepository
import com.example.domain.model.*

data class PatientUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isUploading: Boolean = false,
    val isOffline: Boolean = false,
    val currentUserSession: UserSession? = null,
    val patientIdentifier: String = "PAT-1001",
    val doctors: List<ClinicDoctor> = emptyList(),
    val activeSessions: List<QueueSession> = emptyList(),
    val selectedDoctor: ClinicDoctor? = null,
    val selectedSession: QueueSession? = null,
    val selectedSlot: DoctorScheduleSlot? = null,
    val activeToken: QueueToken? = null,
    val nowServingToken: QueueToken? = null,
    val peopleAhead: Int = 0,
    val calculatedWaitMinutes: Int? = null,
    val calculatedWaitQuality: String = "LOW",
    val calculatedMessage: String = "",
    val pastVisits: List<QueueToken> = emptyList(),
    val historyCriteria: HistoryFilterCriteria = HistoryFilterCriteria(),
    val filteredHistory: FilteredHistoryResult = FilteredHistoryResult(
        records = emptyList(),
        totalMatchedCount = 0,
        page = 1,
        pageSize = 25,
        hasMorePages = false,
        activeWindowLabel = HistoryDateRangePreset.LAST_3_MONTHS.label
    ),
    val selectedHistoryRecord: ConsultationRecord? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

class PatientViewModel(
    private val queueRepository: QueueRepository = ServiceLocator.queueRepository,
    private val prescriptionRepository: PrescriptionRepository = ServiceLocator.prescriptionRepository,
    private val sessionPreferences: SessionPreferences = ServiceLocator.sessionPreferences,
    private val consultationHistoryRepository: ConsultationHistoryRepository = ServiceLocator.consultationHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientUiState())
    val uiState: StateFlow<PatientUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        loadInitialData()
        observeActiveSessionAndToken()
    }

    fun refresh() {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Fetch available doctors with resilient fallback
            val fetchedDocs = when (val doctorsRes = queueRepository.fetchDoctors()) {
                is Resource.Success -> {
                    if (doctorsRes.data.isNotEmpty()) doctorsRes.data else DemoDataProvider.demoDoctors
                }
                is Resource.Error -> {
                    DemoDataProvider.demoDoctors
                }
                Resource.Loading -> DemoDataProvider.demoDoctors
            }
            _uiState.update { it.copy(doctors = fetchedDocs) }

            // 2. Fetch active sessions with resilient fallback
            when (val sessionsRes = queueRepository.fetchSessions()) {
                is Resource.Success -> {
                    val openSessions = sessionsRes.data.filter { it.status != "CLOSED" }
                        .ifEmpty { listOf(DemoDataProvider.getSession("")) }
                    val defaultSession = openSessions.firstOrNull() ?: DemoDataProvider.getSession("")
                    _uiState.update { state ->
                        state.copy(
                            activeSessions = openSessions,
                            selectedSession = defaultSession,
                            selectedDoctor = state.selectedDoctor ?: state.doctors.find { it.id == defaultSession.doctorId } ?: state.doctors.firstOrNull(),
                            isLoading = false
                        )
                    }
                }
                is Resource.Error -> {
                    val fallbackSession = DemoDataProvider.getSession("")
                    _uiState.update { state ->
                        state.copy(
                            activeSessions = listOf(fallbackSession),
                            selectedSession = fallbackSession,
                            selectedDoctor = state.selectedDoctor ?: state.doctors.firstOrNull(),
                            isLoading = false
                        )
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    private fun observeActiveSessionAndToken() {
        viewModelScope.launch {
            combine(
                sessionPreferences.activeTokenId,
                sessionPreferences.activeSessionId,
                sessionPreferences.userSession
            ) { activeTokenId, activeSessionId, userSession ->
                Triple(activeTokenId, activeSessionId, userSession)
            }.collectLatest { (tokenId, sessionId, userSession) ->
                if (userSession == null) {
                    clearPatientSessionState()
                } else {
                    val patIdBadge = PatientIdentityRegistry.getIdentifierForPatientId(userSession.userId)
                    _uiState.update { it.copy(currentUserSession = userSession, patientIdentifier = patIdBadge) }
                    refreshHistoryForCurrentSession(userSession, _uiState.value.historyCriteria)
                    if (!tokenId.isNullOrBlank() && !sessionId.isNullOrBlank()) {
                        startRealtimeQueueSync(sessionId, tokenId)
                    } else {
                        loadPastVisits(userSession.userId)
                    }
                }
            }
        }
    }

    fun selectDoctor(doctor: ClinicDoctor, slot: DoctorScheduleSlot? = null) {
        val session = _uiState.value.activeSessions.find { it.doctorId == doctor.id }
            ?: DemoDataProvider.getSession("").copy(
                doctorId = doctor.id,
                doctorName = doctor.displayName,
                clinicId = slot?.clinicId ?: doctor.primaryClinic ?: "xyz_clinic"
            )
        _uiState.update {
            it.copy(
                selectedDoctor = doctor,
                selectedSession = session,
                selectedSlot = slot
            )
        }
    }

    fun uploadPrescriptionAndRequestToken(
        context: Context,
        fileUri: Uri?,
        displayName: String?,
        consentAccepted: Boolean,
        presentingComplaint: String? = null,
        needsDoctorAssignment: Boolean = false,
        onSuccess: (tokenId: String, sessionId: String) -> Unit
    ) {
        viewModelScope.launch {
            val session = _uiState.value.selectedSession ?: DemoDataProvider.getSession("")
            val doctor = _uiState.value.selectedDoctor
            val slot = _uiState.value.selectedSlot

            if (!needsDoctorAssignment && doctor == null) {
                _uiState.update { it.copy(errorMessage = "Please select an available doctor or request receptionist assignment.") }
                return@launch
            }

            val hasRx = fileUri != null
            val complaintValidation = DataRetentionPolicyEngine.validateAndSanitizePresentingComplaint(
                rawComplaint = presentingComplaint,
                hasPrescription = hasRx
            )
            if (complaintValidation.isFailure) {
                _uiState.update {
                    it.copy(errorMessage = complaintValidation.exceptionOrNull()?.message ?: "Please enter your presenting problem.")
                }
                return@launch
            }
            val sanitizedComplaint = complaintValidation.getOrNull()

            _uiState.update { it.copy(isUploading = true, errorMessage = null) }

            val targetDoctorId = if (needsDoctorAssignment) "UNASSIGNED" else (doctor?.id ?: "doc_john")
            val clinicName = slot?.clinicName ?: doctor?.primaryClinic ?: "XYZ Clinic"
            val requestedDay = slot?.dayOfWeek ?: "Monday"
            val authPatientId = _uiState.value.currentUserSession?.userId ?: "pat_demo_patient"

            // Step 1: If prescription file provided, upload it; otherwise proceed with complaint-only booking
            val uploadedRxId: String? = if (fileUri != null) {
                when (val uploadRes = prescriptionRepository.uploadPrescription(
                    context = context,
                    fileUri = fileUri,
                    clinicId = session.clinicId,
                    doctorId = targetDoctorId,
                    consentAccepted = consentAccepted
                )) {
                    is Resource.Success -> uploadRes.data.prescriptionId
                    is Resource.Error -> {
                        _uiState.update { it.copy(isUploading = false, errorMessage = uploadRes.message) }
                        return@launch
                    }
                    Resource.Loading -> null
                }
            } else {
                null
            }

            // Step 2: Request digital queue token / appointment
            when (val tokenRes = queueRepository.requestAppointment(
                sessionId = session.id,
                prescriptionId = uploadedRxId,
                displayName = displayName,
                presentingComplaint = sanitizedComplaint,
                doctorId = targetDoctorId,
                requestedDay = requestedDay,
                clinicName = clinicName,
                authenticatedPatientId = authPatientId
            )) {
                is Resource.Success -> {
                    val token = tokenRes.data
                    sessionPreferences.saveActiveToken(token.id, session.id)

                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            activeToken = token,
                            patientIdentifier = token.patientIdentifier ?: it.patientIdentifier,
                            successMessage = if (needsDoctorAssignment) {
                                "Appointment request submitted! Receptionist will assign an eligible doctor based on your problem."
                            } else if (uploadedRxId != null) {
                                "Prescription submitted! Awaiting receptionist verification."
                            } else {
                                "Appointment requested without prescription! Receptionist will verify and allocate your token."
                            }
                        )
                    }
                    onSuccess(token.id, session.id)
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(isUploading = false, errorMessage = tokenRes.message)
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun startRealtimeQueueSync(sessionId: String, tokenId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshQueueData(sessionId, tokenId)
                delay(5000) // 5s resilient polling sync
            }
        }
    }

    private suspend fun refreshQueueData(sessionId: String, tokenId: String) {
        when (val res = queueRepository.refreshQueueView(sessionId)) {
            is Resource.Success -> {
                val (session, allTokens) = res.data
                val myToken = allTokens.find { it.id == tokenId } ?: _uiState.value.activeToken
                val nowServing = allTokens.find { it.status == TokenStatus.IN_CONSULTATION || it.status == TokenStatus.CALLED }

                val waitingTokens = allTokens.filter { it.status == TokenStatus.WAITING }
                val aheadCount = if (myToken != null && myToken.status == TokenStatus.WAITING) {
                    val myIndex = waitingTokens.indexOfFirst { it.id == myToken.id }
                    if (myIndex >= 0) myIndex else 0
                } else {
                    0
                }

                // Compute pure local ETA
                val etaInput = EtaCalculator.CalculationInput(
                    ownStatus = myToken?.status ?: TokenStatus.WAITING,
                    peopleAhead = aheadCount,
                    isSessionPaused = session.status == "PAUSED",
                    isSessionClosed = session.status == "CLOSED",
                    fallbackMinutes = session.fallbackConsultMinutes,
                    recentDurationsMinutes = emptyList(),
                    activeConsultationStartedEpochSec = null,
                    currentServerEpochSec = System.currentTimeMillis() / 1000
                )
                val etaResult = EtaCalculator.calculate(etaInput)

                _uiState.update {
                    it.copy(
                        isOffline = false,
                        selectedSession = session,
                        activeToken = myToken,
                        nowServingToken = nowServing,
                        peopleAhead = aheadCount,
                        calculatedWaitMinutes = myToken?.estimatedWaitMinutes ?: etaResult.estimatedWaitMinutes,
                        calculatedWaitQuality = myToken?.estimateQuality ?: etaResult.estimateQuality,
                        calculatedMessage = etaResult.userFriendlyMessage,
                        lastUpdatedEpochMs = System.currentTimeMillis()
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isOffline = true) }
            }
            Resource.Loading -> {}
        }
    }

    fun cancelActiveToken(reason: String) {
        val token = _uiState.value.activeToken ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = queueRepository.cancelToken(token.id, reason)) {
                is Resource.Success -> {
                    sessionPreferences.saveActiveToken(null, null)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeToken = res.data,
                            successMessage = "Your token was cancelled."
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun loadPastVisits(patientId: String) {
        viewModelScope.launch {
            queueRepository.observePatientHistory(patientId).collect { visits ->
                _uiState.update { it.copy(pastVisits = visits) }
            }
        }
    }

    fun updateHistoryCriteria(newCriteria: HistoryFilterCriteria) {
        val session = _uiState.value.currentUserSession ?: UserSession(
            token = "demo_jwt_patient",
            userId = "pat_demo_patient",
            displayName = "David Miller",
            role = Role.PATIENT,
            clinicId = "clinic_xyz",
            doctorId = null
        )
        refreshHistoryForCurrentSession(session, newCriteria)
    }

    fun resetHistoryFilters() {
        updateHistoryCriteria(HistoryFilterCriteria())
    }

    private fun refreshHistoryForCurrentSession(session: UserSession, criteria: HistoryFilterCriteria) {
        val result = consultationHistoryRepository.queryHistory(session = session, criteria = criteria)
        _uiState.update {
            it.copy(
                historyCriteria = criteria,
                filteredHistory = result
            )
        }
    }

    fun openHistoryDetail(recordId: String) {
        val session = _uiState.value.currentUserSession ?: UserSession(
            token = "demo_jwt_patient",
            userId = "pat_demo_patient",
            displayName = "David Miller",
            role = Role.PATIENT,
            clinicId = "clinic_xyz",
            doctorId = null
        )
        when (val res = consultationHistoryRepository.getConsultationDetail(recordId, session)) {
            is Resource.Success -> {
                _uiState.update { it.copy(selectedHistoryRecord = res.data, errorMessage = null) }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(selectedHistoryRecord = null, errorMessage = res.message) }
            }
            Resource.Loading -> {}
        }
    }

    fun dismissHistoryDetail() {
        _uiState.update { it.copy(selectedHistoryRecord = null) }
    }

    fun clearPatientSessionState() {
        pollJob?.cancel()
        pollJob = null
        _uiState.update { state ->
            state.copy(
                currentUserSession = null,
                activeToken = null,
                nowServingToken = null,
                peopleAhead = 0,
                calculatedWaitMinutes = null,
                calculatedMessage = "",
                pastVisits = emptyList(),
                historyCriteria = HistoryFilterCriteria(),
                filteredHistory = FilteredHistoryResult(
                    records = emptyList(),
                    totalMatchedCount = 0,
                    page = 1,
                    pageSize = 25,
                    hasMorePages = false,
                    activeWindowLabel = HistoryDateRangePreset.LAST_3_MONTHS.label
                ),
                selectedHistoryRecord = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}

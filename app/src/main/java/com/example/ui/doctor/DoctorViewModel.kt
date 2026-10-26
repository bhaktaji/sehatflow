package com.example.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.Resource
import com.example.core.datastore.SessionPreferences
import com.example.data.di.ServiceLocator
import com.example.data.repository.PrescriptionRepository
import com.example.data.repository.QueueRepository
import com.example.data.repository.ConsultationHistoryRepository
import com.example.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DoctorUiState(
    val isLoading: Boolean = false,
    val isActionBusy: Boolean = false,
    val isOffline: Boolean = false,
    val currentUserSession: UserSession? = null,
    val currentSession: QueueSession? = null,
    val currentPatient: QueueToken? = null,
    val waitingTokens: List<QueueToken> = emptyList(),
    val stats: DoctorStats? = null,
    val selectedPrescriptionUrl: String? = null,
    val selectedPrescriptionTokenLabel: String? = null,
    val historyCriteria: HistoryFilterCriteria = HistoryFilterCriteria(
        dateRangePreset = HistoryDateRangePreset.LAST_3_MONTHS
    ),
    val filteredHistory: FilteredHistoryResult = FilteredHistoryResult(
        records = emptyList(),
        totalMatchedCount = 0,
        page = 1,
        pageSize = 25,
        hasMorePages = false,
        activeWindowLabel = HistoryDateRangePreset.LAST_3_MONTHS.label
    ),
    val selectedHistoryRecord: ConsultationRecord? = null,
    val patientMedicalTimeline: List<ConsultationRecord> = emptyList(),
    val timelinePatientLabel: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class DoctorViewModel(
    private val queueRepository: QueueRepository = ServiceLocator.queueRepository,
    private val prescriptionRepository: PrescriptionRepository = ServiceLocator.prescriptionRepository,
    private val sessionPreferences: SessionPreferences = ServiceLocator.sessionPreferences,
    private val consultationHistoryRepository: ConsultationHistoryRepository = ServiceLocator.consultationHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorUiState())
    val uiState: StateFlow<DoctorUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        loadDoctorSession()
        observeDoctorAuthSession()
    }

    private fun observeDoctorAuthSession() {
        viewModelScope.launch {
            sessionPreferences.userSession.collectLatest { userSession ->
                _uiState.update { it.copy(currentUserSession = userSession) }
                refreshDoctorHistory(_uiState.value.historyCriteria)
            }
        }
    }

    fun loadDoctorSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = queueRepository.fetchSessions()) {
                is Resource.Success -> {
                    // Match current doctor session or open session
                    val sessions = res.data
                    val session = sessions.find { it.status != "CLOSED" } ?: sessions.firstOrNull()
                    _uiState.update {
                        it.copy(
                            currentSession = session,
                            isLoading = false
                        )
                    }
                    if (session != null) {
                        startDoctorRealtimeSync(session.id)
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, isOffline = true, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    private fun startDoctorRealtimeSync(sessionId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshDoctorQueue(sessionId)
                delay(3500) // 3.5s sync
            }
        }
    }

    private suspend fun refreshDoctorQueue(sessionId: String) {
        when (val res = queueRepository.refreshQueueView(sessionId)) {
            is Resource.Success -> {
                val (session, tokens) = res.data
                val activePatient = tokens.find { it.status == TokenStatus.IN_CONSULTATION || it.status == TokenStatus.CALLED }
                val waitingList = tokens.filter { it.status == TokenStatus.WAITING }

                _uiState.update {
                    it.copy(
                        currentSession = session,
                        currentPatient = activePatient,
                        waitingTokens = waitingList,
                        isOffline = false
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isOffline = true) }
            }
            Resource.Loading -> {}
        }

        // Fetch today's stats
        when (val statsRes = queueRepository.fetchDoctorStats(sessionId)) {
            is Resource.Success -> {
                _uiState.update { it.copy(stats = statsRes.data) }
            }
            is Resource.Error -> {}
            Resource.Loading -> {}
        }
    }

    fun callNext() {
        val session = _uiState.value.currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.callNext(session.id)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            currentPatient = res.data,
                            successMessage = "Called token ${res.data.label}"
                        )
                    }
                    refreshDoctorQueue(session.id)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun startConsultation(tokenId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.startConsultation(tokenId)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            currentPatient = res.data,
                            successMessage = "Consultation started"
                        )
                    }
                    _uiState.value.currentSession?.let { refreshDoctorQueue(it.id) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun completeConsultation(
        tokenId: String,
        clinicalNotes: String? = null,
        diagnosis: String? = null,
        followUpRecommendations: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.completeConsultation(tokenId)) {
                is Resource.Success -> {
                    val session = _uiState.value.currentSession
                    consultationHistoryRepository.recordCompletedConsultationFromToken(
                        token = res.data,
                        clinicId = session?.clinicId ?: "clinic_xyz",
                        clinicName = if (session?.clinicId == "clinic_abc") "ABC Clinic" else "XYZ Clinic",
                        doctorName = session?.doctorName?.let { if (it.startsWith("Dr.")) it else "Dr. $it" } ?: "Dr. John",
                        clinicalNotes = clinicalNotes,
                        diagnosis = diagnosis,
                        followUpRecommendations = followUpRecommendations
                    )
                    refreshDoctorHistory(_uiState.value.historyCriteria)
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            currentPatient = null,
                            successMessage = "Consultation complete"
                        )
                    }
                    _uiState.value.currentSession?.let { refreshDoctorQueue(it.id) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun skipPatient(tokenId: String, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.skipToken(tokenId, reason)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            currentPatient = null,
                            successMessage = "Token skipped: $reason"
                        )
                    }
                    _uiState.value.currentSession?.let { refreshDoctorQueue(it.id) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun toggleBreak(pauseMessage: String?) {
        val session = _uiState.value.currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            val call = if (session.status == "PAUSED") {
                queueRepository.resumeSession(session.id)
            } else {
                queueRepository.pauseSession(session.id, pauseMessage)
            }
            when (call) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            currentSession = call.data,
                            successMessage = if (session.status == "PAUSED") "Consultations resumed." else "Doctor break started."
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = call.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun viewVerifiedPrescription(token: QueueToken) {
        val rxId = token.prescriptionId ?: return
        viewModelScope.launch {
            when (val res = prescriptionRepository.getSignedPrescriptionUrl(rxId)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedPrescriptionUrl = res.data,
                            selectedPrescriptionTokenLabel = token.label
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun dismissPrescriptionModal() {
        _uiState.update {
            it.copy(selectedPrescriptionUrl = null, selectedPrescriptionTokenLabel = null)
        }
    }

    private fun effectiveDoctorSession(): UserSession {
        return _uiState.value.currentUserSession ?: UserSession(
            token = "demo_jwt_doctor",
            userId = "doc_1",
            displayName = "Dr. John",
            role = Role.DOCTOR,
            clinicId = "clinic_xyz",
            doctorId = "doc_john"
        )
    }

    fun updateHistoryCriteria(newCriteria: HistoryFilterCriteria) {
        refreshDoctorHistory(newCriteria)
    }

    fun resetHistoryFilters() {
        refreshDoctorHistory(HistoryFilterCriteria(dateRangePreset = HistoryDateRangePreset.LAST_3_MONTHS))
    }

    fun refreshDoctorHistory(criteria: HistoryFilterCriteria = _uiState.value.historyCriteria) {
        val result = consultationHistoryRepository.queryHistory(
            session = effectiveDoctorSession(),
            criteria = criteria
        )
        _uiState.update {
            it.copy(
                historyCriteria = criteria,
                filteredHistory = result
            )
        }
    }

    fun openConsultationDetail(recordId: String) {
        when (val res = consultationHistoryRepository.getConsultationDetail(recordId, effectiveDoctorSession())) {
            is Resource.Success -> {
                _uiState.update { it.copy(selectedHistoryRecord = res.data, errorMessage = null) }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(selectedHistoryRecord = null, errorMessage = res.message) }
            }
            Resource.Loading -> {}
        }
    }

    fun dismissConsultationDetail() {
        _uiState.update { it.copy(selectedHistoryRecord = null) }
    }

    fun addClinicalAddendum(recordId: String, addendumText: String) {
        when (val res = consultationHistoryRepository.addClinicalAddendum(
            recordId = recordId,
            addendumText = addendumText,
            session = effectiveDoctorSession()
        )) {
            is Resource.Success -> {
                refreshDoctorHistory(_uiState.value.historyCriteria)
                _uiState.update {
                    it.copy(
                        selectedHistoryRecord = res.data,
                        successMessage = "Clinical addendum saved (v${res.data.version})"
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(errorMessage = res.message) }
            }
            Resource.Loading -> {}
        }
    }

    fun openPatientMedicalTimeline(patientIdOrIdentifier: String, patientDisplayName: String? = null) {
        when (val res = consultationHistoryRepository.getPatientMedicalTimelineForDoctor(
            patientIdOrIdentifier = patientIdOrIdentifier,
            session = effectiveDoctorSession()
        )) {
            is Resource.Success -> {
                val first = res.data.firstOrNull()
                val label = "${patientDisplayName ?: first?.patientName ?: "Patient"} (${first?.patientIdentifier ?: patientIdOrIdentifier})"
                _uiState.update {
                    it.copy(
                        patientMedicalTimeline = res.data,
                        timelinePatientLabel = label,
                        errorMessage = null
                    )
                }
            }
            is Resource.Error -> {
                val label = "${patientDisplayName ?: "Patient"} ($patientIdOrIdentifier)"
                _uiState.update {
                    it.copy(
                        patientMedicalTimeline = emptyList(),
                        timelinePatientLabel = label,
                        errorMessage = res.message
                    )
                }
            }
            Resource.Loading -> {}
        }
    }

    fun dismissPatientMedicalTimeline() {
        _uiState.update {
            it.copy(patientMedicalTimeline = emptyList(), timelinePatientLabel = null)
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

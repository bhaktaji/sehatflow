package com.example.ui.receptionist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.Resource
import com.example.core.datastore.SessionPreferences
import com.example.data.di.ServiceLocator
import com.example.data.repository.PrescriptionRepository
import com.example.data.repository.QueueRepository
import com.example.core.demo.DemoDataProvider
import com.example.domain.model.ClinicDoctor
import com.example.domain.model.PendingPrescription
import com.example.domain.model.QueueSession
import com.example.domain.model.QueueToken
import com.example.domain.model.TokenStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ReceptionistUiState(
    val isLoading: Boolean = false,
    val isActionBusy: Boolean = false,
    val isOffline: Boolean = false,
    val sessions: List<QueueSession> = emptyList(),
    val selectedSession: QueueSession? = null,
    val tokens: List<QueueToken> = emptyList(),
    val pendingPrescriptions: List<PendingPrescription> = emptyList(),
    val selectedPrescription: PendingPrescription? = null,
    val requestToAssignDoctor: PendingPrescription? = null,
    val availableDoctors: List<ClinicDoctor> = DemoDataProvider.demoDoctors,
    val signedPrescriptionUrl: String? = null,
    val waitingCount: Int = 0,
    val inConsultationCount: Int = 0,
    val completedCount: Int = 0,
    val noShowCount: Int = 0,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ReceptionistViewModel(
    private val queueRepository: QueueRepository = ServiceLocator.queueRepository,
    private val prescriptionRepository: PrescriptionRepository = ServiceLocator.prescriptionRepository,
    private val sessionPreferences: SessionPreferences = ServiceLocator.sessionPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceptionistUiState())
    val uiState: StateFlow<ReceptionistUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = queueRepository.fetchSessions()) {
                is Resource.Success -> {
                    val sessions = res.data
                    val current = _uiState.value.selectedSession ?: sessions.firstOrNull()
                    _uiState.update {
                        it.copy(
                            sessions = sessions,
                            selectedSession = current,
                            isLoading = false
                        )
                    }
                    if (current != null) {
                        startRealtimeSync(current.id)
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, isOffline = true, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun selectSession(session: QueueSession) {
        _uiState.update { it.copy(selectedSession = session) }
        startRealtimeSync(session.id)
    }

    private fun startRealtimeSync(sessionId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshQueueAndPrescriptions(sessionId)
                delay(4000) // 4s sync interval
            }
        }
    }

    private suspend fun refreshQueueAndPrescriptions(sessionId: String) {
        // 1. Fetch queue view
        when (val res = queueRepository.refreshQueueView(sessionId)) {
            is Resource.Success -> {
                val (session, tokens) = res.data
                val waiting = tokens.count { it.status == TokenStatus.WAITING }
                val inConsultation = tokens.count { it.status == TokenStatus.IN_CONSULTATION || it.status == TokenStatus.CALLED }
                val completed = tokens.count { it.status == TokenStatus.COMPLETED }
                val noShow = tokens.count { it.status == TokenStatus.NO_SHOW }

                _uiState.update {
                    it.copy(
                        selectedSession = session,
                        tokens = tokens,
                        waitingCount = waiting,
                        inConsultationCount = inConsultation,
                        completedCount = completed,
                        noShowCount = noShow,
                        isOffline = false
                    )
                }
            }
            is Resource.Error -> {
                _uiState.update { it.copy(isOffline = true) }
            }
            Resource.Loading -> {}
        }

        // 2. Fetch pending prescriptions
        when (val rxRes = queueRepository.fetchPendingPrescriptions(sessionId)) {
            is Resource.Success -> {
                _uiState.update { it.copy(pendingPrescriptions = rxRes.data) }
            }
            is Resource.Error -> {}
            Resource.Loading -> {}
        }
    }

    fun selectPrescriptionForReview(pending: PendingPrescription) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedPrescription = pending, signedPrescriptionUrl = null) }
            when (val urlRes = prescriptionRepository.getSignedPrescriptionUrl(pending.prescriptionId)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(signedPrescriptionUrl = urlRes.data) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(errorMessage = urlRes.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun dismissPrescriptionReview() {
        _uiState.update { it.copy(selectedPrescription = null, signedPrescriptionUrl = null) }
    }

    fun openAssignDoctorDialog(pending: PendingPrescription) {
        _uiState.update { it.copy(requestToAssignDoctor = pending, errorMessage = null) }
    }

    fun dismissAssignDoctorDialog() {
        _uiState.update { it.copy(requestToAssignDoctor = null) }
    }

    fun assignDoctorToRequest(
        tokenId: String,
        targetDoctorId: String,
        requestedClinicName: String = "XYZ Clinic",
        requestedDay: String = "Monday"
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true, errorMessage = null) }
            when (val res = queueRepository.assignDoctorToRequest(
                tokenId = tokenId,
                targetDoctorId = targetDoctorId,
                requestedClinicName = requestedClinicName,
                requestedDay = requestedDay,
                actorId = "recep_1"
            )) {
                is Resource.Success -> {
                    val docName = DemoDataProvider.demoDoctors.find { it.id == targetDoctorId }?.displayName ?: targetDoctorId
                    val dayNote = if (res.data.isRescheduled) " (${res.data.appointmentDate})" else ""
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            requestToAssignDoctor = null,
                            successMessage = "Assigned ${res.data.displayName ?: "Patient"} to $docName (${res.data.chamber}$dayNote) • Token ${res.data.label} active in queue."
                        )
                    }
                    _uiState.value.selectedSession?.let { refreshQueueAndPrescriptions(it.id) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun approvePrescription(tokenId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.verifyPrescription(tokenId, approve = true, reason = null)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            selectedPrescription = null,
                            signedPrescriptionUrl = null,
                            successMessage = "Prescription approved. Token ${res.data.label} assigned."
                        )
                    }
                    _uiState.value.selectedSession?.let { refreshQueueAndPrescriptions(it.id) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun rejectPrescription(tokenId: String, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.verifyPrescription(tokenId, approve = false, reason = reason)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            selectedPrescription = null,
                            signedPrescriptionUrl = null,
                            successMessage = "Prescription rejected: $reason"
                        )
                    }
                    _uiState.value.selectedSession?.let { refreshQueueAndPrescriptions(it.id) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun addWalkIn(displayName: String, isPriority: Boolean) {
        val session = _uiState.value.selectedSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.createWalkInToken(session.id, displayName, isPriority)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            successMessage = "Walk-in patient registered. Token ${res.data.label} assigned."
                        )
                    }
                    refreshQueueAndPrescriptions(session.id)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun togglePriority(tokenId: String, enabled: Boolean, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.setPriority(tokenId, enabled, reason)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            successMessage = if (enabled) "Emergency priority set." else "Priority removed."
                        )
                    }
                    _uiState.value.selectedSession?.let { refreshQueueAndPrescriptions(it.id) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun reorderQueue(tokenId: String, targetPosition: Int, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionBusy = true) }
            when (val res = queueRepository.reorderToken(tokenId, targetPosition, reason)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionBusy = false,
                            successMessage = "Token position updated to #$targetPosition"
                        )
                    }
                    _uiState.value.selectedSession?.let { refreshQueueAndPrescriptions(it.id) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isActionBusy = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun toggleDoctorPause(pauseMessage: String?) {
        val session = _uiState.value.selectedSession ?: return
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
                            selectedSession = call.data,
                            successMessage = if (session.status == "PAUSED") "Consultations resumed." else "Session paused."
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

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}

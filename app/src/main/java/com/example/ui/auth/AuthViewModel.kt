package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.Resource
import com.example.core.datastore.AppThemeMode
import com.example.data.di.ServiceLocator
import com.example.data.repository.AuthRepository
import com.example.domain.model.Role
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val otpSent: Boolean = false,
    val otpMessage: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    val currentSession: StateFlow<UserSession?> = authRepository.currentSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val serverUrl: StateFlow<String> = ServiceLocator.sessionPreferences.serverUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.core.common.Constants.DEFAULT_BASE_URL)

    val isDemoMode: StateFlow<Boolean> = ServiceLocator.sessionPreferences.isDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val themeMode: StateFlow<AppThemeMode> = ServiceLocator.sessionPreferences.themeMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ServiceLocator.sessionPreferences.getThemeModeSync()
        )

    val queueSoundVibrationEnabled: StateFlow<Boolean> = ServiceLocator.sessionPreferences.queueSoundVibrationEnabled
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ServiceLocator.sessionPreferences.isQueueSoundVibrationEnabledSync()
        )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun staffLogin(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val res = authRepository.staffLogin(username, password)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun loginAsDemo(role: Role) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val res = authRepository.loginAsDemo(role)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun completeStaffInviteOnboarding(
        assignedRole: com.example.data.model.UserRole?,
        clinicId: String?,
        actorId: String
    ) {
        val domainRole = when (assignedRole) {
            com.example.data.model.UserRole.DOCTOR -> Role.DOCTOR
            com.example.data.model.UserRole.RECEPTIONIST -> Role.RECEPTIONIST
            com.example.data.model.UserRole.CLINIC_ADMIN -> Role.OWNER
            else -> return
        }
        viewModelScope.launch {
            val resolvedClinicId = clinicId?.ifBlank { "clinic_xyz" } ?: "clinic_xyz"
            val session = UserSession(
                token = "staff_invite_${actorId}_${System.currentTimeMillis()}",
                userId = actorId,
                displayName = if (domainRole == Role.DOCTOR) "Dr. ${actorId.replaceFirstChar { it.uppercase() }}" else "Clinic Reception",
                role = domainRole,
                clinicId = resolvedClinicId,
                doctorId = if (domainRole == Role.DOCTOR) "doc_john" else null
            )
            ServiceLocator.sessionPreferences.saveSession(session)
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
        }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            runCatching {
                ServiceLocator.sessionPreferences.setServerUrl(url)
                _uiState.value = _uiState.value.copy(errorMessage = null)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.message)
            }
        }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            ServiceLocator.sessionPreferences.setDemoMode(enabled)
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            ServiceLocator.sessionPreferences.setThemeMode(mode)
        }
    }

    fun setQueueSoundVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            ServiceLocator.sessionPreferences.setQueueSoundVibrationEnabled(enabled)
        }
    }

    fun requestOtp(phoneNumber: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val res = authRepository.requestPatientOtp(phoneNumber)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otpSent = true,
                        otpMessage = res.data,
                        errorMessage = null
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun verifyOtp(phoneNumber: String, otp: String, displayName: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val res = authRepository.verifyPatientOtp(phoneNumber, otp, displayName)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

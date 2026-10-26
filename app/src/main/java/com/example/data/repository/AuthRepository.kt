package com.example.data.repository

import com.example.core.common.Resource
import com.example.core.datastore.SessionPreferences
import com.example.core.demo.DemoDataProvider
import com.example.core.network.ApiService
import com.example.core.network.dto.LoginRequestDto
import com.example.core.network.dto.PatientOtpRequestDto
import com.example.core.network.dto.PatientVerifyOtpRequestDto
import com.example.domain.model.Role
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

interface AuthRepository {
    val currentSession: Flow<UserSession?>
    suspend fun staffLogin(username: String, password: String): Resource<UserSession>
    suspend fun requestPatientOtp(phoneNumber: String): Resource<String>
    suspend fun verifyPatientOtp(phoneNumber: String, otp: String, displayName: String?): Resource<UserSession>
    suspend fun loginAsDemo(role: Role): Resource<UserSession>
    suspend fun logout(): Resource<Unit>
}

class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val sessionPreferences: SessionPreferences,
    private val database: com.example.core.database.AppDatabase? = null
) : AuthRepository {

    override val currentSession: Flow<UserSession?> = sessionPreferences.userSession

    override suspend fun loginAsDemo(role: Role): Resource<UserSession> {
        sessionPreferences.setDemoMode(true)
        val session = when (role) {
            Role.DOCTOR -> UserSession(
                token = "demo_jwt_doctor",
                userId = "doc_1",
                displayName = "Dr. Sarah Jenkins",
                role = Role.DOCTOR,
                clinicId = "clinic_1",
                doctorId = "doc_1"
            )
            Role.RECEPTIONIST -> UserSession(
                token = "demo_jwt_reception",
                userId = "recep_1",
                displayName = "Front Desk",
                role = Role.RECEPTIONIST,
                clinicId = "clinic_1",
                doctorId = null
            )
            Role.PATIENT -> {
                val demoPatientSession = UserSession(
                    token = "demo_jwt_patient",
                    userId = "pat_demo_patient",
                    displayName = "David Miller",
                    role = Role.PATIENT,
                    clinicId = "clinic_1",
                    doctorId = "doc_1"
                )
                sessionPreferences.saveActiveToken("tok_3", "sess_demo_1")
                demoPatientSession
            }
            Role.OWNER -> UserSession(
                token = "demo_jwt_owner",
                userId = "owner_1",
                displayName = "Clinic Admin",
                role = Role.OWNER,
                clinicId = "clinic_1",
                doctorId = null
            )
        }
        sessionPreferences.saveSession(session)
        return Resource.Success(session)
    }

    override suspend fun staffLogin(username: String, password: String): Resource<UserSession> {
        val trimmedUser = username.trim()
        val trimmedPass = password.trim()

        if (trimmedUser.isBlank()) {
            return Resource.Error("Username or email cannot be empty.")
        }
        if (trimmedPass.isBlank()) {
            return Resource.Error("Password cannot be empty.")
        }

        // Demo bypass is ONLY permitted when demo mode is explicitly active
        if (sessionPreferences.isDemoModeSync()) {
            return when (trimmedUser) {
                "doctor", "doc123" -> loginAsDemo(Role.DOCTOR)
                "reception", "recep123" -> loginAsDemo(Role.RECEPTIONIST)
                "owner", "owner123" -> loginAsDemo(Role.OWNER)
                else -> loginAsDemo(Role.RECEPTIONIST)
            }
        }

        return try {
            val response = apiService.staffLogin(LoginRequestDto(trimmedUser, trimmedPass))
            val session = UserSession(
                token = response.token,
                userId = response.userId,
                displayName = response.displayName,
                role = Role.valueOf(response.role),
                clinicId = response.clinicId,
                doctorId = response.doctorId
            )
            sessionPreferences.saveSession(session)
            Resource.Success(session)
        } catch (e: Exception) {
            Resource.Error(formatNetworkError(e), e)
        }
    }

    override suspend fun requestPatientOtp(phoneNumber: String): Resource<String> {
        val trimmedPhone = phoneNumber.trim()
        if (trimmedPhone.length < 10) {
            return Resource.Error("Please enter a valid 10-digit mobile number.")
        }

        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success("Demo OTP sent! Enter 123456 to verify.")
        }

        return try {
            val response = apiService.requestPatientOtp(PatientOtpRequestDto(trimmedPhone))
            val msg = response["message"]?.toString() ?: "OTP code sent successfully"
            Resource.Success(msg)
        } catch (e: Exception) {
            if (isConnectionException(e)) {
                Resource.Error(formatNetworkError(e), e)
            } else {
                Resource.Error(e.message ?: "Failed to send OTP", e)
            }
        }
    }

    override suspend fun verifyPatientOtp(phoneNumber: String, otp: String, displayName: String?): Resource<UserSession> {
        val trimmedOtp = otp.trim()
        val trimmedPhone = phoneNumber.trim()

        if (trimmedOtp.length != 6) {
            return Resource.Error("Invalid OTP format. OTP must be a 6-digit code.")
        }

        // Static OTP code 123456 is ONLY valid when demo mode is active
        if (sessionPreferences.isDemoModeSync() && trimmedOtp == "123456") {
            val name = displayName?.ifBlank { null } ?: "David Miller"
            val session = UserSession(
                token = "demo_patient_jwt",
                userId = "pat_demo_patient",
                displayName = name,
                role = Role.PATIENT,
                clinicId = "clinic_1",
                doctorId = "doc_1"
            )
            sessionPreferences.saveSession(session)
            sessionPreferences.saveActiveToken("tok_3", "sess_demo_1")
            return Resource.Success(session)
        }

        return try {
            val response = apiService.verifyPatientOtp(
                PatientVerifyOtpRequestDto(phoneNumber.trim(), otp.trim(), displayName?.trim())
            )
            val session = UserSession(
                token = response.token,
                userId = response.userId,
                displayName = response.displayName,
                role = Role.PATIENT,
                clinicId = response.clinicId,
                doctorId = null
            )
            sessionPreferences.saveSession(session)
            Resource.Success(session)
        } catch (e: Exception) {
            Resource.Error(formatNetworkError(e), e)
        }
    }

    override suspend fun logout(): Resource<Unit> {
        return try {
            // Clear patient-specific cached tokens in Room to isolate subsequent users on the same device
            runCatching { database?.queueTokenDao()?.clearAllCachedTokens() }
            // Sign out of Firebase Auth cleanly without revoking global Google account permissions
            runCatching<Unit> { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
            // Clear locally stored JWT, userId, activeTokenId, and activeSessionId
            sessionPreferences.clearSession(preserveConfig = true)
            Resource.Success(Unit)
        } catch (e: Exception) {
            // Ensure local session preferences are still cleared even if a secondary provider throws
            runCatching { sessionPreferences.clearSession(preserveConfig = true) }
            Resource.Error(e.message ?: "Logout encountered an error, local session cleared.", e)
        }
    }

    private fun isConnectionException(e: Exception): Boolean {
        return e is ConnectException || e is SocketTimeoutException || e is UnknownHostException ||
                e.message?.contains("Failed to connect", ignoreCase = true) == true ||
                e.message?.contains("ECONNREFUSED", ignoreCase = true) == true
    }

    private fun formatNetworkError(e: Exception): String {
        return if (isConnectionException(e)) {
            val currentUrl = sessionPreferences.getServerUrlSync()
            "Cannot reach server at $currentUrl. Check if Spring Boot is running on port 8090, configure host IP in Server Settings, or tap 'Explore Demo Mode'."
        } else {
            e.message ?: "Request failed"
        }
    }
}

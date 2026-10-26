package com.example.core.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequestDto(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class PatientOtpRequestDto(
    @Json(name = "phoneNumber") val phoneNumber: String
)

@JsonClass(generateAdapter = true)
data class PatientVerifyOtpRequestDto(
    @Json(name = "phoneNumber") val phoneNumber: String,
    @Json(name = "otp") val otp: String,
    @Json(name = "displayName") val displayName: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @Json(name = "token") val token: String,
    @Json(name = "userId") val userId: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "role") val role: String,
    @Json(name = "clinicId") val clinicId: String?,
    @Json(name = "doctorId") val doctorId: String?
)

@JsonClass(generateAdapter = true)
data class TokenResponseDto(
    @Json(name = "id") val id: String,
    @Json(name = "tokenNumber") val tokenNumber: Int,
    @Json(name = "label") val label: String,
    @Json(name = "status") val status: String,
    @Json(name = "priority") val priority: Boolean,
    @Json(name = "queuePosition") val queuePosition: Int,
    @Json(name = "displayName") val displayName: String?,
    @Json(name = "checkedInAt") val checkedInAt: String?,
    @Json(name = "calledAt") val calledAt: String?,
    @Json(name = "patientId") val patientId: String?,
    @Json(name = "prescriptionId") val prescriptionId: String?,
    @Json(name = "rejectionReason") val rejectionReason: String?,
    @Json(name = "estimatedWaitMinutes") val estimatedWaitMinutes: Int?,
    @Json(name = "estimateQuality") val estimateQuality: String?
)

@JsonClass(generateAdapter = true)
data class SessionResponseDto(
    @Json(name = "id") val id: String,
    @Json(name = "clinicId") val clinicId: String,
    @Json(name = "doctorId") val doctorId: String,
    @Json(name = "doctorName") val doctorName: String,
    @Json(name = "businessDate") val businessDate: String,
    @Json(name = "status") val status: String,
    @Json(name = "tokenPrefix") val tokenPrefix: String,
    @Json(name = "nextTokenNumber") val nextTokenNumber: Int,
    @Json(name = "fallbackConsultMinutes") val fallbackConsultMinutes: Int,
    @Json(name = "pauseMessage") val pauseMessage: String?,
    @Json(name = "waitingCount") val waitingCount: Int,
    @Json(name = "totalTokens") val totalTokens: Int
)

@JsonClass(generateAdapter = true)
data class QueueViewResponseDto(
    @Json(name = "session") val session: SessionResponseDto,
    @Json(name = "tokens") val tokens: List<TokenResponseDto>,
    @Json(name = "nowServing") val nowServing: TokenResponseDto?,
    @Json(name = "peopleWaiting") val peopleWaiting: Int
)

@JsonClass(generateAdapter = true)
data class PendingPrescriptionDto(
    @Json(name = "tokenId") val tokenId: String,
    @Json(name = "prescriptionId") val prescriptionId: String,
    @Json(name = "patientId") val patientId: String,
    @Json(name = "patientName") val patientName: String,
    @Json(name = "patientPhone") val patientPhone: String?,
    @Json(name = "requestedAt") val requestedAt: String?,
    @Json(name = "fileType") val fileType: String,
    @Json(name = "fileSize") val fileSize: Long
)

@JsonClass(generateAdapter = true)
data class VerifyPrescriptionRequestDto(
    @Json(name = "approve") val approve: Boolean,
    @Json(name = "rejectionReason") val rejectionReason: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateWalkInRequestDto(
    @Json(name = "displayName") val displayName: String,
    @Json(name = "priority") val priority: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class RequestTokenRequestDto(
    @Json(name = "prescriptionId") val prescriptionId: String,
    @Json(name = "displayName") val displayName: String? = null
)

@JsonClass(generateAdapter = true)
data class TokenActionRequestDto(
    @Json(name = "reason") val reason: String,
    @Json(name = "targetPosition") val targetPosition: Int? = null
)

@JsonClass(generateAdapter = true)
data class PriorityRequestDto(
    @Json(name = "enabled") val enabled: Boolean,
    @Json(name = "reason") val reason: String
)

@JsonClass(generateAdapter = true)
data class ReorderRequestDto(
    @Json(name = "targetPosition") val targetPosition: Int,
    @Json(name = "reason") val reason: String
)

@JsonClass(generateAdapter = true)
data class PauseRequestDto(
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class DoctorStatsResponseDto(
    @Json(name = "patientsSeenToday") val patientsSeenToday: Int,
    @Json(name = "averageConsultationMinutes") val averageConsultationMinutes: Double,
    @Json(name = "currentlyWaiting") val currentlyWaiting: Int
)

@JsonClass(generateAdapter = true)
data class DoctorResponseDto(
    @Json(name = "id") val id: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "specialty") val specialty: String?,
    @Json(name = "active") val active: Boolean
)

@JsonClass(generateAdapter = true)
data class SignedUrlResponseDto(
    @Json(name = "signedUrl") val signedUrl: String,
    @Json(name = "expiresAt") val expiresAt: Long
)

@JsonClass(generateAdapter = true)
data class UploadPrescriptionResponseDto(
    @Json(name = "prescriptionId") val prescriptionId: String,
    @Json(name = "fileType") val fileType: String,
    @Json(name = "size") val size: Long,
    @Json(name = "uploadedAt") val uploadedAt: String
)

@JsonClass(generateAdapter = true)
data class DeviceRegistrationDto(
    @Json(name = "fcmToken") val fcmToken: String,
    @Json(name = "deviceModel") val deviceModel: String? = null
)

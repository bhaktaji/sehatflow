package com.example.core.network

import com.example.core.network.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/login")
    suspend fun staffLogin(@Body request: LoginRequestDto): AuthResponseDto

    @POST("auth/patient/otp/request")
    suspend fun requestPatientOtp(@Body request: PatientOtpRequestDto): Map<String, Any>

    @POST("auth/patient/otp/verify")
    suspend fun verifyPatientOtp(@Body request: PatientVerifyOtpRequestDto): AuthResponseDto

    // Clinic & Doctors
    @GET("doctors")
    suspend fun getDoctors(): List<DoctorResponseDto>

    @GET("queue-sessions")
    suspend fun getSessions(@Query("date") date: String? = null): List<SessionResponseDto>

    @GET("queue-sessions/{id}")
    suspend fun getSession(@Path("id") id: String): SessionResponseDto

    @GET("queue-sessions/{id}/queue")
    suspend fun getQueueView(@Path("id") id: String): QueueViewResponseDto

    // Prescriptions
    @Multipart
    @POST("prescriptions/upload")
    suspend fun uploadPrescription(
        @Part file: MultipartBody.Part,
        @Part("clinicId") clinicId: RequestBody,
        @Part("doctorId") doctorId: RequestBody,
        @Part("consentAccepted") consentAccepted: RequestBody
    ): UploadPrescriptionResponseDto

    @GET("prescriptions/{id}/signed-url")
    suspend fun getSignedPrescriptionUrl(@Path("id") id: String): SignedUrlResponseDto

    @Streaming
    @GET
    suspend fun downloadPrescriptionFile(@Url fileUrl: String): ResponseBody

    // Queue Operations (Patient)
    @POST("queue-sessions/{id}/request-token")
    suspend fun requestToken(
        @Path("id") sessionId: String,
        @Body request: RequestTokenRequestDto
    ): TokenResponseDto

    @POST("tokens/{id}/cancel")
    suspend fun cancelToken(
        @Path("id") tokenId: String,
        @Body request: TokenActionRequestDto
    ): TokenResponseDto

    // Queue Operations (Receptionist)
    @GET("queue-sessions/{id}/pending-prescriptions")
    suspend fun getPendingPrescriptions(@Path("id") sessionId: String): List<PendingPrescriptionDto>

    @POST("tokens/{id}/verify")
    suspend fun verifyPrescription(
        @Path("id") tokenId: String,
        @Body request: VerifyPrescriptionRequestDto
    ): TokenResponseDto

    @POST("queue-sessions/{id}/tokens")
    suspend fun createWalkInToken(
        @Path("id") sessionId: String,
        @Body request: CreateWalkInRequestDto
    ): TokenResponseDto

    @POST("tokens/{id}/priority")
    suspend fun setTokenPriority(
        @Path("id") tokenId: String,
        @Body request: PriorityRequestDto
    ): TokenResponseDto

    @POST("tokens/{id}/reorder")
    suspend fun reorderToken(
        @Path("id") tokenId: String,
        @Body request: ReorderRequestDto
    ): TokenResponseDto

    // Queue Operations (Doctor)
    @POST("queue-sessions/{id}/call-next")
    suspend fun callNext(@Path("id") sessionId: String): TokenResponseDto

    @POST("tokens/{id}/start")
    suspend fun startConsultation(@Path("id") tokenId: String): TokenResponseDto

    @POST("tokens/{id}/complete")
    suspend fun completeConsultation(@Path("id") tokenId: String): TokenResponseDto

    @POST("tokens/{id}/skip")
    suspend fun skipToken(
        @Path("id") tokenId: String,
        @Body request: TokenActionRequestDto
    ): TokenResponseDto

    @POST("queue-sessions/{id}/pause")
    suspend fun pauseSession(
        @Path("id") sessionId: String,
        @Body request: PauseRequestDto
    ): SessionResponseDto

    @POST("queue-sessions/{id}/resume")
    suspend fun resumeSession(@Path("id") sessionId: String): SessionResponseDto

    @GET("queue-sessions/{id}/doctor-stats")
    suspend fun getDoctorStats(@Path("id") sessionId: String): DoctorStatsResponseDto

    // Device token registration
    @POST("notifications/device-token")
    suspend fun registerDeviceToken(@Body request: DeviceRegistrationDto): Response<Unit>
}

package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole {
    CLINIC_ADMIN,
    DOCTOR,
    RECEPTIONIST,
    PATIENT
}

enum class SubscriptionStatus {
    TRIAL,
    ACTIVE,
    EXPIRED
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = UserRole.PATIENT.name,
    val clinicId: String = "",
    val phone: String = "",
    val specialization: String = "",
    val roomNumber: String = "",
    val deskName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Clinic(
    @DocumentId val id: String = "",
    val clinicId: String = "",
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val adminUid: String = "",
    val inviteCodeDoctor: String = "",
    val inviteCodeReceptionist: String = "",
    val subscriptionStatus: String = SubscriptionStatus.TRIAL.name,
    val monthlyFee: Long = 499L,
    @ServerTimestamp val trialEndsAt: Timestamp? = null,
    @ServerTimestamp val subscribedUntil: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TokenItem(
    @DocumentId val id: String = "",
    val clinicId: String = "",
    val tokenNumber: Long = 0L,
    val tokenCode: String = "",
    val patientName: String = "",
    val patientPhone: String = "",
    val patientUid: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val status: String = "WAITING",
    val isPriority: Boolean = false,
    val notes: String = "",
    val estimatedWaitMinutes: Long = 15L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SubscriptionRecord(
    @DocumentId val id: String = "",
    val clinicId: String = "",
    val adminUid: String = "",
    val planName: String = "SehatFlow Pro (₹499/mo)",
    val amount: Long = 499L,
    val currency: String = "INR",
    val status: String = "ACTIVE",
    val paymentMethod: String = "MOCK_UPI",
    val transactionId: String = "",
    @ServerTimestamp val paidAt: Timestamp? = null,
    @ServerTimestamp val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

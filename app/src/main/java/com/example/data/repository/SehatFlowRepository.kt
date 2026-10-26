package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Clinic
import com.example.data.model.SubscriptionRecord
import com.example.data.model.TokenItem
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SehatFlowRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be authenticated before accessing SehatFlow Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val snapshot = db.collection("users").document(userId).get().await()
            if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        require(profile.userId == uid) { "User profile UID must match authenticated user." }
        val docRef = db.collection("users").document(uid)
        try {
            val payload = hashMapOf<String, Any>(
                "userId" to uid,
                "email" to profile.email,
                "displayName" to profile.displayName,
                "role" to profile.role,
                "clinicId" to profile.clinicId,
                "phone" to profile.phone,
                "specialization" to profile.specialization,
                "roomNumber" to profile.roomNumber,
                "deskName" to profile.deskName,
                "createdAt" to (profile.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeClinic(clinicId: String): Flow<Clinic?> = flow {
        val path = "clinics/$clinicId"
        emitAll(
            db.collection("clinics").document(clinicId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(Clinic::class.java) else null
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun saveClinic(clinic: Clinic) {
        val uid = requireUserId()
        val docRef = db.collection("clinics").document(clinic.clinicId)
        try {
            val payload = hashMapOf<String, Any>(
                "clinicId" to clinic.clinicId,
                "name" to clinic.name,
                "address" to clinic.address,
                "phone" to clinic.phone,
                "adminUid" to if (clinic.adminUid.isNotBlank()) clinic.adminUid else uid,
                "inviteCodeDoctor" to clinic.inviteCodeDoctor,
                "inviteCodeReceptionist" to clinic.inviteCodeReceptionist,
                "subscriptionStatus" to clinic.subscriptionStatus,
                "monthlyFee" to clinic.monthlyFee,
                "trialEndsAt" to (clinic.trialEndsAt ?: FieldValue.serverTimestamp()),
                "subscribedUntil" to (clinic.subscribedUntil ?: FieldValue.serverTimestamp()),
                "createdAt" to (clinic.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeTokens(clinicId: String): Flow<List<TokenItem>> = flow {
        val path = "clinics/$clinicId/tokens"
        emitAll(
            db.collection("clinics").document(clinicId).collection("tokens")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(TokenItem::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addToken(token: TokenItem): String {
        requireUserId()
        val clinicTokensRef = db.collection("clinics").document(token.clinicId).collection("tokens")
        val docRef = if (token.id.isNotBlank()) clinicTokensRef.document(token.id) else clinicTokensRef.document()
        try {
            val payload = hashMapOf<String, Any>(
                "id" to docRef.id,
                "clinicId" to token.clinicId,
                "tokenNumber" to token.tokenNumber,
                "tokenCode" to token.tokenCode,
                "patientName" to token.patientName,
                "patientPhone" to token.patientPhone,
                "patientUid" to token.patientUid,
                "doctorId" to token.doctorId,
                "doctorName" to token.doctorName,
                "status" to token.status,
                "isPriority" to token.isPriority,
                "notes" to token.notes,
                "estimatedWaitMinutes" to token.estimatedWaitMinutes,
                "createdAt" to (token.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateTokenStatus(clinicId: String, tokenId: String, newStatus: String) {
        requireUserId()
        val docRef = db.collection("clinics").document(clinicId).collection("tokens").document(tokenId)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun recordSubscription(record: SubscriptionRecord): String {
        val uid = requireUserId()
        val subRef = db.collection("subscriptions").document()
        try {
            val payload = hashMapOf<String, Any>(
                "id" to subRef.id,
                "clinicId" to record.clinicId,
                "adminUid" to uid,
                "planName" to record.planName,
                "amount" to record.amount,
                "currency" to record.currency,
                "status" to record.status,
                "paymentMethod" to record.paymentMethod,
                "transactionId" to record.transactionId,
                "paidAt" to FieldValue.serverTimestamp(),
                "expiresAt" to (record.expiresAt ?: FieldValue.serverTimestamp()),
                "createdAt" to FieldValue.serverTimestamp()
            )
            subRef.set(payload).await()

            // Update clinic subscription status
            db.collection("clinics").document(record.clinicId).update(
                mapOf(
                    "subscriptionStatus" to "ACTIVE",
                    "subscribedUntil" to (record.expiresAt ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            return subRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, subRef.path)
            throw e
        }
    }

    fun observeSubscriptions(clinicId: String): Flow<List<SubscriptionRecord>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "subscriptions"
        emitAll(
            db.collection("subscriptions")
                .whereEqualTo("adminUid", uid)
                .whereEqualTo("clinicId", clinicId)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(SubscriptionRecord::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    emit(emptyList())
                }
        )
    }
}

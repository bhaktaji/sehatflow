package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_tokens")
data class CachedQueueToken(
    @PrimaryKey val id: String,
    val tokenNumber: Int,
    val label: String,
    val status: String,
    val priority: Boolean,
    val queuePosition: Int,
    val displayName: String?,
    val checkedInAt: String?,
    val calledAt: String?,
    val patientId: String?,
    val prescriptionId: String?,
    val rejectionReason: String?,
    val estimatedWaitMinutes: Int?,
    val estimateQuality: String?,
    val sessionId: String,
    val cachedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_sessions")
data class CachedQueueSession(
    @PrimaryKey val id: String,
    val clinicId: String,
    val doctorId: String,
    val doctorName: String,
    val businessDate: String,
    val status: String,
    val tokenPrefix: String,
    val nextTokenNumber: Int,
    val fallbackConsultMinutes: Int,
    val pauseMessage: String?,
    val waitingCount: Int,
    val totalTokens: Int,
    val cachedAtEpochMs: Long = System.currentTimeMillis()
)

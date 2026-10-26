package com.example.domain.logic

import com.example.domain.model.TokenStatus
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure, unit-testable function for estimated wait time calculation.
 * Follows the server formula:
 * ETA = Remaining Time of Active Consultation + (Patients Ahead * Rolling Average Duration)
 */
object EtaCalculator {

    data class CalculationInput(
        val ownStatus: TokenStatus,
        val peopleAhead: Int,
        val isSessionPaused: Boolean,
        val isSessionClosed: Boolean,
        val fallbackMinutes: Int,
        val recentDurationsMinutes: List<Double>,
        val activeConsultationStartedEpochSec: Long?,
        val currentServerEpochSec: Long
    )

    data class CalculationResult(
        val estimatedWaitMinutes: Int?,
        val estimateQuality: String,
        val userFriendlyMessage: String
    )

    fun calculate(input: CalculationInput): CalculationResult {
        if (input.isSessionPaused) {
            return CalculationResult(
                estimatedWaitMinutes = null,
                estimateQuality = "UNAVAILABLE",
                userFriendlyMessage = "The doctor is currently taking a short break. Queue is paused."
            )
        }

        if (input.isSessionClosed) {
            return CalculationResult(
                estimatedWaitMinutes = null,
                estimateQuality = "UNAVAILABLE",
                userFriendlyMessage = "The clinic session is closed for today."
            )
        }

        return when (input.ownStatus) {
            TokenStatus.PENDING_VERIFICATION -> CalculationResult(
                estimatedWaitMinutes = null,
                estimateQuality = "PENDING",
                userFriendlyMessage = "Prescription is being reviewed by the receptionist."
            )
            TokenStatus.REJECTED -> CalculationResult(
                estimatedWaitMinutes = null,
                estimateQuality = "UNAVAILABLE",
                userFriendlyMessage = "Prescription was rejected. Please re-upload or visit reception."
            )
            TokenStatus.WAITING -> computeWaitingEta(input)
            TokenStatus.CALLED, TokenStatus.IN_CONSULTATION -> CalculationResult(
                estimatedWaitMinutes = 0,
                estimateQuality = "NOW",
                userFriendlyMessage = "Please proceed into the doctor's room."
            )
            TokenStatus.SKIPPED -> CalculationResult(
                estimatedWaitMinutes = null,
                estimateQuality = "UNAVAILABLE",
                userFriendlyMessage = "Your token was skipped. Please speak with the receptionist."
            )
            TokenStatus.NO_SHOW -> CalculationResult(
                estimatedWaitMinutes = null,
                estimateQuality = "UNAVAILABLE",
                userFriendlyMessage = "Marked as not present."
            )
            TokenStatus.CANCELLED -> CalculationResult(
                estimatedWaitMinutes = null,
                estimateQuality = "UNAVAILABLE",
                userFriendlyMessage = "Token has been cancelled."
            )
            TokenStatus.COMPLETED -> CalculationResult(
                estimatedWaitMinutes = null,
                estimateQuality = "COMPLETED",
                userFriendlyMessage = "Your consultation is complete. Thank you!"
            )
        }
    }

    private fun computeWaitingEta(input: CalculationInput): CalculationResult {
        val durations = input.recentDurationsMinutes
        val (rollingAverage, quality) = if (durations.size >= 2) {
            val avg = durations.sum() / durations.size
            val q = if (durations.size >= 5) "HIGH" else "MEDIUM"
            Pair(avg, q)
        } else {
            val fallback = if (input.fallbackMinutes > 0) input.fallbackMinutes.toDouble() else 10.0
            Pair(fallback, "LOW")
        }

        var remainingActiveMinutes = 0.0
        if (input.activeConsultationStartedEpochSec != null && input.activeConsultationStartedEpochSec > 0) {
            val elapsedSec = max(0L, input.currentServerEpochSec - input.activeConsultationStartedEpochSec)
            val elapsedMin = elapsedSec / 60.0
            remainingActiveMinutes = max(1.0, rollingAverage - elapsedMin)
        }

        val totalEtaMin = remainingActiveMinutes + (input.peopleAhead * rollingAverage)
        val finalWait = max(1, totalEtaMin.roundToInt())

        val message = when {
            input.peopleAhead == 0 -> "You are next in line. Please be ready right outside."
            finalWait <= 10 -> "About $finalWait minutes remaining. Please head to the clinic."
            else -> "Approx. $finalWait minutes remaining."
        }

        return CalculationResult(
            estimatedWaitMinutes = finalWait,
            estimateQuality = quality,
            userFriendlyMessage = message
        )
    }
}

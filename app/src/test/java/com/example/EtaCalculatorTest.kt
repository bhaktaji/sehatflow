package com.example

import com.example.domain.logic.EtaCalculator
import com.example.domain.model.TokenStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EtaCalculatorTest {

    @Test
    fun testPausedSession_suppressesEstimate() {
        val input = EtaCalculator.CalculationInput(
            ownStatus = TokenStatus.WAITING,
            peopleAhead = 3,
            isSessionPaused = true,
            isSessionClosed = false,
            fallbackMinutes = 10,
            recentDurationsMinutes = listOf(8.0, 10.0, 12.0),
            activeConsultationStartedEpochSec = null,
            currentServerEpochSec = 1700000000L
        )

        val result = EtaCalculator.calculate(input)
        assertNull(result.estimatedWaitMinutes)
        assertEquals("UNAVAILABLE", result.estimateQuality)
    }

    @Test
    fun testClosedSession_suppressesEstimate() {
        val input = EtaCalculator.CalculationInput(
            ownStatus = TokenStatus.WAITING,
            peopleAhead = 1,
            isSessionPaused = false,
            isSessionClosed = true,
            fallbackMinutes = 10,
            recentDurationsMinutes = emptyList(),
            activeConsultationStartedEpochSec = null,
            currentServerEpochSec = 1700000000L
        )

        val result = EtaCalculator.calculate(input)
        assertNull(result.estimatedWaitMinutes)
        assertEquals("UNAVAILABLE", result.estimateQuality)
    }

    @Test
    fun testPendingVerification_returnsPending() {
        val input = EtaCalculator.CalculationInput(
            ownStatus = TokenStatus.PENDING_VERIFICATION,
            peopleAhead = 0,
            isSessionPaused = false,
            isSessionClosed = false,
            fallbackMinutes = 10,
            recentDurationsMinutes = emptyList(),
            activeConsultationStartedEpochSec = null,
            currentServerEpochSec = 1700000000L
        )

        val result = EtaCalculator.calculate(input)
        assertNull(result.estimatedWaitMinutes)
        assertEquals("PENDING", result.estimateQuality)
    }

    @Test
    fun testCalledPatient_returnsZeroMinutesNow() {
        val input = EtaCalculator.CalculationInput(
            ownStatus = TokenStatus.CALLED,
            peopleAhead = 0,
            isSessionPaused = false,
            isSessionClosed = false,
            fallbackMinutes = 10,
            recentDurationsMinutes = emptyList(),
            activeConsultationStartedEpochSec = null,
            currentServerEpochSec = 1700000000L
        )

        val result = EtaCalculator.calculate(input)
        assertEquals(0, result.estimatedWaitMinutes)
        assertEquals("NOW", result.estimateQuality)
    }

    @Test
    fun testWaitingPatient_fallbackWhenNoRecentDurations() {
        // Fallback default: 10 mins. 2 patients ahead -> 2 * 10 = 20 mins.
        val input = EtaCalculator.CalculationInput(
            ownStatus = TokenStatus.WAITING,
            peopleAhead = 2,
            isSessionPaused = false,
            isSessionClosed = false,
            fallbackMinutes = 10,
            recentDurationsMinutes = emptyList(),
            activeConsultationStartedEpochSec = null,
            currentServerEpochSec = 1700000000L
        )

        val result = EtaCalculator.calculate(input)
        assertEquals(20, result.estimatedWaitMinutes)
        assertEquals("LOW", result.estimateQuality)
    }

    @Test
    fun testWaitingPatient_withRollingAverageAndRemainingActiveConsultation() {
        // Last 5 durations: 6, 8, 10, 8, 8 -> average = 8.0 mins.
        // Active consultation started 3 minutes ago -> remaining = 8.0 - 3.0 = 5.0 mins.
        // 2 patients ahead -> 2 * 8.0 = 16.0 mins.
        // Total expected = 5.0 + 16.0 = 21 mins.
        val currentServerTime = 1700000300L
        val activeStarted = currentServerTime - (3 * 60L) // 3 mins ago

        val input = EtaCalculator.CalculationInput(
            ownStatus = TokenStatus.WAITING,
            peopleAhead = 2,
            isSessionPaused = false,
            isSessionClosed = false,
            fallbackMinutes = 10,
            recentDurationsMinutes = listOf(6.0, 8.0, 10.0, 8.0, 8.0),
            activeConsultationStartedEpochSec = activeStarted,
            currentServerEpochSec = currentServerTime
        )

        val result = EtaCalculator.calculate(input)
        assertEquals(21, result.estimatedWaitMinutes)
        assertEquals("HIGH", result.estimateQuality)
    }
}

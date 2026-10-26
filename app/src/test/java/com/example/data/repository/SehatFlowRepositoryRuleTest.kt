package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Clinic
import com.example.data.model.SubscriptionRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SehatFlowRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveUserProfile_succeeds_for_authenticated_user(): Unit = runBlocking {
        val uid = signInTestUser("doctor.test@sehatflow.com")
        val repo = SehatFlowRepository(firestore)

        val profile = UserProfile(
            userId = uid,
            email = "doctor.test@sehatflow.com",
            displayName = "Dr. Aisha Khan",
            role = UserRole.DOCTOR.name,
            specialization = "Pediatrician",
            roomNumber = "OPD-3"
        )

        repo.saveUserProfile(profile)

        val retrieved = repo.observeUserProfile(uid).first()
        assertNotNull(retrieved)
        assertEquals("Dr. Aisha Khan", retrieved?.displayName)
        assertEquals(UserRole.DOCTOR.name, retrieved?.role)
    }

    @Test
    fun unauthenticated_access_throws_exception(): Unit = runBlocking {
        auth.signOut()
        val repo = SehatFlowRepository(firestore)

        try {
            repo.saveUserProfile(UserProfile(userId = "unknown", email = "test@test.com", displayName = "Nobody"))
            fail("Expected exception when unauthenticated")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException || e.message?.contains("authenticated") == true)
        }
    }

    @Test
    fun clinic_and_subscription_flow_succeeds(): Unit = runBlocking {
        val adminUid = signInTestUser("admin.care@sehatflow.com")
        val repo = SehatFlowRepository(firestore)

        val clinicId = "clinic_north_delhi"
        val clinic = Clinic(
            clinicId = clinicId,
            name = "Sehat Super Clinic",
            address = "Sector 14, Rohini",
            phone = "+919876543210",
            adminUid = adminUid,
            inviteCodeDoctor = "DOC999",
            inviteCodeReceptionist = "REC999",
            subscriptionStatus = "TRIAL",
            monthlyFee = 499L
        )

        repo.saveClinic(clinic)

        val savedClinic = repo.observeClinic(clinicId).first()
        assertNotNull(savedClinic)
        assertEquals("Sehat Super Clinic", savedClinic?.name)

        val subRecord = SubscriptionRecord(
            clinicId = clinicId,
            adminUid = adminUid,
            planName = "SehatFlow Pro (₹499/mo)",
            amount = 499L,
            status = "ACTIVE",
            paymentMethod = "MOCK_UPI",
            transactionId = "UPI-SF-889900"
        )

        val subId = repo.recordSubscription(subRecord)
        assertTrue(subId.isNotBlank())

        val subscriptions = repo.observeSubscriptions(clinicId).first()
        assertTrue(subscriptions.isNotEmpty())
        assertEquals(499L, subscriptions[0].amount)
    }
}

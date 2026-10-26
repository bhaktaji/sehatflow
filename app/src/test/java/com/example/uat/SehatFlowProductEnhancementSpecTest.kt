package com.example.uat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.SessionPreferences
import com.example.data.model.UserRole
import com.example.domain.model.ClinicalAuditLogger
import com.example.domain.model.DpdpPrivacyRegistry
import com.example.domain.model.IdentityMatchStatus
import com.example.domain.model.PatientHealthProfile
import com.example.domain.model.PatientHealthProfileRegistry
import com.example.domain.model.PatientIdentityRegistry
import com.example.domain.model.Role
import com.example.domain.model.StaffInvitationValidator
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Verification & Regression Test Suite for the
 * SehatFlow Product Enhancement & Implementation Specification (Sections 3–13).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SehatFlowProductEnhancementSpecTest {

    private val referenceNow = 1792836000000L

    @Before
    fun setUp() {
        ClinicalAuditLogger.clearForTest()
        PatientIdentityRegistry.resetToSeedForTesting()
        PatientHealthProfileRegistry.resetForTesting()
        StaffInvitationValidator.resetForTesting(referenceNow)
        DpdpPrivacyRegistry.resetForTesting()
    }

    // =========================================================================
    // 13.1 Welcome Page & Branding Tests
    // =========================================================================

    @Test
    fun `test 13_1 - welcome page branding strings and SehatFlow logo resources match specification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("SehatFlow", context.getString(R.string.app_name))
        assertEquals("SehatFlow", context.getString(R.string.login_title))
        assertEquals("Less Waiting. Smarter Care.", context.getString(R.string.login_subtitle))
        assertEquals(
            "Track your clinic token, know your estimated waiting time, and plan your visit.",
            context.getString(R.string.login_supporting_text)
        )
        assertEquals("For Patients", context.getString(R.string.for_patients_header))
        assertEquals("Continue with Google", context.getString(R.string.continue_with_google_btn))
        assertEquals("For Clinic Staff", context.getString(R.string.for_clinic_staff_header))
        assertEquals("Are you a doctor or clinic staff member?", context.getString(R.string.staff_prompt_label))
        assertEquals("Staff Sign In", context.getString(R.string.staff_sign_in_btn))
        assertEquals("Skip for now", context.getString(R.string.health_profile_skip_btn))
        assertEquals(
            "Reported by patient • Not clinically verified",
            context.getString(R.string.health_profile_unverified_badge)
        )

        // Verify SehatFlow official logo drawable & launcher icon resources exist and maintain 1:1 aspect ratio
        val logoDrawable = context.getDrawable(R.drawable.ic_sehatflow_logo)
        assertNotNull("Official SehatFlow logo drawable must exist", logoDrawable)
        assertTrue("Logo drawable must have positive width", logoDrawable!!.intrinsicWidth > 0)
        assertEquals(
            "Logo drawable must maintain 1:1 aspect ratio without distortion",
            logoDrawable.intrinsicWidth,
            logoDrawable.intrinsicHeight
        )
        val darkLogoDrawable = context.getDrawable(R.drawable.ic_sehatflow_logo_dark)
        assertNotNull("Dark surface SehatFlow logo drawable must exist", darkLogoDrawable)
        assertEquals(
            "Dark logo drawable must maintain 1:1 aspect ratio without distortion",
            darkLogoDrawable!!.intrinsicWidth,
            darkLogoDrawable.intrinsicHeight
        )

        assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground))
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_background))
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher))
    }

    // =========================================================================
    // 13.2 Patient Authentication & Optional Health Profile Tests
    // =========================================================================

    @Test
    fun `test 13_2a - skipping optional health profile leaves fields null without fabricated defaults`() {
        val newPatientRes = PatientIdentityRegistry.resolveOrRegisterPatient(
            authenticatedPatientId = "uid_google_new_pat_1",
            fullName = "Meera Krishnan",
            phoneNumber = null
        )
        assertEquals(IdentityMatchStatus.NEW_PATIENT_CREATED, newPatientRes.status)
        val patientId = newPatientRes.patientRecord!!.patientId

        val skippedProfile = PatientHealthProfileRegistry.skipHealthProfileOnboarding(
            patientId = patientId,
            nowEpochMs = referenceNow
        )

        assertTrue(skippedProfile.isSkipped)
        assertFalse(skippedProfile.isCompleted)
        assertFalse(skippedProfile.consentGranted)
        assertNull("Blood group must remain null when skipped", skippedProfile.bloodGroup)
        assertNull("Height must remain null when skipped", skippedProfile.heightCm)
        assertNull("Weight must remain null when skipped", skippedProfile.weightKg)
        assertNull("Allergies must remain null when skipped", skippedProfile.knownAllergies)
        assertNull("Conditions must remain null when skipped", skippedProfile.existingConditions)
        assertNull("Medications must remain null when skipped", skippedProfile.currentMedications)
        assertNull("Emergency contact must remain null when skipped", skippedProfile.emergencyContact)
        assertFalse(skippedProfile.hasAnyReportedMedicalDetail())

        // Returning sign-in for the same Google UID must match existing patient without duplicates
        val returningRes = PatientIdentityRegistry.resolveOrRegisterPatient(
            authenticatedPatientId = "uid_google_new_pat_1",
            fullName = "Meera Krishnan"
        )
        assertEquals(IdentityMatchStatus.EXACT_AUTHENTICATED_MATCH, returningRes.status)
        assertEquals(newPatientRes.patientRecord?.patientIdentifier, returningRes.patientRecord?.patientIdentifier)
    }

    @Test
    fun `test 13_2b - saving and updating optional health profile validates inputs and marks unverified`() {
        val patientId = "pat_1" // Amitabh Sen, assigned to doc_john

        // 1. Saving without consent must fail
        val noConsentResult = PatientHealthProfileRegistry.saveOrUpdateHealthProfile(
            patientId = patientId,
            bloodGroup = "B+",
            heightCmInput = "172",
            weightKgInput = "70",
            consentGranted = false
        )
        assertTrue(noConsentResult.isFailure)

        // 2. Saving with invalid height or invalid blood group must fail
        val invalidHeight = PatientHealthProfileRegistry.saveOrUpdateHealthProfile(
            patientId = patientId,
            bloodGroup = "B+",
            heightCmInput = "999",
            weightKgInput = "70",
            consentGranted = true
        )
        assertTrue(invalidHeight.isFailure)

        val invalidBloodGroup = PatientHealthProfileRegistry.saveOrUpdateHealthProfile(
            patientId = patientId,
            bloodGroup = "XYZ+",
            heightCmInput = "172",
            weightKgInput = "70",
            consentGranted = true
        )
        assertTrue(invalidBloodGroup.isFailure)

        // 3. Valid save with consent succeeds and labels as patient-reported & unverified
        val validSave = PatientHealthProfileRegistry.saveOrUpdateHealthProfile(
            patientId = patientId,
            dateOfBirthOrAge = "62 yrs",
            bloodGroup = "B+",
            heightCmInput = "168.5",
            weightKgInput = "71.0",
            knownAllergies = "Sulfa drugs",
            existingConditions = "Hypertension",
            currentMedications = "Amlodipine 5mg",
            emergencyContact = "+91-9830011229",
            consentGranted = true,
            nowEpochMs = referenceNow
        )
        assertTrue(validSave.isSuccess)
        val saved = validSave.getOrThrow()
        assertEquals("B+", saved.bloodGroup)
        assertEquals(168.5, saved.heightCm!!, 0.01)
        assertEquals(71.0, saved.weightKg!!, 0.01)
        assertEquals("Sulfa drugs", saved.knownAllergies)
        assertEquals(PatientHealthProfile.UNVERIFIED_PATIENT_REPORTED_LABEL, saved.verificationStatusLabel)
        assertFalse("Patient-reported health profile must never be marked clinically verified", saved.isClinicallyVerified)

        // 4. Role-based access control: Patient and assigned Doctor (doc_john) can view; Receptionist and Admin cannot
        val doctorSession = UserSession("tok", "u_doc", "Dr. John", Role.DOCTOR, "clinic_xyz", "doc_john")
        val receptionSession = UserSession("tok", "u_rec", "Desk", Role.RECEPTIONIST, "clinic_xyz", null)
        val ownerSession = UserSession("tok", "u_own", "Admin", Role.OWNER, "clinic_xyz", null)

        assertNotNull(PatientHealthProfileRegistry.getAuthorizedHealthProfile(patientId, doctorSession))
        assertNull(
            "Receptionist must be denied access to clinical health profile fields",
            PatientHealthProfileRegistry.getAuthorizedHealthProfile(patientId, receptionSession)
        )
        assertNull(
            "Clinic Owner/Admin must be denied access to clinical health profile fields",
            PatientHealthProfileRegistry.getAuthorizedHealthProfile(patientId, ownerSession)
        )
    }

    // =========================================================================
    // 13.3 Staff Authentication & Invite Validation Tests
    // =========================================================================

    @Test
    fun `test 13_3 - staff invitation validation enforces role binding, expiry, single-use, and rate limiting`() {
        // 1. Google Sign-In alone NEVER grants staff privileges
        assertEquals(
            UserRole.PATIENT,
            StaffInvitationValidator.resolveRoleAfterGoogleSignIn("unverified_google_user_99")
        )

        // 2. Role mismatch: Receptionist code REC-1234 cannot be used to claim DOCTOR role
        val mismatchRes = StaffInvitationValidator.validateAndRedeemInvitation(
            userId = "staff_user_1",
            rawInviteCode = "REC-1234",
            requestedRole = UserRole.DOCTOR,
            nowEpochMs = referenceNow
        )
        assertFalse(mismatchRes.isSuccess)
        assertEquals("ROLE_MISMATCH", mismatchRes.errorCode)

        // 3. Expired invite code is rejected
        val expiredRes = StaffInvitationValidator.validateAndRedeemInvitation(
            userId = "staff_user_1",
            rawInviteCode = "DOC-EXPIRED",
            requestedRole = UserRole.DOCTOR,
            nowEpochMs = referenceNow
        )
        assertFalse(expiredRes.isSuccess)
        assertEquals("EXPIRED_CODE", expiredRes.errorCode)

        // 4. Valid single-use invite code DOC-7890 succeeds for first doctor
        val validDocRes = StaffInvitationValidator.validateAndRedeemInvitation(
            userId = "staff_doc_user_1",
            rawInviteCode = "DOC-7890",
            requestedRole = UserRole.DOCTOR,
            nowEpochMs = referenceNow
        )
        assertTrue(validDocRes.isSuccess)
        assertEquals(UserRole.DOCTOR, validDocRes.assignedRole)
        assertEquals("clinic_xyz", validDocRes.clinicId)
        assertEquals(
            UserRole.DOCTOR,
            StaffInvitationValidator.resolveRoleAfterGoogleSignIn("staff_doc_user_1")
        )

        // 5. Reusing single-use invite code DOC-7890 by a second user is rejected
        val reuseRes = StaffInvitationValidator.validateAndRedeemInvitation(
            userId = "staff_doc_user_2",
            rawInviteCode = "DOC-7890",
            requestedRole = UserRole.DOCTOR,
            nowEpochMs = referenceNow
        )
        assertFalse(reuseRes.isSuccess)
        assertEquals("INVITE_ALREADY_USED", reuseRes.errorCode)

        // 6. Brute-force / rate-limiting lockout after 5 failed attempts
        val attackerId = "brute_force_actor"
        repeat(StaffInvitationValidator.MAX_FAILED_ATTEMPTS) { attempt ->
            val bad = StaffInvitationValidator.validateAndRedeemInvitation(
                userId = attackerId,
                rawInviteCode = "DOC-WRONG-$attempt",
                nowEpochMs = referenceNow
            )
            assertFalse(bad.isSuccess)
        }
        val lockedOutRes = StaffInvitationValidator.validateAndRedeemInvitation(
            userId = attackerId,
            rawInviteCode = "REC-1234", // Even a valid code is blocked while rate-limited
            nowEpochMs = referenceNow
        )
        assertFalse(lockedOutRes.isSuccess)
        assertEquals("RATE_LIMITED_LOCKED_OUT", lockedOutRes.errorCode)
    }

    // =========================================================================
    // 13.4 Settings & Theme Persistence Tests
    // =========================================================================

    @Test
    fun `test 13_4 - theme selector persists Light, Dark, and System Default across sessions and logout`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SessionPreferences(context)

        // Default is SYSTEM
        prefs.setThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, prefs.themeMode.first())

        // Switch to DARK
        prefs.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, prefs.themeMode.first())
        assertEquals(AppThemeMode.DARK, prefs.getThemeModeSync())

        // Switch to LIGHT
        prefs.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, prefs.themeMode.first())
        assertEquals(AppThemeMode.LIGHT, prefs.getThemeModeSync())

        // Toggle queue sound/vibration preference
        prefs.setQueueSoundVibrationEnabled(false)
        assertFalse(prefs.queueSoundVibrationEnabled.first())

        // Clearing user session on logout with preserveConfig = true preserves theme and sound settings
        prefs.clearSession(preserveConfig = true)
        assertEquals(AppThemeMode.LIGHT, prefs.themeMode.first())
        assertFalse(prefs.queueSoundVibrationEnabled.first())
    }

    // =========================================================================
    // 13.5 Privacy & Consent Tests (DPDP Act 2023)
    // =========================================================================

    @Test
    fun `test 13_5 - DPDP consent withdrawal purges optional health profile while preserving clinical retention rules`() {
        val patientId = "pat_demo_patient"
        val doctorSession = UserSession("tok", "u_doc", "Dr. John", Role.DOCTOR, "clinic_xyz", "doc_john")

        // Initially pat_demo_patient has consented optional health profile
        assertNotNull(PatientHealthProfileRegistry.getAuthorizedHealthProfile(patientId, doctorSession))

        // Withdraw optional health profile consent
        val withdrawalRecord = DpdpPrivacyRegistry.withdrawOptionalHealthProfileConsent(
            userId = patientId,
            nowEpochMs = referenceNow
        )
        assertEquals("COMPLETED", withdrawalRecord.status)
        assertFalse(DpdpPrivacyRegistry.getConsentRecord(patientId).optionalHealthProfileConsentGranted)

        // Doctor can no longer access self-reported optional health fields after consent withdrawal
        assertNull(PatientHealthProfileRegistry.getAuthorizedHealthProfile(patientId, doctorSession))

        // Patient's own view shows optional fields cleared
        val patientSession = UserSession("tok", patientId, "David Miller", Role.PATIENT, null, null)
        val clearedProfile = PatientHealthProfileRegistry.getAuthorizedHealthProfile(patientId, patientSession)
        assertNotNull(clearedProfile)
        assertFalse(clearedProfile!!.hasAnyReportedMedicalDetail())

        // Account/data erasure request when completed clinical consultations exist respects statutory retention
        val erasureWithClinicalHistory = DpdpPrivacyRegistry.submitDataErasureOrAccountDeletionRequest(
            userId = patientId,
            hasRetainedClinicalConsultations = true,
            nowEpochMs = referenceNow
        )
        assertEquals("PARTIAL_RETAINED_UNDER_CLINICAL_LAW", erasureWithClinicalHistory.status)

        // Account/data erasure request when no clinical consultations exist completes full erasure
        val erasureNoClinicalHistory = DpdpPrivacyRegistry.submitDataErasureOrAccountDeletionRequest(
            userId = "pat_new_no_consults",
            hasRetainedClinicalConsultations = false,
            nowEpochMs = referenceNow
        )
        assertEquals("COMPLETED", erasureNoClinicalHistory.status)
    }
}

package com.example.uat

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.core.demo.DemoDataProvider
import com.example.core.payment.*
import com.example.core.update.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SehatFlowSubscriptionAndUpdateTest {

    @Before
    fun setUp() {
        SubscriptionPaymentEngine.resetForTests()
        AppUpdateManager.resetForTests()
    }

    @After
    fun tearDown() {
        SubscriptionPaymentEngine.resetForTests()
        AppUpdateManager.resetForTests()
    }

    @Test
    fun testSuccessfulPaymentVerificationActivatesSubscriptionAndStacksRenewal() {
        val clinicId = "clinic_pay_success_1"
        val now = System.currentTimeMillis()

        // Initial state is 14-day trial
        val initial = SubscriptionPaymentEngine.getOrCreateClinicSubscription(clinicId, "admin_1", now)
        assertEquals(SubscriptionLifecycleStatus.TRIAL, initial.status)

        // Step 1: Create monthly order (₹499)
        val order1 = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_monthly",
            idempotencyKey = "idem_monthly_1",
            autoRenew = true,
            nowMillis = now
        )
        assertEquals(499L, order1.amountInr)
        assertEquals(49900L, order1.amountPaise)
        assertEquals(PaymentTransactionStatus.CREATED, order1.status)

        // Subscription must STILL be TRIAL before verification
        assertEquals(
            SubscriptionLifecycleStatus.TRIAL,
            SubscriptionPaymentEngine.getOrCreateClinicSubscription(clinicId, "admin_1", now).status
        )

        // Step 2: Verify payment with valid server HMAC-SHA256 signature
        val payId1 = "pay_rzp_valid_001"
        val sig1 = SubscriptionPaymentEngine.computeOrderPaymentSignature(order1.orderId, payId1)
        val metadata1 = SubscriptionPaymentEngine.validateAndSanitizeInstrument(
            PaymentGatewayMethod.UPI,
            "clinic@okhdfcbank"
        )

        val outcome1 = SubscriptionPaymentEngine.verifyPaymentAndActivate(
            orderId = order1.orderId,
            gatewayPaymentId = payId1,
            gatewaySignature = sig1,
            paymentMetadata = metadata1,
            nowMillis = now
        )
        assertTrue(outcome1 is PaymentVerificationOutcome.Activated)
        val activated1 = outcome1 as PaymentVerificationOutcome.Activated
        assertEquals(SubscriptionLifecycleStatus.ACTIVE, activated1.subscription.status)
        assertEquals(now + 30L * 86_400_000L, activated1.subscription.expiresAtMillis)
        assertEquals(PaymentTransactionStatus.CAPTURED, activated1.transaction.status)
        assertTrue(activated1.transaction.signatureVerified)
        assertNotNull(activated1.transaction.gstInvoiceNumber)

        // Step 3: Early renewal with Quarterly Plan (90 days) stacks onto existing expiry
        val order2 = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_quarterly",
            idempotencyKey = "idem_quarterly_2",
            autoRenew = true,
            nowMillis = now + 5L * 86_400_000L
        )
        val payId2 = "pay_rzp_valid_002"
        val sig2 = SubscriptionPaymentEngine.computeOrderPaymentSignature(order2.orderId, payId2)
        val outcome2 = SubscriptionPaymentEngine.verifyPaymentAndActivate(
            orderId = order2.orderId,
            gatewayPaymentId = payId2,
            gatewaySignature = sig2,
            paymentMetadata = metadata1,
            nowMillis = now + 5L * 86_400_000L
        )
        assertTrue(outcome2 is PaymentVerificationOutcome.Activated)
        val activated2 = outcome2 as PaymentVerificationOutcome.Activated
        // 30 days + 90 days = 120 days from initial `now`
        assertEquals(now + 120L * 86_400_000L, activated2.subscription.expiresAtMillis)
        assertEquals("SehatFlow Pro Quarterly", activated2.subscription.currentPlanName)
    }

    @Test
    fun testInvalidSignatureRejectedAndNeverActivatesSubscription() {
        val clinicId = "clinic_forged_sig"
        val now = System.currentTimeMillis()
        val order = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_monthly",
            nowMillis = now
        )

        val forgedOutcome = SubscriptionPaymentEngine.verifyPaymentAndActivate(
            orderId = order.orderId,
            gatewayPaymentId = "pay_rzp_forged_999",
            gatewaySignature = "0000deadbeefinvalidhmacsignature0000",
            paymentMetadata = SafePaymentMetadata(PaymentGatewayMethod.UPI, "attacker@upi"),
            nowMillis = now
        )

        assertTrue(forgedOutcome is PaymentVerificationOutcome.SignatureMismatch)
        val sub = SubscriptionPaymentEngine.getOrCreateClinicSubscription(clinicId, "admin_1", now)
        assertEquals("Forged signature must NOT activate subscription", SubscriptionLifecycleStatus.TRIAL, sub.status)

        val txns = SubscriptionPaymentEngine.getTransactionsForClinic(clinicId)
        assertEquals(1, txns.size)
        assertEquals(PaymentTransactionStatus.FAILED, txns.first().status)
        assertEquals("SIGNATURE_VERIFICATION_FAILED", txns.first().failureReason)
    }

    @Test
    fun testPendingAndFailedPaymentsDoNotActivateSubscription() {
        val clinicId = "clinic_pend_fail"
        val now = System.currentTimeMillis()

        val orderPending = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_monthly",
            nowMillis = now
        )
        val pendingTxn = SubscriptionPaymentEngine.recordPendingPayment(
            orderId = orderPending.orderId,
            gatewayPaymentId = "pay_pend_1",
            paymentMetadata = SafePaymentMetadata(PaymentGatewayMethod.UPI, "clinic@upi"),
            nowMillis = now
        )
        assertEquals(PaymentTransactionStatus.PENDING, pendingTxn.status)
        assertEquals(
            SubscriptionLifecycleStatus.TRIAL,
            SubscriptionPaymentEngine.getOrCreateClinicSubscription(clinicId, "admin_1", now).status
        )

        val orderFailed = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_monthly",
            nowMillis = now
        )
        val failedTxn = SubscriptionPaymentEngine.recordFailedPayment(
            orderId = orderFailed.orderId,
            gatewayPaymentId = "pay_fail_1",
            paymentMetadata = SafePaymentMetadata(PaymentGatewayMethod.CARD, "Card •••• 4242"),
            failureReason = "ISSUER_BANK_DECLINED",
            nowMillis = now
        )
        assertEquals(PaymentTransactionStatus.FAILED, failedTxn.status)
        assertEquals("ISSUER_BANK_DECLINED", failedTxn.failureReason)
        assertEquals(
            SubscriptionLifecycleStatus.TRIAL,
            SubscriptionPaymentEngine.getOrCreateClinicSubscription(clinicId, "admin_1", now).status
        )
    }

    @Test
    fun testDuplicatePaymentCallbacksAndWebhooksAreIdempotent() {
        val clinicId = "clinic_idempotent_1"
        val now = System.currentTimeMillis()

        val order = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_monthly",
            idempotencyKey = "idem_unique_101",
            nowMillis = now
        )
        // Calling createPaymentOrder again with same idempotencyKey returns exact same orderId
        val duplicateOrderCall = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_monthly",
            idempotencyKey = "idem_unique_101",
            nowMillis = now
        )
        assertEquals(order.orderId, duplicateOrderCall.orderId)

        val payId = "pay_rzp_idem_101"
        val sig = SubscriptionPaymentEngine.computeOrderPaymentSignature(order.orderId, payId)
        val meta = SafePaymentMetadata(PaymentGatewayMethod.UPI, "clinic@okhdfcbank")

        val firstVerify = SubscriptionPaymentEngine.verifyPaymentAndActivate(
            orderId = order.orderId,
            gatewayPaymentId = payId,
            gatewaySignature = sig,
            paymentMetadata = meta,
            nowMillis = now
        ) as PaymentVerificationOutcome.Activated

        // Duplicate callback with same gatewayPaymentId must NOT extend expiry again
        val secondVerify = SubscriptionPaymentEngine.verifyPaymentAndActivate(
            orderId = order.orderId,
            gatewayPaymentId = payId,
            gatewaySignature = sig,
            paymentMetadata = meta,
            nowMillis = now + 60_000L
        )
        assertTrue(secondVerify is PaymentVerificationOutcome.DuplicateAlreadyProcessed)
        val dupOutcome = secondVerify as PaymentVerificationOutcome.DuplicateAlreadyProcessed
        assertEquals(firstVerify.subscription.expiresAtMillis, dupOutcome.subscription.expiresAtMillis)
        assertEquals(1, SubscriptionPaymentEngine.getTransactionsForClinic(clinicId).size)

        // Signed webhook for the same captured payment is also idempotent
        val payload = """{"event":"payment.captured","order_id":"${order.orderId}","payment_id":"$payId"}"""
        val whSig = SubscriptionPaymentEngine.computeWebhookSignature(payload)
        val wh1 = SubscriptionPaymentEngine.processSignedWebhook(
            eventId = "evt_rzp_1001",
            eventType = "payment.captured",
            orderId = order.orderId,
            gatewayPaymentId = payId,
            rawPayloadJson = payload,
            signatureHeader = whSig,
            nowMillis = now + 120_000L
        )
        assertTrue(wh1 is WebhookOutcome.Processed)
        assertEquals(
            firstVerify.subscription.expiresAtMillis,
            (wh1 as WebhookOutcome.Processed).updatedSubscription?.expiresAtMillis
        )

        // Replay of the same webhook eventId is ignored
        val wh2 = SubscriptionPaymentEngine.processSignedWebhook(
            eventId = "evt_rzp_1001",
            eventType = "payment.captured",
            orderId = order.orderId,
            gatewayPaymentId = payId,
            rawPayloadJson = payload,
            signatureHeader = whSig,
            nowMillis = now + 180_000L
        )
        assertTrue(wh2 is WebhookOutcome.DuplicateIgnored)
    }

    @Test
    fun testSignedWebhookRefundRevokesSubscriptionAndEnforcesBackendEntitlement() {
        val clinicId = "clinic_refund_test"
        val now = System.currentTimeMillis()

        val order = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_monthly",
            nowMillis = now
        )
        val payId = "pay_rzp_to_refund"
        val sig = SubscriptionPaymentEngine.computeOrderPaymentSignature(order.orderId, payId)
        SubscriptionPaymentEngine.verifyPaymentAndActivate(
            orderId = order.orderId,
            gatewayPaymentId = payId,
            gatewaySignature = sig,
            paymentMetadata = SafePaymentMetadata(PaymentGatewayMethod.UPI, "clinic@upi"),
            nowMillis = now
        )

        // While ACTIVE, walk-in token creation succeeds
        val token = DemoDataProvider.createWalkIn(
            displayName = "Entitled Patient",
            priority = false,
            clinicId = clinicId
        )
        assertNotNull(token)

        // Forged webhook signature must fail
        val refundPayload = """{"event":"refund.processed","order_id":"${order.orderId}","payment_id":"$payId"}"""
        val badWh = SubscriptionPaymentEngine.processSignedWebhook(
            eventId = "evt_refund_bad",
            eventType = "refund.processed",
            orderId = order.orderId,
            gatewayPaymentId = payId,
            rawPayloadJson = refundPayload,
            signatureHeader = "bad_signature"
        )
        assertTrue(badWh is WebhookOutcome.InvalidSignature)

        // Valid signed refund webhook revokes subscription immediately
        val validWhSig = SubscriptionPaymentEngine.computeWebhookSignature(refundPayload)
        val goodWh = SubscriptionPaymentEngine.processSignedWebhook(
            eventId = "evt_refund_good_1",
            eventType = "refund.processed",
            orderId = order.orderId,
            gatewayPaymentId = payId,
            rawPayloadJson = refundPayload,
            signatureHeader = validWhSig,
            refundId = "rfnd_rzp_7788",
            nowMillis = now + 3_600_000L
        )
        assertTrue(goodWh is WebhookOutcome.Processed)
        val processed = goodWh as WebhookOutcome.Processed
        assertEquals(SubscriptionLifecycleStatus.REVOKED_REFUNDED, processed.updatedSubscription?.status)
        assertEquals(PaymentTransactionStatus.REFUNDED, processed.updatedTransaction?.status)

        // Backend entitlement check now blocks paid operational mutation (walk-in token issuance)
        try {
            DemoDataProvider.createWalkIn(
                displayName = "Blocked After Refund",
                priority = false,
                clinicId = clinicId
            )
            fail("Expected SubscriptionEntitlementException after refund revocation")
        } catch (e: SubscriptionEntitlementException) {
            assertEquals("SUBSCRIPTION_REVOKED_REFUNDED", e.decision.reasonCode)
        }
    }

    @Test
    fun testCancelSubscriptionRetainsAccessUntilExpiryThenBlocks() {
        val clinicId = "clinic_cancel_test"
        val now = System.currentTimeMillis()

        val order = SubscriptionPaymentEngine.createPaymentOrder(
            clinicId = clinicId,
            adminUid = "admin_1",
            planId = "plan_pro_monthly",
            nowMillis = now
        )
        val payId = "pay_rzp_cancel_1"
        val sig = SubscriptionPaymentEngine.computeOrderPaymentSignature(order.orderId, payId)
        SubscriptionPaymentEngine.verifyPaymentAndActivate(
            orderId = order.orderId,
            gatewayPaymentId = payId,
            gatewaySignature = sig,
            paymentMetadata = SafePaymentMetadata(PaymentGatewayMethod.UPI, "clinic@upi"),
            nowMillis = now
        )

        val cancelled = SubscriptionPaymentEngine.cancelSubscription(
            clinicId = clinicId,
            reason = "Clinic closing next month",
            nowMillis = now + 86_400_000L
        )
        assertEquals(SubscriptionLifecycleStatus.CANCELLED_AT_PERIOD_END, cancelled.status)
        assertFalse(cancelled.autoRenew)

        // Before period end (day 10 of 30), clinic is still entitled
        val midPeriodDecision = SubscriptionPaymentEngine.evaluateEntitlement(
            clinicId = clinicId,
            nowMillis = now + 10L * 86_400_000L
        )
        assertTrue(midPeriodDecision.allowed)

        // After period end (day 31 of 30), subscription transitions to EXPIRED and blocks access
        val postExpiryDecision = SubscriptionPaymentEngine.evaluateEntitlement(
            clinicId = clinicId,
            nowMillis = now + 31L * 86_400_000L
        )
        assertFalse(postExpiryDecision.allowed)
        assertEquals(SubscriptionLifecycleStatus.EXPIRED, postExpiryDecision.effectiveStatus)
        assertEquals("SUBSCRIPTION_EXPIRED", postExpiryDecision.reasonCode)
    }

    @Test
    fun testSensitiveCardNumbersAndCvvRejectedAndNoSecretsInApk() {
        // 1. Raw 16-digit PAN must be rejected
        try {
            SubscriptionPaymentEngine.validateAndSanitizeInstrument(
                PaymentGatewayMethod.CARD,
                "4111222233334444"
            )
            fail("Expected security rejection for raw 16-digit PAN")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("SECURITY_VIOLATION"))
        }

        // 2. CVV string must be rejected
        try {
            SubscriptionPaymentEngine.validateAndSanitizeInstrument(
                PaymentGatewayMethod.CARD,
                "4242 cvv=123"
            )
            fail("Expected security rejection for CVV")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("SECURITY_VIOLATION"))
        }

        // 3. Verify Android BuildConfig has zero merchant secret fields
        val buildConfigFields = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse("RAZORPAY_KEY_SECRET must never be in BuildConfig", buildConfigFields.any { it.contains("RAZORPAY") })
        assertFalse("APP_DB_PASSWORD must never be in BuildConfig", buildConfigFields.any { it.contains("PASSWORD") })
    }

    @Test
    fun testInAppUpdateCheck_upToDate_optional_mandatory_andUntrustedSourceBlocking() = runBlocking {
        // 1. When installed version matches latest (v1.0, code 1), status is UpToDate
        val upToDateState = AppUpdateManager.checkForUpdates(
            installedVersionCode = 1,
            installedVersionName = "1.0"
        )
        assertTrue(upToDateState is UpdateUiState.UpToDate)

        // 2. Optional update when latestVersionCode = 2, minRequiredVersionCode = 1
        val optionalManifest = AppUpdateManager.CURRENT_PUBLISHED_MANIFEST.copy(
            latestVersionCode = 2,
            latestVersionName = "1.1",
            minRequiredVersionCode = 1,
            isMandatoryCriticalRelease = false
        )
        val optionalState = AppUpdateManager.checkForUpdates(
            installedVersionCode = 1,
            installedVersionName = "1.0",
            manifestProvider = { optionalManifest }
        )
        assertTrue(optionalState is UpdateUiState.UpdateAvailable)
        assertFalse((optionalState as UpdateUiState.UpdateAvailable).isMandatory)

        // 3. Mandatory update when minRequiredVersionCode = 2 and installed = 1
        val mandatoryManifest = optionalManifest.copy(minRequiredVersionCode = 2)
        val mandatoryState = AppUpdateManager.checkForUpdates(
            installedVersionCode = 1,
            installedVersionName = "1.0",
            manifestProvider = { mandatoryManifest }
        )
        assertTrue(mandatoryState is UpdateUiState.UpdateAvailable)
        assertTrue((mandatoryState as UpdateUiState.UpdateAvailable).isMandatory)

        // 4. Untrusted or cleartext HTTP URLs must be rejected
        assertFalse(AppUpdateManager.isTrustedOfficialReleaseUrl("http://api.sehatflow.app/releases/app.apk"))
        assertFalse(AppUpdateManager.isTrustedOfficialReleaseUrl("https://untrusted-mirror.example.org/app.apk"))
        assertTrue(AppUpdateManager.isTrustedOfficialReleaseUrl("https://api.sehatflow.app/releases/SehatFlow-1.0-release.apk"))
    }

    @Test
    fun testUpgradeFromOlderVersionToLatestSignedReleaseApkWithSha256AndSignatureCheck() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val releaseApk = File("release/SehatFlow-1.0-release.apk").let {
            if (it.exists()) it else File("../release/SehatFlow-1.0-release.apk")
        }
        val targetManifest: ReleaseManifest
        val expectedSha: String
        if (releaseApk.exists()) {
            targetManifest = AppUpdateManager.CURRENT_PUBLISHED_MANIFEST
            expectedSha = AppUpdateManager.OFFICIAL_RELEASE_SHA256
        } else {
            // Hermetic fallback: deterministic test APK byte stream
            val testBytes = "SehatFlow Official Release v1.0 Test Byte Stream".toByteArray()
            expectedSha = java.security.MessageDigest.getInstance("SHA-256")
                .digest(testBytes)
                .joinToString("") { "%02x".format(it) }
            targetManifest = ReleaseManifest(
                appName = "SehatFlow",
                packageName = "com.aistudio.clinicqueue.rwnq",
                latestVersionCode = 1,
                latestVersionName = "1.0",
                minRequiredVersionCode = 1,
                isMandatoryCriticalRelease = false,
                apkDownloadUrl = AppUpdateManager.OFFICIAL_APK_DOWNLOAD_URL,
                sha256Checksum = expectedSha,
                apkSizeBytes = testBytes.size.toLong(),
                signingCertSha256 = AppUpdateManager.OFFICIAL_SIGNING_CERT_SHA256,
                publishedAtIso = "2026-10-26T12:24:59Z",
                releaseNotes = listOf("Test release")
            )
            AppUpdateManager.setStreamFetcherForTesting {
                Pair(ByteArrayInputStream(testBytes), testBytes.size.toLong())
            }
        }

        val progressUpdates = mutableListOf<Int>()

        // Simulate upgrading from an older installed version (versionCode = 0 -> latestVersionCode = 1)
        val resultState = AppUpdateManager.downloadAndVerifyUpdate(
            context = context,
            manifest = targetManifest,
            installedVersionCode = 0,
            installedPackageName = "com.aistudio.clinicqueue.rwnq",
            installedSigningCertSha256 = AppUpdateManager.OFFICIAL_SIGNING_CERT_SHA256,
            onProgress = { prog -> progressUpdates.add(prog.progressPercent) }
        )

        assertTrue(
            "Expected ReadyToInstall state after downloading & verifying signed release APK, got: $resultState",
            resultState is UpdateUiState.ReadyToInstall
        )
        val ready = resultState as UpdateUiState.ReadyToInstall
        assertEquals(expectedSha, ready.computedSha256)
        assertTrue("Progress updates must reach 100%", progressUpdates.contains(100))

        // Verify FileProvider install intent creation preserves package upgrade semantics
        val installIntent = AppUpdateManager.createInstallApkIntent(context, ready.verifiedApkFile)
        assertEquals(Intent.ACTION_VIEW, installIntent.action)
        assertEquals("application/vnd.android.package-archive", installIntent.type)
        assertNotNull(installIntent.data)
        assertTrue(installIntent.data.toString().startsWith("content://com.aistudio.clinicqueue.rwnq.fileprovider/"))
        assertTrue((installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun testUpdateChecksumMismatchSignatureMismatchAndNetworkRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val validBytes = "Simulated Verified APK Payload Bytes v2.0".toByteArray()
        val validSha256 = java.security.MessageDigest.getInstance("SHA-256")
            .digest(validBytes)
            .joinToString("") { "%02x".format(it) }

        val targetManifest = ReleaseManifest(
            latestVersionCode = 2,
            latestVersionName = "2.0",
            minRequiredVersionCode = 1,
            apkDownloadUrl = "https://api.sehatflow.app/releases/SehatFlow-2.0-release.apk",
            sha256Checksum = validSha256,
            apkSizeBytes = validBytes.size.toLong(),
            signingCertSha256 = AppUpdateManager.OFFICIAL_SIGNING_CERT_SHA256,
            publishedAtIso = "2026-11-01T00:00:00Z",
            releaseNotes = listOf("Version 2.0 update test")
        )

        // 1. Tampered bytes -> CHECKSUM_MISMATCH
        AppUpdateManager.setStreamFetcherForTesting {
            Pair(ByteArrayInputStream("Tampered Corrupted APK Bytes".toByteArray()), 28L)
        }
        val checksumFailState = AppUpdateManager.downloadAndVerifyUpdate(
            context = context,
            manifest = targetManifest,
            installedVersionCode = 1
        )
        assertTrue(checksumFailState is UpdateUiState.Error)
        assertEquals(
            VerificationFailureReason.CHECKSUM_MISMATCH,
            (checksumFailState as UpdateUiState.Error).failedReason
        )

        // 2. Mismatched signing certificate -> SIGNATURE_MISMATCH
        AppUpdateManager.setStreamFetcherForTesting {
            Pair(ByteArrayInputStream(validBytes), validBytes.size.toLong())
        }
        val wrongCertManifest = targetManifest.copy(
            signingCertSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )
        val sigFailState = AppUpdateManager.downloadAndVerifyUpdate(
            context = context,
            manifest = wrongCertManifest,
            installedVersionCode = 1
        )
        assertTrue(sigFailState is UpdateUiState.Error)
        assertEquals(
            VerificationFailureReason.SIGNATURE_MISMATCH,
            (sigFailState as UpdateUiState.Error).failedReason
        )

        // 3. Simulated network failure -> Error with retryManifest, followed by successful retry
        var attempt = 0
        AppUpdateManager.setStreamFetcherForTesting {
            attempt++
            if (attempt == 1) {
                throw IOException("Simulated socket timeout during APK download")
            }
            Pair(ByteArrayInputStream(validBytes), validBytes.size.toLong())
        }

        val firstAttempt = AppUpdateManager.downloadAndVerifyUpdate(
            context = context,
            manifest = targetManifest,
            installedVersionCode = 1
        )
        assertTrue(firstAttempt is UpdateUiState.Error)
        assertEquals(
            VerificationFailureReason.NETWORK_DOWNLOAD_FAILED,
            (firstAttempt as UpdateUiState.Error).failedReason
        )
        assertNotNull(firstAttempt.retryManifest)

        // Retry succeeds!
        val retryAttempt = AppUpdateManager.downloadAndVerifyUpdate(
            context = context,
            manifest = firstAttempt.retryManifest!!,
            installedVersionCode = 1
        )
        assertTrue(retryAttempt is UpdateUiState.ReadyToInstall)
        assertEquals(validSha256, (retryAttempt as UpdateUiState.ReadyToInstall).computedSha256)
    }

    @Test
    fun testDefaultUpiConfigCredentialsAndNumber() {
        val config = SubscriptionPaymentEngine.upiConfig
        assertTrue("Default UPI VPA should be populated", config.upiVpa.isNotBlank())
        assertTrue("Default Payee Name should be populated", config.payeeName.isNotBlank())
        
        // Verify updating UPI config dynamically
        SubscriptionPaymentEngine.updateUpiConfig(
            vpa = "doctor@okaxis",
            payeeName = "Care Clinic",
            upiNumber = "9876543210"
        )
        val updated = SubscriptionPaymentEngine.upiConfig
        assertEquals("doctor@okaxis", updated.upiVpa)
        assertEquals("Care Clinic", updated.payeeName)
        assertEquals("9876543210", updated.upiNumber)
    }

    @Test
    fun testBuildUpiUriFormatAndParams() {
        SubscriptionPaymentEngine.updateUpiConfig(
            vpa = "doctor.clinic@okhdfcbank",
            payeeName = "Dr Roy Care Clinic",
            merchantCategoryCode = "8099"
        )
        val uri = SubscriptionPaymentEngine.buildUpiUri(
            amountInr = 499L,
            note = "SehatFlow Pro Subscription",
            txnRef = "SF-TEST-1234"
        )
        assertTrue("URI must start with upi://pay", uri.startsWith("upi://pay?"))
        assertTrue("Must contain payee VPA", uri.contains("pa=doctor.clinic@okhdfcbank"))
        assertTrue("Must contain currency INR", uri.contains("cu=INR"))
        assertTrue("Must contain amount 499.00", uri.contains("am=499.00"))
        assertTrue("Must contain txnRef", uri.contains("tr=SF-TEST-1234"))
        assertTrue("Must contain merchant category code", uri.contains("mc=8099"))
    }

    @Test
    fun testParseUpiReturnResponseVariations() {
        // 1. Success response with standard NPCI ApprovalRefNo (12-digit UTR)
        val successString = "Status=SUCCESS&txnId=UPI987654321012&responseCode=0&ApprovalRefNo=427812345678&txnRef=SF-ORD-1"
        val successResult = SubscriptionPaymentEngine.parseUpiReturn(successString)
        assertEquals(UpiTransactionStatus.SUCCESS, successResult.status)
        assertEquals("UPI987654321012", successResult.txnId)
        assertEquals("427812345678", successResult.approvalRefNo)
        assertEquals("0", successResult.responseCode)

        // 2. Failure response
        val failString = "Status=FAILURE&txnId=UPI111&responseCode=U16"
        val failResult = SubscriptionPaymentEngine.parseUpiReturn(failString)
        assertEquals(UpiTransactionStatus.FAILURE, failResult.status)

        // 3. Submitted / Pending response
        val pendingString = "Status=SUBMITTED&txnId=UPI222"
        val pendingResult = SubscriptionPaymentEngine.parseUpiReturn(pendingString)
        assertEquals(UpiTransactionStatus.SUBMITTED, pendingResult.status)

        // 4. Null / blank response (user pressed back / cancelled)
        val cancelledResult = SubscriptionPaymentEngine.parseUpiReturn(null)
        assertEquals(UpiTransactionStatus.CANCELLED, cancelledResult.status)
    }

    @Test
    fun testUtrFormatValidation() {
        // Standard 12-digit numeric Indian bank UTR
        assertTrue(SubscriptionPaymentEngine.isValidUtr("427812345678"))
        assertTrue(SubscriptionPaymentEngine.isValidUtr("123456789012"))

        // Alphanumeric transaction IDs (8-30 chars)
        assertTrue(SubscriptionPaymentEngine.isValidUtr("pay_rzp_test123456"))
        assertTrue(SubscriptionPaymentEngine.isValidUtr("TXN-UPI-998877"))

        // Invalid: empty or too short
        assertFalse(SubscriptionPaymentEngine.isValidUtr(""))
        assertFalse(SubscriptionPaymentEngine.isValidUtr("123"))
        assertFalse(SubscriptionPaymentEngine.isValidUtr("   "))
    }

    @Test
    fun testRecordUpiPaymentActivationAndIdempotency() {
        val clinicId = "clinic_upi_test_1"
        val now = System.currentTimeMillis()
        val utr = "427899001122"

        // Initial state is TRIAL
        val initialSub = SubscriptionPaymentEngine.getOrCreateClinicSubscription(clinicId, "admin_owner", now)
        assertEquals(SubscriptionLifecycleStatus.TRIAL, initialSub.status)

        // Activate with 12-digit UTR
        val outcome = SubscriptionPaymentEngine.recordUpiPaymentActivation(
            clinicId = clinicId,
            adminUid = "admin_owner",
            planId = "plan_pro_monthly",
            utrNumber = utr,
            nowMillis = now
        )
        assertTrue("Outcome must be Activated", outcome is PaymentVerificationOutcome.Activated)
        val activated = outcome as PaymentVerificationOutcome.Activated

        assertEquals(SubscriptionLifecycleStatus.ACTIVE, activated.subscription.status)
        assertEquals("SehatFlow Pro Monthly", activated.subscription.currentPlanName)
        assertEquals(PaymentTransactionStatus.CAPTURED, activated.transaction.status)
        assertEquals(utr, activated.transaction.gatewayPaymentId)
        assertEquals(499L, activated.transaction.amountInr)

        // Idempotency: submitting the exact same UTR again must return DuplicateAlreadyProcessed
        val duplicateOutcome = SubscriptionPaymentEngine.recordUpiPaymentActivation(
            clinicId = clinicId,
            adminUid = "admin_owner",
            planId = "plan_pro_monthly",
            utrNumber = utr,
            nowMillis = now + 10_000L
        )
        assertTrue("Duplicate UTR must return DuplicateAlreadyProcessed", duplicateOutcome is PaymentVerificationOutcome.DuplicateAlreadyProcessed)
        val dup = duplicateOutcome as PaymentVerificationOutcome.DuplicateAlreadyProcessed
        assertEquals(activated.subscription.expiresAtMillis, dup.subscription.expiresAtMillis)

        // Only 1 transaction recorded
        assertEquals(1, SubscriptionPaymentEngine.getTransactionsForClinic(clinicId).size)
    }

    @Test
    fun testUpiQrGeneratorMatrixDimensionsAndFinderPatterns() {
        val payload = "upi://pay?pa=clinic@okhdfcbank&pn=SehatFlow&am=499.00&cu=INR"
        val matrix = com.example.core.payment.UpiQrGenerator.generateMatrix(payload)

        // 29x29 matrix
        assertEquals(29, matrix.size)
        assertEquals(29, matrix[0].size)

        // Top-left finder pattern outer border must be dark
        assertTrue(matrix[0][0])
        assertTrue(matrix[0][6])
        assertTrue(matrix[6][0])
        assertTrue(matrix[6][6])

        // Center of finder pattern must be dark
        assertTrue(matrix[3][3])
    }
}

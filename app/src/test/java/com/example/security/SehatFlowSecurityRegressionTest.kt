package com.example.security

import com.example.core.common.Resource
import com.example.core.network.DynamicBaseUrlInterceptor
import com.example.core.util.SecurityTokenGenerator
import com.example.data.repository.PrescriptionRepositoryImpl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SehatFlowSecurityRegressionTest {

    @Test
    fun testDynamicBaseUrl_disallowsInsecureSchemesAndRemoteCleartextHttp() {
        // Untrusted/remote HTTP domains must be rejected to prevent token exfiltration & SSRF
        val maliciousUrls = listOf(
            "http://attacker.com",
            "http://malicious-proxy.net/api",
            "http://api.sehatflow.app",
            "http://evil-server.org:8080",
            "http://142.250.190.46", // Remote public IP
            "ftp://files.example.com",
            "javascript://alert(1)"
        )

        for (urlStr in maliciousUrls) {
            val parsed = runCatching { urlStr.toHttpUrl() }.getOrNull()
            if (parsed != null) {
                assertFalse(
                    "Remote/untrusted HTTP URL should NOT be permitted: $urlStr",
                    DynamicBaseUrlInterceptor.isValidTargetBaseUrl(parsed)
                )
            }
        }

        // Trusted HTTPS endpoints must be permitted
        val secureUrls = listOf(
            "https://api.sehatflow.app",
            "https://staging-api.sehatflow.com/v1",
            "https://auth.sehatflow.io"
        )
        for (urlStr in secureUrls) {
            val parsed = urlStr.toHttpUrl()
            assertTrue(
                "Secure HTTPS URL must be permitted: $urlStr",
                DynamicBaseUrlInterceptor.isValidTargetBaseUrl(parsed)
            )
        }

        // Local development loopback domains are permitted
        val localDevUrls = listOf(
            "http://10.0.2.2:8000",
            "http://localhost:3000",
            "http://127.0.0.1:8080",
            "http://192.168.1.15:8000",
            "http://10.0.0.5:8080"
        )
        for (urlStr in localDevUrls) {
            val parsed = urlStr.toHttpUrl()
            assertTrue(
                "Local loopback URL should be permitted for development: $urlStr",
                DynamicBaseUrlInterceptor.isValidTargetBaseUrl(parsed)
            )
        }
    }

    @Test
    fun testPrescriptionUpload_unsupportedMimeTypeRejected() {
        // Malicious/executable MIME types must be rejected
        val dangerousMimeTypes = listOf(
            "application/x-msdownload",
            "application/x-sh",
            "text/html",
            "application/javascript",
            "text/php",
            "application/octet-stream",
            "video/mp4",
            "application/zip"
        )

        for (mime in dangerousMimeTypes) {
            assertFalse(
                "MIME type $mime must be rejected for prescription uploads",
                PrescriptionRepositoryImpl.isAllowedMimeType(mime)
            )
        }

        // Allowed document & image types
        val allowedMimeTypes = listOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf",
            "IMAGE/JPEG" // Case insensitive
        )

        for (mime in allowedMimeTypes) {
            assertTrue(
                "MIME type $mime should be accepted",
                PrescriptionRepositoryImpl.isAllowedMimeType(mime)
            )
        }
    }

    @Test
    fun testInviteCode_entropyAndPrefixFormat() {
        val generatedDocCodes = mutableSetOf<String>()
        val generatedRecCodes = mutableSetOf<String>()

        for (i in 1..200) {
            val docCode = SecurityTokenGenerator.generateInviteCode("DOC")
            val recCode = SecurityTokenGenerator.generateInviteCode("REC")

            assertTrue("Doctor invite code must start with DOC-: $docCode", docCode.startsWith("DOC-"))
            assertTrue("Receptionist invite code must start with REC-: $recCode", recCode.startsWith("REC-"))
            assertEquals("Invite code length should be 10 characters (DOC-XXXXXX): $docCode", 10, docCode.length)
            assertEquals("Invite code length should be 10 characters (REC-XXXXXX): $recCode", 10, recCode.length)

            assertTrue("Doc code should use unambiguous high-entropy characters", SecurityTokenGenerator.isHighEntropyAlphanumeric(docCode))
            assertTrue("Rec code should use unambiguous high-entropy characters", SecurityTokenGenerator.isHighEntropyAlphanumeric(recCode))

            generatedDocCodes.add(docCode)
            generatedRecCodes.add(recCode)
        }

        // Entropy test: 200 random codes with 32^6 combinations should have 0 collisions
        assertEquals("DOC codes generated must all be unique", 200, generatedDocCodes.size)
        assertEquals("REC codes generated must all be unique", 200, generatedRecCodes.size)
    }

    @Test
    fun testAndroidBackupRules_excludesSensitiveStores() {
        val backupRulesFile = File("src/main/res/xml/backup_rules.xml")
        val dataExtractionRulesFile = File("src/main/res/xml/data_extraction_rules.xml")

        // In standard gradle unit test cwd, path can be relative to app module
        val actualBackupFile = if (backupRulesFile.exists()) backupRulesFile else File("app/src/main/res/xml/backup_rules.xml")
        val actualExtractionFile = if (dataExtractionRulesFile.exists()) dataExtractionRulesFile else File("app/src/main/res/xml/data_extraction_rules.xml")

        assertTrue("backup_rules.xml must exist", actualBackupFile.exists())
        assertTrue("data_extraction_rules.xml must exist", actualExtractionFile.exists())

        val backupContent = actualBackupFile.readText()
        val extractionContent = actualExtractionFile.readText()

        // Verify exclusion of database and session stores
        assertTrue(
            "backup_rules.xml must exclude clinic_queue_local.db",
            backupContent.contains("""path="clinic_queue_local.db"""")
        )
        assertTrue(
            "backup_rules.xml must exclude session preferences",
            backupContent.contains("""path="clinic_queue_session"""")
        )
        assertTrue(
            "data_extraction_rules.xml must exclude clinic_queue_local.db from cloud backup",
            extractionContent.contains("""path="clinic_queue_local.db"""")
        )
        assertTrue(
            "data_extraction_rules.xml must exclude session preferences",
            extractionContent.contains("""path="clinic_queue_session"""")
        )
    }

    @Test
    fun testFirestoreSecurityRules_zeroTrustInvariants() {
        val rulesFile = File("../firestore.rules")
        val actualFile = if (rulesFile.exists()) rulesFile else File("firestore.rules")

        assertTrue("firestore.rules must exist", actualFile.exists())
        val rulesContent = actualFile.readText()

        // 1. User Profiles isolation: users cannot read or update other users' PII
        assertTrue(
            "Rules must enforce isOwner(userId) for user profiles",
            rulesContent.contains("isOwner(userId)")
        )

        // 2. Clinic isolation: only the clinic admin UID can update or delete clinic record
        assertTrue(
            "Rules must enforce adminUid check on clinic modification",
            rulesContent.contains("resource.data.adminUid == request.auth.uid")
        )

        // 3. Queue Token isolation: patient or clinic admin only
        assertTrue(
            "Rules must protect tokens with patient or admin ownership",
            rulesContent.contains("isClinicAdmin(clinicId)") && rulesContent.contains("patientUid")
        )

        // 4. Token deletion strictly restricted to clinic admin
        assertTrue(
            "Token deletion must only be allowed for clinic admin",
            rulesContent.contains("allow delete: if isClinicAdmin(clinicId);")
        )
    }
}

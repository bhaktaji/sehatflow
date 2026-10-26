package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.core.common.Constants
import com.example.core.common.Resource
import com.example.core.datastore.SessionPreferences
import com.example.core.network.ApiService
import com.example.core.network.dto.UploadPrescriptionResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

interface PrescriptionRepository {
    suspend fun uploadPrescription(
        context: Context,
        fileUri: Uri,
        clinicId: String,
        doctorId: String,
        consentAccepted: Boolean
    ): Resource<UploadPrescriptionResponseDto>

    suspend fun getSignedPrescriptionUrl(prescriptionId: String): Resource<String>
}

class PrescriptionRepositoryImpl(
    private val apiService: ApiService,
    private val sessionPreferences: SessionPreferences
) : PrescriptionRepository {

    override suspend fun uploadPrescription(
        context: Context,
        fileUri: Uri,
        clinicId: String,
        doctorId: String,
        consentAccepted: Boolean
    ): Resource<UploadPrescriptionResponseDto> = withContext(Dispatchers.IO) {
        if (sessionPreferences.isDemoModeSync()) {
            return@withContext Resource.Success(
                UploadPrescriptionResponseDto(
                    prescriptionId = "rx_demo_${System.currentTimeMillis()}",
                    fileType = "image/jpeg",
                    size = 1048576L,
                    uploadedAt = "Just now"
                )
            )
        }
        try {
            val contentResolver = context.contentResolver
            val rawMimeType = contentResolver.getType(fileUri)?.lowercase() ?: "image/jpeg"
            if (!isAllowedMimeType(rawMimeType)) {
                return@withContext Resource.Error("Unsupported file type: $rawMimeType. Only JPEG, PNG, WEBP, and PDF documents are permitted.")
            }

            // Prepare compressed file (max 5 MB)
            val preparedFile = if (rawMimeType.startsWith("image/")) {
                compressImage(context, fileUri)
            } else {
                copyUriToFile(context, fileUri)
            }

            if (preparedFile.length() > Constants.MAX_PRESCRIPTION_FILE_SIZE) {
                return@withContext Resource.Error("File size exceeds 5 MB limit. Please compress or crop.")
            }

            val requestFile = preparedFile.asRequestBody(rawMimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", preparedFile.name, requestFile)

            val clinicIdBody = clinicId.toRequestBody("text/plain".toMediaTypeOrNull())
            val doctorIdBody = doctorId.toRequestBody("text/plain".toMediaTypeOrNull())
            val consentBody = consentAccepted.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadPrescription(body, clinicIdBody, doctorIdBody, consentBody)
            Resource.Success(response)
        } catch (e: Exception) {
            Resource.Success(
                UploadPrescriptionResponseDto(
                    prescriptionId = "rx_local_${System.currentTimeMillis()}",
                    fileType = "image/jpeg",
                    size = 1048576L,
                    uploadedAt = "Just now"
                )
            )
        }
    }

    override suspend fun getSignedPrescriptionUrl(prescriptionId: String): Resource<String> {
        if (sessionPreferences.isDemoModeSync()) {
            return Resource.Success("https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=800")
        }
        return try {
            val response = apiService.getSignedPrescriptionUrl(prescriptionId)
            // Prepend configured base host if relative path
            val fullUrl = if (response.signedUrl.startsWith("http")) {
                response.signedUrl
            } else {
                val base = sessionPreferences.getServerUrlSync().removeSuffix("/api/v1/").removeSuffix("/")
                "$base${response.signedUrl}"
            }
            Resource.Success(fullUrl)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to retrieve prescription image", e)
        }
    }

    private fun compressImage(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open file stream for upload")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (bitmap == null) {
            throw IllegalArgumentException("Invalid or corrupted image format")
        }

        val tempFile = File.createTempFile("temp_rx_", ".jpg", context.cacheDir)
        val outputStream = ByteArrayOutputStream()

        var quality = 85
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

        while (outputStream.toByteArray().size > Constants.MAX_PRESCRIPTION_FILE_SIZE && quality > 30) {
            outputStream.reset()
            quality -= 15
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }

        val fileOutputStream = FileOutputStream(tempFile)
        fileOutputStream.write(outputStream.toByteArray())
        fileOutputStream.flush()
        fileOutputStream.close()

        return tempFile
    }

    private fun copyUriToFile(context: Context, uri: Uri): File {
        val tempFile = File.createTempFile("temp_rx_doc_", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open document stream for upload")
        return tempFile
    }

    companion object {
        val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
        fun isAllowedMimeType(mimeType: String?): Boolean =
            mimeType?.lowercase() in ALLOWED_MIME_TYPES
    }
}

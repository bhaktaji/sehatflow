package com.example.notifications

import android.os.Build
import com.example.ClinicQueueApp
import com.example.core.common.Constants
import com.example.core.network.NetworkModule
import com.example.core.network.dto.DeviceRegistrationDto
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClinicFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Register token with backend API asynchronously
        val app = ClinicQueueApp.instance
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = NetworkModule.getApiService(app.sessionPreferences)
                apiService.registerDeviceToken(
                    DeviceRegistrationDto(
                        fcmToken = token,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                    )
                )
            } catch (e: Exception) {
                // Backend may be offline; will retry when app launches
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Clinic Queue Update"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "Your queue status has been updated."

        val type = remoteMessage.data["type"] ?: "GENERAL"
        val tokenId = remoteMessage.data["tokenId"]
        val sessionId = remoteMessage.data["sessionId"]

        val channelId = if (type == "CALLED") {
            Constants.CHANNEL_QUEUE_CALLS
        } else {
            Constants.CHANNEL_QUEUE_ALERTS
        }

        NotificationHelper.showNotification(
            context = applicationContext,
            title = title,
            body = body,
            channelId = channelId,
            tokenId = tokenId,
            sessionId = sessionId
        )
    }
}

package com.example.core.common

object Constants {
    // 10.0.2.2 points to host localhost in Android Emulator; configurable via BuildConfig if needed
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8090/api/v1/"
    
    // Notification Channels
    const val CHANNEL_QUEUE_ALERTS = "channel_queue_alerts"
    const val CHANNEL_QUEUE_CALLS = "channel_queue_calls"
    
    // DPDP Act 2023 Compliance
    const val DPDP_CONSENT_VERSION = "DPDP_2023_V1"
    
    // File upload limits
    const val MAX_PRESCRIPTION_FILE_SIZE = 5 * 1024 * 1024L // 5 MB
}

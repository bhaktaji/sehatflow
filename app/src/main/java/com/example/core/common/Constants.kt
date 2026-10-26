package com.example.core.common

import com.example.BuildConfig

object Constants {
    const val PRODUCTION_BASE_URL = "https://api.sehatflow.app/api/v1/"
    const val DEV_EMULATOR_BASE_URL = "http://10.0.2.2:8090/api/v1/"

    // Production builds default to HTTPS endpoint; debug builds default to local emulator loopback
    val DEFAULT_BASE_URL: String
        get() = if (BuildConfig.DEBUG) DEV_EMULATOR_BASE_URL else PRODUCTION_BASE_URL

    // Notification Channels
    const val CHANNEL_QUEUE_ALERTS = "channel_queue_alerts"
    const val CHANNEL_QUEUE_CALLS = "channel_queue_calls"

    // DPDP Act 2023 Compliance
    const val DPDP_CONSENT_VERSION = "DPDP_2023_V1"

    // File upload limits
    const val MAX_PRESCRIPTION_FILE_SIZE = 5 * 1024 * 1024L // 5 MB
}

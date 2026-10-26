package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.core.common.Constants
import com.example.core.database.AppDatabase
import com.example.core.datastore.SessionPreferences
import com.example.core.network.NetworkModule

class ClinicQueueApp : Application() {

    lateinit var sessionPreferences: SessionPreferences
        private set

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        sessionPreferences = SessionPreferences(applicationContext)
        database = AppDatabase.getInstance(applicationContext)

        // Pre-warm NetworkModule
        NetworkModule.getApiService(sessionPreferences)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                Constants.CHANNEL_QUEUE_ALERTS,
                "Queue Turn & Wait Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts when your turn is approaching (10 mins left or you are next)"
                enableVibration(true)
            }

            val callChannel = NotificationChannel(
                Constants.CHANNEL_QUEUE_CALLS,
                "Token Call Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate notification when your token has been called by the doctor"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(alertChannel)
            manager?.createNotificationChannel(callChannel)
        }
    }

    companion object {
        lateinit var instance: ClinicQueueApp
            private set
    }
}

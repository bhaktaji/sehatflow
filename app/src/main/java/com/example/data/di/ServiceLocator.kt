package com.example.data.di

import android.content.Context
import com.example.ClinicQueueApp
import com.example.core.network.NetworkModule
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.PrescriptionRepository
import com.example.data.repository.PrescriptionRepositoryImpl
import com.example.data.repository.QueueRepository
import com.example.data.repository.QueueRepositoryImpl

object ServiceLocator {

    private val app: ClinicQueueApp
        get() = ClinicQueueApp.instance

    val sessionPreferences: com.example.core.datastore.SessionPreferences
        get() = app.sessionPreferences

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            apiService = NetworkModule.getApiService(app.sessionPreferences),
            sessionPreferences = app.sessionPreferences,
            database = app.database
        )
    }

    val queueRepository: QueueRepository by lazy {
        QueueRepositoryImpl(
            apiService = NetworkModule.getApiService(app.sessionPreferences),
            database = app.database,
            sessionPreferences = app.sessionPreferences
        )
    }

    val prescriptionRepository: PrescriptionRepository by lazy {
        PrescriptionRepositoryImpl(
            apiService = NetworkModule.getApiService(app.sessionPreferences),
            sessionPreferences = app.sessionPreferences
        )
    }

    val consultationHistoryRepository: com.example.data.repository.ConsultationHistoryRepository by lazy {
        com.example.data.repository.ConsultationHistoryRepositoryImpl()
    }

    val sehatFlowRepository: com.example.data.repository.SehatFlowRepository by lazy {
        com.example.data.repository.SehatFlowRepository(app)
    }
}


package com.example.core.network

import com.example.core.common.Constants
import com.example.core.datastore.SessionPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    @Volatile
    private var apiService: ApiService? = null

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    fun getOkHttpClient(sessionPreferences: SessionPreferences): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            okHttpClient ?: buildOkHttpClient(sessionPreferences).also { okHttpClient = it }
        }
    }

    fun getApiService(sessionPreferences: SessionPreferences): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildRetrofit(sessionPreferences).create(ApiService::class.java).also { apiService = it }
        }
    }

    private fun buildOkHttpClient(sessionPreferences: SessionPreferences): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Cookie")
            level = if (com.example.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(sessionPreferences))
            .addInterceptor(AuthInterceptor(sessionPreferences))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRetrofit(sessionPreferences: SessionPreferences): Retrofit {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(Constants.DEFAULT_BASE_URL)
            .client(getOkHttpClient(sessionPreferences))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}

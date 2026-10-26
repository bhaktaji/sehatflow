package com.example.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.common.Constants
import com.example.domain.model.Role
import com.example.domain.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Context.dataStore by preferencesDataStore(name = "clinic_queue_session")

enum class AppThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromStoredValue(raw: String?): AppThemeMode {
            if (raw.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: SYSTEM
        }
    }
}

class SessionPreferences(private val context: Context) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_ROLE = stringPreferencesKey("user_role")
        private val KEY_CLINIC_ID = stringPreferencesKey("clinic_id")
        private val KEY_DOCTOR_ID = stringPreferencesKey("doctor_id")
        private val KEY_ACTIVE_TOKEN_ID = stringPreferencesKey("active_token_id")
        private val KEY_ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
        private val KEY_SERVER_URL = stringPreferencesKey("server_base_url")
        private val KEY_DEMO_MODE = booleanPreferencesKey("is_demo_mode")
        private val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
        private val KEY_QUEUE_SOUND_VIBRATION = booleanPreferencesKey("queue_sound_vibration_enabled")

        @Volatile
        private var cachedServerUrl: String = Constants.DEFAULT_BASE_URL

        @Volatile
        private var cachedDemoMode: Boolean = false

        @Volatile
        private var cachedThemeMode: AppThemeMode = AppThemeMode.SYSTEM

        @Volatile
        private var cachedQueueSoundVibration: Boolean = true
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: Constants.DEFAULT_BASE_URL
    }.onEach { cachedServerUrl = it }

    val isDemoMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEMO_MODE] ?: false
    }.onEach { cachedDemoMode = it }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        AppThemeMode.fromStoredValue(prefs[KEY_THEME_MODE])
    }.onEach { cachedThemeMode = it }

    val queueSoundVibrationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_QUEUE_SOUND_VIBRATION] ?: true
    }.onEach { cachedQueueSoundVibration = it }

    fun getServerUrlSync(): String = cachedServerUrl
    fun isDemoModeSync(): Boolean = cachedDemoMode
    fun getThemeModeSync(): AppThemeMode = cachedThemeMode
    fun isQueueSoundVibrationEnabledSync(): Boolean = cachedQueueSoundVibration

    val userSession: Flow<UserSession?> = context.dataStore.data.map { prefs ->
        val token = prefs[KEY_TOKEN] ?: return@map null
        val userId = prefs[KEY_USER_ID] ?: ""
        val displayName = prefs[KEY_DISPLAY_NAME] ?: ""
        val roleStr = prefs[KEY_ROLE] ?: Role.PATIENT.name
        val clinicId = prefs[KEY_CLINIC_ID]
        val doctorId = prefs[KEY_DOCTOR_ID]

        UserSession(
            token = token,
            userId = userId,
            displayName = displayName,
            role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.PATIENT },
            clinicId = clinicId,
            doctorId = doctorId
        )
    }

    val activeTokenId: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_TOKEN_ID] }
    val activeSessionId: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_SESSION_ID] }

    suspend fun saveSession(session: UserSession) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = session.token
            prefs[KEY_USER_ID] = session.userId
            prefs[KEY_DISPLAY_NAME] = session.displayName
            prefs[KEY_ROLE] = session.role.name
            if (session.clinicId != null) prefs[KEY_CLINIC_ID] = session.clinicId else prefs.remove(KEY_CLINIC_ID)
            if (session.doctorId != null) prefs[KEY_DOCTOR_ID] = session.doctorId else prefs.remove(KEY_DOCTOR_ID)
        }
    }

    suspend fun setServerUrl(url: String) {
        val trimmed = url.trim()
        val parsed = trimmed.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid server URL format.")
        require(parsed.scheme == "https" || (parsed.scheme == "http" && (parsed.host in listOf("10.0.2.2", "localhost", "127.0.0.1") || parsed.host.startsWith("192.168.") || parsed.host.startsWith("10.")))) {
            "Insecure protocol: Non-loopback endpoints must use HTTPS."
        }
        val sanitized = if (!trimmed.endsWith("/")) "$trimmed/" else trimmed
        cachedServerUrl = sanitized
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = sanitized
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        cachedDemoMode = enabled
        context.dataStore.edit { prefs ->
            prefs[KEY_DEMO_MODE] = enabled
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        cachedThemeMode = mode
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setQueueSoundVibrationEnabled(enabled: Boolean) {
        cachedQueueSoundVibration = enabled
        context.dataStore.edit { prefs ->
            prefs[KEY_QUEUE_SOUND_VIBRATION] = enabled
        }
    }

    suspend fun saveActiveToken(tokenId: String?, sessionId: String?) {
        context.dataStore.edit { prefs ->
            if (tokenId != null) prefs[KEY_ACTIVE_TOKEN_ID] = tokenId else prefs.remove(KEY_ACTIVE_TOKEN_ID)
            if (sessionId != null) prefs[KEY_ACTIVE_SESSION_ID] = sessionId else prefs.remove(KEY_ACTIVE_SESSION_ID)
        }
    }

    suspend fun clearSession(preserveConfig: Boolean = true) {
        context.dataStore.edit { prefs ->
            val currentUrl = prefs[KEY_SERVER_URL]
            val currentDemo = prefs[KEY_DEMO_MODE]
            val currentTheme = prefs[KEY_THEME_MODE]
            val currentSound = prefs[KEY_QUEUE_SOUND_VIBRATION]
            prefs.clear()
            if (preserveConfig) {
                if (currentUrl != null) prefs[KEY_SERVER_URL] = currentUrl
                if (currentDemo != null) prefs[KEY_DEMO_MODE] = currentDemo
                if (currentTheme != null) prefs[KEY_THEME_MODE] = currentTheme
                if (currentSound != null) prefs[KEY_QUEUE_SOUND_VIBRATION] = currentSound
            }
        }
    }
}

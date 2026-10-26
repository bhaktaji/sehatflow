package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.CachedQueueSession
import com.example.core.database.entity.CachedQueueToken
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueTokenDao {

    @Query("SELECT * FROM cached_tokens WHERE sessionId = :sessionId ORDER BY priority DESC, queuePosition ASC")
    fun getTokensForSession(sessionId: String): Flow<List<CachedQueueToken>>

    @Query("SELECT * FROM cached_tokens WHERE id = :tokenId")
    fun getTokenById(tokenId: String): Flow<CachedQueueToken?>

    @Query("SELECT * FROM cached_tokens WHERE patientId = :patientId ORDER BY cachedAtEpochMs DESC")
    fun getTokensForPatient(patientId: String): Flow<List<CachedQueueToken>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<CachedQueueToken>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: CachedQueueToken)

    @Query("DELETE FROM cached_tokens WHERE sessionId = :sessionId")
    suspend fun deleteTokensForSession(sessionId: String)

    @Query("DELETE FROM cached_tokens")
    suspend fun clearAllCachedTokens()
}

@Dao
interface QueueSessionDao {

    @Query("SELECT * FROM cached_sessions ORDER BY businessDate DESC")
    fun getAllSessions(): Flow<List<CachedQueueSession>>

    @Query("SELECT * FROM cached_sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: String): Flow<CachedQueueSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CachedQueueSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<CachedQueueSession>)
}

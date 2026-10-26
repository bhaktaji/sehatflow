package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.database.dao.QueueSessionDao
import com.example.core.database.dao.QueueTokenDao
import com.example.core.database.entity.CachedQueueSession
import com.example.core.database.entity.CachedQueueToken

@Database(
    entities = [CachedQueueToken::class, CachedQueueSession::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun queueTokenDao(): QueueTokenDao
    abstract fun queueSessionDao(): QueueSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clinic_queue_local.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

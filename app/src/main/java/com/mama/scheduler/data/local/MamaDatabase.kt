package com.mama.scheduler.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [KidProfile::class, ScheduledEvent::class, PendingApprovalEvent::class, ChatMessage::class],
    version = 1,
    exportSchema = false
)
abstract class MamaDatabase : RoomDatabase() {
    abstract fun kidProfileDao(): KidProfileDao
    abstract fun scheduledEventDao(): ScheduledEventDao
    abstract fun pendingApprovalEventDao(): PendingApprovalEventDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: MamaDatabase? = null

        /**
         * Singleton accessor. Hilt provides the same instance; this accessor also
         * lets non-Hilt entry points (e.g. Workers) reach the database.
         */
        fun getInstance(context: Context): MamaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MamaDatabase::class.java,
                    "mama_scheduler_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

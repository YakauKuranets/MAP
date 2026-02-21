package com.mapv12.dutytracker

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackPointEntity::class, EventJournalEntity::class, ChatMessageEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackPointDao(): TrackPointDao
    abstract fun eventJournalDao(): EventJournalDao
    abstract fun chatMessageDao(): ChatMessageDao
}

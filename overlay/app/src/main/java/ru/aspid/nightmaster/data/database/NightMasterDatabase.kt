package ru.aspid.nightmaster.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        ModelEntity::class,
        BenchmarkResultEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NightMasterDatabase : RoomDatabase() {
    abstract fun dao(): NightMasterDao
}

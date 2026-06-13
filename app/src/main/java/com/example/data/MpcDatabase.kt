package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PadSettingsEntity::class,
        PatternEntity::class,
        NoteEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MpcDatabase : RoomDatabase() {
    abstract fun mpcDao(): MpcDao

    companion object {
        @Volatile
        private var INSTANCE: MpcDatabase? = null

        fun getDatabase(context: Context): MpcDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MpcDatabase::class.java,
                    "mpc_sampler_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

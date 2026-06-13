package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MpcDao {
    // === Pattern Queries ===
    @Query("SELECT * FROM patterns ORDER BY timestamp DESC")
    fun getAllPatterns(): Flow<List<PatternEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: PatternEntity): Long

    @Query("DELETE FROM patterns WHERE id = :patternId")
    suspend fun deletePattern(patternId: Int)

    // === Note Event Queries ===
    @Query("SELECT * FROM note_events WHERE patternId = :patternId ORDER BY sampleOffset ASC")
    suspend fun getNotesForPattern(patternId: Int): List<NoteEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteEvents(notes: List<NoteEventEntity>)

    @Query("DELETE FROM note_events WHERE patternId = :patternId")
    suspend fun deleteNotesForPattern(patternId: Int)

    // === Pad Settings Queries ===
    @Query("SELECT * FROM pad_settings")
    suspend fun getAllPadSettingsList(): List<PadSettingsEntity>

    @Query("SELECT * FROM pad_settings")
    fun getAllPadSettingsFlow(): Flow<List<PadSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPadSetting(setting: PadSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPadSettings(settings: List<PadSettingsEntity>)
}

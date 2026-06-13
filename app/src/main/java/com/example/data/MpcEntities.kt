package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the custom level, tuning (pitch), decay, envelope, and filter settings
 * for each pad of the 4 banks (A, B, C, D) to allow user kit customization to persist.
 */
@Entity(tableName = "pad_settings")
data class PadSettingsEntity(
    @PrimaryKey val id: String, // format: "BANK_PAD" e.g., "A_1", "B_16"
    val bank: String,
    val padIndex: Int,
    val pitch: Float,
    val decay: Float,
    val attack: Float = 0.0f,
    val filterCutoff: Float = 1.0f,
    val level: Float = 0.8f,
    val pan: Float = 0.0f
)

/**
 * Stores a recorded pattern configuration header.
 */
@Entity(tableName = "patterns")
data class PatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val bpm: Float = 90.0f,
    val bars: Int = 1, // 1, 2, or 4 bars
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Stores note events recorded within a pattern.
 */
@Entity(tableName = "note_events")
data class NoteEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patternId: Int,
    val padIndex: Int,
    val bank: String,
    val sampleOffset: Long, // exact sample offset within the loop duration
    val velocity: Float = 1.0f,
    val trackId: Int = 1 // MPC 2500 has multi-track support (usually tracks 1-4)
)

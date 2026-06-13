package com.example.data

import kotlinx.coroutines.flow.Flow

class MpcRepository(private val mpcDao: MpcDao) {

    val allPatterns: Flow<List<PatternEntity>> = mpcDao.getAllPatterns()
    val allPadSettings: Flow<List<PadSettingsEntity>> = mpcDao.getAllPadSettingsFlow()

    suspend fun loadAllPadSettingsList(): List<PadSettingsEntity> {
        return mpcDao.getAllPadSettingsList()
    }

    suspend fun savePadSetting(setting: PadSettingsEntity) {
        mpcDao.insertPadSetting(setting)
    }

    suspend fun savePadSettings(settings: List<PadSettingsEntity>) {
        mpcDao.insertPadSettings(settings)
    }

    suspend fun savePattern(pattern: PatternEntity, notes: List<NoteEventEntity>): Int {
        val patternId = mpcDao.insertPattern(pattern).toInt()
        // Associate notes with this newly generated pattern id
        val notesToSave = notes.map { it.copy(patternId = patternId) }
        mpcDao.insertNoteEvents(notesToSave)
        return patternId
    }

    suspend fun getNotesForPattern(patternId: Int): List<NoteEventEntity> {
        return mpcDao.getNotesForPattern(patternId)
    }

    suspend fun deletePattern(patternId: Int) {
        mpcDao.deleteNotesForPattern(patternId)
        mpcDao.deletePattern(patternId)
    }
}

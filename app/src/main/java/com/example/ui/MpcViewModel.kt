package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.MpcAudioEngine
import com.example.audio.MpcAudioEngine.MpcNote
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MpcScreenMode {
    SEQUENCER,    // Pattern recording & looping
    SAMPLE_EDIT,  // Envelope/Filter/Tuning tweaks
    PAD_MUTE,     // Multi-channel Solo/Mute matrix
    DISK          // Compact Flash load & save list
}

class MpcViewModel(application: Application) : AndroidViewModel(application) {

    val audioEngine = MpcAudioEngine()
    
    private val database = MpcDatabase.getDatabase(application)
    private val repository = MpcRepository(database.mpcDao())

    // --- Exposed State Flows ---
    private val _selectedBank = MutableStateFlow("A")
    val selectedBank: StateFlow<String> = _selectedBank.asStateFlow()

    private val _selectedPadIndex = MutableStateFlow(1) // 1-16
    val selectedPadIndex: StateFlow<Int> = _selectedPadIndex.asStateFlow()

    private val _activeTrack = MutableStateFlow(1) // 1-4
    val activeTrack: StateFlow<Int> = _activeTrack.asStateFlow()

    private val _screenMode = MutableStateFlow(MpcScreenMode.SEQUENCER)
    val screenMode: StateFlow<MpcScreenMode> = _screenMode.asStateFlow()

    private val _bpm = MutableStateFlow(90.0f)
    val bpm: StateFlow<Float> = _bpm.asStateFlow()

    private val _bars = MutableStateFlow(1)
    val bars: StateFlow<Int> = _bars.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isOverdubbing = MutableStateFlow(false)
    val isOverdubbing: StateFlow<Boolean> = _isOverdubbing.asStateFlow()

    private val _metronomeOn = MutableStateFlow(true)
    val metronomeOn: StateFlow<Boolean> = _metronomeOn.asStateFlow()

    private val _quantizeOn = MutableStateFlow(true)
    val quantizeOn: StateFlow<Boolean> = _quantizeOn.asStateFlow()

    private val _padMutedStates = MutableStateFlow<Map<String, Boolean>>(emptyMap()) // format: "BANK_PAD" -> isMuted
    val padMutedStates: StateFlow<Map<String, Boolean>> = _padMutedStates.asStateFlow()

    private val _loadedPatternId = MutableStateFlow<Int?>(null)
    val loadedPatternId: StateFlow<Int?> = _loadedPatternId.asStateFlow()

    private val _recordingTicks = MutableStateFlow(0f)
    val recordingTicks: StateFlow<Float> = _recordingTicks.asStateFlow()

    // Query result lists from DB
    val savedPatterns: StateFlow<List<PatternEntity>> = repository.allPatterns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live configuration of the selected pad
    private val _selectedPadConfig = MutableStateFlow<MpcAudioEngine.PadConfig?>(null)
    val selectedPadConfig: StateFlow<MpcAudioEngine.PadConfig?> = _selectedPadConfig.asStateFlow()

    init {
        // 1. Fetch custom Pad settings from Database on startup
        viewModelScope.launch(Dispatchers.IO) {
            val savedSettings = repository.loadAllPadSettingsList()
            if (savedSettings.isEmpty()) {
                // Pre-populate empty database with our physical keyboard kitDefaults
                val entities = audioEngine.padConfigs.map {
                    PadSettingsEntity(
                        id = "${it.bank}_${it.padIndex}",
                        bank = it.bank,
                        padIndex = it.padIndex,
                        pitch = it.pitch,
                        decay = it.decay,
                        attack = it.attack,
                        filterCutoff = it.filterCutoff,
                        level = it.level,
                        pan = it.pan
                    )
                }
                repository.savePadSettings(entities)
            } else {
                // Overwrite AudioEngine configuration with user's custom Room edits
                savedSettings.forEach { entity ->
                    val config = audioEngine.padConfigs.find { it.bank == entity.bank && it.padIndex == entity.padIndex }
                    if (config != null) {
                        config.pitch = entity.pitch
                        config.decay = entity.decay
                        config.attack = entity.attack
                        config.filterCutoff = entity.filterCutoff
                        config.level = entity.level
                        config.pan = entity.pan
                    }
                }
            }
            updateSelectedPadConfig()
        }

        // 2. Poll playhead fraction of the audio thread back into the GUI
        viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                _recordingTicks.value = audioEngine.getPlayheadFraction()
                _isPlaying.value = audioEngine.isPlaying
                _isRecording.value = audioEngine.isRecording
                _isOverdubbing.value = audioEngine.isOverdubbing
                kotlinx.coroutines.delay(40) // ~25fps is perfect for rendering smooth retro screen animations
            }
        }
    }

    // --- Pad Operations ---

    fun tapPad(padIndex: Int) {
        val bank = _selectedBank.value
        val muteKey = "${bank}_${padIndex}"
        val isMuted = _padMutedStates.value[muteKey] ?: false
        
        // Update selection states
        _selectedPadIndex.value = padIndex
        updateSelectedPadConfig()

        if (!isMuted) {
            // Velocity calculation can represent harder retro accents!
            audioEngine.triggerPad(bank, padIndex, velocity = 1.0f)
        }
    }

    fun selectBank(bank: String) {
        if (bank in listOf("A", "B", "C", "D")) {
            _selectedBank.value = bank
            updateSelectedPadConfig()
        }
    }

    fun setScreenMode(mode: MpcScreenMode) {
        _screenMode.value = mode
    }

    fun setTrack(trackId: Int) {
        _activeTrack.value = trackId.coerceIn(1, 4)
        audioEngine.activeTrackId = _activeTrack.value
    }

    private fun updateSelectedPadConfig() {
        val config = audioEngine.getPadConfig(_selectedBank.value, _selectedPadIndex.value)
        _selectedPadConfig.value = config
    }

    // --- Pad Parameter Fine-tuning (Room & Live updates) ---

    fun updateSelectedPadParams(
        pitch: Float? = null,
        decay: Float? = null,
        attack: Float? = null,
        filterCutoff: Float? = null,
        level: Float? = null,
        pan: Float? = null
    ) {
        val config = audioEngine.getPadConfig(_selectedBank.value, _selectedPadIndex.value) ?: return

        pitch?.let { config.pitch = it.coerceIn(0.2f, 3.0f) }
        decay?.let { config.decay = it.coerceIn(0.1f, 3.0f) }
        attack?.let { config.attack = it.coerceIn(0.0f, 1.0f) }
        filterCutoff?.let { config.filterCutoff = it.coerceIn(0.05f, 1.0f) }
        level?.let { config.level = it.coerceIn(0.0f, 1.0f) }
        pan?.let { config.pan = it.coerceIn(-1.0f, 1.0f) }

        _selectedPadConfig.value = config

        // Save immediately to Database
        viewModelScope.launch(Dispatchers.IO) {
            repository.savePadSetting(
                PadSettingsEntity(
                    id = "${config.bank}_${config.padIndex}",
                    bank = config.bank,
                    padIndex = config.padIndex,
                    pitch = config.pitch,
                    decay = config.decay,
                    attack = config.attack,
                    filterCutoff = config.filterCutoff,
                    level = config.level,
                    pan = config.pan
                )
            )
        }
    }

    // --- Solo & Mute Operations ---

    fun togglePadMute(bank: String, padIndex: Int) {
        val key = "${bank}_${padIndex}"
        val curMutes = _padMutedStates.value.toMutableMap()
        val newState = !(curMutes[key] ?: false)
        curMutes[key] = newState
        _padMutedStates.value = curMutes
    }

    fun clearAllMutes() {
        _padMutedStates.value = emptyMap()
    }

    // --- Transport Controls ---

    fun pressPlay() {
        if (audioEngine.isPlaying) {
            audioEngine.isPlaying = false
        } else {
            audioEngine.isRecording = false
            audioEngine.isOverdubbing = false
            audioEngine.isPlaying = true
        }
        syncTransportStates()
    }

    fun pressPlayStart() {
        audioEngine.isPlaying = false
        // Wait 10ms to let render loop clear
        viewModelScope.launch {
            audioEngine.isRecording = false
            audioEngine.isOverdubbing = false
            audioEngine.isPlaying = true
            syncTransportStates()
        }
    }

    fun pressStop() {
        audioEngine.isPlaying = false
        audioEngine.isRecording = false
        audioEngine.isOverdubbing = false
        syncTransportStates()
    }

    fun pressRec() {
        if (audioEngine.isRecording) {
            audioEngine.isRecording = false
            audioEngine.isOverdubbing = false
        } else {
            audioEngine.isOverdubbing = false
            audioEngine.isRecording = true
            audioEngine.isPlaying = true
        }
        syncTransportStates()
    }

    fun pressOverdub() {
        if (audioEngine.isOverdubbing) {
            audioEngine.isOverdubbing = false
        } else {
            audioEngine.isRecording = false
            audioEngine.isOverdubbing = true
            audioEngine.isPlaying = true
        }
        syncTransportStates()
    }

    fun toggleMetronome() {
        val state = !_metronomeOn.value
        _metronomeOn.value = state
        audioEngine.metronomeOn = state
    }

    fun toggleQuantize() {
        val state = !_quantizeOn.value
        _quantizeOn.value = state
        audioEngine.quantizeOn = state
    }

    fun setBpm(newBpm: Float) {
        val clamped = newBpm.coerceIn(40.0f, 240.0f)
        _bpm.value = clamped
        audioEngine.bpm = clamped
    }

    fun setBars(newBars: Int) {
        val clamped = newBars.coerceIn(1, 4)
        _bars.value = clamped
        audioEngine.bars = clamped
    }

    private fun syncTransportStates() {
        _isPlaying.value = audioEngine.isPlaying
        _isRecording.value = audioEngine.isRecording
        _isOverdubbing.value = audioEngine.isOverdubbing
    }

    // --- Sequencer Editing ---

    fun eraseSelectedPadNotes() {
        audioEngine.clearPadNotes(_selectedPadIndex.value, _selectedBank.value)
    }

    fun clearSequencer() {
        audioEngine.clearPattern()
    }

    // --- Pattern Save / Load ---

    fun savePatternToDatabase(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val header = PatternEntity(
                name = name,
                bpm = _bpm.value,
                bars = _bars.value
            )

            // Convert Note struct variables to NoteEntryEntities
            val noteEntities = audioEngine.recordedNotes.map {
                NoteEventEntity(
                    patternId = 0, // will be generated by DB
                    padIndex = it.padIndex,
                    bank = it.bank,
                    sampleOffset = it.sampleOffset,
                    velocity = it.velocity,
                    trackId = it.trackId
                )
            }

            val patternId = repository.savePattern(header, noteEntities)
            _loadedPatternId.value = patternId
        }
    }

    fun loadPatternFromDatabase(patternId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // Find pattern header
            val patternList = savedPatterns.value
            val match = patternList.find { it.id == patternId } ?: return@launch

            // Load all notes from database
            val noteEntities = repository.getNotesForPattern(patternId)

            // Stop sequencer playback to swap notes safely
            audioEngine.isPlaying = false
            audioEngine.recordedNotes.clear()

            // Repopulate note events
            val notes = noteEntities.map {
                MpcNote(
                    padIndex = it.padIndex,
                    bank = it.bank,
                    sampleOffset = it.sampleOffset,
                    velocity = it.velocity,
                    trackId = it.trackId
                )
            }
            audioEngine.recordedNotes.addAll(notes)

            // Sync visual states
            _bpm.value = match.bpm
            audioEngine.bpm = match.bpm

            _bars.value = match.bars
            audioEngine.bars = match.bars

            _loadedPatternId.value = patternId
            syncTransportStates()
        }
    }

    fun deletePatternFromDatabase(patternId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePattern(patternId)
            if (_loadedPatternId.value == patternId) {
                _loadedPatternId.value = null
                audioEngine.clearPattern()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.shutdown()
    }
}

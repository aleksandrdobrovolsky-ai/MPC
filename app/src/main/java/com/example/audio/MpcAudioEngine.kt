package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*
import kotlin.random.Random

/**
 * High-performance, Real-time audio synthesizer and looper engine in the style of Akai MPC 2500.
 * Synthesizes vintage-style lo-fi instruments completely in Kotlin, bypassing standard files.
 */
class MpcAudioEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SIZE = 512 // ~11.6ms buffer latency for snappy tap response
    }

    // --- Pad Configuration Data ---
    data class PadConfig(
        val bank: String,
        val padIndex: Int,
        val label: String,
        val descName: String,
        val soundType: SoundType,
        val baseFreq: Float, // for synth sounds
        var pitch: Float = 1.0f,     // 0.2f to 3.0f (tuning)
        var decay: Float = 1.0f,     // 0.1f to 3.0f (length)
        var attack: Float = 0.0f,    // 0.0f to 1.0f (fade in)
        var filterCutoff: Float = 1.0f, // 0.05f to 1.0f (cutoff freq resonance)
        var level: Float = 0.8f,     // 0.0f to 1.0f (level)
        var pan: Float = 0.0f        // -1.0f to 1.0f (panning)
    )

    enum class SoundType {
        KICK, SNARE, HH_CLOSED, HH_OPEN, CLAP, RIMSHOT, TOM, COWBELL, 
        SUB_BASS, SYNTH_CHORD, VINYL_CRACKLE, VOX_ECHO, METAL_HIT, NOISE_BLIP
    }

    // Note event for recorded sequencer patterns
    data class MpcNote(
        val padIndex: Int,
        val bank: String,
        val sampleOffset: Long, // sample offset inside the loop duration
        val velocity: Float = 1.0f,
        val trackId: Int = 1
    )

    // Struct for an actively playing sound voice
    private class ActiveVoice(
        val padIndex: Int,
        val bank: String,
        val samples: FloatArray,
        var playhead: Double, // dynamic fractional index supporting pitching
        val pitch: Float,
        val level: Float,
        val attackSamples: Float,
        val decaySamples: Float,
        val filterCutoff: Float,
        val pan: Float,
        var ageSamples: Long = 0L,
        // Low pass filter state variables
        var filterYLeft: Float = 0.0f,
        var filterYRight: Float = 0.0f
    )

    // --- State Variables ---
    val padConfigs = CopyOnWriteArrayList<PadConfig>()
    val recordedNotes = CopyOnWriteArrayList<MpcNote>()
    
    var activeTrackId = 1 // MPC has multi-track support (Tracks 1-4)
    var bpm = 90.0f
        set(value) {
            field = value.coerceIn(40.0f, 240.0f)
            recalculateLoop()
        }
    var bars = 1 // pattern bar lengths: 1, 2, or 4 bars
        set(value) {
            field = value.coerceIn(1, 4)
            recalculateLoop()
        }

    var isPlaying = false
    var isRecording = false
    var isOverdubbing = false
    var metronomeOn = true
    var quantizeOn = true // Snaps recording to nearest 1/16th note

    // Loop sizing based on BPM and Bar Length
    private var loopLengthSamples = 0L
    private var playheadSamples = 0L

    // Metronome samples
    private var metroClickHi = FloatArray(0)
    private var metroClickLo = FloatArray(0)

    // Pre-synthesized wave caches for each SoundType
    private val synthesizedWaveforms = mutableMapOf<SoundType, MutableMap<Int, FloatArray>>()

    // Thread management
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private val activeVoices = CopyOnWriteArrayList<ActiveVoice>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // 1. Generate Metronome Click Sounds
        generateMetronomeClicks()
        
        // 2. Pre-generate and cache the basic sound waves to maximize real-time speed
        preGenerateSounds()

        // 3. Initialize default Pad Configurations for 4 Banks (A, B, C, D)
        initializeDefaultKits()

        // 4. Recalculate loop samples initially
        recalculateLoop()

        // 5. Fire up the audio rendering thread
        startAudioTrack()
    }

    private fun generateMetronomeClicks() {
        val clickLength = 2000
        metroClickHi = FloatArray(clickLength)
        metroClickLo = FloatArray(clickLength)
        // High click: 1500Hz sine wave decay
        for (i in 0 until clickLength) {
            val t = i.toFloat() / SAMPLE_RATE
            val amp = exp(-40.0 * t).toFloat()
            metroClickHi[i] = sin(2.0f * PI.toFloat() * 1500.0f * t) * amp * 0.4f
        }
        // Low click: 800Hz sine wave decay
        for (i in 0 until clickLength) {
            val t = i.toFloat() / SAMPLE_RATE
            val amp = exp(-40.0 * t).toFloat()
            metroClickLo[i] = sin(2.0f * PI.toFloat() * 800.0f * t) * amp * 0.3f
        }
    }

    private fun preGenerateSounds() {
        for (type in SoundType.values()) {
            val pitchIndexMap = mutableMapOf<Int, FloatArray>()
            // Generate multiple octave versions based on indexes
            for (idx in 1..16) {
                pitchIndexMap[idx] = generateWaveform(type, idx)
            }
            synthesizedWaveforms[type] = pitchIndexMap
        }
    }

    private fun generateWaveform(type: SoundType, index: Int): FloatArray {
        val length = (SAMPLE_RATE * 1.5).toInt() // max 1.5 seconds per sample
        val buffer = FloatArray(length)
        val rand = Random(index * 100)

        when (type) {
            SoundType.KICK -> {
                // Pitch bent exponential envelope
                val duration = 0.5f
                val size = (SAMPLE_RATE * duration).toInt()
                val kickWave = FloatArray(size)
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Pitch slides from 150Hz to 45Hz
                    val f = 45.0f + 105.0f * exp(-60.0f * t)
                    val sine = sin(2.0f * PI.toFloat() * f * t)
                    val amp = exp(-7.5f * t)
                    // Click component for punch on attack
                    val click = sin(2.0f * PI.toFloat() * 2000.0f * t) * exp(-1500.0f * t) * 0.3f
                    kickWave[i] = (sine * amp + click).coerceIn(-1.0f, 1.0f)
                }
                return kickWave
            }
            SoundType.SNARE -> {
                val duration = 0.4f
                val size = (SAMPLE_RATE * duration).toInt()
                val snareWave = FloatArray(size)
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Body is a sine pulse at 180Hz
                    val body = sin(2.0f * PI.toFloat() * 180.0f * t) * exp(-40.0f * t) * 0.4f
                    // Snare tail is filtered noise
                    val noiseSample = rand.nextFloat() * 2.0f - 1.0f
                    val noiseTail = noiseSample * exp(-14.0f * t) * 0.6f
                    snareWave[i] = (body + noiseTail).coerceIn(-1.0f, 1.0f)
                }
                return snareWave
            }
            SoundType.HH_CLOSED -> {
                val duration = 0.06f
                val size = (SAMPLE_RATE * duration).toInt()
                val hhWave = FloatArray(size)
                var lastNoise = 0.0f
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val rawNoise = rand.nextFloat() * 2.0f - 1.0f
                    // Simple High pass filter: subtract adjacent noise samples
                    val hiPass = rawNoise - lastNoise
                    lastNoise = rawNoise
                    hhWave[i] = hiPass * exp(-60.0f * t) * 0.4f
                }
                return hhWave
            }
            SoundType.HH_OPEN -> {
                val duration = 0.35f
                val size = (SAMPLE_RATE * duration).toInt()
                val hhWave = FloatArray(size)
                var lastNoise = 0.0f
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val rawNoise = rand.nextFloat() * 2.0f - 1.0f
                    val hiPass = rawNoise - lastNoise
                    lastNoise = rawNoise
                    hhWave[i] = hiPass * exp(-12.0f * t) * 0.35f
                }
                return hhWave
            }
            SoundType.CLAP -> {
                val duration = 0.45f
                val size = (SAMPLE_RATE * duration).toInt()
                val clapWave = FloatArray(size)
                // Clap is characterized by 3 small fast decaying noise bursts, and then a large decaying burst
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val noise = rand.nextFloat() * 2.0f - 1.0f
                    var env = 0.0f
                    when {
                        t < 0.01f -> env = exp(-300.0f * t) * 0.3f
                        t < 0.02f -> env = exp(-300.0f * (t - 0.01f)) * 0.3f
                        t < 0.03f -> env = exp(-300.0f * (t - 0.02f)) * 0.3f
                        else -> env = exp(-11.0f * (t - 0.03f)) * 0.6f
                    }
                    clapWave[i] = noise * env
                }
                return clapWave
            }
            SoundType.RIMSHOT -> {
                val duration = 0.08f
                val size = (SAMPLE_RATE * duration).toInt()
                val rimWave = FloatArray(size)
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Metal rim striking wood shell: detuned modulated dual sines
                    val sine1 = sin(2.0f * PI.toFloat() * 1200.0f * t)
                    val sine2 = sin(2.0f * PI.toFloat() * 270.0f * t)
                    val metallic = (sine1 * 0.6f + sine2 * 0.4f) * exp(-45.0f * t)
                    rimWave[i] = metallic.coerceIn(-1.0f, 1.0f)
                }
                return rimWave
            }
            SoundType.TOM -> {
                val duration = 0.6f
                val size = (SAMPLE_RATE * duration).toInt()
                val tomWave = FloatArray(size)
                val pitchFactor = 1.0f - (index * 0.05f) // customize base tom pitch based on pad order
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val f = (180.0f * pitchFactor) * exp(-12.0f * t)
                    val sine = sin(2.0f * PI.toFloat() * f * t)
                    tomWave[i] = sine * exp(-6.0f * t) * 0.6f
                }
                return tomWave
            }
            SoundType.COWBELL -> {
                val duration = 0.5f
                val size = (SAMPLE_RATE * duration).toInt()
                val bellWave = FloatArray(size)
                // 808 Cowbell: 2 metallic square waves. Detuned at F=540Hz and F2=800Hz
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val s1 = if (sin(2.0f * PI.toFloat() * 540.0f * t) > 0.0f) 1.0f else -1.0f
                    val s2 = if (sin(2.0f * PI.toFloat() * 800.0f * t) > 0.0f) 1.0f else -1.0f
                    val env = exp(-12.0f * t)
                    bellWave[i] = (s1 * 0.5f + s2 * 0.5f) * env * 0.25f
                }
                return bellWave
            }
            SoundType.SUB_BASS -> {
                val duration = 1.2f
                val size = (SAMPLE_RATE * duration).toInt()
                val bassWave = FloatArray(size)
                // C and Eb/G chromatic notes
                val notes = doubleArrayOf(32.7, 36.7, 41.2, 43.7, 49.0, 55.0, 61.7, 65.4, 73.4, 82.4, 87.3, 98.0, 110.0, 123.5, 130.8, 146.8)
                val baseFreq = notes[(index - 1) % notes.size].toFloat()
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Soft sine wave mixed with a soft triangle to add retro analog warmth
                    val sine = sin(2.0f * PI.toFloat() * baseFreq * t)
                    // Triangle wave
                    val tri = 2.0f * abs(2.0f * ((t * baseFreq) % 1.0f) - 1.0f) - 1.0f
                    val mixed = sine * 0.7f + tri * 0.3f
                    val env = exp(-2.5f * t)
                    bassWave[i] = mixed * env * 0.5f
                }
                return bassWave
            }
            SoundType.SYNTH_CHORD -> {
                val duration = 1.5f
                val size = (SAMPLE_RATE * duration).toInt()
                val chordWave = FloatArray(size)
                // Warm minor chord. Base MIDI note: index-dependent scale mapping
                val scaleFreqs = floatArrayOf(
                    130.81f, // C3
                    146.83f, // D3
                    155.56f, // Eb3
                    174.61f, // F3
                    196.00f, // G3
                    207.65f, // Ab3
                    233.08f, // Bb3
                    261.63f, // C4
                    293.66f, // D4
                    311.13f, // Eb4
                    349.23f, // F4
                    392.00f, // G4
                    415.30f, // Ab4
                    466.16f, // Bb4
                    523.25f, // C5
                    587.33f  // D5
                )
                val root = scaleFreqs[(index - 1).coerceIn(0, 15)]
                val minThird = root * 1.1962f   // Eb relative to C
                val fifth = root * 1.4983f      // G relative to C
                val seventh = root * 1.7818f    // Bb relative to C

                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Mix 4 sines to make a gorgeous warm lofi jazz chord
                    val s1 = sin(2.0f * PI.toFloat() * root * t)
                    val s2 = sin(2.0f * PI.toFloat() * minThird * t)
                    val s3 = sin(2.0f * PI.toFloat() * fifth * t)
                    val s4 = sin(2.0f * PI.toFloat() * seventh * t)
                    val mixed = (s1 * 0.35f + s2 * 0.25f + s3 * 0.20f + s4 * 0.20f)
                    val env = exp(-1.8f * t) // slow decaying pad-like chords
                    chordWave[i] = mixed * env * 0.5f
                }
                return chordWave
            }
            SoundType.VINYL_CRACKLE -> {
                val duration = 1.0f
                val size = (SAMPLE_RATE * duration).toInt()
                val crackleWave = FloatArray(size)
                for (i in 0 until size) {
                    // Continuous analog floor and random dirt bursts
                    val hum = sin(2.0f * PI.toFloat() * 50.0f * (i.toFloat() / SAMPLE_RATE)) * 0.02f
                    val white = (rand.nextFloat() * 2.0f - 1.0f) * 0.005f
                    // Decaying crack pops
                    var pop = 0.0f
                    if (rand.nextFloat() < 0.0003f) {
                        pop = (rand.nextFloat() * 2.0f - 1.0f) * 0.4f
                    }
                    crackleWave[i] = (hum + white + pop).coerceIn(-1.0f, 1.0f)
                }
                return crackleWave
            }
            SoundType.VOX_ECHO -> {
                val duration = 1.2f
                val size = (SAMPLE_RATE * duration).toInt()
                val voxWave = FloatArray(size)
                // Filtered vocal formant sweep
                val vocalFreq = 700.0f + 250.0f * sin(2.0f * PI.toFloat() * 2.0f * (index.toFloat() / 16.0f))
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Sawtooth formant resonance
                    val saw = 2.0f * ((t * vocalFreq) % 1.0f) - 1.0f
                    val env = exp(-8.5f * t)
                    // Generate echoes manually
                    val echo = if (i > 10000) voxWave[i - 10000] * 0.45f else 0.0f
                    voxWave[i] = (saw * env + echo).coerceIn(-1.0f, 1.0f) * 0.35f
                }
                return voxWave
            }
            SoundType.METAL_HIT -> {
                val duration = 0.8f
                val size = (SAMPLE_RATE * duration).toInt()
                val metalWave = FloatArray(size)
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Detroit metal fm hits: 730Hz modulated by 2000Hz
                    val mod = sin(2.0f * PI.toFloat() * 2000.0f * t) * 3.5f
                    val carrier = sin(2.0f * PI.toFloat() * 730.0f * t + mod)
                    metalWave[i] = carrier * exp(-10.0f * t) * 0.4f
                }
                return metalWave
            }
            SoundType.NOISE_BLIP -> {
                val duration = 0.15f
                val size = (SAMPLE_RATE * duration).toInt()
                val blipWave = FloatArray(size)
                for (i in 0 until size) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Short decaying retro chiptune SFX
                    val f = 600.0f * (1.0f + 4.0f * index * exp(-50.0f * t))
                    val sq = if (sin(2.0f * PI.toFloat() * f * t) > 0) 1.0f else -1.0f
                    blipWave[i] = sq * exp(-30.0f * t) * 0.2f
                }
                return blipWave
            }
        }
    }

    private fun initializeDefaultKits() {
        // Create 16 default pad mappings for Bank A (Hip Hop)
        for (i in 1..16) {
            val (desc, type, freq) = getBankAInstrument(i)
            padConfigs.add(PadConfig("A", i, "A${String.format("%02d", i)}", desc, type, freq))
        }
        // Create Bank B (Electro Engine)
        for (i in 1..16) {
            val (desc, type, freq) = getBankBInstrument(i)
            padConfigs.add(PadConfig("B", i, "B${String.format("%02d", i)}", desc, type, freq))
        }
        // Create Bank C (Lofi Lounge Jazz)
        for (i in 1..16) {
            val (desc, type, freq) = getBankCInstrument(i)
            padConfigs.add(PadConfig("C", i, "C${String.format("%02d", i)}", desc, type, freq))
        }
        // Create Bank D (Vintage Retro Chiptunes)
        for (i in 1..16) {
            val (desc, type, freq) = getBankDInstrument(i)
            padConfigs.add(PadConfig("D", i, "D${String.format("%02d", i)}", desc, type, freq))
        }
    }

    private fun getBankAInstrument(pad: Int): Triple<String, SoundType, Float> {
        return when (pad) {
            1 -> Triple("MPC Fat Kick", SoundType.KICK, 50.0f)
            2 -> Triple("90s Street Snare", SoundType.SNARE, 200.0f)
            3 -> Triple("Lofi Closed Hat", SoundType.HH_CLOSED, 12000.0f)
            4 -> Triple("Lofi Open Hat", SoundType.HH_OPEN, 12000.0f)
            5 -> Triple("Vintage Clap", SoundType.CLAP, 1000.0f)
            6 -> Triple("Acoustic Rimshot", SoundType.RIMSHOT, 900.0f)
            7 -> Triple("Hand Conga Low", SoundType.TOM, 120.0f)
            8 -> Triple("MPC Cowbell", SoundType.COWBELL, 540.0f)
            9 -> Triple("Low Pitch Tom", SoundType.TOM, 80.0f)
            10 -> Triple("Mid Pitch Tom", SoundType.TOM, 140.0f)
            11 -> Triple("High Pitch Tom", SoundType.TOM, 220.0f)
            12 -> Triple("Vox Echo Hit", SoundType.VOX_ECHO, 650.0f)
            13 -> Triple("Warm Bass C2", SoundType.SUB_BASS, 65.41f)
            14 -> Triple("Warm Bass G2", SoundType.SUB_BASS, 98.00f)
            15 -> Triple("EP Rhodes Chord C3", SoundType.SYNTH_CHORD, 130.81f)
            16 -> Triple("EP Rhodes Chord G3", SoundType.SYNTH_CHORD, 196.00f)
            else -> Triple("Percussion", SoundType.KICK, 100.0f)
        }
    }

    private fun getBankBInstrument(pad: Int): Triple<String, SoundType, Float> {
        return when (pad) {
            1 -> Triple("Electro SubKick", SoundType.KICK, 45.0f)
            2 -> Triple("Techno Noise Snare", SoundType.SNARE, 220.0f)
            3 -> Triple("Cyber Tight Hat", SoundType.HH_CLOSED, 14000.0f)
            4 -> Triple("Cyber Open Hat", SoundType.HH_OPEN, 14000.0f)
            5 -> Triple("Synthwave HandClap", SoundType.CLAP, 1100.0f)
            6 -> Triple("FM Rim Shot", SoundType.RIMSHOT, 950.0f)
            7 -> Triple("Retro Metal Hit", SoundType.METAL_HIT, 800.0f)
            8 -> Triple("FM Bell Shot", SoundType.COWBELL, 580.0f)
            9 -> Triple("Sub Tom Low", SoundType.TOM, 75.0f)
            10 -> Triple("Sub Tom Mid", SoundType.TOM, 130.0f)
            11 -> Triple("Synth Vox Sweep", SoundType.VOX_ECHO, 700.0f)
            12 -> Triple("Blip SFX 1", SoundType.NOISE_BLIP, 440.0f)
            13 -> Triple("Cyber Bass C1", SoundType.SUB_BASS, 32.70f)
            14 -> Triple("Cyber Bass Eb1", SoundType.SUB_BASS, 38.89f)
            15 -> Triple("Chiptune Chord C", SoundType.SYNTH_CHORD, 261.63f)
            16 -> Triple("Chiptune Chord Eb", SoundType.SYNTH_CHORD, 311.13f)
            else -> Triple("Percussion", SoundType.KICK, 100.0f)
        }
    }

    private fun getBankCInstrument(pad: Int): Triple<String, SoundType, Float> {
        // Acoustic Lounge Brush Style
        return when (pad) {
            1 -> Triple("Soft Jazz Kick", SoundType.KICK, 52.0f)
            2 -> Triple("Brush Rim Snare", SoundType.SNARE, 175.0f)
            3 -> Triple("Soft Closed Hat", SoundType.HH_CLOSED, 11000.0f)
            4 -> Triple("Acoustic Ride Cym", SoundType.HH_OPEN, 10000.0f)
            5 -> Triple("Acoustic Woodclap", SoundType.CLAP, 900.0f)
            6 -> Triple("Jazz Rimshot", SoundType.RIMSHOT, 850.0f)
            7 -> Triple("Muted Jazz Conga", SoundType.TOM, 160.0f)
            8 -> Triple("Lounge Cowbell", SoundType.COWBELL, 512.0f)
            9 -> Triple("Lounge Tom Low", SoundType.TOM, 90.0f)
            10 -> Triple("Lounge Tom High", SoundType.TOM, 160.0f)
            11 -> Triple("Vinyl Crackle Floor", SoundType.VINYL_CRACKLE, 0.0f)
            12 -> Triple("Vinyl Pop Noise", SoundType.VINYL_CRACKLE, 0.0f)
            13 -> Triple("Jazz Bass C", SoundType.SUB_BASS, 65.41f)
            14 -> Triple("Jazz Bass F", SoundType.SUB_BASS, 87.31f)
            15 -> Triple("Lofi Piano Chord C-6", SoundType.SYNTH_CHORD, 130.81f)
            16 -> Triple("Lofi Piano Chord F-9", SoundType.SYNTH_CHORD, 174.61f)
            else -> Triple("Percussion", SoundType.KICK, 100.0f)
        }
    }

    private fun getBankDInstrument(pad: Int): Triple<String, SoundType, Float> {
        // Chiptune / Retro Gaming
        return when (pad) {
            1 -> Triple("8-Bit Blip Kick", SoundType.KICK, 55.0f)
            2 -> Triple("8-Bit Noise Snare", SoundType.SNARE, 250.0f)
            3 -> Triple("8-Bit Metal Hat", SoundType.HH_CLOSED, 15000.0f)
            4 -> Triple("8-Bit Laser Sweep", SoundType.HH_OPEN, 14000.0f)
            5 -> Triple("Blip SFX Left", SoundType.NOISE_BLIP, 300.0f)
            6 -> Triple("Blip SFX Right", SoundType.NOISE_BLIP, 600.0f)
            7 -> Triple("Square Tom Low", SoundType.TOM, 100.0f)
            8 -> Triple("Square Tom High", SoundType.TOM, 200.0f)
            9 -> Triple("NES Metal Ring", SoundType.METAL_HIT, 900.0f)
            10 -> Triple("Game Cowbell", SoundType.COWBELL, 600.0f)
            11 -> Triple("8-Bit Vox Echo", SoundType.VOX_ECHO, 800.0f)
            12 -> Triple("Chiptune Alarm", SoundType.NOISE_BLIP, 1200.0f)
            13 -> Triple("Chiptune Lead C", SoundType.SYNTH_CHORD, 523.25f)
            14 -> Triple("Chiptune Lead Eb", SoundType.SYNTH_CHORD, 622.25f)
            15 -> Triple("Chiptune Lead F", SoundType.SYNTH_CHORD, 698.46f)
            16 -> Triple("Chiptune Lead G", SoundType.SYNTH_CHORD, 783.99f)
            else -> Triple("Percussion", SoundType.KICK, 100.0f)
        }
    }

    private fun recalculateLoop() {
        val beatDurationSec = 60.0f / bpm
        val barDurationSec = 4.0f * beatDurationSec
        loopLengthSamples = (SAMPLE_RATE * barDurationSec * bars).toLong()
        if (playheadSamples >= loopLengthSamples) {
            playheadSamples = 0L
        }
    }

    // --- Action Methods ---

    /**
     * Triggers a pad hit in real-time, instantly spinning up an dynamic voice.
     */
    fun triggerPad(bank: String, padIndex: Int, velocity: Float = 1.0f) {
        val config = getPadConfig(bank, padIndex) ?: return
        val rawSample = synthesizedWaveforms[config.soundType]?.get(padIndex) ?: return

        // Set up parameters for this voice
        val durationSamples = rawSample.size.toFloat() * config.decay
        val attackSamples = SAMPLE_RATE * (config.attack * 0.25f) // attack up to 250ms max

        val voice = ActiveVoice(
            padIndex = padIndex,
            bank = bank,
            samples = rawSample,
            playhead = 0.0,
            pitch = config.pitch,
            level = config.level * velocity,
            attackSamples = attackSamples,
            decaySamples = durationSamples,
            filterCutoff = config.filterCutoff,
            pan = config.pan
        )

        activeVoices.add(voice)

        // If recording or overdubbing, record the note sequence!
        if (isPlaying && (isRecording || isOverdubbing)) {
            var sampleOffset = playheadSamples
            
            if (quantizeOn) {
                // Snap note to the nearest 1/16th note boundary
                val stepsInPattern = 16 * bars
                val stepSamples = loopLengthSamples.toDouble() / stepsInPattern
                val rawStep = playheadSamples.toDouble() / stepSamples
                val closestStep = round(rawStep).toLong()
                sampleOffset = (closestStep * stepSamples).toLong() % loopLengthSamples
            }

            // Prevent duplicate records on identical offsets to avoid phase issues
            val isDuplicate = recordedNotes.any { 
                it.padIndex == padIndex && it.bank == bank && abs(it.sampleOffset - sampleOffset) < 150 && it.trackId == activeTrackId
            }
            if (!isDuplicate) {
                recordedNotes.add(MpcNote(padIndex, bank, sampleOffset, velocity, activeTrackId))
            }
        }
    }

    fun getPadConfig(bank: String, padIndex: Int): PadConfig? {
        return padConfigs.find { it.bank == bank && it.padIndex == padIndex }
    }

    fun clearPattern() {
        recordedNotes.clear()
    }

    fun clearPadNotes(padIndex: Int, bank: String) {
        recordedNotes.removeAll { it.padIndex == padIndex && it.bank == bank && it.trackId == activeTrackId }
    }

    // --- Audio Rendering Thread ---

    private fun startAudioTrack() {
        // Initialize AudioTrack for low-latency floating PCM stereo streaming
        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val optimalBufferSize = max(minBufSize, BUFFER_SIZE * 2 * 4) // floating data is 4 bytes

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .build()
                )
                .setBufferSizeInBytes(optimalBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("MpcAudioEngine", "Error initializing AudioTrack: ${e.message}")
        }

        // Loop the low-latency thread
        recordingJob = scope.launch(Dispatchers.Default) {
            val audioBuffer = FloatArray(BUFFER_SIZE * 2) // Stereo buffer (Left/Right interleaved)

            while (isActive) {
                if (audioTrack == null || audioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
                    delay(300)
                    continue
                }

                // Fill the buffer with mixed PCM data
                val frameCount = BUFFER_SIZE
                val outLeft = FloatArray(frameCount)
                val outRight = FloatArray(frameCount)

                // 1. Process Sequencer Notes (If playing)
                if (isPlaying) {
                    val nextPlayhead = playheadSamples + frameCount
                    
                    // Trigger metronome click at beat boundaries (quarter notes)
                    if (metronomeOn) {
                        val beatIntervalSamples = (SAMPLE_RATE * (60.0f / bpm)).toLong()
                        for (i in 0 until frameCount) {
                            val absolutePos = playheadSamples + i
                            if (absolutePos % beatIntervalSamples == 0L) {
                                val beatIndex = (absolutePos / beatIntervalSamples) % 4
                                val metroSample = if (beatIndex == 0L) metroClickHi else metroClickLo
                                
                                // Spin up voice for metronome click (padIndex = -1 to bypass filter/mutes)
                                val metroVoice = ActiveVoice(
                                    padIndex = -1, bank = "METRO", samples = metroSample, playhead = 0.0,
                                    pitch = 1.0f, level = 0.7f, attackSamples = 0.0f, decaySamples = metroSample.size.toFloat(),
                                    filterCutoff = 1.0f, pan = 0.0f
                                )
                                activeVoices.add(metroVoice)
                            }
                        }
                    }

                    // Scan for recorded note events
                    for (note in recordedNotes) {
                        val offset = note.sampleOffset
                        
                        // Check if the note offset falls in the current buffer window [playhead, playhead + frameCount]
                        var isTriggered = false
                        var triggerBufferOffset = 0
                        
                        if (nextPlayhead <= loopLengthSamples) {
                            if (offset >= playheadSamples && offset < nextPlayhead) {
                                isTriggered = true
                                triggerBufferOffset = (offset - playheadSamples).toInt()
                            }
                        } else {
                            // Wrapping wrap-around case
                            val remainder = loopLengthSamples - playheadSamples
                            if (offset >= playheadSamples && offset < loopLengthSamples) {
                                isTriggered = true
                                triggerBufferOffset = (offset - playheadSamples).toInt()
                            } else if (offset >= 0 && offset < (nextPlayhead % loopLengthSamples)) {
                                isTriggered = true
                                triggerBufferOffset = (offset + remainder).toInt()
                            }
                        }

                        if (isTriggered) {
                            val config = getPadConfig(note.bank, note.padIndex)
                            if (config != null) {
                                val rawSample = synthesizedWaveforms[config.soundType]?.get(note.padIndex)
                                if (rawSample != null) {
                                    val durationSamples = rawSample.size.toFloat() * config.decay
                                    val attackSamples = SAMPLE_RATE * (config.attack * 0.25f)
                                    val triggerLatencyOffset = triggerBufferOffset.toDouble()

                                    val voice = ActiveVoice(
                                        padIndex = note.padIndex,
                                        bank = note.bank,
                                        samples = rawSample,
                                        playhead = -triggerLatencyOffset * config.pitch, // set dynamic offset to delay sample sound slightly!
                                        pitch = config.pitch,
                                        level = config.level * note.velocity,
                                        attackSamples = attackSamples,
                                        decaySamples = durationSamples,
                                        filterCutoff = config.filterCutoff,
                                        pan = config.pan
                                    )
                                    activeVoices.add(voice)
                                }
                            }
                        }
                    }

                    // Advance master playhead
                    playheadSamples = (playheadSamples + frameCount) % loopLengthSamples
                } else {
                    playheadSamples = 0L
                }

                // 2. Mix Active Playing Voices
                val voicesToProcess = activeVoices
                val finishedVoices = mutableListOf<ActiveVoice>()

                for (voice in voicesToProcess) {
                    for (i in 0 until frameCount) {
                        val currentPlayhead = voice.playhead + (i * voice.pitch)
                        
                        // Wait if voice is latency-delayed (playhead < 0)
                        if (currentPlayhead < 0.0) {
                            continue
                        }

                        val truncIdx = currentPlayhead.toInt()
                        if (truncIdx >= voice.samples.size - 1) {
                            finishedVoices.add(voice)
                            break
                        }

                        // Linear interpolation
                        val fract = currentPlayhead - truncIdx
                        val s0 = voice.samples[truncIdx]
                        val s1 = voice.samples[truncIdx + 1]
                        val rawOutput = s0 + fract.toFloat() * (s1 - s0)

                        // Apply Amplitude Envelopes (Attack + Decay)
                        val currAge = voice.ageSamples + i
                        var env = 1.0f

                        // Attack portion (gain ramp up)
                        if (voice.attackSamples > 0f && currAge < voice.attackSamples) {
                            env *= (currAge.toFloat() / voice.attackSamples)
                        }

                        // Exponential decay envelope portion
                        val decayFactor = currAge.toFloat() / voice.decaySamples
                        if (decayFactor > 0.0f) {
                            env *= exp(-5.0f * decayFactor).coerceIn(0.0f, 1.0f)
                        }

                        // Silence check to prune early
                        if (env < 0.0005f) {
                            finishedVoices.add(voice)
                            break
                        }

                        var filteredSample = rawOutput * env * voice.level

                        // Apply 1st order Low-pass resonant filter in real time (except for Metronome Ticks)
                        if (voice.padIndex != -1) {
                            val alpha = voice.filterCutoff.coerceIn(0.05f, 1.0f)
                            if (alpha < 0.98f) {
                                voice.filterYLeft = voice.filterYLeft + alpha * (filteredSample - voice.filterYLeft)
                                filteredSample = voice.filterYLeft
                            }
                        }

                        // Panning Left and Right channel mixing
                        val panL = (1.0f - voice.pan).coerceIn(0.0f, 1.0f)
                        val panR = (1.0f + voice.pan).coerceIn(0.0f, 1.0f)

                        outLeft[i] += filteredSample * panL
                        outRight[i] += filteredSample * panR
                    }

                    // Advance the playhead state for the next write cycle
                    voice.playhead += (frameCount * voice.pitch)
                    voice.ageSamples += frameCount
                }

                // Clean up finished streams
                if (finishedVoices.isNotEmpty()) {
                    activeVoices.removeAll(finishedVoices)
                }

                // Interleave left & right buffers and clip to prevent digital overlay distortion
                for (i in 0 until frameCount) {
                    val l = outLeft[i].coerceIn(-1.0f, 1.0f)
                    val r = outRight[i].coerceIn(-1.0f, 1.0f)
                    // Apply vintage analog soft clipping compression saturation!
                    val softL = if (l > 0.6f) 0.6f + 0.4f * tanh((l - 0.6f) / 0.4f).toFloat() else if (l < -0.6f) -0.6f + 0.4f * tanh((l + 0.6f) / 0.4f).toFloat() else l
                    val softR = if (r > 0.6f) 0.6f + 0.4f * tanh((r - 0.6f) / 0.4f).toFloat() else if (r < -0.6f) -0.6f + 0.4f * tanh((r + 0.6f) / 0.4f).toFloat() else r

                    audioBuffer[i * 2] = softL
                    audioBuffer[i * 2 + 1] = softR
                }

                // Write stereo Float frames to AudioTrack
                try {
                    audioTrack?.write(audioBuffer, 0, BUFFER_SIZE * 2, AudioTrack.WRITE_BLOCKING)
                } catch (e: Exception) {
                    Log.e("MpcAudioEngine", "Write failed: ${e.message}")
                }
            }
        }
    }

    fun shutdown() {
        try {
            isPlaying = false
            recordingJob?.cancel()
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e("MpcAudioEngine", "Shutdown error: ${e.message}")
        }
    }

    /**
     * Helper to retrieve active index positions to feed graphic progress loops.
     */
    fun getPlayheadFraction(): Float {
        if (loopLengthSamples == 0L || !isPlaying) return 0f
        return (playheadSamples.toFloat() / loopLengthSamples).coerceIn(0f, 1f)
    }

    fun getPlayheadStepIndex(): Int {
        if (loopLengthSamples == 0L) return 0
        val stepsInPattern = 16 * bars
        val stepSamples = loopLengthSamples.toDouble() / stepsInPattern
        return (playheadSamples.toDouble() / stepSamples).toInt() % stepsInPattern
    }
}

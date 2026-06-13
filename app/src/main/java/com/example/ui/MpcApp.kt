package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpcApp(
    viewModel: MpcViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val selectedBank by viewModel.selectedBank.collectAsState()
    val selectedPadIndex by viewModel.selectedPadIndex.collectAsState()
    val activeTrack by viewModel.activeTrack.collectAsState()
    val screenMode by viewModel.screenMode.collectAsState()
    val bpm by viewModel.bpm.collectAsState()
    val bars by viewModel.bars.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isOverdubbing by viewModel.isOverdubbing.collectAsState()
    val metronomeOn by viewModel.metronomeOn.collectAsState()
    val quantizeOn by viewModel.quantizeOn.collectAsState()
    val padMutedStates by viewModel.padMutedStates.collectAsState()
    val loadedPatternId by viewModel.loadedPatternId.collectAsState()
    val loopProgress by viewModel.recordingTicks.collectAsState()

    val padConfig by viewModel.selectedPadConfig.collectAsState()
    val savedPatterns by viewModel.savedPatterns.collectAsState()

    // Screen State variables
    val context = LocalContext.current
    var patternNameInput by remember { mutableStateOf("retro_beat_01") }
    var showEraseWarning by remember { mutableStateOf(false) }

    // Derive active step index (0-15) based on loop progress for the cathode terminal grid
    val activeStepIndex = (loopProgress * 16 * bars).toInt()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MpcColors.ChassisDark),
        containerColor = MpcColors.ChassisDark,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(MpcColors.ChassisDark, Color(0xFF25232A))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // --- ROW 1: Master Vintage LCD Display Panel ---
            RetroPixelLcdDisplay(
                mode = screenMode,
                bank = selectedBank,
                padIndex = selectedPadIndex,
                selectedSampleName = padConfig?.descName ?: "Empty Preset",
                padConfig = padConfig,
                bpm = bpm,
                bars = bars,
                track = activeTrack,
                isPlaying = isPlaying,
                isRecording = isRecording || isOverdubbing,
                loopProgress = loopProgress,
                beatFraction = activeStepIndex,
                metronomeOn = metronomeOn,
                quantizeOn = quantizeOn,
                activeNotesCount = viewModel.audioEngine.recordedNotes.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // --- ROW 2: Screen Mode Tabs (Selector toolbar matching LCD sub-options) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF25232A))
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    MpcScreenMode.SEQUENCER to "F1:SEQUENCER",
                    MpcScreenMode.SAMPLE_EDIT to "F2:KIT EDIT",
                    MpcScreenMode.PAD_MUTE to "F3:LIVE MUTE",
                    MpcScreenMode.DISK to "F4:CF DISK"
                ).forEach { (mode, label) ->
                    val isSelected = screenMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isSelected) Color(0xFF4A4458) else Color.Transparent)
                            .clickable { viewModel.setScreenMode(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFFD0BCFF) else Color(0xFFCCC2DC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- ROW 3: Context-Sensitive Editing Workspace ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
                    .border(1.5.dp, Color(0xFF49454F), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2930))
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    when (screenMode) {
                        MpcScreenMode.SEQUENCER -> {
                            // BPM parameters, metronome trigger, bar counts & quantization toggles
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Left Section: Tempo Dial
                                Column(
                                    modifier = Modifier.weight(1.1f),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "PATTERN CONTROL ENGINE",
                                        color = Color(0xFFCCC2DC),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Numeric readout
                                        Column {
                                            Text("TEMPO", color = Color(0xFF938F99), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                            Text("${bpm.toInt()} BPM", color = Color(0xFFD0BCFF), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Button(
                                                onClick = { viewModel.setBpm(bpm - 1f) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4458)),
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Text("-", color = Color(0xFFD0BCFF), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                            Button(
                                                onClick = { viewModel.setBpm(bpm + 1f) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4458)),
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Text("+", color = Color(0xFFD0BCFF), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }

                                    // Track Selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("ACTIVE TRACK", color = Color(0xFFCCC2DC), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            for (t in 1..4) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(if (activeTrack == t) MpcColors.ScreenAmberBacklight else Color(0xFF36343B))
                                                        .clickable { viewModel.setTrack(t) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = t.toString(),
                                                        color = if (activeTrack == t) Color(0xFF381E72) else Color(0xFFE6E1E5),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Loops Bar length
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("LOOP BARS", color = Color(0xFFCCC2DC), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(1, 2, 4).forEach { b ->
                                                Box(
                                                    modifier = Modifier
                                                        .padding(2.dp)
                                                        .width(32.dp)
                                                        .height(20.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (bars == b) Color(0xFF4A4458) else Color.Transparent)
                                                        .border(1.dp, if (bars == b) Color(0xFFD0BCFF) else Color.Transparent, RoundedCornerShape(4.dp))
                                                        .clickable { viewModel.setBars(b) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${b}B",
                                                        color = if (bars == b) Color(0xFFD0BCFF) else Color(0xFF938F99),
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Right Section: Jog Dial & Sequencer Actions
                                Column(
                                    modifier = Modifier.weight(0.9f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    HardwareJogDial(
                                        onIncrement = { viewModel.setBpm(bpm + 1f) },
                                        onDecrement = { viewModel.setBpm(bpm - 1f) },
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }
                            }
                        }

                        MpcScreenMode.SAMPLE_EDIT -> {
                            // Displays sliders to real-time repitch, muffle decay, or filter active pad
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Parameter sliders columns
                                Column(
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text(
                                        text = "SOUND PARAMETER SHAPING",
                                        color = Color(0xFFCCC2DC),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    
                                    val soundName = padConfig?.descName ?: "Empty Location"
                                    Text(
                                        text = "[PAD $selectedBank${String.format("%02d", selectedPadIndex)}]  $soundName",
                                        color = MpcColors.ScreenAmberBacklight,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        // Pitch Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("TUNING:", color = Color(0xFF938F99), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(55.dp))
                                            Slider(
                                                value = padConfig?.pitch ?: 1.0f,
                                                onValueChange = { viewModel.updateSelectedPadParams(pitch = it) },
                                                valueRange = 0.2f..2.5f,
                                                colors = SliderDefaults.colors(activeTrackColor = MpcColors.ScreenAmberBacklight, thumbColor = Color.White),
                                                modifier = Modifier.weight(1f).height(18.dp)
                                            )
                                            Text(String.format("x%.2f", padConfig?.pitch ?: 1f), color = Color(0xFFCCC2DC), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(35.dp), textAlign = TextAlign.End)
                                        }

                                        // Decay Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("DECAY:", color = Color(0xFF938F99), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(55.dp))
                                            Slider(
                                                value = padConfig?.decay ?: 1.0f,
                                                onValueChange = { viewModel.updateSelectedPadParams(decay = it) },
                                                valueRange = 0.1f..3.0f,
                                                colors = SliderDefaults.colors(activeTrackColor = MpcColors.ScreenAmberBacklight, thumbColor = Color.White),
                                                modifier = Modifier.weight(1f).height(18.dp)
                                            )
                                            Text(String.format("x%.2f", padConfig?.decay ?: 1f), color = Color(0xFFCCC2DC), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(35.dp), textAlign = TextAlign.End)
                                        }

                                        // Filter Cutoff Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("LP FLTR:", color = Color(0xFF938F99), fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(55.dp))
                                            Slider(
                                                value = padConfig?.filterCutoff ?: 1.0f,
                                                onValueChange = { viewModel.updateSelectedPadParams(filterCutoff = it) },
                                                valueRange = 0.05f..1.0f,
                                                colors = SliderDefaults.colors(activeTrackColor = MpcColors.ScreenAmberBacklight, thumbColor = Color.White),
                                                modifier = Modifier.weight(1f).height(18.dp)
                                            )
                                            Text(String.format("%d%%", ((padConfig?.filterCutoff ?: 1f) * 100).toInt()), color = Color(0xFFCCC2DC), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(35.dp), textAlign = TextAlign.End)
                                        }
                                    }
                                }

                                // Q-Link Slider simulation
                                Box(
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    QLinkSlider(
                                        value = padConfig?.pitch ?: 1.0f,
                                        range = 0.2f..2.5f,
                                        label = "Q-LINK TUNE",
                                        onValueChange = { viewModel.updateSelectedPadParams(pitch = it) },
                                        modifier = Modifier.fillMaxHeight().scale(0.85f)
                                    )
                                }
                            }
                        }

                        MpcScreenMode.PAD_MUTE -> {
                            // Layout designed for live muting triggers (shows 16 cells layout)
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LIVE PERFORMANCE MUTE MATRIX",
                                        color = Color(0xFFCCC2DC),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "CLR MUTES",
                                        color = MpcColors.ScreenAmberBacklight,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .border(1.dp, MpcColors.ScreenAmberBacklight, RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                            .clickable { viewModel.clearAllMutes() }
                                    )
                                }

                                Text(
                                    text = "Tap a pad below to toggle MUTE state in real-time. Red keys are silenced.",
                                    color = Color(0xFF938F99),
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                // Grid preview of 16 pads and their mute states
                                Row(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (colIdx in 0..3) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            for (rowIdx in 0..3) {
                                                val padNum = (3 - rowIdx) * 4 + colIdx + 1
                                                val mKey = "${selectedBank}_${padNum}"
                                                val isMuted = padMutedStates[mKey] ?: false
                                                val padLabel = viewModel.audioEngine.getPadConfig(selectedBank, padNum)?.descName ?: "Pad"
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(if (isMuted) Color(0xFF601414) else Color(0xFF36343B))
                                                        .border(1.dp, if (selectedPadIndex == padNum) MpcColors.ScreenAmberBacklight else Color(0xFF1C1B1F))
                                                        .clickable { viewModel.togglePadMute(selectedBank, padNum) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "$selectedBank$padNum: ${padLabel.take(4).uppercase()}",
                                                        color = if (isMuted) Color(0xFFF2B8B5) else Color(0xFFCCC2DC),
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        MpcScreenMode.DISK -> {
                            // CF Compact Flash persistence: Save recorded pattern loops or Load previously saved tracks
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "COMPACT FLASH DRIVE",
                                    color = Color(0xFFCCC2DC),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = patternNameInput,
                                        onValueChange = { patternNameInput = it },
                                        placeholder = { Text("pattern_name", color = Color(0xFF938F99)) },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color(0xFFE6E1E5),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MpcColors.ScreenAmberBacklight,
                                            unfocusedBorderColor = Color(0xFF49454F),
                                            focusedContainerColor = Color(0xFF1C1B1F),
                                            unfocusedContainerColor = Color(0xFF1C1B1F)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("pattern_name_input")
                                    )

                                    Button(
                                        onClick = {
                                            if (patternNameInput.isNotBlank()) {
                                                viewModel.savePatternToDatabase(patternNameInput)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4A4458),
                                            contentColor = Color(0xFFD0BCFF)
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Done, contentDescription = "Save", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("SAVE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // List of patterns stored in database
                                Text(
                                    text = "SAVED NOTE PATTERNS LOOP LIST (TAP TO LOAD):",
                                    color = Color(0xFF938F99),
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                                )

                                if (savedPatterns.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .border(1.dp, Color(0xFF49454F))
                                            .background(Color(0xFF25232A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "[NO PATTERNS SAVED YET]",
                                            color = Color(0xFF938F99),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(Color(0xFF25232A)),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        contentPadding = PaddingValues(4.dp)
                                    ) {
                                        items(savedPatterns) { pattern ->
                                            val isLoaded = loadedPatternId == pattern.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(if (isLoaded) Color(0xFF4A4458) else Color.Transparent)
                                                    .border(1.dp, if (isLoaded) MpcColors.ScreenAmberBacklight else Color.Transparent)
                                                    .clickable { viewModel.loadPatternFromDatabase(pattern.id) }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PlayArrow,
                                                        contentDescription = "PatternIcon",
                                                        tint = if (isLoaded) MpcColors.ScreenAmberBacklight else Color(0xFF938F99),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "${pattern.name} (${pattern.bpm.toInt()}BPM) - ${pattern.bars}Bar",
                                                        color = if (isLoaded) Color.White else Color(0xFFCCC2DC),
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.deletePatternFromDatabase(pattern.id) },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color(0xFFF2B8B5),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- ROW 4: The 16 physical responsive trigger pads (4x4 Matrix) ---
            Column(
                modifier = Modifier
                    .weight(3.5f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Arrange in a classic Akai 4x4 layout, going down 13-16, 9-12, 5-8, 1-4
                for (row in 0..3) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (col in 0..3) {
                            // Authentic MPC numbering: Pad 1-4 at the bottom. Row indexing:
                            // Row 0 -> Pad 13, 14, 15, 16
                            // Row 1 -> Pad 9, 10, 11, 12
                            // Row 2 -> Pad 5, 6, 7, 8
                            // Row 3 -> Pad 1, 2, 3, 4
                            val padIndex = (3 - row) * 4 + col + 1
                            val mKey = "${selectedBank}_${padIndex}"
                            val isMuted = padMutedStates[mKey] ?: false
                            val config = viewModel.audioEngine.getPadConfig(selectedBank, padIndex)
                            val padLabel = config?.label ?: "Pad"
                            val soundTypeShort = config?.soundType?.name?.take(7) ?: "PRE"

                            MpcRubberPad(
                                padIndex = padIndex,
                                bank = selectedBank,
                                label = soundTypeShort,
                                isMuted = isMuted,
                                isSelected = selectedPadIndex == padIndex,
                                onTap = { viewModel.tapPad(padIndex) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- ROW 5: Bottom Physical Action Keys ---
            // Banks (A, B, C, D) + Metronome toggles + Sequencer action + Transport keys
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(6.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF25232A))
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    // Row 5.1: Bank buttons + Mute/Quantize settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bank Trigger Keys (A-D)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PAD BANK",
                                color = Color(0xFF938F99),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                            listOf("A", "B", "C", "D").forEach { bk ->
                                val isSelected = selectedBank == bk
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .shadow(2.dp, RoundedCornerShape(3.dp))
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isSelected) MpcColors.ScreenAmberBacklight else Color(0xFF36343B))
                                        .border(1.dp, if (isSelected) MpcColors.ScreenAmberBacklight else Color(0xFF49454F), RoundedCornerShape(3.dp))
                                        .clickable { viewModel.selectBank(bk) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = bk,
                                        color = if (isSelected) Color(0xFF381E72) else Color(0xFFE6E1E5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Metronome and Quantizer Toggles
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.toggleMetronome() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (metronomeOn) Color(0xFF4A4458) else Color(0xFF36343B)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF49454F)),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (metronomeOn) MpcColors.ButtonPlayOn else Color(0xFF49454F))
                                )
                                Text("METRO", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (metronomeOn) Color(0xFFD0BCFF) else Color(0xFFCCC2DC))
                            }

                            Button(
                                onClick = { viewModel.toggleQuantize() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (quantizeOn) Color(0xFF4A4458) else Color(0xFF36343B)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF49454F)),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (quantizeOn) MpcColors.ButtonPlayOn else Color(0xFF49454F))
                                )
                                Text("QUANT16", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (quantizeOn) Color(0xFFD0BCFF) else Color(0xFFCCC2DC))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 5.2: Hardware Transport controls: REDEEM BEATS loops
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left - Sequencer Edit utilities: Pad Note ERASE and Global Pattern CLEAR
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Erase pad sequence button
                            Button(
                                onClick = { showEraseWarning = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF601414)),
                                border = BorderStroke(1.dp, Color(0xFF8C1D18)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(3.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("ERASE PAD", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFF2B8B5))
                            }

                            // Erase entire sequencer button
                            Button(
                                onClick = { viewModel.clearSequencer() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25232A)),
                                border = BorderStroke(1.dp, Color(0xFF8C1D18)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(3.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("RESET ALL", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFF2B8B5))
                            }
                        }

                        // Right: Physical chunky transport keys (PLAY, PLAY START, STOP, REC, OVERDUB)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HardwareTransportButton(
                                label = "REC",
                                isActive = isRecording,
                                onPress = { viewModel.pressRec() },
                                activeLedColor = MpcColors.ButtonLightOn
                            )

                            HardwareTransportButton(
                                label = "OVERDUB",
                                isActive = isOverdubbing,
                                onPress = { viewModel.pressOverdub() },
                                activeLedColor = MpcColors.ButtonLightOn
                            )

                            HardwareTransportButton(
                                label = "STOP",
                                isActive = !isPlaying,
                                onPress = { viewModel.pressStop() },
                                activeLedColor = MpcColors.ButtonPlayOn
                            )

                            HardwareTransportButton(
                                label = "PLAY START",
                                isActive = false,
                                onPress = { viewModel.pressPlayStart() }
                            )

                            HardwareTransportButton(
                                label = "PLAY",
                                isActive = isPlaying,
                                onPress = { viewModel.pressPlay() },
                                activeLedColor = MpcColors.ButtonPlayOn
                            )
                        }
                    }
                }
            }
        }
    }

    // Erase selected pad confirmations
    if (showEraseWarning) {
        AlertDialog(
            onDismissRequest = { showEraseWarning = false },
            title = { Text("Erase Recorded Notes", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("Do you want to delete all recorded sequencer notes for Pad $selectedBank$selectedPadIndex on Track $activeTrack?", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eraseSelectedPadNotes()
                        showEraseWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("ERASE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseWarning = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

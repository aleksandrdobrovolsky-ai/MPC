package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.MpcAudioEngine
import kotlin.math.*

// --- Color Constants for the Hardware Unit ---
object MpcColors {
    val ChassisDark = Color(0xFF1C1B1F)      // Deep elegant dark charcoal-purple background
    val ChassisBeige = Color(0xFFE6E1E5)     // Slate grey / off-white highlights
    val SideCaps = Color(0xFF25232A)         // Deep background color for containers / sections
    val ScreenAmberBacklight = Color(0xFFD0BCFF) // Elegant Lavender glow for current selection / backlight
    val ScreenGreenBacklight = Color(0xFFCCC2DC) // Secondary slate lavender
    val ScreenBackground = Color(0xFF2D2930) // Deep warm purple-charcoal LCD frame background
    val ScreenTerminalText = Color(0xFFD0BCFF) // Glowing lavender phosphor text
    val RubberPadActive = Color(0xFFD0BCFF)  // Glowing lavender active trigger ring
    val RubberPadDefault = Color(0xFF36343B) // Dark charcoal purple-grey pads
    val RubberPadRim = Color(0xFF25232A)     // Dark container spacing
    val MetalSliderTrack = Color(0xFF1C1B1F) // Slide slot
    val MetalCap = Color(0xFF4A4458)         // Matte purple-grey cap
    val ButtonLightOn = Color(0xFFF2B8B5)    // Muted soft rose/red for warning LED indicators (or record)
    val ButtonPlayOn = Color(0xFFD0BCFF)     // Play run indicator is elegant lavender
}

/**
 * Highly immersive vintage LCD display, utilizing retro styling, custom waveforms
 * representing envelopes, beat tracking grids, and pixel readouts.
 */
@Composable
fun RetroPixelLcdDisplay(
    mode: MpcScreenMode,
    bank: String,
    padIndex: Int,
    selectedSampleName: String,
    padConfig: MpcAudioEngine.PadConfig?,
    bpm: Float,
    bars: Int,
    track: Int,
    isPlaying: Boolean,
    isRecording: Boolean,
    loopProgress: Float,
    beatFraction: Int, // current active step index (0-15)
    metronomeOn: Boolean,
    quantizeOn: Boolean,
    activeNotesCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("retro_lcd_display")
            .shadow(12.dp, RoundedCornerShape(4.dp))
            .border(4.dp, Color(0xFF49454F), RoundedCornerShape(4.dp)),
        colors = CardDefaults.cardColors(containerColor = MpcColors.ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MpcColors.ScreenGreenBacklight.copy(0.3f))
                    .background(MpcColors.ScreenGreenBacklight.copy(0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AKAI MPC 2500 [v1.0a]",
                    color = MpcColors.ScreenGreenBacklight,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "BANK: $bank",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "METRO: ${if (metronomeOn) "ON" else "OFF"}",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Q16: ${if (quantizeOn) "ON" else "OFF"}",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Display Layout (SPLIT Workspace: Left stats, Right Scope)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Left Column: Parameters
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "MODE: ${mode.name}",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "TRACK $track  [LOFI GEN]",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "BPM: ${String.format("%.1f", bpm)}",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "BARS: $bars",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ACTIVE PAD: $bank${String.format("%02d", padIndex)}",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = selectedSampleName,
                        color = MpcColors.ScreenGreenBacklight.copy(0.85f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                // Split divider line
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MpcColors.ScreenGreenBacklight.copy(0.25f))
                )

                // Right Column: Waveform Analyzer / Step Loop Grid Tracker
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SAMPLE ENVELOPE SCOPE",
                        color = MpcColors.ScreenGreenBacklight,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // Waveform Custom Drawing Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, MpcColors.ScreenGreenBacklight.copy(0.2f))
                            .background(MpcColors.ScreenGreenBacklight.copy(0.04f))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            
                            // Draw horizontal centerline
                            drawLine(
                                color = MpcColors.ScreenGreenBacklight.copy(0.15f),
                                start = Offset(0f, h / 2),
                                end = Offset(w, h / 2),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Fetch pad config properties to shape visual envelopes
                            val att = padConfig?.attack ?: 0f
                            val dec = padConfig?.decay ?: 1f
                            val cut = padConfig?.filterCutoff ?: 1f

                            val pointsCount = 48
                            val path = Path()
                            path.moveTo(0f, h / 2)

                            for (i in 0..pointsCount) {
                                val fraction = i.toFloat() / pointsCount
                                val x = fraction * w
                                
                                // Synthesise a mathematical waveform envelope display in real-time
                                var amplitude = 0f
                                if (fraction < att * 0.3f) {
                                    // Attack ramp
                                    amplitude = if (att > 0) fraction / (att * 0.3f) else 1f
                                } else {
                                    // Decay curve
                                    val decaySec = (fraction - att * 0.3f) / (1f - att * 0.3f)
                                    amplitude = exp(-4.0f * decaySec * (2.0f / dec))
                                }

                                // High-frequency oscillations styled like lofi sound particles
                                val frequency = 25.0f * (1f + (1f - cut) * 4f)
                                val osc = sin(fraction * Math.PI * frequency) * cos(fraction * Math.PI * 0.5f)
                                val y = (h / 2) + (osc * (h * 0.42f) * amplitude * cut).toFloat()
                                
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = MpcColors.ScreenGreenBacklight,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sub-Screen: Step Matrix Looper / Sequencer Tracker (16 cells representing bar looping)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .border(1.dp, MpcColors.ScreenGreenBacklight.copy(0.2f))
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEQ:",
                    color = MpcColors.ScreenGreenBacklight,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                // 16 pixels cells in grid
                Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (step in 0..15) {
                        val isCurrentStep = beatFraction % 16 == step && isPlaying
                        val cellColor = if (isCurrentStep) {
                            MpcColors.ScreenGreenBacklight
                        } else {
                            MpcColors.ScreenGreenBacklight.copy(0.18f)
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .border(1.dp, MpcColors.ScreenGreenBacklight.copy(0.40f))
                                .background(cellColor)
                        )
                    }
                }

                // Quick loop fraction meter displaying %
                Text(
                    text = "${String.format("%03d", (loopProgress * 100).toInt())}%",
                    color = MpcColors.ScreenGreenBacklight,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Authentic Akai MPC 2500 responsive rubber key.
 * Triggers a blooming glowing red neon LED border when tapped or played.
 */
@Composable
fun MpcRubberPad(
    padIndex: Int,
    bank: String,
    label: String,
    isMuted: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic spring-based scale down when pad is tapped to simulate thick tactile rubber travel
    var isPressed by remember { mutableStateOf(false) }
    val padScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
    )

    // Simulate pad glow fading after trigger
    var triggerGlow by remember { mutableStateOf(0f) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            triggerGlow = 1.0f
        } else {
            // Slower decay on glowing LED ring
            animate(
                initialValue = triggerGlow,
                targetValue = 0.0f,
                animationSpec = tween(durationMillis = 250, easing = LinearEasing)
            ) { value, _ ->
                triggerGlow = value
            }
        }
    }

    // Wrap in tactile bounding box
    Box(
        modifier = modifier
            .testTag("mpc_pad_${bank}_${padIndex}")
            .padding(4.dp)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onTap()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
            .shadow(
                elevation = if (isPressed) 2.dp else 6.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .drawBehind {
                // Render neon glowing red LED border ring if triggered
                if (triggerGlow > 0.05f) {
                    drawRoundRect(
                        color = MpcColors.RubberPadActive.copy(alpha = triggerGlow),
                        size = size,
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 4.dp.toPx())
                    )
                } else if (isSelected) {
                    // Subtle amber indicator showing active editing pad
                    drawRoundRect(
                        color = MpcColors.ScreenAmberBacklight.copy(alpha = 0.7f),
                        size = size,
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isMuted) {
                        listOf(Color(0xFF2E3133), Color(0xFF1F2122))
                    } else if (isPressed) {
                        listOf(Color(0xFF6B6F7A), Color(0xFF51545D))
                    } else {
                        listOf(MpcColors.RubberPadDefault, Color(0xFF3B3E44))
                    }
                )
            )
            .border(
                width = 1.5.dp,
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(8.dp)
            )
            .graphicsLayer {
                scaleX = padScale
                scaleY = padScale
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Row 1: Outer pad identifier
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color(0xFFFF5555) else Color.Black.copy(0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = padIndex.toString(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                if (isMuted) {
                    Text(
                        text = "MUTE",
                        color = Color(0xFFFF5555),
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Row 2: Short descriptive label (e.g. SL KB, S SN)
            Text(
                text = label.uppercase(),
                color = if (isMuted) Color.Gray else Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            )
        }
    }
}

/**
 * Visual vertical Q-Link slide potentiometer.
 */
@Composable
fun QLinkSlider(
    value: Float,
    range: ClosedRange<Float>,
    label: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .width(42.dp)
                .height(130.dp)
                .background(MpcColors.ChassisDark.copy(0.4f), RoundedCornerShape(4.dp))
                .border(2.dp, Color(0xFF1E1F22), RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val rawRange = range.endInclusive - range.start
                        val sizeY = 130.dp.toPx()
                        // Moving up decrements visual distance from top -> increases value!
                        val delta = -(dragAmount.y / sizeY) * rawRange
                        onValueChange((value + delta).coerceIn(range.start, range.endInclusive))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val progressFraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

            // Slider channel groove line
            Canvas(modifier = Modifier.fillMaxHeight().width(4.dp)) {
                drawRect(
                    color = Color.Black,
                    size = size
                )
            }

            // Slider floating silver knob caps
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .offset(y = (-progressFraction * 110).dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(MpcColors.MetalCap, Color(0xFF6B6D72), MpcColors.MetalCap)
                        )
                    )
                    .border(1.dp, Color.Black)
            ) {
                // Reflective slider groove indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black)
                        .align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = String.format("%.2f", value),
            color = MpcColors.ScreenTerminalText,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Swipable circular virtual jog dial wheel for scrolling lists and increments.
 */
@Composable
fun HardwareJogDial(
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rotationAngle by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "VALUE DIAL",
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .size(90.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4A4458), Color(0xFF25232A))
                    )
                )
                .border(3.dp, Color(0xFF49454F), CircleShape)
                .pointerInput(Unit) {
                    var lastX = 0f
                    var lastY = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            lastX = offset.x - 45.dp.toPx()
                            lastY = offset.y - 45.dp.toPx()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val curX = lastX + dragAmount.x
                            val curY = lastY + dragAmount.y
                            
                            val angle1 = atan2(lastY, lastX)
                            val angle2 = atan2(curY, curX)
                            var diff = Math.toDegrees((angle2 - angle1).toDouble()).toFloat()
                            
                            if (diff < -180f) diff += 360f
                            if (diff > 180f) diff -= 360f

                            rotationAngle = (rotationAngle + diff) % 360f
                            
                            // Threshold: trigger ticks every 15 degrees drag rotation
                            if (diff > 12f) {
                                onIncrement()
                            } else if (diff < -12f) {
                                onDecrement()
                            }

                            lastX = curX
                            lastY = curY
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Draw visual ridges inside dial wheel
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                
                // Draw rotating groove indents
                for (angleDeg in 0..360 step 30) {
                    val angleRad = Math.toRadians((angleDeg + rotationAngle).toDouble())
                    val grooveStart = r * 0.45f
                    val grooveEnd = r * 0.72f
                    drawLine(
                        color = Color.Black.copy(0.40f),
                        start = Offset(
                            (center.x + grooveStart * cos(angleRad)).toFloat(),
                            (center.y + grooveStart * sin(angleRad)).toFloat()
                        ),
                        end = Offset(
                            (center.x + grooveEnd * cos(angleRad)).toFloat(),
                            (center.y + grooveEnd * sin(angleRad)).toFloat()
                        ),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // Smooth round tactile finger crater
                val fingerRad = Math.toRadians((120 + rotationAngle).toDouble())
                val craterDist = r * 0.60f
                drawCircle(
                    color = Color.Black,
                    radius = r * 0.16f,
                    center = Offset(
                        (center.x + craterDist * cos(fingerRad)).toFloat(),
                        (center.y + craterDist * sin(fingerRad)).toFloat()
                    )
                )
            }

            // Metallic center dust cap
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF555960))
                    .border(1.dp, Color.Black, CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Push buttons to increment or decrement in case gestures are tricky on emulation
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF4A4458), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(4.dp))
            ) {
                Text("<", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            IconButton(
                onClick = onIncrement,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF4A4458), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(4.dp))
            ) {
                Text(">", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/**
 * Beautiful rectangular hardware sequence transport execution button.
 */
@Composable
fun HardwareTransportButton(
    label: String,
    isActive: Boolean,
    onPress: () -> Unit,
    activeLedColor: Color = MpcColors.ButtonLightOn,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Blinking notification led above key
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isActive) activeLedColor else Color.Black.copy(0.7f))
                .border(0.5.dp, Color.Yellow.copy(0.12f), CircleShape)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Tactile plastic key body
        Button(
            onClick = onPress,
            shape = RoundedCornerShape(3.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) Color(0xFF4A4458) else Color(0xFF36343B)
            ),
            border = BorderStroke(1.dp, Color(0xFF49454F)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier
                .testTag("transport_button_${label.lowercase()}")
                .height(34.dp)
                .shadow(if (isActive) 1.dp else 4.dp, RoundedCornerShape(3.dp))
        ) {
            Text(
                text = label.uppercase(),
                color = if (isActive) Color(0xFFD0BCFF) else Color(0xFFCCC2DC),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

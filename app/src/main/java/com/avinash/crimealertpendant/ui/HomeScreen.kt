package com.avinash.crimealertpendant.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avinash.crimealertpendant.ble.BleConnectionState

private val Background = Color(0xFFF4F5F7)
private val Ink = Color(0xFF17243A)
private val Muted = Color(0xFF667085)
private val Accent = Color(0xFFD92D3A)
private val BorderColor = Color(0xFFE2E6EB)
private val OnlineGreen = Color(0xFF0F9D58)

@Composable
fun HomeScreen(
    alertActive: Boolean,
    alertSource: String,
    seconds: Int,
    bleState: BleConnectionState,
    contactsCount: Int,
    locationString: String,
    voiceTranscript: String,
    isRecordingVoice: Boolean,
    voiceAudioLevel: Float,
    voiceStatus: String,
    onSosClick: () -> Unit,
    onCancelAlert: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onConnectPendant: () -> Unit,
    onShowLocationModal: () -> Unit,
    onTestVoiceRecognition: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Pulse animation for active SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "SosPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (alertActive) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        // Top Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "PERSONAL SAFETY",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Crime Alert",
                    color = Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Pendant",
                    color = Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Text(
                    "CA",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Pendant Hardware Status Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Pendant status",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when (bleState) {
                            is BleConnectionState.Connected -> "ESP32-C3 Online (GPIO4 Active)"
                            is BleConnectionState.Scanning -> "Scanning for CrimeAlertPendant..."
                            is BleConnectionState.Connecting -> "Connecting to ESP32..."
                            is BleConnectionState.Error -> "Connection error. Tap to retry."
                            BleConnectionState.Disconnected -> "Bluetooth not connected"
                        },
                        color = when (bleState) {
                            is BleConnectionState.Connected -> OnlineGreen
                            else -> Muted
                        },
                        fontSize = 13.sp
                    )
                }

                when (bleState) {
                    is BleConnectionState.Connected -> {
                        Surface(
                            color = Color(0xFFE6F4EA),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "CONNECTED",
                                color = OnlineGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    is BleConnectionState.Scanning -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Ink
                        )
                    }
                    else -> {
                        Button(
                            onClick = onConnectPendant,
                            colors = ButtonDefaults.buttonColors(containerColor = Ink),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Trigger Status Banner
        if (alertActive) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFDE8E8),
                border = BorderStroke(1.dp, Accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚨", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "ALERT IN PROGRESS!",
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Triggered via: $alertSource",
                            color = Ink,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Text(
                "Need help?",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
        }

        // Circular SOS Button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onSosClick,
                modifier = Modifier
                    .size(184.dp)
                    .scale(if (alertActive) pulseScale else 1f),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (alertActive) 8.dp else 2.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "SOS",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (alertActive) "$seconds SECONDS"
                        else "TAP TO START",
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            if (alertActive)
                "Countdown active. Push button on GPIO4 or cancel button below to abort."
            else
                "Linked to your ESP32-C3 GPIO4 push button. Pressing it triggers this SOS instantly.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Muted,
            fontSize = 12.sp
        )

        // -------------------------------------------------------------
        // LIVE VOICE RECORDING & REAL-TIME TRANSCRIPTION CARD (ACTIVE)
        // -------------------------------------------------------------
        if (alertActive) {
            Spacer(Modifier.height(14.dp))
            VoiceTranscriptionCard(
                isListening = isRecordingVoice,
                transcribedText = voiceTranscript,
                audioLevel = voiceAudioLevel,
                statusMessage = voiceStatus
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCancelAlert,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Accent)
            ) {
                Text("Cancel SOS Alert", color = Accent, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onTestVoiceRecognition,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Text("🎙️ Test Voice Transcription (Mic Check)", color = Ink, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "QUICK ACCESS",
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )

        Spacer(Modifier.height(10.dp))

        // Emergency Contacts Card
        QuickAccessCard(
            title = "Emergency contacts",
            subtitle = if (contactsCount > 0) "$contactsCount trusted contacts registered" else "Manage up to 6 trusted people",
            symbol = "01",
            onClick = onNavigateToContacts
        )

        Spacer(Modifier.height(10.dp))

        // My Location Card
        QuickAccessCard(
            title = "My location",
            subtitle = locationString.ifEmpty { "Location sharing active" },
            symbol = "02",
            onClick = onShowLocationModal
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * High-tech Voice Recording & Real-Time Transcription UI Card
 * Shows animated blinking recording indicator, dynamic audio level bars,
 * live transcribed speech text, and emergency SMS inclusion status.
 */
@Composable
fun VoiceTranscriptionCard(
    isListening: Boolean,
    transcribedText: String,
    audioLevel: Float,
    statusMessage: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BlinkingRec")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotAlpha"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF7F7),
        border = BorderStroke(1.5.dp, Accent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pulsing red recording dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Accent.copy(alpha = if (isListening) dotAlpha else 0.4f), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isListening) "LIVE MICROPHONE RECORDING (30s)" else "MICROPHONE RECORDING",
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.weight(1f))

                // Sound level visualizer wave bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(16.dp)
                ) {
                    val barCount = 5
                    for (i in 0 until barCount) {
                        val factor = (i + 1).toFloat() / barCount
                        val heightDp = (4 + (audioLevel * 14 * factor)).coerceIn(4f, 16f).dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(if (isListening) heightDp else 4.dp)
                                .background(if (isListening) Accent else Muted, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                statusMessage,
                color = Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(8.dp))

            // Real-time transcribed text container
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "LIVE SPEECH-TO-TEXT TRANSCRIPT (WILL GO IN SMS):",
                        fontSize = 10.sp,
                        color = Muted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (transcribedText.isNotBlank()) "“$transcribedText”"
                        else "Speak clearly into your phone... (e.g., 'Emergency! Attacked near 4th street, send help!')",
                        fontSize = 14.sp,
                        fontWeight = if (transcribedText.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                        color = if (transcribedText.isNotBlank()) Ink else Muted
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "💬 When the 30s countdown finishes, this voice transcript + your live GPS location will be automatically texted to your emergency contacts.",
                fontSize = 11.sp,
                color = Muted,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun QuickAccessCard(
    title: String,
    subtitle: String,
    symbol: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                symbol,
                color = Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Text("›", color = Muted, fontSize = 25.sp)
        }
    }
}

package com.avinash.crimealertpendant.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avinash.crimealertpendant.ble.BleConnectionState
import com.avinash.crimealertpendant.data.SafetyPreferences

private val Ink = Color(0xFF17243A)
private val Muted = Color(0xFF667085)
private val Accent = Color(0xFFD92D3A)
private val BorderColor = Color(0xFFE2E6EB)
private val DarkConsoleBg = Color(0xFF111827)
private val ConsoleGreen = Color(0xFF10B981)

@Composable
fun SettingsScreen(
    prefs: SafetyPreferences,
    bleState: BleConnectionState,
    discoveredDevices: List<BluetoothDevice>,
    logMessages: List<String>,
    voiceTranscript: String = "",
    isRecordingVoice: Boolean = false,
    voiceAudioLevel: Float = 0f,
    voiceStatus: String = "",
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onTestGpio4Click: () -> Unit,
    onStartVoiceTest: () -> Unit = {},
    onStopVoiceTest: () -> Unit = {}
) {
    var deviceName by remember { mutableStateOf(prefs.targetDeviceName) }
    var countdownDuration by remember { mutableIntStateOf(prefs.countdownDuration) }
    var sirenEnabled by remember { mutableStateOf(prefs.isSirenEnabled) }
    var vibrationEnabled by remember { mutableStateOf(prefs.isVibrationEnabled) }
    var autoSmsEnabled by remember { mutableStateOf(prefs.isAutoSmsEnabled) }
    var voiceToTextEnabled by remember { mutableStateOf(prefs.isVoiceToTextEnabled) }
    var muteSirenDuringCountdown by remember { mutableStateOf(prefs.isMuteSirenDuringCountdown) }
    var emergencyMessage by remember { mutableStateOf(prefs.emergencyMessage) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        Text(
            "PENDANT & SYSTEM CONFIGURATION",
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Hardware & Settings",
            color = Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(18.dp))

        // ---------------------------------
        // ESP32-C3 HARDWARE STATUS CARD
        // ---------------------------------
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("ESP32-C3 Pendant Link", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Push button attached to GPIO4", color = Muted, fontSize = 12.sp)
                    }

                    // Connection Badge
                    val (badgeText, badgeBg, badgeFg) = when (bleState) {
                        is BleConnectionState.Connected -> Triple("ONLINE", Color(0xFFE6F4EA), Color(0xFF137333))
                        is BleConnectionState.Scanning -> Triple("SCANNING", Color(0xFFFEF7E0), Color(0xFFB06000))
                        is BleConnectionState.Connecting -> Triple("CONNECTING", Color(0xFFE8F0FE), Color(0xFF1A73E8))
                        is BleConnectionState.Error -> Triple("ERROR", Color(0xFFFCE8E6), Color(0xFFC5221F))
                        BleConnectionState.Disconnected -> Triple("DISCONNECTED", Color(0xFFF1F3F4), Muted)
                    }

                    Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            badgeText,
                            color = badgeFg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = {
                        deviceName = it
                        prefs.targetDeviceName = it
                    },
                    label = { Text("Pendant Bluetooth Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (bleState is BleConnectionState.Connected) {
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Disconnect")
                        }
                    } else if (bleState is BleConnectionState.Scanning) {
                        OutlinedButton(
                            onClick = onStopScan,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop Scan")
                        }
                    } else {
                        Button(
                            onClick = onStartScan,
                            colors = ButtonDefaults.buttonColors(containerColor = Ink),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scan & Pair Pendant")
                        }
                    }

                    // Simulate GPIO4 Button Press
                    OutlinedButton(
                        onClick = onTestGpio4Click,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Simulate GPIO4", maxLines = 1)
                    }
                }

                // Discovered Devices List
                if (discoveredDevices.isNotEmpty() && bleState !is BleConnectionState.Connected) {
                    Spacer(Modifier.height(12.dp))
                    Text("Discovered Nearby Devices:", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))

                    discoveredDevices.take(4).forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                @Suppress("MissingPermission")
                                Text(device.name ?: "Unnamed Device", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(device.address, fontSize = 11.sp, color = Muted)
                            }
                            TextButton(onClick = { onConnectDevice(device) }) {
                                Text("Connect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---------------------------------
        // 30-SECOND VOICE-TO-TEXT SETTINGS
        // ---------------------------------
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎙️", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("30s Voice-to-Text Recording", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Microphone audio converts to text for emergency SMS", color = Muted, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Voice Recording Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable Voice-to-Text during SOS", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Listens for 30s countdown and embeds spoken words in SMS", color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = voiceToTextEnabled,
                        onCheckedChange = {
                            voiceToTextEnabled = it
                            prefs.isVoiceToTextEnabled = it
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Mute Siren During 30s Recording Window
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Defer Siren Until Countdown Ends", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Prevents phone siren from deafening microphone while you speak. Siren roars after SMS is sent.", color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = muteSirenDuringCountdown,
                        onCheckedChange = {
                            muteSirenDuringCountdown = it
                            prefs.isMuteSirenDuringCountdown = it
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(Modifier.height(12.dp))

                // Interactive Voice-to-Text Mic Test
                Text("Test Microphone & Speech Recognition:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(6.dp))

                if (isRecordingVoice) {
                    Button(
                        onClick = onStopVoiceTest,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⏹️ Stop Microphone Test")
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartVoiceTest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🎙️ Start Microphone & Voice-to-Text Test")
                    }
                }

                if (voiceStatus.isNotEmpty() || voiceTranscript.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF9FAFB),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("STATUS: $voiceStatus", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold)
                            if (voiceTranscript.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("RECOGNIZED: “$voiceTranscript”", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---------------------------------
        // SOS RESPONSE BEHAVIOR
        // ---------------------------------
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Emergency Dispatch Settings", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                // Countdown duration
                Text("Countdown Cancellation Window (Seconds):", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 20, 30, 60).forEach { sec ->
                        val isSelected = countdownDuration == sec
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                countdownDuration = sec
                                prefs.countdownDuration = sec
                            },
                            label = { Text("${sec}s${if (sec == 30) " (Default)" else ""}") }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(Modifier.height(12.dp))

                // Siren Audio Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Audible Alarm Siren", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Sounds high-pitched alert tone when triggered", color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = sirenEnabled,
                        onCheckedChange = {
                            sirenEnabled = it
                            prefs.isSirenEnabled = it
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Vibration Feedback Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptic Pulsing Vibration", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Vibrates phone during active alert", color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = {
                            vibrationEnabled = it
                            prefs.isVibrationEnabled = it
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Auto-SMS Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-SMS with GPS & Voice Note", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Sends automated SMS to emergency contacts when countdown finishes", color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoSmsEnabled,
                        onCheckedChange = {
                            autoSmsEnabled = it
                            prefs.isAutoSmsEnabled = it
                        }
                    )
                }

                if (autoSmsEnabled) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = emergencyMessage,
                        onValueChange = {
                            emergencyMessage = it
                            prefs.emergencyMessage = it
                        },
                        label = { Text("Emergency SMS Message Template") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    // Outgoing SMS Preview
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("SAMPLE OUTGOING SMS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Muted)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$emergencyMessage\n\n🎙️ Voice Note: \"[Your 30s spoken voice converted to text]\"\n\n📍 Location: https://maps.google.com/?q=17.3850,78.4867",
                                fontSize = 12.sp,
                                color = Ink
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---------------------------------
        // LIVE HARDWARE DEBUG CONSOLE
        // ---------------------------------
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkConsoleBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LIVE HARDWARE PACKET CONSOLE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(color = ConsoleGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("GATT LOGS", color = ConsoleGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (logMessages.isEmpty()) {
                        Text("Awaiting BLE packets from ESP32-C3...", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(logMessages.reversed()) { msg ->
                                Text(
                                    msg,
                                    color = if (msg.contains("GPIO4") || msg.contains("SOS")) Color(0xFFEF4444) else ConsoleGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))
    }
}

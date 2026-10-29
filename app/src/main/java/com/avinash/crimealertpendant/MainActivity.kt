package com.avinash.crimealertpendant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.avinash.crimealertpendant.ble.BleConnectionState
import com.avinash.crimealertpendant.ble.PendantBleManager
import com.avinash.crimealertpendant.data.EmergencyContact
import com.avinash.crimealertpendant.data.SafetyPreferences
import com.avinash.crimealertpendant.ui.ContactsScreen
import com.avinash.crimealertpendant.ui.HomeScreen
import com.avinash.crimealertpendant.ui.SettingsScreen
import com.avinash.crimealertpendant.util.EmergencyDispatcher
import com.avinash.crimealertpendant.util.VoiceToTextManager
import kotlinx.coroutines.delay

private val Background = Color(0xFFF4F5F7)
private val Ink = Color(0xFF17243A)
private val Muted = Color(0xFF667085)
private val BorderColor = Color(0xFFE2E6EB)

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: PendantBleManager
    private lateinit var preferences: SafetyPreferences
    private lateinit var dispatcher: EmergencyDispatcher
    private lateinit var voiceManager: VoiceToTextManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = SafetyPreferences(this)
        dispatcher = EmergencyDispatcher(this)
        voiceManager = VoiceToTextManager(this)
        bleManager = PendantBleManager(this).apply {
            targetDeviceName = preferences.targetDeviceName
        }

        setContent {
            MaterialTheme {
                CrimeAlertApp(
                    bleManager = bleManager,
                    preferences = preferences,
                    dispatcher = dispatcher,
                    voiceManager = voiceManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dispatcher.stopSiren()
        voiceManager.destroy()
        bleManager.disconnect()
    }
}

@Composable
fun CrimeAlertApp(
    bleManager: PendantBleManager,
    preferences: SafetyPreferences,
    dispatcher: EmergencyDispatcher,
    voiceManager: VoiceToTextManager
) {
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(preferences.countdownDuration) }
    var alertActive by remember { mutableStateOf(false) }
    var alertSource by remember { mutableStateOf("Manual Button") }
    var showLocationDialog by remember { mutableStateOf(false) }

    // Emergency Contacts State
    var contacts by remember { mutableStateOf(preferences.getContacts()) }

    // BLE States
    val bleState by bleManager.connectionState.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val logMessages by bleManager.logMessages.collectAsState()

    // Real-Time Voice-to-Text States
    val voiceTranscript by voiceManager.transcribedText.collectAsState()
    val isRecordingVoice by voiceManager.isListening.collectAsState()
    val voiceAudioLevel by voiceManager.audioLevel.collectAsState()
    val voiceStatus by voiceManager.statusMessage.collectAsState()

    // Android Permissions Launcher (Bluetooth, Location, Microphone, SMS)
    val requiredPermissions = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            bleManager.log("All permissions (BLE, GPS, Audio, SMS) granted.")
            bleManager.startScan()
        } else {
            bleManager.log("Some permissions denied. Certain features (Mic, SMS, BLE) may be limited.")
            Toast.makeText(context, "Permissions required for full emergency safety features.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-prompt permissions on launch if not granted
    LaunchedEffect(Unit) {
        val missing = requiredPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            // Auto start scan for ESP32 pendant
            bleManager.startScan()
        }
    }

    // Listen to hardware SOS trigger events coming from ESP32-C3 GPIO4!
    LaunchedEffect(Unit) {
        bleManager.sosTriggerFlow.collect { event ->
            // Triggered from GPIO4 Push Button on ESP32-C3!
            alertSource = event.source
            seconds = preferences.countdownDuration
            alertActive = true

            if (preferences.isVibrationEnabled) {
                dispatcher.triggerVibration()
            }
            if (preferences.isSirenEnabled && !preferences.isMuteSirenDuringCountdown) {
                dispatcher.startSiren()
            }
            if (preferences.isVoiceToTextEnabled) {
                voiceManager.startListening()
            }
        }
    }

    // Countdown Timer & Voice Recording Dispatch Logic
    LaunchedEffect(alertActive) {
        if (alertActive) {
            // Start real-time voice recording & speech-to-text conversion during the countdown window
            if (preferences.isVoiceToTextEnabled) {
                voiceManager.startListening()
            }

            while (alertActive && seconds > 0) {
                delay(1000)
                seconds--
                if (preferences.isVibrationEnabled && seconds > 0) {
                    dispatcher.triggerVibration()
                }
            }

            // Countdown reached 0: Emergency Trigger!
            if (seconds == 0 && alertActive) {
                bleManager.log("🚨 30-Second Countdown Expired! Finalizing voice note and dispatching...")

                // Stop microphone recording and retrieve complete transcribed text
                val finalVoiceTranscript = if (preferences.isVoiceToTextEnabled) {
                    voiceManager.stopListening()
                } else ""

                // Sound high-decibel alert siren now that speaking window is complete
                if (preferences.isSirenEnabled) {
                    dispatcher.startSiren()
                }

                // Send Emergency SMS populated with GPS location and the 30s transcribed voice note!
                if (preferences.isAutoSmsEnabled && contacts.isNotEmpty()) {
                    val fullMessage = dispatcher.formatEmergencyMessage(
                        baseMessage = preferences.emergencyMessage,
                        voiceTranscript = finalVoiceTranscript
                    )
                    dispatcher.sendEmergencySms(contacts, fullMessage)
                    Toast.makeText(
                        context,
                        "🚨 Emergency SMS with voice transcript sent to ${contacts.size} contacts!",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (contacts.isEmpty()) {
                    Toast.makeText(
                        context,
                        "Countdown finished! Please register contacts in Contacts tab to receive SMS.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            // Reset state when alert is cancelled
            voiceManager.reset()
            dispatcher.stopSiren()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Main Content Area based on Selected Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        alertActive = alertActive,
                        alertSource = alertSource,
                        seconds = seconds,
                        bleState = bleState,
                        contactsCount = contacts.size,
                        locationString = dispatcher.getLastKnownLocation()?.let {
                            "Lat: ${String.format("%.4f", it.latitude)}, Lon: ${String.format("%.4f", it.longitude)}"
                        } ?: "GPS Location Active",
                        voiceTranscript = voiceTranscript,
                        isRecordingVoice = isRecordingVoice,
                        voiceAudioLevel = voiceAudioLevel,
                        voiceStatus = voiceStatus,
                        onSosClick = {
                            if (!alertActive) {
                                alertSource = "App Screen Button"
                                seconds = preferences.countdownDuration
                                alertActive = true
                                if (preferences.isVibrationEnabled) dispatcher.triggerVibration()
                                if (preferences.isSirenEnabled && !preferences.isMuteSirenDuringCountdown) {
                                    dispatcher.startSiren()
                                }
                                if (preferences.isVoiceToTextEnabled) {
                                    voiceManager.startListening()
                                }
                            }
                        },
                        onCancelAlert = {
                            alertActive = false
                            seconds = preferences.countdownDuration
                            voiceManager.reset()
                            dispatcher.stopSiren()
                            bleManager.log("SOS Alert cancelled by user. Voice recording aborted.")
                        },
                        onNavigateToContacts = { selectedTab = 1 },
                        onNavigateToSettings = { selectedTab = 2 },
                        onConnectPendant = {
                            val missing = requiredPermissions.any {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing) {
                                permissionLauncher.launch(requiredPermissions)
                            } else {
                                bleManager.startScan()
                            }
                        },
                        onShowLocationModal = { showLocationDialog = true },
                        onTestVoiceRecognition = {
                            if (isRecordingVoice) {
                                voiceManager.stopListening()
                            } else {
                                voiceManager.startListening()
                            }
                        }
                    )
                    1 -> ContactsScreen(
                        contacts = contacts,
                        onAddContact = { newContact ->
                            val updated = contacts + newContact
                            contacts = updated
                            preferences.saveContacts(updated)
                        },
                        onDeleteContact = { toDelete ->
                            val updated = contacts.filterNot { it.id == toDelete.id }
                            contacts = updated
                            preferences.saveContacts(updated)
                        },
                        onCallContact = { phone ->
                            dispatcher.initiateEmergencyCall(phone)
                        }
                    )
                    2 -> SettingsScreen(
                        prefs = preferences,
                        bleState = bleState,
                        discoveredDevices = discoveredDevices,
                        logMessages = logMessages,
                        voiceTranscript = voiceTranscript,
                        isRecordingVoice = isRecordingVoice,
                        voiceAudioLevel = voiceAudioLevel,
                        voiceStatus = voiceStatus,
                        onStartScan = {
                            val missing = requiredPermissions.any {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing) {
                                permissionLauncher.launch(requiredPermissions)
                            } else {
                                bleManager.startScan()
                            }
                        },
                        onStopScan = { bleManager.stopScan() },
                        onConnectDevice = { device -> bleManager.connectToDevice(device) },
                        onDisconnect = { bleManager.disconnect() },
                        onTestGpio4Click = { bleManager.simulateGpio4SosClick() },
                        onStartVoiceTest = { voiceManager.startListening() },
                        onStopVoiceTest = { voiceManager.stopListening() }
                    )
                }
            }

            HorizontalDivider(color = BorderColor)

            // Bottom Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    title = "Home",
                    iconSymbol = "⌂",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                BottomNavItem(
                    title = "Contacts",
                    iconSymbol = "👥",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                BottomNavItem(
                    title = "Settings",
                    iconSymbol = "⚙",
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    }

    // Modal Location Info Dialog
    if (showLocationDialog) {
        val loc = dispatcher.getLastKnownLocation()
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            confirmButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold, color = Ink)
                }
            },
            title = {
                Text("Real-Time GPS Location", fontWeight = FontWeight.Bold, color = Ink)
            },
            text = {
                Column {
                    if (loc != null) {
                        Text("Latitude: ${loc.latitude}", fontSize = 14.sp)
                        Text("Longitude: ${loc.longitude}", fontSize = 14.sp)
                        Text("Accuracy: ±${loc.accuracy}m", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Map Link: https://maps.google.com/?q=${loc.latitude},${loc.longitude}",
                            fontSize = 11.sp,
                            color = Muted
                        )
                    } else {
                        Text(
                            "Location service is currently unavailable or permissions are pending.",
                            fontSize = 13.sp,
                            color = Muted
                        )
                    }
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun BottomNavItem(
    title: String,
    iconSymbol: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) Ink else Muted
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                iconSymbol,
                fontSize = 18.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
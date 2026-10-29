package com.avinash.crimealertpendant.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import com.avinash.crimealertpendant.data.EmergencyContact

class EmergencyDispatcher(private val context: Context) {

    private var alarmRingtone: Ringtone? = null
    private var isAlarmActive = false

    init {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            alarmRingtone = RingtoneManager.getRingtone(context, alertUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmRingtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
        } catch (e: Exception) {
            Log.e("EmergencyDispatcher", "Error initializing alarm tone", e)
        }
    }

    fun startSiren() {
        if (!isAlarmActive) {
            try {
                alarmRingtone?.play()
                isAlarmActive = true
            } catch (e: Exception) {
                Log.e("EmergencyDispatcher", "Failed to play siren", e)
            }
        }
    }

    fun stopSiren() {
        if (isAlarmActive) {
            try {
                alarmRingtone?.stop()
            } catch (e: Exception) {
                Log.e("EmergencyDispatcher", "Failed to stop siren", e)
            }
            isAlarmActive = false
        }
    }

    fun triggerVibration() {
        try {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e("EmergencyDispatcher", "Error triggering vibration", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Location? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            gpsLocation ?: networkLocation
        } catch (e: Exception) {
            Log.e("EmergencyDispatcher", "Location permission missing or disabled", e)
            null
        }
    }

    fun formatEmergencyMessage(baseMessage: String, voiceTranscript: String? = null): String {
        val location = getLastKnownLocation()
        val sb = StringBuilder(baseMessage.trim())

        val cleanVoice = voiceTranscript?.trim()
        if (!cleanVoice.isNullOrEmpty()) {
            sb.append("\n\n🎙️ Voice Note: \"").append(cleanVoice).append("\"")
        } else {
            sb.append("\n\n🎙️ Voice Note: (No voice audio detected during 30s recording)")
        }

        if (location != null) {
            sb.append("\n\n📍 Location: https://maps.google.com/?q=${location.latitude},${location.longitude}")
        } else {
            sb.append("\n\n📍 Location: (GPS location unavailable)")
        }
        return sb.toString()
    }

    fun sendEmergencySms(contacts: List<EmergencyContact>, message: String) {
        if (contacts.isEmpty()) {
            Log.w("EmergencyDispatcher", "No emergency contacts configured for SMS dispatch.")
            return
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            for (contact in contacts) {
                val phone = contact.phone.trim()
                if (phone.isNotEmpty()) {
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                    Log.d("EmergencyDispatcher", "Emergency SMS sent to ${contact.name} ($phone)")
                }
            }
        } catch (e: Exception) {
            Log.e("EmergencyDispatcher", "Failed to send emergency SMS", e)
        }
    }

    fun initiateEmergencyCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("EmergencyDispatcher", "Failed to launch phone call intent", e)
        }
    }
}

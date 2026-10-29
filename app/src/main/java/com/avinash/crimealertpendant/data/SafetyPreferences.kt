package com.avinash.crimealertpendant.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class SafetyPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("crime_alert_safety_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CONTACTS = "key_emergency_contacts"
        private const val KEY_COUNTDOWN_DURATION = "key_countdown_duration"
        private const val KEY_ENABLE_SIREN = "key_enable_siren"
        private const val KEY_ENABLE_VIBRATION = "key_enable_vibration"
        private const val KEY_ENABLE_AUTO_SMS = "key_enable_auto_sms"
        private const val KEY_ENABLE_VOICE_TO_TEXT = "key_enable_voice_to_text"
        private const val KEY_MUTE_SIREN_DURING_COUNTDOWN = "key_mute_siren_during_countdown"
        private const val KEY_TARGET_DEVICE_NAME = "key_target_device_name"
        private const val KEY_EMERGENCY_MESSAGE = "key_emergency_message"
    }

    var countdownDuration: Int
        get() = prefs.getInt(KEY_COUNTDOWN_DURATION, 30) // default 30 seconds for voice recording window
        set(value) = prefs.edit().putInt(KEY_COUNTDOWN_DURATION, value).apply()

    var isSirenEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SIREN, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SIREN, value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_VIBRATION, value).apply()

    var isAutoSmsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_AUTO_SMS, true) // enabled by default for emergency safety
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_AUTO_SMS, value).apply()

    var isVoiceToTextEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_VOICE_TO_TEXT, true) // record voice and transcribe during 30s countdown
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_VOICE_TO_TEXT, value).apply()

    var isMuteSirenDuringCountdown: Boolean
        get() = prefs.getBoolean(KEY_MUTE_SIREN_DURING_COUNTDOWN, true) // prevents siren from deafening microphone while speaking
        set(value) = prefs.edit().putBoolean(KEY_MUTE_SIREN_DURING_COUNTDOWN, value).apply()

    var targetDeviceName: String
        get() = prefs.getString(KEY_TARGET_DEVICE_NAME, "CrimeAlertPendant") ?: "CrimeAlertPendant"
        set(value) = prefs.edit().putString(KEY_TARGET_DEVICE_NAME, value).apply()

    var emergencyMessage: String
        get() = prefs.getString(
            KEY_EMERGENCY_MESSAGE,
            "EMERGENCY! I need immediate help. Sent from Crime Alert Pendant."
        ) ?: "EMERGENCY! I need immediate help. Sent from Crime Alert Pendant."
        set(value) = prefs.edit().putString(KEY_EMERGENCY_MESSAGE, value).apply()

    fun getContacts(): List<EmergencyContact> {
        val jsonString = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        val list = mutableListOf<EmergencyContact>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    EmergencyContact(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        phone = obj.optString("phone", ""),
                        relationship = obj.optString("relationship", "Contact")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveContacts(contacts: List<EmergencyContact>) {
        try {
            val jsonArray = JSONArray()
            for (contact in contacts) {
                val obj = JSONObject()
                obj.put("id", contact.id)
                obj.put("name", contact.name)
                obj.put("phone", contact.phone)
                obj.put("relationship", contact.relationship)
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_CONTACTS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

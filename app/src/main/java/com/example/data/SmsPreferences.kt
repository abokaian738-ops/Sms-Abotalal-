package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SmsPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("talal_sms_preferences", Context.MODE_PRIVATE)

    var deliveryReportsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DELIVERY_REPORTS, true)
        set(value) = prefs.edit().putBoolean(KEY_DELIVERY_REPORTS, value).apply()

    var preferredSubId: Int
        get() = prefs.getInt(KEY_PREFERRED_SUB_ID, -1) // -1 means ask or default
        set(value) = prefs.edit().putInt(KEY_PREFERRED_SUB_ID, value).apply()

    var customSmsc: String
        get() = prefs.getString(KEY_CUSTOM_SMSC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_SMSC, value).apply()

    var signatureEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIGNATURE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SIGNATURE_ENABLED, value).apply()

    var signatureText: String
        get() = prefs.getString(KEY_SIGNATURE_TEXT, "— أُرسلت عبر TALAL SMS") ?: "— أُرسلت عبر TALAL SMS"
        set(value) = prefs.edit().putString(KEY_SIGNATURE_TEXT, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ENABLED, value).apply()

    var themeMode: Int // 0 = System, 1 = Light, 2 = Dark
        get() = prefs.getInt(KEY_THEME_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    companion object {
        private const val KEY_DELIVERY_REPORTS = "key_delivery_reports"
        private const val KEY_PREFERRED_SUB_ID = "key_preferred_sub_id"
        private const val KEY_CUSTOM_SMSC = "key_custom_smsc"
        private const val KEY_SIGNATURE_ENABLED = "key_signature_enabled"
        private const val KEY_SIGNATURE_TEXT = "key_signature_text"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_VIBRATE_ENABLED = "key_vibrate_enabled"
        private const val KEY_THEME_MODE = "key_theme_mode"
    }
}

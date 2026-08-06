package com.dakomi.smartspacer.alarms.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SharedPreferences wrapper for persisting plugin settings.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Package names of clock apps the user has selected to monitor. Empty means all apps. */
    var selectedPackages: Set<String>
        get() = prefs.getStringSet(KEY_SELECTED_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_SELECTED_PACKAGES, value) }

    /** Whether Shizuku has been granted and should be used for reading alarms. */
    var shizukuEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHIZUKU_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_SHIZUKU_ENABLED, value) }

    /** Persisted dismissed alarm time — prevents re-showing the same alarm after dismiss. */
    var dismissedAlarmTime: Long
        get() = prefs.getLong(KEY_DISMISSED_ALARM_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_DISMISSED_ALARM_TIME, value) }

    companion object {
        private const val PREFS_NAME = "next_alarm_settings"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_SHIZUKU_ENABLED = "shizuku_enabled"
        private const val KEY_DISMISSED_ALARM_TIME = "dismissed_alarm_time"

        @Volatile
        private var instance: Settings? = null

        fun getInstance(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(context.applicationContext).also { instance = it }
        }
    }
}

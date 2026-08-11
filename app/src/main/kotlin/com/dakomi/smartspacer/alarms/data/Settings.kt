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

    /**
     * Short-lived in-memory flag set by [AlarmUpdateReceiver] each time an alarm-related broadcast
     * fires. [AlarmRepository] consumes (resets) this flag when it runs the Shizuku path, ensuring
     * Shizuku is only invoked in response to real alarm-change events rather than speculatively.
     *
     * Intentionally NOT persisted to SharedPreferences: the flag is only meaningful within the
     * current process lifetime. Persisting it would risk a stale `true` value triggering an
     * unexpected Shizuku call on the very first `getNextAlarm()` invocation after a fresh process
     * start.
     */
    @Volatile
    var shizukuRefreshRequested: Boolean = false

    /**
     * Optional text shown immediately before the alarm time in the target title.
     * E.g. "⏰" produces "⏰ 07:30", or "Next alarm:" produces "Next alarm: 07:30".
     * An empty string means no prefix — just the time is shown.
     */
    var prefixText: String
        get() = prefs.getString(KEY_PREFIX_TEXT, DEFAULT_PREFIX_TEXT) ?: DEFAULT_PREFIX_TEXT
        set(value) = prefs.edit { putString(KEY_PREFIX_TEXT, value) }

    /**
     * Whether to show the clock app name as a subtitle on the Smartspace target.
     * When false only the (prefixed) time is shown, giving a more compact single-line appearance.
     */
    var showSubtitle: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SUBTITLE, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_SUBTITLE, value) }

    /** Persisted dismissed alarm time — prevents re-showing the same alarm after dismiss. */
    var dismissedAlarmTime: Long
        get() = prefs.getLong(KEY_DISMISSED_ALARM_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_DISMISSED_ALARM_TIME, value) }

    /**
     * How many hours before an alarm it should start appearing in Smartspace.
     * Defaults to 12 hours to match the original hard-coded behaviour.
     */
    var displayWindowHours: Int
        get() = prefs.getInt(KEY_DISPLAY_WINDOW_HOURS, DEFAULT_DISPLAY_WINDOW_HOURS)
        set(value) = prefs.edit { putInt(KEY_DISPLAY_WINDOW_HOURS, value) }

    companion object {
        private const val PREFS_NAME = "next_alarm_settings"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_SHIZUKU_ENABLED = "shizuku_enabled"
        private const val KEY_DISMISSED_ALARM_TIME = "dismissed_alarm_time"
        private const val KEY_DISPLAY_WINDOW_HOURS = "display_window_hours"
        private const val KEY_PREFIX_TEXT = "prefix_text"
        private const val KEY_SHOW_SUBTITLE = "show_subtitle"
        const val DEFAULT_DISPLAY_WINDOW_HOURS = 12
        const val DEFAULT_PREFIX_TEXT = ""

        @Volatile
        private var instance: Settings? = null

        fun getInstance(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(context.applicationContext).also { instance = it }
        }
    }
}

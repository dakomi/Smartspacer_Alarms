package com.dakomi.smartspacer.alarms.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dakomi.smartspacer.alarms.complications.NextAlarmComplication
import com.dakomi.smartspacer.alarms.data.Settings
import com.dakomi.smartspacer.alarms.targets.NextAlarmTarget
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider

/**
 * Listens for system broadcasts that indicate the next alarm has changed and notifies
 * Smartspacer to re-query both the [NextAlarmTarget] and [NextAlarmComplication] providers.
 *
 * Relevant broadcasts:
 * - [AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED] — alarm added/removed/fired
 * - `"android.intent.action.TIME_SET"`                — user manually changed device time
 * - [Intent.ACTION_TIMEZONE_CHANGED]                — device timezone changed
 * - [Intent.ACTION_BOOT_COMPLETED]                  — ensure fresh data after reboot
 * - [Intent.ACTION_USER_PRESENT]                    — device unlocked / user returned to launcher:
 *   when Shizuku is enabled, triggers a Shizuku refresh at most once per
 *   [LAUNCHER_SHIZUKU_THROTTLE_MS] to keep the widget up-to-date without hammering dumpsys.
 */
class AlarmUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                // Signal to AlarmRepository that a Shizuku refresh is warranted on this event.
                // The flag is consumed (reset) by the repository after the Shizuku call so it
                // is never polled speculatively outside of a real alarm-change event.
                Settings.getInstance(context).shizukuRefreshRequested = true
                SmartspacerTargetProvider.notifyChange(context, NextAlarmTarget::class.java)
                SmartspacerComplicationProvider.notifyChange(context, NextAlarmComplication::class.java)
            }

            Intent.ACTION_USER_PRESENT -> {
                // The user has unlocked the device and is on the launcher — they can see the
                // Smartspacer widget. If Shizuku is enabled, schedule a refresh (throttled to
                // once per LAUNCHER_SHIZUKU_THROTTLE_MS) so the widget stays current without
                // calling dumpsys repeatedly as the user switches apps.
                val settings = Settings.getInstance(context)
                if (settings.shizukuEnabled) {
                    val now = System.currentTimeMillis()
                    if (now - settings.lastShizukuLauncherCheckTime >= LAUNCHER_SHIZUKU_THROTTLE_MS) {
                        settings.lastShizukuLauncherCheckTime = now
                        settings.shizukuRefreshRequested = true
                        SmartspacerTargetProvider.notifyChange(context, NextAlarmTarget::class.java)
                        SmartspacerComplicationProvider.notifyChange(context, NextAlarmComplication::class.java)
                    }
                }
            }
        }
    }

    private companion object {
        /** Minimum gap between launcher-triggered Shizuku refreshes: 15 minutes. */
        private const val LAUNCHER_SHIZUKU_THROTTLE_MS = 15L * 60L * 1_000L
    }
}

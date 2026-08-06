package com.dakomi.smartspacer.alarms.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dakomi.smartspacer.alarms.complications.NextAlarmComplication
import com.dakomi.smartspacer.alarms.targets.NextAlarmTarget
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider

/**
 * Listens for system broadcasts that indicate the next alarm has changed and notifies
 * Smartspacer to re-query both the [NextAlarmTarget] and [NextAlarmComplication] providers.
 *
 * Relevant broadcasts:
 * - [AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED] — alarm added/removed/fired
 * - [Intent.ACTION_TIME_SET]                        — user manually changed device time
 * - [Intent.ACTION_TIMEZONE_CHANGED]                — device timezone changed
 * - [Intent.ACTION_BOOT_COMPLETED]                  — ensure fresh data after reboot
 */
class AlarmUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            Intent.ACTION_TIME_SET,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                SmartspacerTargetProvider.notifyChange(context, NextAlarmTarget::class.java)
                SmartspacerComplicationProvider.notifyChange(context, NextAlarmComplication::class.java)
            }
        }
    }
}

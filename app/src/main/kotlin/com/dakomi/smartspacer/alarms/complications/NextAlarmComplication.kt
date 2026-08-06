package com.dakomi.smartspacer.alarms.complications

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import com.dakomi.smartspacer.alarms.BuildConfig
import com.dakomi.smartspacer.alarms.R
import com.dakomi.smartspacer.alarms.data.AlarmRepository
import com.dakomi.smartspacer.alarms.data.Settings
import com.dakomi.smartspacer.alarms.model.NextAlarm
import com.dakomi.smartspacer.alarms.ui.ClockAppPickerActivity
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider
import com.kieronquinn.app.smartspacer.sdk.utils.ComplicationTemplate
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import android.graphics.drawable.Icon as AndroidIcon

/**
 * Smartspacer Complication: Next Alarm
 *
 * Shows the next scheduled alarm from user-selected clock apps as a compact complication
 * alongside the Smartspace date. Displays an alarm icon and the formatted trigger time.
 *
 * Only shown when an alarm is within [DISPLAY_WINDOW] of firing.
 */
class NextAlarmComplication : SmartspacerComplicationProvider() {

    private val repository by lazy { AlarmRepository(provideContext()) }
    private val settings by lazy { Settings.getInstance(provideContext()) }

    override fun getSmartspaceActions(smartspacerId: String): List<SmartspaceAction> {
        val alarm = runBlocking {
            try {
                repository.getNextAlarm()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading next alarm", e)
                null
            }
        } ?: return emptyList()

        if (!isWithinDisplayWindow(alarm.triggerTime)) return emptyList()

        return listOf(buildComplication(alarm, smartspacerId))
    }

    private fun buildComplication(alarm: NextAlarm, smartspacerId: String): SmartspaceAction {
        val ctx = provideContext()
        val timeLabel = formatAlarmTime(ctx, alarm.triggerTime)

        val tapAction = alarm.showIntent?.let { TapAction(pendingIntent = it) }
            ?: ctx.packageManager.getLaunchIntentForPackage(alarm.packageName)
                ?.let { TapAction(intent = it) }

        return ComplicationTemplate.Basic(
            id = "${BuildConfig.APPLICATION_ID}.complication_$smartspacerId",
            icon = Icon(AndroidIcon.createWithResource(BuildConfig.APPLICATION_ID, R.drawable.ic_alarm)),
            content = Text(timeLabel),
            onClick = tapAction
        ).create()
    }

    override fun getConfig(smartspacerId: String?): Config {
        val ctx = provideContext()
        val selected = settings.selectedPackages
        val description = if (selected.isEmpty()) {
            ctx.getString(R.string.complication_description_all)
        } else {
            ctx.getString(R.string.complication_description_selected, selected.size)
        }
        return Config(
            label = ctx.getString(R.string.complication_label),
            description = description,
            icon = AndroidIcon.createWithResource(ctx, R.drawable.ic_alarm),
            setupActivity = Intent(ctx, ClockAppPickerActivity::class.java),
            configActivity = Intent(ctx, ClockAppPickerActivity::class.java),
            allowAddingMoreThanOnce = false,
            refreshPeriodMinutes = 15
        )
    }

    private fun isWithinDisplayWindow(triggerTime: Long): Boolean {
        val now = ZonedDateTime.now()
        val alarmTime = Instant.ofEpochMilli(triggerTime).atZone(ZoneId.systemDefault())
        val diff = Duration.between(now, alarmTime)
        return diff.isNegative.not() && diff <= DISPLAY_WINDOW
    }

    private fun formatAlarmTime(ctx: Context, triggerTime: Long): String =
        DateFormat.getTimeFormat(ctx).format(Date(triggerTime))

    private companion object {
        private const val TAG = "NextAlarmComplication"
        private val DISPLAY_WINDOW: Duration = Duration.ofHours(12)
    }
}

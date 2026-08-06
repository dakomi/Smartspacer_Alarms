package com.dakomi.smartspacer.alarms.targets

import android.content.ComponentName
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
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import android.graphics.drawable.Icon as AndroidIcon

/**
 * Smartspacer Target: Next Alarm
 *
 * Shows the next scheduled alarm from user-selected clock apps as a full Smartspace target
 * with an alarm icon, the formatted trigger time as the title, and the app name as the subtitle.
 *
 * The target is only displayed when an alarm is within [DISPLAY_WINDOW] of firing.
 * Tapping the target opens the corresponding clock app.
 */
class NextAlarmTarget : SmartspacerTargetProvider() {

    private val repository by lazy { AlarmRepository(provideContext()) }
    private val settings by lazy { Settings.getInstance(provideContext()) }

    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val alarm = runBlocking {
            try {
                repository.getNextAlarm()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading next alarm", e)
                null
            }
        } ?: return emptyList()

        if (!isWithinDisplayWindow(alarm.triggerTime)) return emptyList()
        // Respect the user's dismiss — don't re-show the same alarm
        if (alarm.triggerTime == settings.dismissedAlarmTime) return emptyList()

        return listOf(buildTarget(alarm, smartspacerId))
    }

    private fun buildTarget(alarm: NextAlarm, smartspacerId: String): SmartspaceTarget {
        val ctx = provideContext()
        val timeLabel = formatAlarmTime(ctx, alarm.triggerTime)
        val appLabel = getAppLabel(ctx, alarm.packageName)

        val launchIntent = alarm.showIntent?.let { TapAction(pendingIntent = it) }
            ?: getLaunchIntentForPackage(ctx, alarm.packageName)?.let { TapAction(intent = it) }

        return TargetTemplate.Basic(
            id = "${BuildConfig.APPLICATION_ID}.target_$smartspacerId",
            componentName = ComponentName(BuildConfig.APPLICATION_ID, TARGET_CLASS),
            featureType = SmartspaceTarget.FEATURE_ALARM,
            title = Text(timeLabel),
            subtitle = appLabel?.let { Text(it) },
            icon = Icon(AndroidIcon.createWithResource(BuildConfig.APPLICATION_ID, R.drawable.ic_alarm)),
            onClick = launchIntent
        ).create()
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean {
        // Persist the dismissed alarm time so this specific alarm isn't re-shown
        val currentAlarmTime = runBlocking {
            try { repository.getNextAlarm()?.triggerTime } catch (_: Exception) { null }
        }
        if (currentAlarmTime != null) {
            settings.dismissedAlarmTime = currentAlarmTime
        }
        notifyChange()
        return true
    }

    override fun getConfig(smartspacerId: String?): Config {
        val ctx = provideContext()
        val selected = settings.selectedPackages
        val description = if (selected.isEmpty()) {
            ctx.getString(R.string.target_description_all)
        } else {
            ctx.getString(R.string.target_description_selected, selected.size)
        }
        return Config(
            label = ctx.getString(R.string.target_label),
            description = description,
            icon = AndroidIcon.createWithResource(ctx, R.drawable.ic_alarm),
            setupActivity = Intent(ctx, ClockAppPickerActivity::class.java),
            configActivity = Intent(ctx, ClockAppPickerActivity::class.java),
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

    private fun getAppLabel(ctx: Context, packageName: String): String? = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        null
    }

    private fun getLaunchIntentForPackage(ctx: Context, packageName: String): Intent? =
        ctx.packageManager.getLaunchIntentForPackage(packageName)

    private companion object {
        private const val TAG = "NextAlarmTarget"
        private const val TARGET_CLASS = "com.dakomi.smartspacer.alarms.targets.NextAlarmTarget"
        private val DISPLAY_WINDOW: Duration = Duration.ofHours(12)
    }
}

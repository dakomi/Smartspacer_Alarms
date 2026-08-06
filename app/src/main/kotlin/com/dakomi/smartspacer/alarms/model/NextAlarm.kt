package com.dakomi.smartspacer.alarms.model

/**
 * Represents the next scheduled alarm from a clock app.
 */
data class NextAlarm(
    val packageName: String,
    val triggerTime: Long,
    val showIntent: android.app.PendingIntent? = null
)

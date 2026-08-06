package com.dakomi.smartspacer.alarms.data

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.IBinder
import android.util.Log
import com.dakomi.smartspacer.alarms.BuildConfig
import com.dakomi.smartspacer.alarms.model.NextAlarm
import com.dakomi.smartspacer.alarms.service.AlarmReaderService
import com.dakomi.smartspacer.alarms.service.IAlarmReaderService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Central data access layer for next alarm information.
 *
 * Uses two strategies to find the next alarm from user-selected clock apps:
 *
 * 1. **Shizuku mode** (preferred): Parses `dumpsys alarm` output via a privileged Shizuku
 *    UserService to enumerate all alarms from the selected apps and return the earliest future one.
 *
 * 2. **Fallback mode**: Uses the public `AlarmManager.getNextAlarmClock()` API and checks
 *    whether the alarm's creator package is among the user-selected apps. This only sees the
 *    globally-next alarm, so it may miss alarms from a preferred app when another app has an
 *    earlier alarm scheduled.
 */
class AlarmRepository(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val settings = Settings.getInstance(context)

    /**
     * Returns the next alarm from the user's selected clock apps, or null if none is active.
     *
     * Tries Shizuku first; falls back to AlarmManager if Shizuku is unavailable.
     */
    suspend fun getNextAlarm(): NextAlarm? {
        val selected = settings.selectedPackages
        Log.d(TAG, "getNextAlarm() called; selectedPackages=$selected")

        val shizukuGranted = isShizukuGranted()
        Log.d(TAG, "Shizuku check: binderAlive=${safeShizukuPing()} permissionGranted=$shizukuGranted")

        if (shizukuGranted) {
            try {
                Log.d(TAG, "Attempting Shizuku path")
                val result = readAlarmViaShizuku(selected)
                Log.d(TAG, "Shizuku path succeeded: result=$result")
                return result
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku alarm read failed, falling back to AlarmManager", e)
            }
        } else {
            Log.w(TAG, "Shizuku not available; using AlarmManager fallback")
        }

        return readAlarmViaAlarmManager(selected)
    }

    /** Returns true when the Shizuku binder is alive AND our permission is granted. */
    fun isShizukuGranted(): Boolean = try {
        Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    /** Returns true if Shizuku binder is alive without throwing, for logging. */
    private fun safeShizukuPing(): Boolean = try { Shizuku.pingBinder() } catch (_: Exception) { false }

    /** Requests Shizuku permission (asynchronous — result comes via onRequestPermissionResult). */
    fun requestShizukuPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    // -------------------------------------------------------------------------
    // Shizuku path
    // -------------------------------------------------------------------------

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, AlarmReaderService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("alarm_reader")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private suspend fun readAlarmViaShizuku(selectedPackages: Set<String>): NextAlarm? {
        Log.d(TAG, "readAlarmViaShizuku: binding Shizuku UserService")
        val service = bindShizukuService()
        if (service == null) {
            Log.w(TAG, "readAlarmViaShizuku: bindShizukuService returned null")
            return null
        }
        return try {
            Log.d(TAG, "readAlarmViaShizuku: service bound, calling getDumpsysAlarm()")
            val output = service.getDumpsysAlarm()
            unbindShizukuService()
            Log.d(TAG, "readAlarmViaShizuku: dumpsys output length=${output.length}")
            val packages = if (selectedPackages.isEmpty()) getClockAppPackages() else selectedPackages
            Log.d(TAG, "readAlarmViaShizuku: scanning for packages=$packages")
            val alarm = parseNextAlarmFromDumpsys(output, packages)
            Log.d(TAG, "readAlarmViaShizuku: parseNextAlarmFromDumpsys result=$alarm")
            if (alarm == null) return null
            // Enrich with showIntent from AlarmManager when the system's next alarm matches,
            // so tapping opens the alarm detail screen rather than just the app launcher.
            val systemNext = alarmManager?.nextAlarmClock
            Log.d(TAG, "readAlarmViaShizuku: systemNext=${systemNext?.triggerTime} pkg=${systemNext?.showIntent?.creatorPackage}")
            val enrichedShowIntent = if (
                systemNext != null &&
                systemNext.showIntent?.creatorPackage == alarm.packageName
            ) {
                systemNext.showIntent
            } else {
                null
            }
            alarm.copy(showIntent = enrichedShowIntent)
        } catch (e: Exception) {
            unbindShizukuService()
            throw e
        }
    }

    private suspend fun bindShizukuService(): IAlarmReaderService? =
        withTimeout(SHIZUKU_BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var connection: ServiceConnection? = null
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        Log.d(TAG, "bindShizukuService: onServiceConnected name=$name")
                        if (cont.isActive) cont.resume(IAlarmReaderService.Stub.asInterface(binder))
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        Log.w(TAG, "bindShizukuService: onServiceDisconnected name=$name")
                        if (cont.isActive) cont.resume(null)
                    }
                }
                try {
                    Shizuku.bindUserService(userServiceArgs, connection)
                    Log.d(TAG, "bindShizukuService: bindUserService called")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind Shizuku user service", e)
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { connection?.let { unbindShizukuService() } }
            }
        }

    private fun unbindShizukuService() {
        try {
            Shizuku.unbindUserService(userServiceArgs, null, true)
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------------------------
    // dumpsys alarm parser
    // -------------------------------------------------------------------------

    /**
     * Parses `dumpsys alarm` output and finds the earliest future RTC_WAKEUP alarm entry
     * whose creator package is in [allowedPackages].
     *
     * When [allowedPackages] is explicitly user-selected, both alarm-clock entries
     * (`tag=*alarm*`) **and** regular `RTC_WAKEUP` alarms from those packages are considered,
     * so third-party clock apps that don't use [AlarmManager.setAlarmClock] are also detected.
     */
    fun parseNextAlarmFromDumpsys(output: String, allowedPackages: Set<String>): NextAlarm? {
        if (output.isBlank() || allowedPackages.isEmpty()) {
            Log.d(TAG, "parseNextAlarmFromDumpsys: blank output or empty packages, returning null")
            return null
        }

        val now = System.currentTimeMillis()
        val candidates = mutableListOf<NextAlarm>()

        // Collect lines into per-alarm blocks delimited by alarm-type header lines.
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()

        for (raw in output.lines()) {
            val line = raw.trim()
            if (line.startsWith("RTC_WAKEUP") || line.startsWith("ELAPSED_WAKEUP") || line.startsWith("RTC ")) {
                if (current.isNotEmpty()) blocks.add(current)
                current = mutableListOf(line)
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) blocks.add(current)

        Log.d(TAG, "parseNextAlarmFromDumpsys: totalBlocks=${blocks.size} now=$now")

        for (block in blocks) {
            // We only care about RTC_WAKEUP (absolute-time wakeup) entries.
            val header = block.firstOrNull() ?: continue
            if (!header.startsWith("RTC_WAKEUP")) continue

            val blockText = block.joinToString("\n")

            // Extract the package name: first try the RTC_WAKEUP header line pattern
            // "when NNNNN com.package.name}", then fall back to the PendingIntentRecord line.
            val pkg = PKG_FROM_HEADER.find(header)?.groupValues?.get(1)
                ?: PKG_FROM_OPERATION.find(blockText)?.groupValues?.get(1)
                ?: continue

            // Only consider alarms from user-selected packages.
            if (pkg !in allowedPackages) continue

            // For explicitly-selected packages, accept both alarm-clock entries and regular
            // RTC_WAKEUP alarms (covering third-party apps that don't use setAlarmClock()).
            // However, skip entries that are clearly not user-facing alarms (e.g. system sync
            // wakeups tagged with known non-alarm tags).
            val isAlarmClock = blockText.contains("tag=*alarm*")
            val hasNonAlarmTag = NON_ALARM_TAGS.any { blockText.contains(it) }
            Log.d(TAG, "parseNextAlarmFromDumpsys: matched pkg=$pkg isAlarmClock=$isAlarmClock hasNonAlarmTag=$hasNonAlarmTag")
            if (!isAlarmClock && hasNonAlarmTag) {
                Log.d(TAG, "parseNextAlarmFromDumpsys: skipping non-alarm-tagged entry for pkg=$pkg")
                continue
            }

            // Extract the absolute trigger time from "when=NNNNN" (NOT "whenElapsed=").
            val whenMs = WHEN_PATTERN.find(blockText)?.groupValues?.get(1)?.toLongOrNull()
                ?: continue
            if (whenMs <= now) {
                Log.d(TAG, "parseNextAlarmFromDumpsys: skipping past alarm pkg=$pkg whenMs=$whenMs")
                continue
            }

            Log.d(TAG, "parseNextAlarmFromDumpsys: candidate pkg=$pkg whenMs=$whenMs")
            candidates.add(NextAlarm(packageName = pkg, triggerTime = whenMs))
        }

        val best = candidates.minByOrNull { it.triggerTime }
        Log.d(TAG, "parseNextAlarmFromDumpsys: ${candidates.size} candidates, best=$best")
        return best
    }

    // -------------------------------------------------------------------------
    // Fallback: AlarmManager.getNextAlarmClock()
    // -------------------------------------------------------------------------

    private fun readAlarmViaAlarmManager(selectedPackages: Set<String>): NextAlarm? {
        val info = alarmManager?.nextAlarmClock
        Log.d(TAG, "readAlarmViaAlarmManager: nextAlarmClock=${info?.triggerTime} creatorPkg=${info?.showIntent?.creatorPackage}")
        if (info == null) {
            Log.d(TAG, "readAlarmViaAlarmManager: no system next alarm")
            return null
        }
        val pkg = info.showIntent?.creatorPackage
        // Require a known creator package when the user has selected specific apps.
        // This prevents system-scheduled pseudo-alarms (e.g. sync wakeups rounded to the
        // nearest 5 minutes) from appearing when their package can't be identified or doesn't
        // match the user's selection.
        if (selectedPackages.isNotEmpty()) {
            if (pkg == null || pkg !in selectedPackages) {
                Log.w(TAG, "readAlarmViaAlarmManager: alarm pkg=$pkg not in selectedPackages=$selectedPackages — discarding")
                return null
            }
        } else if (pkg == null) {
            Log.w(TAG, "readAlarmViaAlarmManager: alarm has null creator package and no selection — discarding")
            return null
        }
        // pkg is guaranteed non-null here: both null-escape paths above return early.
        Log.d(TAG, "readAlarmViaAlarmManager: returning alarm pkg=$pkg triggerTime=${info.triggerTime}")
        return NextAlarm(
            packageName = pkg!!,
            triggerTime = info.triggerTime,
            showIntent = info.showIntent
        )
    }

    // -------------------------------------------------------------------------
    // Clock app discovery
    // -------------------------------------------------------------------------

    /**
     * Returns the set of installed apps that handle the SET_ALARM intent
     * (i.e., apps that are likely clock/alarm apps).
     */
    fun getClockAppPackages(): Set<String> {
        val intent = Intent("android.intent.action.SET_ALARM")
        return context.packageManager
            .queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    /** Returns [AppInfo] for all discovered clock apps. */
    fun getInstalledClockApps(): List<AppInfo> {
        val pm = context.packageManager
        return getClockAppPackages().mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                AppInfo(
                    packageName = pkg,
                    label = pm.getApplicationLabel(ai).toString(),
                    icon = pm.getApplicationIcon(ai)
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { it.label }
    }

    // -------------------------------------------------------------------------

    data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )

    private companion object {
        private const val TAG = "AlarmRepository"
        private const val SHIZUKU_BIND_TIMEOUT_MS = 5_000L

        /** Matches "when=13-digit-epoch-ms" — exactly 13 digits to avoid 10-digit epoch-second values */
        private val WHEN_PATTERN = Regex("""\bwhen=(\d{13})\b""")

        /** Extracts package from a RTC_WAKEUP header line: "...when NNNNN com.pkg.name}" */
        private val PKG_FROM_HEADER = Regex("""\bwhen\s+\d+\s+([\w.]+)\}""")

        /** Extracts package from the PendingIntentRecord in the operation line */
        private val PKG_FROM_OPERATION = Regex("""PendingIntentRecord\{[^ ]+ ([\w.]+)""")

        /**
         * Literal-substring filters for non-user-visible RTC_WAKEUP entries.
         *
         * In `dumpsys alarm` output, alarm tags appear as literal strings including the
         * asterisks (e.g. `tag=*alarm*`, `tag=*sync*`). These strings are matched with
         * [String.contains] as **literal substrings**, NOT as glob or regex patterns —
         * the asterisks are part of the actual tag text, not wildcards.
         */
        private val NON_ALARM_TAGS = listOf(
            "tag=*sync*",
            "tag=*job*",
            "tag=*gcm*",
            "tag=*fcm*",
            "tag=*wake*",
            "tag=*net*"
        )
    }
}

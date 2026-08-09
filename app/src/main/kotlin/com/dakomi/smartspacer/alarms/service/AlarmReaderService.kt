package com.dakomi.smartspacer.alarms.service

import android.util.Log

/**
 * Shizuku UserService implementation.
 *
 * This class runs in a privileged process spawned by Shizuku (as the adb/shell user),
 * giving it access to `dumpsys alarm` which requires elevated permissions.
 *
 * The class is NOT declared in AndroidManifest.xml — Shizuku manages process spawning.
 */
class AlarmReaderService : IAlarmReaderService.Stub() {

    override fun getDumpsysAlarm(): String {
        return try {
            val process = ProcessBuilder("dumpsys", "alarm")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute dumpsys alarm", e)
            ""
        }
    }

    override fun getLogcat(tag: String): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-s", tag)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read logcat for tag $tag", e)
            ""
        }
    }

    private companion object {
        private const val TAG = "AlarmReaderService"
    }
}

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
        Log.d(TAG, "getDumpsysAlarm() called; running as uid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()}")
        return try {
            val process = Runtime.getRuntime().exec("dumpsys alarm")
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            Log.d(TAG, "dumpsys alarm completed: exitCode=$exitCode outputLength=${output.length}")
            if (output.isBlank()) Log.w(TAG, "dumpsys alarm returned blank output")
            output
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute dumpsys alarm", e)
            ""
        }
    }

    private companion object {
        private const val TAG = "AlarmReaderService"
    }
}

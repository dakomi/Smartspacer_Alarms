package com.dakomi.smartspacer.alarms

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dakomi.smartspacer.alarms.data.AlarmRepository
import com.dakomi.smartspacer.alarms.data.Settings

/**
 * Entry-point activity shown in the launcher.
 *
 * Displays brief information about the plugin and its current setup status
 * (Shizuku permission, selected clock apps).
 */
class MainActivity : AppCompatActivity() {

    private val settings by lazy { Settings.getInstance(this) }
    private val repository by lazy { AlarmRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updateStatusText()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun updateStatusText() {
        val shizukuOk = repository.isShizukuGranted()
        val selected = settings.selectedPackages

        val shizukuStatus = if (shizukuOk) {
            getString(R.string.status_shizuku_ok)
        } else {
            getString(R.string.status_shizuku_missing)
        }

        val appsStatus = if (selected.isEmpty()) {
            getString(R.string.status_apps_all)
        } else {
            getString(R.string.status_apps_selected, selected.size)
        }

        findViewById<TextView>(R.id.tv_shizuku_status).text = shizukuStatus
        findViewById<TextView>(R.id.tv_apps_status).text = appsStatus
    }
}

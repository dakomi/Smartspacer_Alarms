package com.dakomi.smartspacer.alarms

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dakomi.smartspacer.alarms.data.AlarmRepository
import com.dakomi.smartspacer.alarms.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry-point activity shown in the launcher.
 *
 * Displays brief information about the plugin and its current setup status
 * (Shizuku permission, selected clock apps).
 *
 * The "Export Widget Logs" button captures any logcat output tagged "Views"
 * (written by the ClockWidgetProvider classes via dumpViewHierarchy) and
 * shares it so the alarm widget view hierarchy can be inspected externally.
 */
class MainActivity : AppCompatActivity() {

    private val settings by lazy { Settings.getInstance(this) }
    private val repository by lazy { AlarmRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updateStatusText()
        setupExportButton()
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

    private fun setupExportButton() {
        findViewById<Button>(R.id.btn_export_widget_logs).setOnClickListener {
            exportWidgetLogs()
        }
    }

    private fun exportWidgetLogs() {
        if (!repository.isShizukuGranted()) {
            Toast.makeText(this, R.string.shizuku_not_running, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) {
                try {
                    repository.getWidgetLogs()
                } catch (e: Exception) {
                    null
                }
            }
            if (logs.isNullOrBlank()) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.widget_logs_empty,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                shareText(logs, getString(R.string.widget_logs_subject))
            }
        }
    }

    private fun shareText(text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.widget_logs_share_title)))
    }
}

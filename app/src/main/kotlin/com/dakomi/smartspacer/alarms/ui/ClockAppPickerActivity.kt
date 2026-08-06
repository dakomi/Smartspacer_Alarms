package com.dakomi.smartspacer.alarms.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dakomi.smartspacer.alarms.R
import com.dakomi.smartspacer.alarms.complications.NextAlarmComplication
import com.dakomi.smartspacer.alarms.data.AlarmRepository
import com.dakomi.smartspacer.alarms.data.Settings
import com.dakomi.smartspacer.alarms.targets.NextAlarmTarget
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import rikka.shizuku.Shizuku

/**
 * Lets the user select which installed clock app(s) should be used as the alarm source.
 *
 * This activity is used both as the **setup activity** (shown when the user first adds the
 * target/complication in Smartspacer) and as the **configuration activity** (accessible from
 * the Smartspacer settings to change the selection later).
 *
 * If no apps are selected, all apps responding to SET_ALARM will be monitored.
 */
class ClockAppPickerActivity : AppCompatActivity() {

    private val settings by lazy { Settings.getInstance(this) }
    private val repository by lazy { AlarmRepository(this) }

    private val adapter = ClockAppAdapter()
    private var clockApps: List<AlarmRepository.AppInfo> = emptyList()

    /** Shizuku permission result listener — must be removed in onDestroy. */
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_SHIZUKU) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    settings.shizukuEnabled = true
                }
                checkShizukuAndRefreshStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock_picker)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        setupRecyclerView()
        setupButtons()
        checkShizukuAndRefreshStatus()
        loadClockApps()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_grant_shizuku).setOnClickListener { requestShizuku() }
    }

    private fun checkShizukuAndRefreshStatus() {
        val shizukuStatus = findViewById<TextView>(R.id.tv_shizuku_status)
        val grantButton = findViewById<Button>(R.id.btn_grant_shizuku)

        if (repository.isShizukuGranted()) {
            shizukuStatus.text = getString(R.string.shizuku_granted)
            shizukuStatus.setTextColor(getColor(R.color.status_ok))
            grantButton.visibility = View.GONE
        } else {
            shizukuStatus.text = getString(R.string.shizuku_not_granted)
            shizukuStatus.setTextColor(getColor(R.color.status_warning))
            grantButton.visibility = View.VISIBLE
        }
    }

    private fun loadClockApps() {
        clockApps = repository.getInstalledClockApps()
        val selected = settings.selectedPackages
        adapter.items = clockApps.map { app ->
            ClockAppItem(app, isChecked = app.packageName in selected)
        }
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, R.string.shizuku_not_running, Toast.LENGTH_LONG).show()
            return
        }
        repository.requestShizukuPermission(REQUEST_SHIZUKU)
    }

    private fun saveAndFinish() {
        val selected = adapter.items
            .filter { it.isChecked }
            .map { it.app.packageName }
            .toSet()
        settings.selectedPackages = selected

        // Notify Smartspacer to refresh both providers
        SmartspacerTargetProvider.notifyChange(this, NextAlarmTarget::class.java)
        SmartspacerComplicationProvider.notifyChange(this, NextAlarmComplication::class.java)

        setResult(RESULT_OK)
        finish()
    }

    // -------------------------------------------------------------------------
    // RecyclerView adapter
    // -------------------------------------------------------------------------

    data class ClockAppItem(val app: AlarmRepository.AppInfo, var isChecked: Boolean)

    inner class ClockAppAdapter : RecyclerView.Adapter<ClockAppAdapter.ViewHolder>() {

        var items: List<ClockAppItem> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_app_icon)
            val label: TextView = view.findViewById(R.id.tv_app_label)
            val checkbox: CheckBox = view.findViewById(R.id.cb_selected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_clock_app, parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.app.icon)
            holder.label.text = item.app.label
            holder.checkbox.isChecked = item.isChecked
            holder.itemView.setOnClickListener {
                item.isChecked = !item.isChecked
                holder.checkbox.isChecked = item.isChecked
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private companion object {
        private const val REQUEST_SHIZUKU = 1001
    }
}


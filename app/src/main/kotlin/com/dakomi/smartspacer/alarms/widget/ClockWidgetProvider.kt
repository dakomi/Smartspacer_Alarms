package com.dakomi.smartspacer.alarms.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerWidgetProvider

/**
 * Base widget provider used to explore the RemoteViews emitted by clock app widgets.
 *
 * When Smartspacer calls [onWidgetChanged] the received [RemoteViews] is inflated and the
 * resulting view hierarchy is dumped to logcat under the "Views" tag so it can be captured
 * and exported from [MainActivity].  Text content of every [TextView] in the hierarchy is
 * included, which is what we need to determine whether the alarm name is available.
 *
 * Concrete subclasses declare the [targetPackage] whose widgets should be bound, and are each
 * registered as separate ContentProviders so Smartspacer can manage them independently.
 *
 * **Setup**: In Smartspacer add the matching target/complication, select this widget provider
 * during setup, and wait for an update. Then press "Export Widget Logs" in the main activity.
 */
abstract class ClockWidgetProvider : SmartspacerWidgetProvider() {

    /** Package name of the clock app whose widget this provider should bind to. */
    abstract val targetPackage: String

    override fun onWidgetChanged(smartspacerId: String, remoteViews: RemoteViews?) {
        val ctx = provideContext()
        Log.d(LOG_TAG, "=== [$targetPackage] onWidgetChanged smartspacerId=$smartspacerId ===")
        if (remoteViews == null) {
            Log.d(LOG_TAG, "[$targetPackage] remoteViews is null")
            return
        }
        try {
            val parent = FrameLayout(ctx)
            val view = remoteViews.apply(ctx, parent)
            dumpViewHierarchy(LOG_TAG, "[$targetPackage]", view, 0)
        } catch (e: Exception) {
            Log.d(LOG_TAG, "[$targetPackage] Failed to apply RemoteViews: ${e.message}")
        }
    }

    override fun getConfig(smartspacerId: String) = Config()

    override fun getAppWidgetProviderInfo(smartspacerId: String): AppWidgetProviderInfo? {
        val wm = AppWidgetManager.getInstance(provideContext())
        val providers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wm.getInstalledProvidersForPackage(targetPackage, null)
        } else {
            wm.installedProviders.filter { it.provider.packageName == targetPackage }
        }
        if (providers.isEmpty()) {
            Log.w(LOG_TAG, "No widget providers found for $targetPackage")
        } else {
            Log.d(LOG_TAG, "Available widget providers for $targetPackage: ${providers.map { it.provider }}")
        }
        return providers.firstOrNull()
    }

    private fun dumpViewHierarchy(tag: String, prefix: String, view: View, depth: Int) {
        val indent = "  ".repeat(depth)
        val id = try {
            if (view.id != View.NO_ID) view.resources.getResourceName(view.id) else "no-id"
        } catch (_: Exception) {
            view.id.toString()
        }
        val text = (view as? TextView)?.let { " text=\"${it.text}\"" }.orEmpty()
        Log.d(tag, "$prefix$indent ${view.javaClass.simpleName} id=$id$text")
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                dumpViewHierarchy(tag, prefix, view.getChildAt(i), depth + 1)
            }
        }
    }

    private companion object {
        private const val LOG_TAG = "Views"
    }
}

/** Widget probe for com.best.deskclock (has a dedicated Next Alarm widget). */
class BestDeskClockWidgetProvider : ClockWidgetProvider() {
    override val targetPackage = "com.best.deskclock"
}

/** Widget probe for com.android.deskclock (Digital Clock widget showing next alarm). */
class AndroidDeskClockWidgetProvider : ClockWidgetProvider() {
    override val targetPackage = "com.android.deskclock"
}

/** Widget probe for com.google.android.deskclock (Digital Clock widget showing next alarm). */
class GoogleDeskClockWidgetProvider : ClockWidgetProvider() {
    override val targetPackage = "com.google.android.deskclock"
}

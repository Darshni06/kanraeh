package com.kanraeh.app.companion

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** MODULE: companion - the permissions the walking avatar and screen-time need, with status checks and settings shortcuts. */
object Permissions {
    fun canOverlay(c: Context) = Settings.canDrawOverlays(c)
    fun overlayIntent(c: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
    fun usageIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    fun listenerGranted(c: Context) = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
    fun listenerIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun batteryOk(c: Context) = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
    fun batteryIntent(c: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
    fun appDetailsIntent(c: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))

    /** Vivo / iQOO "autostart" screens vary by model; try known ones, else fall back to app details. */
    fun vivoAutostartIntents(): List<Intent> = listOf(
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.vivo.abe" to "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
    ).map { Intent().setComponent(ComponentName(it.first, it.second)) }
}

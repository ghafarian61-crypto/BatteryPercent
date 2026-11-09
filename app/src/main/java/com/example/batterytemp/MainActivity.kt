package com.example.batterytemp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/**
 * Invisible launcher: starts the service and walks through the required permissions,
 * one per launch (overlay, notifications, battery optimisation).
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startForegroundService(Intent(this, MonitorService::class.java))
        } catch (e: Exception) {
        }

        val pkg = Uri.parse("package:$packageName")
        val pm = getSystemService(PowerManager::class.java)
        when {
            !Settings.canDrawOverlays(this) -> {
                Toast.makeText(this, "Allow \"Display over other apps\" for Battery Percent", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
            }
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            !pm.isIgnoringBatteryOptimizations(packageName) ->
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            else -> {
                Toast.makeText(this, "Battery Percent is running", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

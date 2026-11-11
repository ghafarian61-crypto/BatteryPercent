package com.example.batterypercent

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
 * Invisible launcher: starts the service and asks for every missing permission in ONE run,
 * one after the other, without the app closing in between:
 * notifications -> battery optimisation -> display over other apps.
 */
class MainActivity : Activity() {

    private companion object {
        const val RC_NOTIF = 1
        const val RC_BATTERY = 2
        const val RC_OVERLAY = 3
        const val STEPS = 3
    }

    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startForegroundService(Intent(this, MonitorService::class.java))
        } catch (e: Exception) {
        }

        if (needsNotifications() || needsBatteryExemption() || needsOverlay()) {
            Toast.makeText(this, R.string.msg_permissions_intro, Toast.LENGTH_LONG).show()
        }
        next()
    }

    private fun needsNotifications() = Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun needsBatteryExemption() =
        !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun needsOverlay() = !Settings.canDrawOverlays(this)

    /** Runs the next permission request that is still missing; every request returns here. */
    private fun next() {
        val pkg = Uri.parse("package:$packageName")
        while (step < STEPS) {
            when (step++) {
                0 -> if (needsNotifications()) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_NOTIF)
                    return
                }
                1 -> if (needsBatteryExemption()) {
                    startActivityForResult(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg), RC_BATTERY
                    )
                    return
                }
                2 -> if (needsOverlay()) {
                    startActivityForResult(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg), RC_OVERLAY
                    )
                    return
                }
            }
        }
        val msg = if (needsOverlay()) R.string.msg_overlay_missing else R.string.msg_running
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finishAndRemoveTask()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        next()
    }

    @Deprecated("Framework Activity result API is fine here (no AndroidX).")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        next()
    }
}

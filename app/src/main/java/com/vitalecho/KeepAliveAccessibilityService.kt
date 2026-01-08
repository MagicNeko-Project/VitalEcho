package com.vitalecho

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class KeepAliveAccessibilityService : AccessibilityService() {

    private var lastBroadcastTime = 0L
    private val DEBOUNCE_DELAY = 5000L // Increased to 5s to reduce spam
    private val TAG = "VitalEchoAccessService"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        // Trigger service immediately on connect
        ensureServiceRunning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBroadcastTime > DEBOUNCE_DELAY) {

                // 1. Notify Main Service of user activity (for heartbeat/status logic)
                val intent = Intent("com.vitalecho.ACTION_USER_ACTIVE")
                intent.setPackage(packageName)
                sendBroadcast(intent)

                // 2. "Keep Alive": Ensure the Foreground Service is actually running
                ensureServiceRunning()

                lastBroadcastTime = now
            }
        }
    }

    private fun ensureServiceRunning() {
        try {
            val serviceIntent = Intent(this, NetworkMonitorService::class.java)
            // Passing a specific action or extra could distinguish this "keep-alive" ping
            serviceIntent.action = "ACTION_KEEP_ALIVE"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service from Accessibility", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
}

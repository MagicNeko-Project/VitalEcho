package com.vitalecho

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.os.SystemClock

class KeepAliveAccessibilityService : AccessibilityService() {

    private var lastBroadcastTime = 0L
    private val DEBOUNCE_DELAY = 2000L // 2 seconds

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBroadcastTime > DEBOUNCE_DELAY) {
                val intent = Intent("com.vitalecho.ACTION_USER_ACTIVE")
                intent.setPackage(packageName)
                sendBroadcast(intent)
                lastBroadcastTime = now
            }
        }
    }

    override fun onInterrupt() {
    }
}

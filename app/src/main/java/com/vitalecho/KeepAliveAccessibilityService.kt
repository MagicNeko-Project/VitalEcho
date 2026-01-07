package com.vitalecho

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

class KeepAliveAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to process events, just being enabled keeps us alive.
    }

    override fun onInterrupt() {
    }
}

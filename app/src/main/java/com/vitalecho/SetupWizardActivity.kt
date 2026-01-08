package com.vitalecho

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var btnAccessibility: Button
    private lateinit var btnBattery: Button
    private lateinit var btnFinish: Button
    private lateinit var tvStatusAccessibility: TextView
    private lateinit var tvStatusBattery: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)

        btnAccessibility = findViewById(R.id.btn_setup_accessibility)
        btnBattery = findViewById(R.id.btn_setup_battery)
        btnFinish = findViewById(R.id.btn_finish)
        tvStatusAccessibility = findViewById(R.id.tv_status_accessibility)
        tvStatusBattery = findViewById(R.id.tv_status_battery)

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnBattery.setOnClickListener {
            requestBatteryOptimization()
        }

        btnFinish.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        checkStatus()
    }

    private fun checkStatus() {
        // Check Accessibility
        if (isAccessibilityServiceEnabled(this, KeepAliveAccessibilityService::class.java)) {
            tvStatusAccessibility.text = "已启用 (Enabled)"
            tvStatusAccessibility.setTextColor(getColor(android.R.color.holo_green_dark))
            btnAccessibility.isEnabled = false
        } else {
            tvStatusAccessibility.text = "未启用 (Disabled)"
            tvStatusAccessibility.setTextColor(getColor(android.R.color.holo_red_dark))
            btnAccessibility.isEnabled = true
        }

        // Check Battery
        if (isIgnoringBatteryOptimizations()) {
            tvStatusBattery.text = "已忽略 (Optimized)"
            tvStatusBattery.setTextColor(getColor(android.R.color.holo_green_dark))
            btnBattery.isEnabled = false
        } else {
            tvStatusBattery.text = "未忽略 (Restricted)"
            tvStatusBattery.setTextColor(getColor(android.R.color.holo_red_dark))
            btnBattery.isEnabled = true
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.name}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val stringColonSplitter = TextUtils.SimpleStringSplitter(':')
        stringColonSplitter.setString(enabledServicesSetting)

        while (stringColonSplitter.hasNext()) {
            val componentName = stringColonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimization() {
        if (!isIgnoringBatteryOptimizations()) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }
}

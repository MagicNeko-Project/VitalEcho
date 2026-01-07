package com.vitalecho

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var switchAutoManual: Switch
    private lateinit var radioGroupStatus: RadioGroup
    private lateinit var btnOpenAccessibility: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchAutoManual = findViewById(R.id.switch_auto_manual)
        radioGroupStatus = findViewById(R.id.radio_group_status)
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility)

        val prefs = getSharedPreferences("VitalEchoPrefs", Context.MODE_PRIVATE)

        // Load saved state
        val isAuto = prefs.getBoolean("is_auto", true)
        switchAutoManual.isChecked = isAuto
        updateUI(isAuto)

        switchAutoManual.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_auto", isChecked).apply()
            updateUI(isChecked)

            // Notify Service about change?
            // Simplest way is restarting service or sending broadcast.
            // Since service reads prefs in loop/on change, we can just restart it to be sure
            // or let it pick up changes.
            // We will make Service listen to SharedPrefs or check periodically.
            // For now, let's just trigger a service restart/start command.
            val intent = Intent(this, NetworkMonitorService::class.java)
            intent.action = if (isChecked) "ACTION_AUTO" else "ACTION_MANUAL"
            startServiceCompat(intent)
        }

        radioGroupStatus.setOnCheckedChangeListener { _, checkedId ->
            if (!switchAutoManual.isChecked) {
                val status = when (checkedId) {
                    R.id.radio_action -> "行动模式"
                    R.id.radio_standby -> "待机模式"
                    R.id.radio_offline -> "离线模式"
                    else -> ""
                }
                if (status.isNotEmpty()) {
                    // Send manual update via Service
                    val intent = Intent(this, NetworkMonitorService::class.java)
                    intent.action = "ACTION_UPDATE_MANUAL"
                    intent.putExtra("manual_status", status)
                    startServiceCompat(intent)
                }
            }
        }

        btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Start the service initially
        val intent = Intent(this, NetworkMonitorService::class.java)
        startServiceCompat(intent)
    }

    private fun updateUI(isAuto: Boolean) {
        switchAutoManual.text = if (isAuto) getString(R.string.mode_auto) else getString(R.string.mode_manual)
        for (i in 0 until radioGroupStatus.childCount) {
            radioGroupStatus.getChildAt(i).isEnabled = !isAuto
        }
    }

    private fun startServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

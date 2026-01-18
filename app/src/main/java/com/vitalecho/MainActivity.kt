package com.vitalecho

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var switchAutoManual: Switch
    private lateinit var radioGroupStatus: RadioGroup
    private lateinit var btnOpenAccessibility: Button
    private lateinit var tvServerStatus: TextView

    private val serverStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NetworkMonitorService.ACTION_SERVER_STATUS) {
                val isOnline = intent.getBooleanExtra(NetworkMonitorService.EXTRA_SERVER_ONLINE, false)
                updateServerStatusUI(isOnline)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchAutoManual = findViewById(R.id.switch_auto_manual)
        radioGroupStatus = findViewById(R.id.radio_group_status)
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility)
        tvServerStatus = findViewById(R.id.tv_server_status)

        val prefs = getSharedPreferences("VitalEchoPrefs", Context.MODE_PRIVATE)

        // Load saved state
        val isAuto = prefs.getBoolean("is_auto", true)
        switchAutoManual.isChecked = isAuto
        updateUI(isAuto)

        switchAutoManual.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_auto", isChecked).apply()
            updateUI(isChecked)

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
                    val intent = Intent(this, NetworkMonitorService::class.java)
                    intent.action = "ACTION_UPDATE_MANUAL"
                    intent.putExtra("manual_status", status)
                    startServiceCompat(intent)
                }
            }
        }

        btnOpenAccessibility.setOnClickListener {
            val intent = Intent(this, SetupWizardActivity::class.java)
            startActivity(intent)
        }

        // Start the service initially
        val intent = Intent(this, NetworkMonitorService::class.java)
        startServiceCompat(intent)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(NetworkMonitorService.ACTION_SERVER_STATUS)
        registerReceiver(serverStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(serverStatusReceiver)
    }

    private fun updateServerStatusUI(isOnline: Boolean) {
        runOnUiThread {
            if (isOnline) {
                tvServerStatus.text = getString(R.string.server_online)
                tvServerStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
            } else {
                tvServerStatus.text = getString(R.string.server_offline)
                tvServerStatus.setTextColor(Color.parseColor("#F44336")) // Red
            }
        }
    }

    private fun updateUI(isAuto: Boolean) {
        switchAutoManual.text = if (isAuto) getString(R.string.mode_auto) else getString(R.string.mode_manual)
        for (i in 0 until radioGroupStatus.childCount) {
            radioGroupStatus.getChildAt(i).isEnabled = !isAuto
        }
    }

    private fun startServiceCompat(intent: Intent) {
        startForegroundService(intent)
    }
}

package com.vitalecho

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class NetworkMonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var powerManager: PowerManager
    private lateinit var prefs: SharedPreferences
    @Volatile
    private var isScreenOn = true
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Configuration
    private val SERVER_URL = "https://vitalecho.kusa.dev"
    private val API_TOKEN = "secret"

    companion object {
        private const val TAG = "VitalEchoService"
        private const val HEARTBEAT_INTERVAL = 10000L // 10 seconds
        private const val CHANNEL_ID = "VitalEchoChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        prefs = getSharedPreferences("VitalEchoPrefs", Context.MODE_PRIVATE)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive

        registerScreenReceiver()
        startForegroundServiceCompat()
        registerNetworkCallback()
        startHeartbeat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                "ACTION_UPDATE_MANUAL" -> {
                    val status = intent.getStringExtra("manual_status")
                    if (!status.isNullOrEmpty()) {
                        sendStatusUpdate(status)
                    }
                }
                "ACTION_AUTO" -> {
                    // Re-check network immediately
                     checkCurrentNetwork()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VitalEcho Network Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (isAutoMode()) {
                checkNetworkType(network)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            if (isAutoMode()) {
                checkNetworkType(network)
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            if (isAutoMode()) {
                checkCurrentNetwork()
            }
        }
    }

    private var lastReportedType = ""

    private fun isAutoMode(): Boolean {
        return prefs.getBoolean("is_auto", true)
    }

    private fun checkCurrentNetwork() {
        if (!isAutoMode()) return

        // Compat for activeNetwork
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                checkNetworkType(activeNetwork)
            } else {
                 // No active network
            }
        } else {
            // For API 21-22, we use getAllNetworks or activeNetworkInfo (deprecated but valid)
            // Or just rely on callbacks.
            // Since minSdk is 23, we are safe with activeNetwork.
        }
    }

    private fun checkNetworkType(network: Network) {
        // Ensure we are checking the active default network to avoid race conditions
        // where a secondary network (e.g. Cellular) updates while WiFi is primary.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null && activeNetwork != network) {
                // The callback network is not the active default network. Ignore.
                return
            }
        }

        val caps = connectivityManager.getNetworkCapabilities(network) ?: return
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> getString(R.string.status_standby) // "待机模式"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getString(R.string.status_action) // "行动模式"
            else -> "UNKNOWN"
        }

        if (type != "UNKNOWN" && type != lastReportedType) {
            lastReportedType = type
            Log.d(TAG, "Network changed to: $type")
            sendStatusUpdate(type)
        }
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
             connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
             val request = android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
             connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                // Only send heartbeat if screen is ON.
                // If screen is OFF for a long time (Backend Timeout), Backend will switch to Offline Mode.
                if (isScreenOn) {
                    sendHeartbeat()
                }
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }

    private fun sendStatusUpdate(type: String) {
        scope.launch {
            try {
                val json = "{\"network_type\": \"$type\"}"
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("$SERVER_URL/status")
                    .addHeader("X-API-TOKEN", API_TOKEN)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to update status: ${response.code}")
                    } else {
                        Log.i(TAG, "Status updated successfully to $type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending status", e)
            }
        }
    }

    private fun sendHeartbeat() {
         scope.launch {
            try {
                val request = Request.Builder()
                    .url("$SERVER_URL/heartbeat")
                    .addHeader("X-API-TOKEN", API_TOKEN)
                    .post("".toRequestBody(null))
                    .build()

                client.newCall(request).execute().use { response ->
                     if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to send heartbeat: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending heartbeat", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        job.cancel()
        Log.d(TAG, "Service Destroyed")
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    lastReportedType = "" // Reset so next check sends update
                    Log.d(TAG, "Screen OFF - Stopping Heartbeats")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.d(TAG, "Screen ON - Resuming Heartbeats")
                    checkCurrentNetwork()
                    sendHeartbeat()
                }
                "com.vitalecho.ACTION_USER_ACTIVE" -> {
                    if (!isScreenOn) {
                        isScreenOn = true
                        Log.d(TAG, "User Active - Resuming Heartbeats")
                        checkCurrentNetwork()
                        sendHeartbeat()
                    }
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction("com.vitalecho.ACTION_USER_ACTIVE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }
}

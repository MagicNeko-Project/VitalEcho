package com.vitalecho

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Configuration
    private val SERVER_URL = "http://YOUR_SERVER_IP:8000"
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
        startForegroundServiceCompat()
        registerNetworkCallback()
        startHeartbeat()
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

        // Ensure R.mipmap.ic_launcher exists.
        // Note: In a real app, use a proper drawable resource ID.
        // Since we created ic_launcher.xml in mipmap, this ID should resolve.
        // If not, we fall back to generic system icon in a real implementation,
        // but for compilation we need a valid R ref.
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VitalEcho")
            .setContentText("Monitoring network status...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "Default network available")
            checkNetworkType(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            checkNetworkType(network)
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "Default network lost")
            // When default network is lost, we might need to wait for the new default network
            // to be available or check if there is another one.
            // But registerDefaultNetworkCallback handles the switch automatically.
            // If we lost the default network, we are effectively offline until a new one appears
            // or we might have switched.
            // We should re-check active network.
            checkCurrentNetwork()
        }
    }

    private var lastReportedType = ""

    private fun checkCurrentNetwork() {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            checkNetworkType(activeNetwork)
        } else {
             // No active network
             // We could report OFFLINE locally, but the heartbeat failure will trigger it on server anyway.
             Log.d(TAG, "No active network")
        }
    }

    private fun checkNetworkType(network: Network) {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
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
             // Fallback for older versions, though registerDefaultNetworkCallback is preferred.
             // For < N, we would need to listen to all and filter, but simpler is to use activeNetwork check.
             // Given the requirements and complexity, restricting to N+ (API 24) is reasonable for this feature
             // or we accept the limitation. The minSdk is 21.
             // We can use the previous method for legacy.
             val request = android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
             connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                sendHeartbeat()
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
                        Log.i(TAG, "Status updated successfully")
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
        connectivityManager.unregisterNetworkCallback(networkCallback)
        job.cancel()
        Log.d(TAG, "Service Destroyed")
    }
}

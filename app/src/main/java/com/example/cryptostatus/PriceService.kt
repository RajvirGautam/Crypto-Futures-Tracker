package com.example.cryptostatus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class PriceService : Service() {

    companion object {
        @Volatile
        private var started = false
    }

    private val channelId = "crypto_price_channel"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Prevent multiple starts
        if (started) return
        started = true

        createNotificationChannel()

        // MUST be called immediately
        startForeground(
            1,
            buildNotification("Fetching crypto prices...")
        )

        startPriceUpdates()
    }

    private fun startPriceUpdates() {
        scope.launch {
            while (isActive) {
                try {
                    val xrp = ApiClient.api.getFuturesPrice("XRPUSDT")
                    val eth = ApiClient.api.getFuturesPrice("ETHUSDT")

                    val xrpPrice = xrp.markPrice.toDouble().let { "%.4f".format(it) }
                    val ethPrice = eth.markPrice.toDouble().let { "%.2f".format(it) }

                    val text = "XRP: $xrpPrice | ETH: $ethPrice"

                    updateNotification(text)

                } catch (e: Exception) {
                    updateNotification("Futures price error")
                }

                delay(60_000)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live Crypto Prices")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Crypto Prices",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        started = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
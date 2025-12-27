package com.example.cryptostatus

import android.app.Notification
import android.graphics.*
import androidx.core.graphics.drawable.IconCompat
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
            buildNotification("Fetching crypto prices...", "--")
        )

        startPriceUpdates()
    }

    private fun startPriceUpdates() {
        scope.launch {
            while (isActive) {
                try {
                    val xrp = ApiClient.api.getFuturesPrice("XRPUSDT")

                    val priceDouble = xrp.markPrice.toDouble()

                // for 03 digits:
                 /*   val decimalPart = ((priceDouble * 1000) % 1000).toInt()
                    val priceText = decimalPart.toString().padStart(3, '0') */

                  /*  val scaled = (priceDouble * 10_000).toInt()   // 1.8527 → 18527
                    val lastThree = scaled % 1000           // → 527

                    val priceText = lastThree
                        .toString()
                        .padStart(3, '0') */

                    val price = priceDouble   // e.g. 1.8563

                    val displayValue = ((price * 1000) % 1000).toInt()
                    val iconText = displayValue.toString().padStart(3, '0')

                    val notificationText =
                        "Mark: $iconText | Funding & bias coming soon"

                    updateNotification(notificationText, iconText)

                } catch (e: Exception) {
                    updateNotification("Futures price error", "--")
                }

                delay(1_000)
            }
        }
    }

    private fun buildNotification(text: String, priceForIcon: String = "--"): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("XRPUSDT Futures")
            .setContentText(text)
            .setSmallIcon(buildPriceIcon(priceForIcon))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun updateNotification(text: String, iconPrice: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification(text, iconPrice))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Crypto Prices",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    private fun buildPriceIcon(price: String): IconCompat {
        val size = 48 // status bar icon size
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2

        canvas.drawText(price, x, y, paint)

        return IconCompat.createWithBitmap(bitmap)
    }


    override fun onDestroy() {
        started = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
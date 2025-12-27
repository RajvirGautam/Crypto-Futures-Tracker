package com.example.cryptostatus

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
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
        if (started) return
        started = true

        createNotificationChannel()

        startForeground(
            1,
            buildNotification("Starting...", "---")
        )

        startPriceUpdates()
    }

    private fun startPriceUpdates() {
        scope.launch {
            while (isActive) {
                try {
                    val prefs = getSharedPreferences("crypto_prefs", MODE_PRIVATE)
                    val symbol = prefs.getString("symbol", "XRPUSDT")!!

                    val premium = ApiClient.api.getFuturesPrice(symbol)
                    val ticker = ApiClient.api.get24hTicker(symbol)
                    val ls = ApiClient.api.getGlobalLongShortRatio(symbol).first()

                    val markPrice = premium.markPrice.toDouble()
                    val fundingRate = premium.lastFundingRate.toDouble() * 100
                    val changePct = ticker.priceChangePercent.toDouble()

                    val millisLeft = premium.nextFundingTime - System.currentTimeMillis()
                    val totalMinutes = (millisLeft / 60000).coerceAtLeast(0)
                    val h = totalMinutes / 60
                    val m = totalMinutes % 60

                    val longPct = ls.longAccount.toDouble() * 100
                    val shortPct = ls.shortAccount.toDouble() * 100

                    val content = """
Price: %.4f
24h: %+.2f%%
Funding: %+.4f%% (%dh %dm)
Long / Short: %.0f%% / %.0f%%
""".trimIndent().format(
                        markPrice,
                        changePct,
                        fundingRate,
                        h,
                        m,
                        longPct,
                        shortPct
                    )

                    val iconText = getIconTextFromPrice(markPrice, prefs)

                    updateNotification("$symbol Perpetual", content, iconText)

                } catch (_: Exception) {
                    updateNotification("Error", "Fetching failed", "---")
                }

                delay(1000)
            }
        }
    }

    private fun getIconTextFromPrice(
        price: Double,
        prefs: SharedPreferences
    ): String {

        val startIndex = prefs.getInt("digit_start_index", 0)

        val raw = String.format("%.4f", price)
            .replace(".", "")
            .trimStart('0')

        if (raw.length < 3) return raw.padStart(3, '0')

        val safeIndex = startIndex.coerceIn(0, raw.length - 3)
        return raw.substring(safeIndex, safeIndex + 3)
    }

    private fun updateNotification(
        title: String,
        content: String,
        iconText: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content) // 🔥 CRITICAL: collapsed foreground text
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(buildPriceIcon(iconText))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(1, notification)
    }

    private fun buildNotification(text: String, icon: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("CryptoStatus")
            .setContentText(text)
            .setSmallIcon(buildPriceIcon(icon))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Crypto Prices",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildPriceIcon(text: String): IconCompat {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, x, y, paint)

        return IconCompat.createWithBitmap(bitmap)
    }

    override fun onDestroy() {
        started = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
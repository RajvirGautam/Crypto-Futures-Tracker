package com.rajvir.FuturesTracker

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.rajvir.FuturesTracker.ApiClient
import com.rajvir.FuturesTracker.R
import com.rajvir.FuturesTracker.MainActivity
import kotlinx.coroutines.*
import java.util.Locale

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

        // Initial empty notification
        startForeground(1, buildNotification("Initializing...", "---"))

        startPriceUpdates()
    }

    private fun startPriceUpdates() {
        scope.launch {
            while (isActive) {
                try {
                    val prefs = getSharedPreferences("crypto_prefs", MODE_PRIVATE)
                    val symbol = prefs.getString("symbol", "XRPUSDT")!!

                    // Fetch Data
                    val premiumDeffered = async { ApiClient.api.getFuturesPrice(symbol) }
                    val tickerDeffered = async { ApiClient.api.get24hTicker(symbol) }
                    val lsDeffered = async { ApiClient.api.getGlobalLongShortRatio(symbol) }

                    val premium = premiumDeffered.await()
                    val ticker = tickerDeffered.await()
                    val lsList = lsDeffered.await()

                    val ls = if (lsList.isNotEmpty()) lsList.first() else null

                    val markPrice = premium.markPrice.toDouble()
                    val fundingRate = premium.lastFundingRate.toDouble() * 100
                    val changePct = ticker.priceChangePercent.toDouble()

                    val millisLeft = premium.nextFundingTime - System.currentTimeMillis()
                    val totalMinutes = (millisLeft / 60000).coerceAtLeast(0)
                    val h = totalMinutes / 60
                    val m = totalMinutes % 60

                    val longPct = (ls?.longAccount?.toDouble() ?: 0.0) * 100
                    val shortPct = (ls?.shortAccount?.toDouble() ?: 0.0) * 100

                    val iconText = getIconTextFromPrice(markPrice, prefs)

                    updateCustomNotification(
                        symbol,
                        markPrice,
                        changePct,
                        fundingRate,
                        h, m,
                        longPct, shortPct,
                        iconText
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(1000)
            }
        }
    }

    private fun updateCustomNotification(
        symbol: String,
        price: Double,
        change: Double,
        funding: Double,
        h: Long, m: Long,
        longPct: Double, shortPct: Double,
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

        // --- PREPARE DATA STRINGS ---
        // We format them here so we can use them in both Small and Big views
        val priceStr = "$${String.format("%,.4f", price)}"
        val changeStr = String.format("%+.2f%%", change)
        val fundingStr = String.format("%.4f%%", funding)
        val timerStr = "${h}h ${m}m"
        val lsStr = "${longPct.toInt()}/${shortPct.toInt()}"

        // Condensed strings for the small view (to save space)
        val smallFundingStr = "FUNDING: ${String.format("%.3f", funding)}%"
        val smallTimerStr = "IN ${h}h ${m}m"
        val smallLsStr = "L/S: ${longPct.toInt()}/${shortPct.toInt()}"

        // 1. Setup EXPANDED View (Big Dashboard)
        val expandedView = RemoteViews(packageName, R.layout.notification_view_expanded)
        expandedView.setTextViewText(R.id.notif_symbol, "$symbol Perpetual")
        expandedView.setTextViewText(R.id.notif_price, priceStr)
        expandedView.setTextViewText(R.id.notif_funding, fundingStr)
        expandedView.setTextViewText(R.id.notif_timer, timerStr)
        expandedView.setTextViewText(R.id.notif_ls, lsStr)

        // 2. Setup COLLAPSED View (Small Bar)
        val collapsedView = RemoteViews(packageName, R.layout.notification_view_small)
        collapsedView.setTextViewText(R.id.notif_symbol_small, symbol)
        collapsedView.setTextViewText(R.id.notif_price_small, priceStr)

        // 🔥 CRITICAL: INJECTING REAL DATA INTO SMALL VIEW 🔥
        collapsedView.setTextViewText(R.id.notif_funding_small, smallFundingStr)
        collapsedView.setTextViewText(R.id.notif_timer_small, smallTimerStr)
        collapsedView.setTextViewText(R.id.notif_ls_small, smallLsStr)

        // 3. Dynamic Coloring (Pills)
        val pillDrawable = if (change >= 0) R.drawable.bg_pill_green else R.drawable.bg_pill_red

        // Apply pill to Expanded
        expandedView.setTextViewText(R.id.notif_change, changeStr)
        expandedView.setInt(R.id.notif_change, "setBackgroundResource", pillDrawable)

        // Apply pill to Small
        collapsedView.setTextViewText(R.id.notif_change_small, changeStr)
        collapsedView.setInt(R.id.notif_change_small, "setBackgroundResource", pillDrawable)

        // 4. Build Notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(buildPriceIcon(iconText))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(Color.parseColor("#F0B90B"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        getSystemService(NotificationManager::class.java).notify(1, notification)
    }

    // ... (Keep existing getIconTextFromPrice, buildNotification, createNotificationChannel, buildPriceIcon, onDestroy) ...
    // Note: buildPriceIcon is still used for the STATUS BAR icon (the tiny numbers at the top).
    // The RemoteViews handle the drawer content.

    private fun getIconTextFromPrice(
        price: Double,
        prefs: SharedPreferences
    ): String {
        val startIndex = prefs.getInt("digit_start_index", 0)
        val raw = String.format(Locale.US, "%.4f", price)
            .replace(".", "")
            .trimStart('0')
        if (raw.length < 3) return raw.padStart(3, '0')
        val safeIndex = startIndex.coerceIn(0, raw.length - 3)
        return raw.substring(safeIndex, safeIndex + 3)
    }

    private fun buildNotification(text: String, icon: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("CryptoStatus")
            .setContentText(text)
            .setSmallIcon(buildPriceIcon(icon))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Live Market Data",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows persistent crypto prices"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildPriceIcon(text: String): IconCompat {
        val size = 72
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
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
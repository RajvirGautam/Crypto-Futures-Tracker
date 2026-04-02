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
import kotlinx.coroutines.*
import java.util.Locale

class PriceService : Service() {

    companion object {
        @Volatile
        private var started = false
        const val ACTION_FORCE_WIDGET_REFRESH = "com.rajvir.FuturesTracker.FORCE_WIDGET_REFRESH"
    }

    private val channelId = "crypto_price_channel"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var previousAlarmPrice: Double? = null
    @Volatile private var forceNextWidgetRefresh = true

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // Always call startForeground() first — Android O+ requires this within 5 seconds
        // of startForegroundService(), regardless of whether a loop is already running.
        startForeground(1, buildNotification("Initializing...", "---"))

        if (started) return   // loop already running — don't start a second one
        started = true

        startPriceUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FORCE_WIDGET_REFRESH) {
            forceNextWidgetRefresh = true
        }
        return START_STICKY
    }

    private fun startPriceUpdates() {
        scope.launch {
            var nextNotifUpdateAt = 0L

            while (isActive) {
                try {
                    val prefs = getSharedPreferences("crypto_prefs", MODE_PRIVATE)
                    val now = System.currentTimeMillis()
                    val notifActive = prefs.getBoolean("notif_tracker_active", false)
                    val alarmActive = prefs.getBoolean(MainActivity.PREF_ALARM_ENABLED, false)
                    val hasWidgets = WidgetUpdater.hasAnyWidgets(applicationContext)

                    if (!notifActive && !hasWidgets && !alarmActive) {
                        stopSelf()
                        break
                    }

                    val notifInterval = UpdateIntervals.byLabel(
                        prefs.getString(UpdateIntervals.NOTIF_INTERVAL_KEY, "1s")
                    )
                    if (notifActive && now >= nextNotifUpdateAt) {
                        val symbol = prefs.getString("symbol", "XRPUSDT")!!

                        val premiumDeferred = async { ApiClient.api.getFuturesPrice(symbol) }
                        val tickerDeferred = async { ApiClient.api.get24hTicker(symbol) }
                        val lsDeferred = async {
                            ApiClient.api.getGlobalLongShortRatio(
                                symbol = symbol,
                                period = notifInterval.longShortPeriod,
                                limit = 1
                            )
                        }

                        val premium = premiumDeferred.await()
                        val ticker = tickerDeferred.await()
                        val lsList = lsDeferred.await()
                        val ls = if (lsList.isNotEmpty()) lsList.first() else null

                        val markPrice = premium.markPrice.toDouble()
                        val fundingRate = premium.lastFundingRate.toDouble() * 100
                        val changePct = ticker.priceChangePercent?.toDoubleOrNull() ?: 0.0

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

                        nextNotifUpdateAt = now + notifInterval.millis
                    }

                    if (alarmActive) {
                        checkAndTriggerAlarm(prefs)
                    } else {
                        previousAlarmPrice = null
                    }

                    if (hasWidgets) {
                        val force = forceNextWidgetRefresh
                        WidgetUpdater.updateAllWidgets(applicationContext, force)
                        forceNextWidgetRefresh = false
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(250)
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
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DashboardActivity.EXTRA_START_PAGE, DashboardActivity.PAGE_HOME)
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

    private suspend fun checkAndTriggerAlarm(prefs: SharedPreferences) {
        val repeat = prefs.getBoolean(MainActivity.PREF_ALARM_REPEAT, false)
        if (!repeat && prefs.getBoolean(MainActivity.PREF_ALARM_TRIGGERED, false)) {
            previousAlarmPrice = null
            return
        }

        val cooldownMin = prefs.getInt(MainActivity.PREF_ALARM_COOLDOWN_MIN, 5).coerceIn(1, 240)
        val cooldownMs = cooldownMin * 60_000L
        val now = System.currentTimeMillis()
        val lastTriggerAt = prefs.getLong(MainActivity.PREF_ALARM_LAST_TRIGGER_AT, 0L)
        if (repeat && lastTriggerAt > 0L && now - lastTriggerAt < cooldownMs) {
            return
        }

        val symbol = prefs.getString(MainActivity.PREF_ALARM_SYMBOL, null)
            ?: prefs.getString("symbol", "BTCUSDT")
            ?: "BTCUSDT"

        val currentPrice = try {
            ApiClient.api.getFuturesPrice(symbol).markPrice.toDoubleOrNull() ?: return
        } catch (_: Exception) {
            return
        }

        val mode = prefs.getString(MainActivity.PREF_ALARM_MODE, "price") ?: "price"
        val targetPrice = if (mode == "percent") {
            val base = prefs.getFloat(MainActivity.PREF_ALARM_BASE_PRICE, 0f).toDouble()
                .takeIf { it > 0 } ?: currentPrice
            val pct = prefs.getFloat(MainActivity.PREF_ALARM_PERCENT, 1f).toDouble()
            base * (1.0 + pct / 100.0)
        } else {
            prefs.getFloat(MainActivity.PREF_ALARM_TARGET_PRICE, 0f).toDouble()
        }

        if (targetPrice <= 0.0) {
            previousAlarmPrice = currentPrice
            return
        }

        val direction = prefs.getString(MainActivity.PREF_ALARM_DIRECTION, "either") ?: "either"

        val prev = previousAlarmPrice
        val crossedEither = when {
            prev == null -> kotlin.math.abs(currentPrice - targetPrice) <= targetPrice * 0.0002
            else -> (prev <= targetPrice && currentPrice >= targetPrice) ||
                (prev >= targetPrice && currentPrice <= targetPrice)
        }

        val touched = if (!crossedEither) {
            false
        } else {
            when (direction) {
                "above" -> currentPrice >= targetPrice
                "below" -> currentPrice <= targetPrice
                else -> true
            }
        }

        if (touched) {
            sendAlarmHitNotification(symbol, currentPrice, targetPrice)
            val edit = prefs.edit()
                .putLong(MainActivity.PREF_ALARM_LAST_TRIGGER_AT, now)
            if (repeat) {
                edit.putBoolean(MainActivity.PREF_ALARM_TRIGGERED, false)
            } else {
                edit.putBoolean(MainActivity.PREF_ALARM_TRIGGERED, true)
                edit.putBoolean(MainActivity.PREF_ALARM_ENABLED, false)
            }
            edit.apply()
            previousAlarmPrice = null
        } else {
            previousAlarmPrice = currentPrice
        }
    }

    private fun sendAlarmHitNotification(symbol: String, currentPrice: Double, targetPrice: Double) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DashboardActivity.EXTRA_START_PAGE, DashboardActivity.PAGE_HOME)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "$symbol touched ${String.format(Locale.US, "$%,.4f", targetPrice)} (now ${String.format(Locale.US, "$%,.4f", currentPrice)})"
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Price alarm triggered")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java).notify(1002, notification)
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
package com.rajvir.FuturesTracker

data class UpdateIntervalOption(
    val label: String,
    val millis: Long,
    val klineInterval: String,
    val longShortPeriod: String
)

object UpdateIntervals {

    const val NOTIF_INTERVAL_KEY = "notif_update_interval"
    const val WIDGET_INTERVAL_KEY = "widget_update_interval"

    private val options = listOf(
        UpdateIntervalOption("1s", 1_000L, "1m", "5m"),
        UpdateIntervalOption("30s", 30_000L, "1m", "5m"),
        UpdateIntervalOption("1m", 60_000L, "1m", "5m"),
        UpdateIntervalOption("5m", 300_000L, "5m", "5m"),
        UpdateIntervalOption("10m", 600_000L, "5m", "15m"),
        UpdateIntervalOption("15m", 900_000L, "15m", "15m"),
        UpdateIntervalOption("30m", 1_800_000L, "30m", "30m"),
        UpdateIntervalOption("1h", 3_600_000L, "1h", "1h"),
        UpdateIntervalOption("1d", 86_400_000L, "1d", "1d")
    )

    fun labels(): Array<String> = options.map { it.label }.toTypedArray()

    fun byLabel(label: String?): UpdateIntervalOption {
        return options.firstOrNull { it.label == label } ?: options.first()
    }
}
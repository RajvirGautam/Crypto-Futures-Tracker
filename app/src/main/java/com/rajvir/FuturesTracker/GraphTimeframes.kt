package com.rajvir.FuturesTracker

data class GraphTimeframeOption(
    val label: String,
    val klineInterval: String
)

object GraphTimeframes {

    private val options = listOf(
        // Binance klines do not support 1s; map to 1m as nearest interval.
        GraphTimeframeOption("1s", "1m"),
        // Binance klines do not support 30s; map to 1m as nearest interval.
        GraphTimeframeOption("30s", "1m"),
        GraphTimeframeOption("1m", "1m"),
        GraphTimeframeOption("5m", "5m"),
        GraphTimeframeOption("15m", "15m"),
        GraphTimeframeOption("30m", "30m"),
        GraphTimeframeOption("1h", "1h"),
        GraphTimeframeOption("8h", "8h"),
        GraphTimeframeOption("1d", "1d")
    )

    fun labels(): Array<String> = options.map { it.label }.toTypedArray()

    fun byLabel(label: String?): GraphTimeframeOption {
        return options.firstOrNull { it.label == label } ?: options[1] // default 1m
    }
}
package com.rajvir.FuturesTracker

data class FuturesPrice(
    val symbol: String,
    val markPrice: String,
    val lastFundingRate: String,
    val nextFundingTime: Long
)

data class LongShortRatio(
    val longAccount: String,
    val shortAccount: String,
    val longShortRatio: String
)

data class FuturesTicker24h(
    val lastPrice: String?,
    val priceChange: String?,
    val priceChangePercent: String?,
    val openInterest: String? = null // optional, depends on endpoint
)
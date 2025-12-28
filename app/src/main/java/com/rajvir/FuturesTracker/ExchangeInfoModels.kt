package com.rajvir.FuturesTracker

data class ExchangeInfo(
    val symbols: List<SymbolInfo>
)

data class SymbolInfo(
    val symbol: String,
    val contractType: String,
    val status: String
)
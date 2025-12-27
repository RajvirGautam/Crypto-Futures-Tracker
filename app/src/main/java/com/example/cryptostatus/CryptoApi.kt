package com.example.cryptostatus

import retrofit2.http.GET
import retrofit2.http.Query

interface CryptoApi {

    @GET("fapi/v1/exchangeInfo")
    suspend fun getExchangeInfo(): ExchangeInfo

    @GET("fapi/v1/ticker/24hr")
    suspend fun get24hTicker(
        @Query("symbol") symbol: String
    ): FuturesTicker24h

    @GET("futures/data/globalLongShortAccountRatio")
    suspend fun getGlobalLongShortRatio(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "5m",
        @Query("limit") limit: Int = 1
    ): List<LongShortRatio>

    @GET("fapi/v1/premiumIndex")
    suspend fun getFuturesPrice(
        @Query("symbol") symbol: String
    ): FuturesPrice
}
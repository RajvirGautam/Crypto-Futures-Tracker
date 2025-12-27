package com.example.cryptostatus

import retrofit2.http.GET
import retrofit2.http.Query

interface CryptoApi {

    @GET("fapi/v1/premiumIndex")
    suspend fun getFuturesPrice(
        @Query("symbol") symbol: String
    ): FuturesPrice
}
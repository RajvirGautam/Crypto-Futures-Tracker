package com.rajvir.FuturesTracker

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    val api: CryptoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://fapi.binance.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CryptoApi::class.java)
    }
}
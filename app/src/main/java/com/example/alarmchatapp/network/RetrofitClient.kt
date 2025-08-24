package com.example.alarmchatapp.network

import android.util.Log // <-- Add this import
import okhttp3.OkHttpClient // <-- Add this import
import okhttp3.logging.HttpLoggingInterceptor // <-- Add this import
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "http://192.168.29.83:5000"

    val instance: ApiService by lazy {

        // --- START OF NEW CODE ---
        // Create a logger to see request and response info
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("RetrofitLog", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY // Logs headers and body
        }

        // Create an OkHttpClient and add the logger as an interceptor
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
        // --- END OF NEW CODE ---

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // <-- USE THE NEW CLIENT WITH THE LOGGER
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}

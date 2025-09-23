package com.example.alarmchatapp.network

import com.example.alarmchatapp.network.ApiResponse
import com.example.alarmchatapp.network.NetworkAlarm
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AlarmApiService {
    @POST("alarms/schedule")
    suspend fun scheduleAlarms(@Body alarms: List<NetworkAlarm>): ApiResponse<Unit>

    @GET("alarms")
    suspend fun getAlarms(): ApiResponse<List<NetworkAlarm>>
}

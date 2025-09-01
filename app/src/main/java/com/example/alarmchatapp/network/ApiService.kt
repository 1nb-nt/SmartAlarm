package com.example.alarmchatapp.network

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("generate")
    suspend fun getAlarmDetails(@Body request: AlarmApiRequest): AlarmApiResponse.AlarmApiWrapper
}

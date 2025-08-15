package com.example.alarmchatapp.network

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    /**
     * Sends the user's text to the API and expects an AlarmApiResponse back.
     */
    // **THE FIX**: This MUST match the @app.route in your Python script.
    @POST("process_alarm")
    suspend fun getAlarmDetails(@Body request: AlarmApiRequest): AlarmApiResponse
}

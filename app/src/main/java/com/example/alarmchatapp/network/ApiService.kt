package com.example.alarmchatapp.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("generate")
    suspend fun getAlarmDetailsRaw(@Body payload: Map<String, @JvmSuppressWildcards Any>):Response<ResponseBody>
}

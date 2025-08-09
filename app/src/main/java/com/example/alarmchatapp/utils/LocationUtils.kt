package com.example.alarmchatapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

object LocationUtils {

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): String? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var best: Location? = null
            for (p in providers) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.accuracy < best.accuracy) best = l
            }
            best?.let { "Lat: ${it.latitude}, Lon: ${it.longitude}" }
        } catch (e: Exception) {
            null
        }
    }
}

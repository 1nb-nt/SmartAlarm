package com.example.alarmchatapp

import android.content.Context

object WowDeviceController {
    fun triggerPlay(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences("wow_prefs", Context.MODE_PRIVATE)
        val transport = prefs.getString("wow_transport", "WIFI") ?: "WIFI"
        val endpoint = prefs.getString("wow_endpoint", null) ?: return false
        return when (transport) {
            "BLE" -> false // TODO: implement BLE GATT write to play
            "WIFI" -> false // TODO: implement HTTP POST http://$endpoint/play
            "CLOUD" -> false // TODO: implement cloud trigger
            else -> false
        }
    }
}

package com.example.alarmchatapp.utils

import android.content.Context

object HydratedStore {

    private val hydratedToday = mutableSetOf<Int>()

    fun markHydrated(alarmId: Int) {
        hydratedToday.add(alarmId)
    }

    fun isHydrated(alarmId: Int): Boolean {
        return hydratedToday.contains(alarmId)
    }

    fun reset() {
        hydratedToday.clear()
    }
}

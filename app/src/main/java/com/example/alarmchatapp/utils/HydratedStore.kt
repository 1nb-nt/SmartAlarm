// app/src/main/java/com/example/alarmchatapp/utils/HydratedStore.kt
package com.example.alarmchatapp.utils

import android.content.Context

object HydratedStore {
    private const val PREF = "clock_hydrated"
    private const val KEY = "ids"

    fun wasHydrated(context: Context, id: Int): Boolean {
        val set = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()
        return set.contains(id.toString())
    }

    fun markHydrated(context: Context, id: Int) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val set = sp.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(id.toString())
        sp.edit().putStringSet(KEY, set).apply()
    }
}

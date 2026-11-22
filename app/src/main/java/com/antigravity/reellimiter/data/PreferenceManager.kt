package com.antigravity.reellimiter.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "reel_limiter_prefs"
        private const val KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
        private const val KEY_TIME_SPENT_SECONDS = "time_spent_seconds"
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        const val MAX_LIMIT_MINUTES = 20
    }

    private val todayString: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init {
        checkAndResetDailyUsage()
    }

    fun checkAndResetDailyUsage() {
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val today = todayString
        if (lastDate != today) {
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putLong(KEY_TIME_SPENT_SECONDS, 0L)
                .apply()
        }
    }

    var dailyLimitMinutes: Int
        get() = prefs.getInt(KEY_DAILY_LIMIT_MINUTES, 20).coerceIn(1, MAX_LIMIT_MINUTES)
        set(value) {
            val clamped = value.coerceIn(1, MAX_LIMIT_MINUTES)
            prefs.edit().putInt(KEY_DAILY_LIMIT_MINUTES, clamped).apply()
        }

    var timeSpentSeconds: Long
        get() {
            checkAndResetDailyUsage()
            return prefs.getLong(KEY_TIME_SPENT_SECONDS, 0L)
        }
        set(value) {
            checkAndResetDailyUsage()
            prefs.edit().putLong(KEY_TIME_SPENT_SECONDS, value).apply()
        }

    fun addSeconds(seconds: Long) {
        val current = timeSpentSeconds
        timeSpentSeconds = current + seconds
    }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    val isLimitReached: Boolean
        get() = timeSpentSeconds >= (dailyLimitMinutes * 60L)

    val remainingSeconds: Long
        get() = ((dailyLimitMinutes * 60L) - timeSpentSeconds).coerceAtLeast(0L)
}

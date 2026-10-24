package com.kanraeh.app.companion

import android.content.Context
import com.kanraeh.app.core.Time

/** MODULE: companion - all user preferences in one place (SharedPreferences, on-device only). */
class CompanionSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("kanraeh_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean get() = p.getBoolean("enabled", true); set(v) = p.edit().putBoolean("enabled", v).apply()
    var stillMode: Boolean get() = p.getBoolean("still", false); set(v) = p.edit().putBoolean("still", v).apply()
    var dailyCap: Int get() = p.getInt("cap", 6); set(v) = p.edit().putInt("cap", v.coerceIn(1, 15)).apply()
    var quietStart: Int get() = p.getInt("qs", 22); set(v) = p.edit().putInt("qs", v).apply()
    var quietEnd: Int get() = p.getInt("qe", 7); set(v) = p.edit().putInt("qe", v).apply()
    var lockEnabled: Boolean get() = p.getBoolean("lock", false); set(v) = p.edit().putBoolean("lock", v).apply()
    var secureScreen: Boolean get() = p.getBoolean("secure", true); set(v) = p.edit().putBoolean("secure", v).apply()
    var takesEstrogen: Boolean get() = p.getBoolean("estrogen", false); set(v) = p.edit().putBoolean("estrogen", v).apply()
    var paletteId: String get() = p.getString("palette", "dusk_lavender")!!; set(v) = p.edit().putString("palette", v).apply()
    var customPalette: String get() = p.getString("custom_palette", "")!!; set(v) = p.edit().putString("custom_palette", v).apply()
    var futureNote: String get() = p.getString("future_note", "")!!; set(v) = p.edit().putString("future_note", v).apply()
    var ignoredStreak: Int get() = p.getInt("ignored", 0); set(v) = p.edit().putInt("ignored", v).apply()
    var onlineAvatarAllowed: Boolean get() = p.getBoolean("online_avatar", false); set(v) = p.edit().putBoolean("online_avatar", v).apply()

    fun lastAsked(kind: String): Long = p.getLong("asked_$kind", 0L)
    fun registerAsked(kind: String, now: Long) {
        val day = Time.startOfDay(now)
        val count = if (p.getLong("count_day", 0L) == day) p.getInt("count", 0) else 0
        p.edit().putLong("asked_$kind", now).putLong("count_day", day).putInt("count", count + 1).apply()
    }
    fun todayCount(now: Long): Int = if (p.getLong("count_day", 0L) == Time.startOfDay(now)) p.getInt("count", 0) else 0

    fun inQuiet(now: Long): Boolean {
        val h = Time.hourOf(now)
        return if (quietStart > quietEnd) (h >= quietStart || h < quietEnd) else (h in quietStart until quietEnd)
    }
    /** Backs off: after 3 ignored prompts in a row she probably is having a hard day, so ask half as often. */
    fun canAskMore(now: Long): Boolean {
        val cap = if (ignoredStreak >= 3) maxOf(1, dailyCap / 2) else dailyCap
        return todayCount(now) < cap
    }
}

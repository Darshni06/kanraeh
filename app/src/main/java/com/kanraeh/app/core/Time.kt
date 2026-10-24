package com.kanraeh.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** MODULE: core - the single source of time. Every tap is stamped here; users never type times. */
object Clock { fun now(): Long = System.currentTimeMillis() }

object Time {
    const val MIN = 60_000L
    const val HOUR = 60 * MIN
    const val DAY = 24 * HOUR

    private fun cal(ts: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ts }
    fun startOfDay(ts: Long): Long {
        val c = cal(ts)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    fun hourOf(ts: Long): Int = cal(ts).get(Calendar.HOUR_OF_DAY)
    fun minuteOfDay(ts: Long): Int { val c = cal(ts); return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) }
    fun fmtTime(ts: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
    fun fmtDate(ts: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ts))
    fun fmtDateTime(ts: Long): String = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(ts))
    fun fmtDuration(ms: Long): String {
        val m = ms / MIN; val h = m / 60
        return if (h > 0) "${h}h ${m % 60}m" else "${m}m"
    }
}

package com.kanraeh.app.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.EventDao
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.LogEvent
import com.kanraeh.app.core.Time

/** MODULE: screentime - reads HOW LONG and WHEN apps were used (Usage access). Never what she reads or types. */
data class ScreenSummary(val totalMin: Long, val nightMin: Long, val longestMin: Long)

class ScreenTimeReader(private val ctx: Context) {
    fun hasAccess(): Boolean {
        val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    fun summarize(from: Long, to: Long): ScreenSummary {
        if (!hasAccess()) return ScreenSummary(0, 0, 0)
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val segs = ArrayList<Triple<String, Long, Long>>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName == ctx.packageName) continue
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> open[e.packageName] = e.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> open.remove(e.packageName)?.let { segs += Triple(e.packageName, it, e.timeStamp) }
            }
        }
        open.forEach { (p, s) -> segs += Triple(p, s, minOf(to, Clock.now())) }

        var total = 0L; var night = 0L
        segs.forEach { (_, s, en) ->
            total += (en - s)
            var t = s
            while (t < en) { val h = Time.hourOf(t); if (h >= 23 || h < 5) night += minOf(Time.MIN, en - t); t += Time.MIN }
        }
        // merge back-to-back segments of the same app (gap < 2 min) to find the longest continuous session
        var longest = 0L
        segs.groupBy { it.first }.values.forEach { list ->
            val sorted = list.sortedBy { it.second }
            var curStart = sorted.first().second; var curEnd = sorted.first().third
            for (i in 1 until sorted.size) {
                val s = sorted[i]
                if (s.second - curEnd < 2 * Time.MIN) curEnd = maxOf(curEnd, s.third)
                else { longest = maxOf(longest, curEnd - curStart); curStart = s.second; curEnd = s.third }
            }
            longest = maxOf(longest, curEnd - curStart)
        }
        return ScreenSummary(total / Time.MIN, night / Time.MIN, longest / Time.MIN)
    }
}

object ScreenTimeLogger {
    /** Stores today's running summary as one SCREEN_DAY event (replaced on every call). */
    suspend fun logToday(reader: ScreenTimeReader, dao: EventDao) {
        if (!reader.hasAccess()) return
        val now = Clock.now(); val start = Time.startOfDay(now)
        val s = reader.summarize(start, now)
        dao.deleteTypeBetween(EventTypes.SCREEN_DAY, start, now)
        dao.insert(LogEvent(type = EventTypes.SCREEN_DAY, value = s.totalMin.toDouble(), text = "night=${s.nightMin};longest=${s.longestMin}", ts = now))
    }
}

package com.kanraeh.app.cycle

import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.EventDao
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.Time
import kotlin.math.max
import kotlin.math.min

/** MODULE: cycle - period tracking built for irregular PCOS cycles (never assumes 28 days). Also hair/acne scoring helpers. */
data class CycleSummary(
    val lastStart: Long?,
    val cycleDay: Int?,
    val lengthsDays: List<Int>,
    val avgLength: Double?,
    val shortest: Int?,
    val longest: Int?,
    val irregular: Boolean,
    val windowStart: Long?,   // earliest expected next period
    val windowEnd: Long?,     // latest expected next period
    val lateByDays: Int?      // days past the end of the expected window, if late
)

class CycleEngine(private val dao: EventDao) {
    suspend fun summary(now: Long = Clock.now()): CycleSummary {
        val starts = dao.latest(EventTypes.PERIOD_START, 12).map { it.ts }.sorted()
        if (starts.isEmpty()) return CycleSummary(null, null, emptyList(), null, null, null, false, null, null, null)
        val lengths = starts.zipWithNext { a, b -> ((b - a) / Time.DAY).toInt() }
        val last = starts.last()
        val day = ((now - last) / Time.DAY).toInt() + 1
        val avg = if (lengths.isEmpty()) null else lengths.average()
        val sh = lengths.minOrNull(); val lo = lengths.maxOrNull()
        val irregular = lengths.size >= 3 && (((lo ?: 0) - (sh ?: 0)) > 7 || (lo ?: 0) > 35)
        val ws = if (lengths.size >= 3 && sh != null) last + sh * Time.DAY else null
        val we = if (lengths.size >= 3 && lo != null) last + lo * Time.DAY else null
        val late = if (we != null && now > we) ((now - we) / Time.DAY).toInt() else null
        return CycleSummary(last, day, lengths, avg, sh, lo, irregular, ws, we, late)
    }

    /** Menstrual-migraine style window: 2 days before to 3 days after a logged period start. */
    fun perimenstrual(ts: Long, periodStarts: List<Long>): Boolean =
        periodStarts.any { ts >= it - 2 * Time.DAY && ts <= it + 3 * Time.DAY }
}

/** Ferriman-Gallwey style areas, each scored 0-4. Interpretation of the total belongs to the knowledge base. */
object HairAreas {
    val areas = listOf("upper lip", "chin", "chest", "upper back", "lower back", "upper abdomen", "lower abdomen", "upper arm", "thigh")
    fun total(latestPerArea: Map<String, Int>): Int = latestPerArea.values.sum()
    fun clampScore(v: Int) = max(0, min(4, v))
}

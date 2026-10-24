package com.kanraeh.app.patterns

import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.FactorKeys
import com.kanraeh.app.core.KanraehDatabase
import com.kanraeh.app.core.LogEvent
import com.kanraeh.app.core.Time
import com.kanraeh.app.food.FoodDatabase
import kotlin.math.abs
import kotlin.math.max

/**
 * MODULE: patterns - learns from HER data only, on the phone. No science text lives here.
 * FactorExtractor answers "what was going on in the 24h before this moment?" using stable factor keys.
 * PatternEngine compares attack moments with ordinary moments to find factors that show up more before attacks.
 */
class FactorExtractor(
    private val events: List<LogEvent>,
    private val foods: FoodDatabase,
    private val periodStarts: List<Long>
) {
    // Only judge "missing" things (water, meals, sleep) if she actually tracks them, otherwise silence != absence.
    private val tracksWater = events.count { it.type == EventTypes.WATER } >= 8
    private val tracksMeals = events.count { it.type == EventTypes.MEAL } >= 8
    private val avgScreen = events.filter { it.type == EventTypes.SCREEN_DAY }.mapNotNull { it.value }
        .let { if (it.size >= 3) it.average() else null }

    fun factorsBefore(ts: Long, windowMs: Long = 24 * Time.HOUR): Set<String> {
        val w = events.filter { it.ts in (ts - windowMs)..ts }
        val out = mutableSetOf<String>()

        val meals = w.filter { it.type == EventTypes.MEAL }
        if (meals.any { it.key == "skipped" }) out += FactorKeys.SKIPPED_MEAL
        val real = meals.filter { it.key != "skipped" }
        if (tracksMeals && real.isNotEmpty() && ts - real.maxOf { it.ts } > 5 * Time.HOUR) out += FactorKeys.LONG_FOOD_GAP
        real.forEach { m -> foods.tagsOf(m.key).forEach { t -> FactorKeys.fromFoodTag(t)?.let { out += it } } }

        if (w.any { it.type == EventTypes.SLEEP && (it.value ?: 99.0) < 6.0 }) out += FactorKeys.SHORT_SLEEP

        if (tracksWater) {
            val glasses = w.filter { it.type == EventTypes.WATER && it.ts >= ts - 12 * Time.HOUR }.sumOf { it.value ?: 1.0 }
            if (glasses < 3) out += FactorKeys.LOW_WATER
        }

        w.filter { it.type == EventTypes.MOOD }.forEach {
            when (it.key) {
                "low", "tearful" -> out += FactorKeys.MOOD_LOW
                "irritable" -> out += FactorKeys.IRRITABLE
                "anxious" -> out += FactorKeys.ANXIOUS
            }
        }
        w.filter { it.type == EventTypes.CRAVING }.forEach {
            if (it.key == "sweet") out += FactorKeys.CRAVING_SWEET
            if (it.key == "salt") out += FactorKeys.CRAVING_SALT
        }
        w.filter { it.type == EventTypes.SCREEN_DAY }.forEach {
            val total = it.value ?: 0.0
            val night = Regex("night=(\\d+)").find(it.text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            if (night >= 30) out += FactorKeys.LATE_SCREEN
            if (total >= max(240.0, (avgScreen ?: 0.0) * 1.3)) out += FactorKeys.HIGH_SCREEN
        }
        if (periodStarts.any { ts >= it - 2 * Time.DAY && ts <= it + 3 * Time.DAY }) out += FactorKeys.PERIMENSTRUAL
        return out
    }
}

enum class Confidence { LOW, MODERATE, STRONG }

data class Pattern(
    val factor: String, val hits: Int, val total: Int,
    val baselineRate: Double, val lift: Double, val confidence: Confidence
)

/** totalAttacks < 3 means "not enough data yet" - the UI must say so instead of guessing. */
data class PatternResult(val totalAttacks: Int, val patterns: List<Pattern>) {
    val enoughData get() = totalAttacks >= 3
}

class PatternEngine(private val db: KanraehDatabase, private val foods: FoodDatabase) {

    suspend fun analyze(): PatternResult {
        val attacks = db.attacks().all()
        if (attacks.size < 3) return PatternResult(attacks.size, emptyList())
        val ev = db.events().all()
        val ex = FactorExtractor(ev, foods, ev.filter { it.type == EventTypes.PERIOD_START }.map { it.ts })

        val perAttack = attacks.map { ex.factorsBefore(it.startTs) }
        val now = Clock.now()
        val first = minOf(ev.minOfOrNull { it.ts } ?: now, attacks.minOf { it.startTs })
        var d = Time.startOfDay(max(first, now - 90 * Time.DAY)) + 14 * Time.HOUR
        val refs = mutableListOf<Set<String>>()
        while (d < now) {
            if (attacks.none { abs(it.startTs - d) < 12 * Time.HOUR }) refs += ex.factorsBefore(d)
            d += Time.DAY
        }

        val n = attacks.size
        val result = perAttack.flatten().distinct().mapNotNull { f ->
            val hits = perAttack.count { f in it }
            if (hits < 2) return@mapNotNull null
            val rate = hits.toDouble() / n
            val base = if (refs.size >= 7) refs.count { f in it }.toDouble() / refs.size else 0.1
            val lift = rate / max(base, 0.05)
            val conf = when {
                n >= 8 && rate >= 0.6 && lift >= 1.5 -> Confidence.STRONG
                n >= 5 && rate >= 0.5 && lift >= 1.3 -> Confidence.MODERATE
                lift >= 1.2 -> Confidence.LOW
                else -> return@mapNotNull null
            }
            Pattern(f, hits, n, base, lift, conf)
        }.sortedWith(compareByDescending<Pattern> { it.confidence }.thenByDescending { it.lift })
        return PatternResult(n, result)
    }
}

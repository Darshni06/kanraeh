package com.kanraeh.app.pain

import com.kanraeh.app.core.Attack
import com.kanraeh.app.core.AttackDao
import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.PainTap
import com.kanraeh.app.core.Time

/** MODULE: pain - migraine attacks. One tap = one meaning + an automatic timestamp. Durations are computed, never typed. */
enum class PainLevel(val code: String, val label: String, val severity: Int) {
    STARTING("starting", "It's starting", 1),
    MANAGEABLE("manageable", "I can manage it", 2),
    HURTING("hurting", "It's hurting", 3),
    VERY_HARD("very_hard", "Bearable, but very hard", 4),
    UNBEARABLE("unbearable", "Unbearable", 5),
    EASING("easing", "It's easing", 2),
    GONE("gone", "It's gone", 0);

    companion object { fun from(code: String?) = values().firstOrNull { it.code == code } }
}

enum class HeadRegion(val code: String, val label: String) {
    FOREHEAD("forehead", "Forehead"), TOP("top", "Top of head"),
    LEFT_SIDE("left_side", "Left side"), RIGHT_SIDE("right_side", "Right side"),
    BEHIND_LEFT_EYE("behind_left_eye", "Behind left eye"), BEHIND_RIGHT_EYE("behind_right_eye", "Behind right eye"),
    BACK_OF_HEAD("back_of_head", "Back of head"), NECK("neck", "Neck"), JAW("jaw", "Jaw"), BOTH_SIDES("both_sides", "Both sides");

    companion object { fun from(code: String?) = values().firstOrNull { it.code == code } }
}

class AttackManager(private val dao: AttackDao) {

    /** "Starting" (or any level) opens an attack if none is open. "Gone" closes it. */
    suspend fun tapLevel(level: PainLevel): Attack? {
        val now = Clock.now()
        var a = dao.open()
        if (a == null) {
            if (level == PainLevel.GONE) return null
            val id = dao.insert(Attack(startTs = now))
            a = Attack(id = id, startTs = now)
        }
        dao.insertTap(PainTap(attackId = a.id, level = level.code, ts = now))
        if (level == PainLevel.GONE) { a = a.copy(endTs = now); dao.update(a) }
        return a
    }

    /** Where it hurts. Can be tapped any number of times as pain moves; each tap is timestamped. */
    suspend fun tapRegion(region: HeadRegion) {
        val a = dao.open() ?: return
        val lastLevel = dao.taps(a.id).lastOrNull { it.region == null }?.level ?: PainLevel.HURTING.code
        dao.insertTap(PainTap(attackId = a.id, level = lastLevel, region = region.code, ts = Clock.now()))
    }

    /** Undo the most recent tap (also reopens an attack if "gone" was a mistake). */
    suspend fun undoLast() {
        val a = dao.open() ?: dao.all().firstOrNull() ?: return
        val taps = dao.taps(a.id)
        val last = taps.lastOrNull()
        if (last == null) { dao.delete(a.id); return }
        dao.deleteTap(last.id)
        if (taps.size == 1) { dao.delete(a.id); return }
        if (last.level == PainLevel.GONE.code && a.endTs != null) dao.update(a.copy(endTs = null, estimatedEnd = false))
    }

    /** If she forgot to tap "gone", close the attack at the last known tap and mark it as an estimate. */
    suspend fun closeStale(maxHours: Int = 18) {
        val a = dao.open() ?: return
        val last = dao.taps(a.id).lastOrNull()?.ts ?: a.startTs
        if (Clock.now() - last > maxHours * Time.HOUR) dao.update(a.copy(endTs = last, estimatedEnd = true))
    }

    /** Time spent at each pain level, in order. */
    suspend fun timeAtLevels(a: Attack): List<Pair<PainLevel, Long>> {
        val taps = dao.taps(a.id).filter { it.region == null }
        val end = a.endTs ?: Clock.now()
        return taps.mapIndexedNotNull { i, t ->
            val lvl = PainLevel.from(t.level) ?: return@mapIndexedNotNull null
            if (lvl == PainLevel.GONE) null
            else lvl to ((taps.getOrNull(i + 1)?.ts ?: end) - t.ts)
        }
    }
}

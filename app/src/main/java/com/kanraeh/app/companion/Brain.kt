package com.kanraeh.app.companion

import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.KanraehDatabase
import com.kanraeh.app.core.LogEvent
import com.kanraeh.app.core.Mood
import com.kanraeh.app.core.Time
import com.kanraeh.app.cycle.CycleEngine
import com.kanraeh.app.pain.PainLevel
import com.kanraeh.app.screentime.ScreenTimeReader
import com.kanraeh.app.songs.SongManager

/**
 * MODULE: companion - decides WHEN to ask WHAT. Rules, in priority order:
 * attack check -> song follow-up -> song suggestion -> meal -> water -> mood -> sleep -> period -> screen check.
 * It only asks when the answer is useful, learns her meal times, respects quiet hours and a daily cap, and backs off if ignored.
 */
class CompanionBrain(
    private val db: KanraehDatabase,
    private val s: CompanionSettings,
    private val cycle: CycleEngine,
    private val screen: ScreenTimeReader,
    private val songs: SongManager
) {
    private fun opt(label: String, code: String, arg: String? = null) = PromptOption(label, PromptAction(code, arg))
    private val moodOptions = Mood.values().map { opt("${it.emoji} ${it.label}", Act.MOOD, it.code) }

    /** force = true is used inside the app (Today screen): ignores quiet hours and the daily cap. */
    suspend fun next(now: Long = Clock.now(), force: Boolean = false): Prompt? {
        if (!s.enabled && !force) return null

        // 1. During an attack: one calm question per hour, nothing else.
        if (db.attacks().open() != null) {
            return if (now - s.lastAsked("attack_check") > Time.HOUR)
                Prompt("attack_check", "I'm here. Do you need anything?", listOf(
                    opt("Water", Act.ATTACK_NEED, "water"), opt("Medicine", Act.ATTACK_NEED, "medicine"),
                    opt("Dark and quiet", Act.ATTACK_NEED, "dark"), opt("I'm okay", Act.SNOOZE)), calm = true)
            else null
        }
        if (!force && (s.inQuiet(now) || !s.canAskMore(now))) return null

        fun due(kind: String, gap: Long) = now - s.lastAsked(kind) >= gap
        val ev = db.events().between(now - 3 * Time.DAY, now)
        val hour = Time.hourOf(now)

        // 2. Song follow-up: "how do you feel now?"
        db.songs().pending(now - Time.HOUR, now - 2 * Time.MIN).firstOrNull { due("song_${it.id}", Time.DAY) }?.let {
            return Prompt("song_${it.id}", "You were listening to \"${it.title}\". How do you feel now?",
                Mood.values().map { m -> opt("${m.emoji} ${m.label}", Act.SONG_AFTER, "${it.id}:${m.code}") })
        }

        // 3. Song suggestion when a low mood was just logged and her own history has something that helped.
        val lastMood = ev.lastOrNull { it.type == EventTypes.MOOD }
        val lm = Mood.from(lastMood?.key)
        if (lastMood != null && lm != null && lm.valence < 0 && now - lastMood.ts < 30 * Time.MIN && due("song_suggest", 2 * Time.HOUR)) {
            songs.recommend(lm)?.let {
                return Prompt("song_suggest", "Last time you felt ${lm.label.lowercase()}, \"${it.title}\" helped you feel ${it.moodAfter.label.lowercase()}. Want to listen to it?",
                    listOf(opt("Okay", Act.SNOOZE), opt("Not now", Act.SNOOZE)))
            }
        }

        // 4. Meals, around her own usual times.
        val meals = ev.filter { it.type == EventTypes.MEAL }
        val slot = mealSlot(now)
        if (slot != null && meals.none { now - it.ts < 3 * Time.HOUR } && due("meal_$slot", 6 * Time.HOUR)) {
            return Prompt("meal_$slot", "Did you eat?", listOf(
                opt("Yes, let me add it", Act.MEAL_PICK), opt("I skipped it", Act.MEAL_SKIPPED), opt("Not yet", Act.SNOOZE)))
        }

        // 5. Water.
        val water = ev.filter { it.type == EventTypes.WATER }
        if (hour in 9..20 && water.none { now - it.ts < 3 * Time.HOUR } && due("water", 3 * Time.HOUR)) {
            return Prompt("water", "Had some water lately?", listOf(opt("Yes, a glass", Act.WATER), opt("Not yet", Act.SNOOZE)))
        }

        // 6. Mood.
        if (hour >= 11 && ev.none { it.type == EventTypes.MOOD && now - it.ts < 6 * Time.HOUR } && due("mood", 6 * Time.HOUR)) {
            return Prompt("mood", "How are you feeling right now?", moodOptions)
        }

        // 7. Sleep, mornings only.
        if (hour in 6..11 && ev.none { it.type == EventTypes.SLEEP && it.ts >= Time.startOfDay(now) } && due("sleep", Time.DAY)) {
            return Prompt("sleep", "How long did you sleep last night?", listOf(
                opt("Under 5 h", Act.SLEEP, "4.0"), opt("5 to 6 h", Act.SLEEP, "5.5"),
                opt("7 to 8 h", Act.SLEEP, "7.5"), opt("9 h or more", Act.SLEEP, "9.5")))
        }

        // 8. Period, only when it may be due (or late) and nothing was logged.
        val c = cycle.summary(now)
        if (c.windowStart != null && now >= c.windowStart && ev.none { it.type == EventTypes.PERIOD_START } && due("period", Time.DAY)) {
            return Prompt("period", "Has your period started?", listOf(opt("Yes", Act.PERIOD_START), opt("Not yet", Act.SNOOZE)))
        }

        // 9. Screen-time check: a long unbroken session in the last 2 hours.
        if (screen.hasAccess() && due("screen_check", 6 * Time.HOUR) && screen.summarize(now - 2 * Time.HOUR, now).longestMin >= 90) {
            return Prompt("screen_check", "You've been on your phone for a while. How are you feeling?", moodOptions)
        }
        return null
    }

    /** Learns typical breakfast/lunch/dinner hour from past meals; asks 30-120 min after that time. */
    private suspend fun mealSlot(now: Long): Int? {
        val past = db.events().latest(EventTypes.MEAL, 120).filter { it.key != "skipped" }
        val windows = listOf(7..10, 12..15, 18..21)
        val defaults = listOf(8 * 60 + 30, 13 * 60, 20 * 60)
        val nowMin = Time.minuteOfDay(now)
        windows.forEachIndexed { i, w ->
            val mins = past.map { Time.minuteOfDay(it.ts) }.filter { it / 60 in w }.sorted()
            val typical = if (mins.size >= 3) mins[mins.size / 2] else defaults[i]
            if (nowMin in (typical + 30)..(typical + 120)) return i
        }
        return null
    }
}

class PromptHandler(private val db: KanraehDatabase, private val songs: SongManager) {
    /** Applies a tapped option. Returns a screen name to open in the app ("food", "meds") or null. */
    suspend fun apply(a: PromptAction): String? {
        val now = Clock.now()
        fun ev(type: String, key: String = "", value: Double? = null) = LogEvent(type = type, key = key, value = value, ts = now)
        when (a.code) {
            Act.WATER -> db.events().insert(ev(EventTypes.WATER, value = 1.0))
            Act.MEAL_SKIPPED -> db.events().insert(ev(EventTypes.MEAL, "skipped"))
            Act.MEAL_PICK -> return "food"
            Act.MOOD -> db.events().insert(ev(EventTypes.MOOD, a.arg ?: "calm"))
            Act.SLEEP -> db.events().insert(ev(EventTypes.SLEEP, value = a.arg?.toDoubleOrNull()))
            Act.PERIOD_START -> db.events().insert(ev(EventTypes.PERIOD_START))
            Act.SONG_AFTER -> a.arg?.split(":")?.let { if (it.size == 2) songs.finish(it[0].toLong(), it[1]) }
            Act.ATTACK_NEED -> when (a.arg) {
                "water" -> db.events().insert(ev(EventTypes.WATER, value = 1.0))
                "medicine" -> return "meds"
            }
        }
        return null
    }
}

class MessageWriter(private val db: KanraehDatabase, private val s: CompanionSettings) {
    /** Every line is built from her own data. No random encouragement. */
    suspend fun today(now: Long = Clock.now()): String {
        val attacks = db.attacks().all()
        val open = attacks.firstOrNull { it.endTs == null }
        if (open != null) return duringAttack(now)
        if (attacks.isEmpty()) return "Welcome. Log whatever you like, one tap at a time. Patterns start to appear after a few attacks."
        val last = attacks.first()
        val end = last.endTs ?: last.startTs
        val days = ((now - end) / Time.DAY).toInt()
        return if (days >= 7) "$days days since your last migraine. Your body had a real break."
        else "Your last migraine lasted ${Time.fmtDuration(end - last.startTs)} and it ended. You got through it."
    }

    suspend fun duringAttack(now: Long = Clock.now()): String {
        val attacks = db.attacks().all()
        val monthStart = now - 30 * Time.DAY
        val thisMonth = attacks.count { it.startTs >= monthStart }
        val prev = attacks.firstOrNull { it.endTs != null }
        val parts = mutableListOf("This is attack $thisMonth in the last 30 days.")
        if (prev?.endTs != null) parts += "Your last one lasted ${Time.fmtDuration(prev.endTs - prev.startTs)} and it ended. This one will too."
        else parts += "It will pass."
        if (s.futureNote.isNotBlank()) parts += "A note from you: \"${s.futureNote}\""
        return parts.joinToString(" ")
    }
}

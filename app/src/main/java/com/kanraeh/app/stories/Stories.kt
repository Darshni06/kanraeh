package com.kanraeh.app.stories

import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.Describe
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.FactorKeys
import com.kanraeh.app.core.KanraehDatabase
import com.kanraeh.app.core.Time
import com.kanraeh.app.cycle.CycleEngine
import com.kanraeh.app.core.Attack
import com.kanraeh.app.food.FoodDatabase
import com.kanraeh.app.knowledge.KnowledgeBase
import com.kanraeh.app.pain.HeadRegion
import com.kanraeh.app.pain.PainLevel
import com.kanraeh.app.patterns.FactorExtractor
import com.kanraeh.app.patterns.PatternResult

/**
 * MODULE: stories - "Story Cards": what happened in order, why it may matter, what to adjust next time.
 * Facts come from her data; explanations come ONLY from the knowledge file. No knowledge entry => no invented science.
 */
data class StoryStep(val ts: Long, val label: String, val text: String)

data class StoryCard(
    val title: String,
    val anchorTs: Long,
    val steps: List<StoryStep>,
    val mayMean: List<String>,
    val tryNext: List<String>,
    val evidenceNote: String,
    val doctorNote: String,
    val sources: List<String>,
    val science: List<String>   // deeper "see the evidence" text (whyScience)
)

class StoryBuilder(
    private val db: KanraehDatabase,
    private val foods: FoodDatabase,
    private val cycle: CycleEngine,
    private val kb: KnowledgeBase
) {
    suspend fun forAttack(a: Attack, result: PatternResult): StoryCard {
        val end = a.endTs ?: Clock.now()
        val ev = db.events().between(a.startTs - 24 * Time.HOUR, end)
        val steps = ev.map { StoryStep(it.ts, Time.fmtDateTime(it.ts), Describe.event(it, foods)) }.toMutableList()
        steps += cycleStep(a.startTs)
        db.attacks().taps(a.id).forEach {
            val lvl = PainLevel.from(it.level)?.label ?: it.level
            val where = HeadRegion.from(it.region)?.let { r -> " (${r.label.lowercase()})" } ?: ""
            steps += StoryStep(it.ts, Time.fmtDateTime(it.ts), "Pain: $lvl$where")
        }
        steps.sortBy { it.ts }
        val factors = factorsAt(a.startTs)
        val matched = result.patterns.filter { it.factor in factors }

        val mayMean = mutableListOf<String>(); val tryNext = mutableListOf<String>()
        val sources = mutableListOf<String>(); val science = mutableListOf<String>(); var doctor = ""
        if (!result.enoughData) {
            mayMean += "Not enough attacks logged yet to say what matters for you. Patterns start to appear after about 3 attacks."
        } else if (matched.isEmpty()) {
            mayMean += "Nothing in the 24 hours before this attack stood out against your usual days."
        }
        matched.forEach { p ->
            mayMean += "${FactorKeys.label(p.factor).replaceFirstChar { it.uppercase() }} came before ${p.hits} of your ${p.total} attacks."
            val entries = kb.forFactor(p.factor)
            entries.forEach { e ->
                if (e.whyPlain.isNotBlank()) mayMean += e.whyPlain
                tryNext += e.tryThis
                sources += e.sources
                if (e.whyScience.isNotBlank()) science += "${e.title}: ${e.whyScience} (evidence: ${e.evidence})"
                if (e.safetyNote.isNotBlank()) doctor = e.safetyNote
            }
            if (entries.isEmpty()) tryNext += "Keep an eye on ${FactorKeys.label(p.factor)} and note what happens."
        }
        val dur = Time.fmtDuration(end - a.startTs) + if (a.estimatedEnd) " (estimated)" else ""
        return StoryCard(
            title = "Migraine on ${Time.fmtDateTime(a.startTs)}  -  $dur",
            anchorTs = a.startTs, steps = steps,
            mayMean = mayMean, tryNext = tryNext.distinct(),
            evidenceNote = "Based on ${result.totalAttacks} logged attacks. A link is not proof of cause.",
            doctorNote = doctor.ifBlank { "Share this pattern with your doctor." },
            sources = sources.distinct(), science = science
        )
    }

    /** Same story shape for any other issue (low mood, craving, late period...). */
    suspend fun forIssue(title: String, anchorTs: Long): StoryCard {
        val ev = db.events().between(anchorTs - 24 * Time.HOUR, anchorTs)
        val steps = ev.map { StoryStep(it.ts, Time.fmtDateTime(it.ts), Describe.event(it, foods)) }.toMutableList()
        steps += cycleStep(anchorTs)
        steps.sortBy { it.ts }
        val factors = factorsAt(anchorTs)
        val mayMean = mutableListOf<String>(); val tryNext = mutableListOf<String>()
        val sources = mutableListOf<String>(); val science = mutableListOf<String>(); var doctor = ""
        factors.forEach { f ->
            kb.forFactor(f).forEach { e ->
                if (e.whyPlain.isNotBlank()) mayMean += e.whyPlain
                tryNext += e.tryThis; sources += e.sources
                if (e.whyScience.isNotBlank()) science += "${e.title}: ${e.whyScience} (evidence: ${e.evidence})"
                if (e.safetyNote.isNotBlank()) doctor = e.safetyNote
            }
        }
        if (mayMean.isEmpty()) mayMean += "Not enough information yet to explain this. Keep logging and it will become clearer."
        return StoryCard(
            title = title, anchorTs = anchorTs, steps = steps, mayMean = mayMean, tryNext = tryNext.distinct(),
            evidenceNote = "Based on what you logged in the 24 hours before. A link is not proof of cause.",
            doctorNote = doctor.ifBlank { "If this keeps happening, tell your doctor." },
            sources = sources.distinct(), science = science
        )
    }

    /** Low mood, strongest recent craving, and a late period, each as its own story. */
    suspend fun issueStories(): List<StoryCard> {
        val out = mutableListOf<StoryCard>()
        db.events().latest(EventTypes.MOOD, 40).firstOrNull { it.key == "low" || it.key == "tearful" || it.key == "anxious" || it.key == "irritable" }
            ?.let { out += forIssue("Mood swing on ${Time.fmtDateTime(it.ts)}", it.ts) }
        db.events().latest(EventTypes.CRAVING, 1).firstOrNull()
            ?.let { out += forIssue("Craving (${it.key}) on ${Time.fmtDateTime(it.ts)}", it.ts) }
        val c = cycle.summary()
        if (c.lateByDays != null) out += forIssue("Period later than your usual range (${c.lateByDays} days)", Clock.now())
        return out
    }

    private suspend fun factorsAt(ts: Long): Set<String> {
        val ev = db.events().all()
        return FactorExtractor(ev, foods, ev.filter { it.type == EventTypes.PERIOD_START }.map { it.ts }).factorsBefore(ts)
    }

    private suspend fun cycleStep(ts: Long): List<StoryStep> {
        val starts = db.events().latest(EventTypes.PERIOD_START, 12).map { it.ts }.sorted()
        val prev = starts.lastOrNull { it <= ts }
        val next = starts.firstOrNull { it > ts }
        val text = when {
            prev != null && ts - prev <= 5 * Time.DAY -> "Cycle: day ${((ts - prev) / Time.DAY) + 1} of your period"
            next != null && next - ts <= 3 * Time.DAY -> "Cycle: ${((next - ts) / Time.DAY)} day(s) before your period"
            else -> return emptyList()
        }
        return listOf(StoryStep(ts - 1, Time.fmtDateTime(ts), text))
    }
}

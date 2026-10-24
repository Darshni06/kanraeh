package com.kanraeh.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kanraeh.app.companion.Prompt
import com.kanraeh.app.companion.PromptAction
import com.kanraeh.app.core.Attack
import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.Describe
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.Graph
import com.kanraeh.app.core.LogEvent
import com.kanraeh.app.core.Mood
import com.kanraeh.app.core.PainTap
import com.kanraeh.app.core.Time
import com.kanraeh.app.meds.MedClass
import com.kanraeh.app.pain.HeadRegion
import com.kanraeh.app.pain.PainLevel
import com.kanraeh.app.patterns.PatternResult
import com.kanraeh.app.safety.RedFlag
import com.kanraeh.app.safety.Safety
import com.kanraeh.app.stories.StoryCard
import com.kanraeh.app.theme.Palette
import com.kanraeh.app.ui.screens.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** MODULE: ui - the ONLY bridge between pure screens and the logic modules. Antigravity should not need to edit this file. */
@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Graph.db

    val openAttack = db.attacks().observeOpen().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val recent = db.events().observeRecent(500).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<LogEvent>())
    val attacks = db.attacks().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Attack>())

    val palette = MutableStateFlow(Graph.paletteStore.current())
    val message = MutableStateFlow("")
    val prompt = MutableStateFlow<Prompt?>(null)
    val patterns = MutableStateFlow(PatternResult(0, emptyList()))
    val stories = MutableStateFlow<List<StoryCard>>(emptyList())
    val issueStories = MutableStateFlow<List<StoryCard>>(emptyList())
    val timeline = MutableStateFlow<List<TimelineItem>>(emptyList())
    val attackTimeline = MutableStateFlow<List<String>>(emptyList())
    val songSuggestion = MutableStateFlow<String?>(null)
    val safetyNotes = MutableStateFlow<List<String>>(emptyList())
    val emergency = MutableStateFlow<String?>(null)
    val status = MutableStateFlow<String?>(null)
    /** "food" or "meds" - set when the avatar asked the app to open an input dialog. */
    val pendingOpen = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { combine(recent, attacks, openAttack) { _, _, _ -> 0 }.debounce(500).collect { refresh() } }
    }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        val now = Clock.now()
        message.value = Graph.messages.today(now)
        prompt.value = Graph.brain.next(now, force = true)
        val ev = db.events().all()
        timeline.value = buildTimeline(ev)
        val open = db.attacks().open()
        attackTimeline.value = if (open == null) emptyList() else
            db.attacks().taps(open.id).map { "${Time.fmtTime(it.ts)}  ${tapText(it)}" }
        val res = Graph.patterns.analyze()
        patterns.value = res
        stories.value = db.attacks().all().take(5).map { Graph.stories.forAttack(it, res) }
        issueStories.value = Graph.stories.issueStories()
        val notes = mutableListOf<String>()
        notes += Safety.overuseNotes(Graph.meds.overuse(now))
        if (Safety.auraWithEstrogen(ev, Graph.settings.takesEstrogen)) notes += Safety.AURA_ESTROGEN_TEXT
        safetyNotes.value = notes
        val lastMood = ev.lastOrNull { it.type == EventTypes.MOOD && now - it.ts < 30 * Time.MIN }
        songSuggestion.value = Mood.from(lastMood?.key)?.takeIf { it.valence < 0 }?.let { m ->
            Graph.songs.recommend(m)?.let { "Last time you felt ${m.label.lowercase()}, \"${it.title}\" helped. Want to listen to it?" }
        }
    }

    private fun tapText(t: PainTap): String {
        val lvl = PainLevel.from(t.level)?.label ?: t.level
        val r = HeadRegion.from(t.region)?.let { " - ${it.label}" } ?: ""
        return lvl + r
    }

    private fun buildTimeline(ev: List<LogEvent>): List<TimelineItem> {
        val items = ev.map { TimelineItem(it.ts, Describe.event(it, Graph.foods)) }.toMutableList()
        return items.sortedByDescending { it.ts }.take(400)
    }

    // ---- attack
    fun startAttack() = launchIO { Graph.attacks.tapLevel(PainLevel.STARTING) }
    fun tapLevel(l: PainLevel) = launchIO { Graph.attacks.tapLevel(l) }
    fun tapRegion(r: HeadRegion) = launchIO { Graph.attacks.tapRegion(r) }
    fun undo() = launchIO { Graph.attacks.undoLast() }
    fun reportRedFlags(f: Set<RedFlag>) { emergency.value = Safety.emergency(f) }
    fun dismissEmergency() { emergency.value = null }

    // ---- quick logs
    private fun add(type: String, key: String = "", value: Double? = null, text: String = "") =
        launchIO { db.events().insert(LogEvent(type = type, key = key, value = value, text = text, ts = Clock.now())) }

    fun logWater() = add(EventTypes.WATER, value = 1.0)
    fun logFood(id: String) = add(EventTypes.MEAL, id)
    fun logSkippedMeal() = add(EventTypes.MEAL, "skipped")
    fun addCustomFood(name: String, tags: List<String>) { val f = Graph.foods.addCustom(name, tags); logFood(f.id) }
    fun logSleep(h: Double) = add(EventTypes.SLEEP, value = h)
    fun logPeriod() = add(EventTypes.PERIOD_START)
    fun logAura() = add(EventTypes.SYMPTOM, "aura")
    fun logCraving(kind: String) = add(EventTypes.CRAVING, kind)
    fun logAcne(level: Int) = add(EventTypes.ACNE, value = level.toDouble())
    fun logHair(area: String, score: Int) = add(EventTypes.HAIR, area, score.toDouble())
    fun logMed(name: String, cls: MedClass, dose: String) = launchIO { Graph.meds.log(name, cls, dose) }
    fun logLab(name: String, v: Double, unit: String) = launchIO { Graph.meds.logLab(name, v, unit) }

    /** Mood, plus the song she is listening to (typed, or read from the media notification) when the mood is low. */
    fun logMood(m: Mood, song: String?) = launchIO {
        db.events().insert(LogEvent(type = EventTypes.MOOD, key = m.code, ts = Clock.now()))
        val title = song ?: com.kanraeh.app.songs.NowPlaying.recent()
        if (title != null && m.valence < 0) Graph.songs.start(m, title)
    }

    fun answer(p: Prompt, a: PromptAction) = launchIO {
        Graph.settings.registerAsked(p.kind, Clock.now())
        Graph.settings.ignoredStreak = 0
        Graph.handler.apply(a)?.let { pendingOpen.value = it }
    }

    private fun launchIO(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block(); refresh() }

    fun setPalette(p: Palette) { palette.value = p }
}

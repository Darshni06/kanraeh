package com.kanraeh.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanraeh.app.companion.Prompt
import com.kanraeh.app.companion.PromptAction
import com.kanraeh.app.core.FactorKeys
import com.kanraeh.app.core.Time
import com.kanraeh.app.pain.HeadRegion
import com.kanraeh.app.pain.PainLevel
import com.kanraeh.app.patterns.PatternResult
import com.kanraeh.app.stories.StoryCard
import com.kanraeh.app.ui.components.BigButton
import com.kanraeh.app.ui.components.ButtonGrid
import com.kanraeh.app.ui.components.SectionTitle
import com.kanraeh.app.ui.components.SoftButton
import com.kanraeh.app.ui.components.SoftCard

/**
 * MODULE: ui/screens - PURE screens. Each takes a State and an Actions object (see docs/SCREEN_CONTRACTS.md).
 * They never touch the database or other modules. Antigravity: restyle these freely, keep the parameters.
 */

// ---------------------------------------------------------------- Today
data class TodayState(
    val message: String, val prompt: Prompt?, val glassesToday: Int, val mealsToday: Int,
    val lastMood: String?, val songSuggestion: String?, val safetyNotes: List<String>
)
data class TodayActions(
    val onMigraine: () -> Unit, val onWater: () -> Unit, val onFood: () -> Unit,
    val onMood: () -> Unit, val onSleep: () -> Unit, val onAnswer: (PromptAction) -> Unit
)

@Composable
fun TodayScreen(s: TodayState, a: TodayActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(s.message, style = MaterialTheme.typography.titleMedium)
        BigButton("I have a migraine", onClick = a.onMigraine)
        s.prompt?.let { p ->
            SoftCard {
                Text(p.text, style = MaterialTheme.typography.titleMedium)
                ButtonGrid(p.options, 2, { it.label }) { a.onAnswer(it.action) }
            }
        }
        s.songSuggestion?.let { SoftCard { Text(it) } }
        s.safetyNotes.forEach { SoftCard { Text(it) } }
        SectionTitle("Quick log")
        ButtonGrid(listOf("\uD83D\uDCA7 Water" to a.onWater, "\uD83C\uDF7D Food" to a.onFood, "\uD83D\uDE42 Mood" to a.onMood, "\uD83D\uDE34 Sleep" to a.onSleep), 2, { it.first }) { it.second() }
        SoftCard {
            Text("Today so far")
            Text("Water: ${s.glassesToday} glass(es)   Meals: ${s.mealsToday}   Mood: ${s.lastMood ?: "not logged"}")
        }
    }
}

// ---------------------------------------------------------------- Attack mode (dim)
data class AttackState(val message: String, val timeline: List<String>, val emergencyText: String?)
data class AttackActions(
    val onLevel: (PainLevel) -> Unit, val onRegion: (HeadRegion) -> Unit, val onUndo: () -> Unit,
    val onMedicine: () -> Unit, val onSomethingWrong: () -> Unit, val onDismissEmergency: () -> Unit
)

@Composable
fun AttackScreen(s: AttackState, a: AttackActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(s.message, style = MaterialTheme.typography.titleMedium)
        s.emergencyText?.let { SoftCard { Text(it, style = MaterialTheme.typography.titleMedium); TextButton(onClick = a.onDismissEmergency) { Text("OK") } } }
        SectionTitle("How is it now?")
        PainLevel.values().forEach { BigButton(it.label) { a.onLevel(it) } }
        SectionTitle("Where does it hurt?")
        ButtonGrid(HeadRegion.values().toList(), 2, { it.label }) { a.onRegion(it) }
        SoftButton("Took medicine", Modifier.fillMaxWidth(), a.onMedicine)
        SoftButton("Something is different or worse", Modifier.fillMaxWidth(), a.onSomethingWrong)
        SoftButton("Undo last tap", Modifier.fillMaxWidth(), a.onUndo)
        if (s.timeline.isNotEmpty()) { SectionTitle("So far"); s.timeline.forEach { Text(it) } }
    }
}

// ---------------------------------------------------------------- My body (timeline)
data class TimelineItem(val ts: Long, val title: String, val detail: String = "")

@Composable
fun BodyScreen(entries: List<TimelineItem>) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("My body, in order") }
        if (entries.isEmpty()) item { Text("Nothing logged yet. Everything you tap appears here with its time.") }
        items(entries.size) { i ->
            val row = entries[i]
            val showDay = i == 0 || Time.fmtDate(entries[i - 1].ts) != Time.fmtDate(row.ts)
            Column {
                if (showDay) Text(Time.fmtDate(row.ts), style = MaterialTheme.typography.titleSmall)
                Text("${Time.fmtTime(row.ts)}   ${row.title}")
                if (row.detail.isNotBlank()) Text(row.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------------------------------------------------------- Patterns & Story Cards
@Composable
fun PatternsScreen(patterns: PatternResult, stories: List<StoryCard>, issueStories: List<StoryCard>) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("What I'm noticing") }
        if (!patterns.enoughData) item { Text("Not enough attacks logged yet (${patterns.totalAttacks}). Patterns appear after about 3.") }
        patterns.patterns.forEach { p ->
            item {
                SoftCard {
                    Text(FactorKeys.label(p.factor).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                    Text("Came before ${p.hits} of ${p.total} attacks. Confidence: ${p.confidence.name.lowercase()}.")
                }
            }
        }
        item { SectionTitle("Story cards") }
        items(stories + issueStories) { StoryCardView(it) }
        item { Text("", Modifier.padding(24.dp)) }
    }
}

@Composable
fun StoryCardView(c: StoryCard) {
    var evidence by remember { mutableStateOf(false) }
    SoftCard {
        Text(c.title, style = MaterialTheme.typography.titleSmall)
        Text("What happened", style = MaterialTheme.typography.labelLarge)
        c.steps.forEach { Text("${it.label}: ${it.text}", style = MaterialTheme.typography.bodyMedium) }
        Text("What it may mean", style = MaterialTheme.typography.labelLarge)
        c.mayMean.forEach { Text(it) }
        if (c.tryNext.isNotEmpty()) { Text("What you could try next time", style = MaterialTheme.typography.labelLarge); c.tryNext.forEach { Text("- $it") } }
        Text(c.doctorNote, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { evidence = !evidence }) { Text(if (evidence) "Hide the evidence" else "See the evidence") }
        if (evidence) {
            Text(c.evidenceNote, style = MaterialTheme.typography.bodySmall)
            c.science.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            c.sources.forEach { Text("Source: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------------------------------------------------------------- Lock
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Kanraeh", style = MaterialTheme.typography.headlineMedium)
        BigButton("Unlock", Modifier.padding(top = 24.dp), onUnlock)
    }
}

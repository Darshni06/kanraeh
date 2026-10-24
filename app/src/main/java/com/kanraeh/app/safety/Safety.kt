package com.kanraeh.app.safety

import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.LogEvent
import com.kanraeh.app.meds.OveruseLine
import com.kanraeh.app.meds.OveruseStatus

/**
 * MODULE: safety - always overrides everything else. The app never diagnoses.
 * Wording here is deliberately generic; have a doctor review it before sharing the app with anyone else.
 */
enum class RedFlag(val label: String) {
    SUDDEN_WORST("Sudden, worst headache of my life"),
    WEAKNESS("Weakness or numbness on one side"),
    SPEECH("Trouble speaking or understanding"),
    VISION("Sudden loss of vision"),
    FEVER_STIFF("Fever with a stiff neck"),
    CONFUSION("Confusion or fainting")
}

object Safety {
    const val EMERGENCY_TEXT =
        "These can be signs of something serious. Please get medical help now: call your local emergency number or go to the nearest emergency room."
    const val AURA_ESTROGEN_TEXT =
        "You've logged aura and you take an estrogen-containing pill. This is worth discussing with your doctor soon."

    fun emergency(flags: Set<RedFlag>): String? = if (flags.isEmpty()) null else EMERGENCY_TEXT

    fun auraWithEstrogen(events: List<LogEvent>, takesEstrogen: Boolean): Boolean =
        takesEstrogen && events.any { it.type == EventTypes.SYMPTOM && it.key == "aura" }

    fun overuseNotes(lines: List<OveruseLine>): List<String> = lines.filter { it.status != OveruseStatus.OK }.map {
        val verb = if (it.status == OveruseStatus.OVER) "has reached" else "is close to"
        "Your ${it.med.label.lowercase()} use (${it.daysLast30} days in the last 30) $verb the monthly limit often used for medication-overuse headache (${it.limit}). Please talk to your doctor."
    }
}

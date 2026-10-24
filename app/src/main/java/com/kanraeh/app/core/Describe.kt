package com.kanraeh.app.core

import com.kanraeh.app.food.FoodDatabase

/** MODULE: core - turns a raw event into one plain sentence (used by timeline and stories). */
object Describe {
    fun event(e: LogEvent, foods: FoodDatabase): String = when (e.type) {
        EventTypes.MEAL -> when (e.key) {
            "skipped" -> "Skipped a meal"
            "unspecified" -> "Ate something"
            else -> "Ate ${foods.nameOf(e.key)}"
        }
        EventTypes.WATER -> "Had water"
        EventTypes.MOOD -> "Mood: ${Mood.from(e.key)?.label ?: e.key}"
        EventTypes.SLEEP -> "Slept about ${"%.1f".format(e.value ?: 0.0)} hours"
        EventTypes.CRAVING -> "Craving ${e.key}"
        EventTypes.MED -> "Took ${e.text.substringBefore('|')}"
        EventTypes.LAB -> "Lab: ${e.key} ${e.value ?: ""} ${e.text}"
        EventTypes.PERIOD_START -> "Period started"
        EventTypes.PERIOD_END -> "Period ended"
        EventTypes.HAIR -> "Hair check: ${e.key} ${e.value?.toInt() ?: 0}/4"
        EventTypes.HAIR_REMOVAL -> "Removed hair (${e.key})"
        EventTypes.ACNE -> "Acne level ${e.value?.toInt() ?: 0}/3"
        EventTypes.SYMPTOM -> e.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
        EventTypes.SCREEN_DAY -> "Screen time ${((e.value ?: 0.0) / 60).let { "%.1f".format(it) }} h"
        EventTypes.WEIGHT -> "Weight ${e.value ?: ""}"
        else -> e.text.ifBlank { e.type }
    }
}

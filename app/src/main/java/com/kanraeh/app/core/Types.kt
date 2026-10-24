package com.kanraeh.app.core

/**
 * MODULE: core - shared vocabulary used by every other module.
 * Event types, moods and "factor keys". Factor keys are the contract between
 * patterns (detects them), knowledge (explains them) and stories (tells them).
 */
object EventTypes {
    const val MEAL = "meal"            // key = food id | "skipped" | "unspecified"
    const val WATER = "water"          // value = glasses
    const val MOOD = "mood"            // key = Mood.code
    const val SLEEP = "sleep"          // value = hours slept last night
    const val CRAVING = "craving"      // key = "sweet" | "salt"
    const val MED = "med"              // key = MedClass.code, text = "name|dose"
    const val LAB = "lab"              // key = test name, value = number, text = unit
    const val PERIOD_START = "period_start"
    const val PERIOD_END = "period_end"
    const val HAIR = "hair"            // key = area, value = 0..4
    const val HAIR_REMOVAL = "hair_removal"
    const val ACNE = "acne"            // value = 0..3
    const val SYMPTOM = "symptom"      // key = "aura", "nausea", ...
    const val SCREEN_DAY = "screen_day" // value = minutes, text = "night=N;longest=N"
    const val WEIGHT = "weight"
    const val NOTE = "note"            // free "anything else" (also analysed by the user later)
}

enum class Mood(val code: String, val label: String, val emoji: String, val valence: Int) {
    CALM("calm", "Calm", "\uD83D\uDE0C", 2),
    ENERGETIC("energetic", "Energetic", "\uD83D\uDE04", 2),
    LOW("low", "Low", "\uD83D\uDE14", -2),
    IRRITABLE("irritable", "Irritable", "\uD83D\uDE20", -1),
    ANXIOUS("anxious", "Anxious", "\uD83D\uDE1F", -2),
    TEARFUL("tearful", "Tearful", "\uD83D\uDE22", -2);

    companion object { fun from(code: String?) = values().firstOrNull { it.code == code } }
}

object FactorKeys {
    const val SKIPPED_MEAL = "skipped_meal"
    const val LONG_FOOD_GAP = "long_food_gap"
    const val SHORT_SLEEP = "short_sleep"
    const val LOW_WATER = "low_water"
    const val SWEET_FOOD = "sweet_food"
    const val CAFFEINE = "caffeine"
    const val SALTY_FOOD = "salty_food"
    const val FRIED_FOOD = "fried_food"
    const val REFINED_CARB = "refined_carb"
    const val TYRAMINE_FOOD = "tyramine_food"
    const val CHOCOLATE = "chocolate"
    const val ALCOHOL = "alcohol"
    const val MOOD_LOW = "mood_low"
    const val IRRITABLE = "irritable"
    const val ANXIOUS = "anxious"
    const val CRAVING_SWEET = "craving_sweet"
    const val CRAVING_SALT = "craving_salt"
    const val HIGH_SCREEN = "high_screen"
    const val LATE_SCREEN = "late_screen"
    const val PERIMENSTRUAL = "perimenstrual"

    val labels = mapOf(
        SKIPPED_MEAL to "a skipped meal", LONG_FOOD_GAP to "a long gap without food",
        SHORT_SLEEP to "short sleep", LOW_WATER to "little water", SWEET_FOOD to "sugary food",
        CAFFEINE to "caffeine", SALTY_FOOD to "salty or processed food", FRIED_FOOD to "fried or oily food",
        REFINED_CARB to "refined carbs", TYRAMINE_FOOD to "aged or fermented food", CHOCOLATE to "chocolate",
        ALCOHOL to "alcohol", MOOD_LOW to "low mood", IRRITABLE to "irritability", ANXIOUS to "anxiety",
        CRAVING_SWEET to "a sugar craving", CRAVING_SALT to "a salt craving",
        HIGH_SCREEN to "a lot of screen time", LATE_SCREEN to "late-night screen use",
        PERIMENSTRUAL to "the days around your period"
    )
    fun label(k: String) = labels[k] ?: k.replace('_', ' ')

    fun fromFoodTag(tag: String): String? = when (tag) {
        "sweet" -> SWEET_FOOD; "caffeine" -> CAFFEINE; "salty", "processed" -> SALTY_FOOD
        "fried" -> FRIED_FOOD; "refined_carb" -> REFINED_CARB; "tyramine" -> TYRAMINE_FOOD
        "chocolate" -> CHOCOLATE; "alcohol" -> ALCOHOL; else -> null
    }
}

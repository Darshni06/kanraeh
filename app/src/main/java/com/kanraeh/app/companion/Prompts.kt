package com.kanraeh.app.companion

import org.json.JSONArray
import org.json.JSONObject

/** MODULE: companion - what the avatar asks. A prompt is one question with a few big buttons. */
data class PromptAction(val code: String, val arg: String? = null)
data class PromptOption(val label: String, val action: PromptAction)
data class Prompt(val kind: String, val text: String, val options: List<PromptOption>, val calm: Boolean = false)

object Act {
    const val WATER = "water"; const val MEAL_PICK = "meal_pick"; const val MEAL_SKIPPED = "meal_skipped"
    const val MOOD = "mood"; const val SLEEP = "sleep"; const val PERIOD_START = "period_start"
    const val SONG_AFTER = "song_after"; const val ATTACK_NEED = "attack_need"; const val SNOOZE = "snooze"
    const val OPEN_SONG = "open_song"
}

object PromptJson {
    fun to(p: Prompt): String {
        val arr = JSONArray()
        p.options.forEach {
            arr.put(JSONObject().put("label", it.label).put("code", it.action.code).put("arg", it.action.arg ?: JSONObject.NULL))
        }
        return JSONObject().put("kind", p.kind).put("text", p.text).put("calm", p.calm).put("options", arr).toString()
    }

    fun from(s: String): Prompt? = try {
        val o = JSONObject(s); val a = o.getJSONArray("options")
        Prompt(o.getString("kind"), o.getString("text"), (0 until a.length()).map {
            val x = a.getJSONObject(it)
            PromptOption(x.getString("label"), PromptAction(x.getString("code"), if (x.isNull("arg")) null else x.getString("arg")))
        }, o.optBoolean("calm", false))
    } catch (e: Exception) { null }
}

package com.kanraeh.app.food

import android.content.Context
import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.EventDao
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.LogEvent
import com.kanraeh.app.core.LogEvent as Ev
import org.json.JSONArray
import org.json.JSONObject

/** MODULE: food - offline food list (assets/foods.json) with property tags, user's own foods, favourites, meal and water logging. */
data class Food(val id: String, val name: String, val tags: List<String>)

class FoodDatabase(private val ctx: Context) {
    private val base: List<Food> = try {
        val arr = JSONObject(ctx.assets.open("foods.json").bufferedReader().use { it.readText() }).getJSONArray("foods")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); val t = o.getJSONArray("tags")
            Food(o.getString("id"), o.getString("name"), (0 until t.length()).map { t.getString(it) })
        }
    } catch (e: Exception) { emptyList() }

    private val prefs = ctx.getSharedPreferences("kanraeh_foods", Context.MODE_PRIVATE)

    private fun custom(): List<Food> = try {
        val arr = JSONArray(prefs.getString("custom", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); val t = o.getJSONArray("tags")
            Food(o.getString("id"), o.getString("name"), (0 until t.length()).map { t.getString(it) })
        }
    } catch (e: Exception) { emptyList() }

    fun all(): List<Food> = base + custom()
    fun find(id: String): Food? = all().firstOrNull { it.id == id }
    fun nameOf(id: String): String = find(id)?.name ?: id.removePrefix("custom:")
    fun tagsOf(id: String): List<String> = find(id)?.tags ?: emptyList()

    fun search(q: String, limit: Int = 12): List<Food> {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return emptyList()
        return all().filter { it.name.lowercase().contains(s) }.take(limit)
    }

    /** A dish that isn't in the list: she names it once and picks what it is like (tags); it's remembered. */
    fun addCustom(name: String, tags: List<String>): Food {
        val f = Food("custom:" + name.trim().lowercase(), name.trim(), tags)
        val arr = JSONArray()
        (custom().filter { it.id != f.id } + f).forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("tags", JSONArray(it.tags)))
        }
        prefs.edit().putString("custom", arr.toString()).apply()
        return f
    }

    /** Most-eaten foods first, so her own foods become one-tap favourites. */
    fun favorites(events: List<Ev>, limit: Int = 8): List<Food> =
        events.filter { it.type == EventTypes.MEAL && find(it.key) != null }
            .groupingBy { it.key }.eachCount().entries.sortedByDescending { it.value }
            .take(limit).mapNotNull { find(it.key) }

    companion object {
        val TAG_LABELS = linkedMapOf(
            "sweet" to "Sugary", "refined_carb" to "Refined carbs", "whole_grain" to "Whole grain", "protein" to "Protein",
            "fried" to "Fried / oily", "salty" to "Salty", "processed" to "Processed", "caffeine" to "Caffeine",
            "tyramine" to "Aged / fermented", "chocolate" to "Chocolate", "alcohol" to "Alcohol", "produce" to "Fruit / veg", "dairy" to "Dairy"
        )
    }
}

class MealLogger(private val dao: EventDao) {
    suspend fun food(foodId: String) { dao.insert(LogEvent(type = EventTypes.MEAL, key = foodId, ts = Clock.now())) }
    suspend fun skipped() { dao.insert(LogEvent(type = EventTypes.MEAL, key = "skipped", ts = Clock.now())) }
    suspend fun water(glasses: Double = 1.0) { dao.insert(LogEvent(type = EventTypes.WATER, value = glasses, ts = Clock.now())) }
}

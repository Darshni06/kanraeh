package com.kanraeh.app.knowledge

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * MODULE: knowledge - the verified science, kept in ONE replaceable file (see docs/KNOWLEDGE_FORMAT.md).
 * Load order: filesDir/knowledge.json (imported) -> assets/knowledge.json (bundled placeholder).
 * A broken file is ignored; the app never crashes because of it.
 */
data class KbEntry(
    val id: String, val factor: String, val title: String,
    val whyPlain: String, val whyScience: String, val evidence: String,
    val sources: List<String>, val tryThis: List<String>, val safetyNote: String, val placeholder: Boolean
)

class KnowledgeBase(private val ctx: Context) {
    var version: String = "none"; private set
    var entries: List<KbEntry> = emptyList(); private set
    var usingImported: Boolean = false; private set

    init { reload() }

    fun reload() {
        val f = File(ctx.filesDir, "knowledge.json")
        val imported = if (f.exists()) parse(f.readText()) else null
        val parsed = imported ?: try { parse(ctx.assets.open("knowledge.json").bufferedReader().use { it.readText() }) } catch (e: Exception) { null }
        usingImported = imported != null
        version = parsed?.first ?: "none"
        entries = parsed?.second ?: emptyList()
    }

    /** Settings > Knowledge > Import. Returns false (and changes nothing) if the file is invalid. */
    fun importFrom(uri: Uri): Boolean = try {
        val txt = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        if (parse(txt) == null) false else { File(ctx.filesDir, "knowledge.json").writeText(txt); reload(); true }
    } catch (e: Exception) { false }

    fun forFactor(factor: String): List<KbEntry> = entries.filter { it.factor == factor }

    private fun parse(text: String): Pair<String, List<KbEntry>>? = try {
        val o = JSONObject(text)
        val arr = o.getJSONArray("entries")
        val list = (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            fun strs(k: String) = e.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            KbEntry(
                e.getString("id"), e.getString("factor"), e.getString("title"),
                e.optString("whyPlain"), e.optString("whyScience"), e.optString("evidence"),
                strs("sources"), strs("tryThis"), e.optString("safetyNote"), e.optBoolean("placeholder", false)
            )
        }
        o.optString("version", "unknown") to list
    } catch (e: Exception) { null }
}

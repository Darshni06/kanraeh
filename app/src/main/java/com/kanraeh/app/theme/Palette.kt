package com.kanraeh.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kanraeh.app.companion.CompanionSettings
import org.json.JSONObject

/** MODULE: theme - six calm built-in palettes + one custom palette the user edits. Attack mode always dims whichever she chose. */
data class Palette(val id: String, val name: String, val bg: Color, val card: Color, val accent: Color, val text: Color, val dark: Boolean)

object Palettes {
    val all = listOf(
        Palette("dusk_lavender", "Dusk Lavender", Color(0xFF2E2B3A), Color(0xFF3C3850), Color(0xFFB7A6D9), Color(0xFFE8E4F0), true),
        Palette("sage_calm", "Sage Calm", Color(0xFFE6EBE3), Color(0xFFD2DCCD), Color(0xFF6F8F77), Color(0xFF2B3A2F), false),
        Palette("warm_sand", "Warm Sand", Color(0xFFF1E8DC), Color(0xFFE4D6C3), Color(0xFFB8896B), Color(0xFF3D2F26), false),
        Palette("ocean_mist", "Ocean Mist", Color(0xFFDDE9EA), Color(0xFFC6DADC), Color(0xFF4E8A93), Color(0xFF1F3438), false),
        Palette("rose_quartz", "Rose Quartz", Color(0xFFF2E3E4), Color(0xFFE6CFD1), Color(0xFFB77C86), Color(0xFF43262B), false),
        Palette("midnight_teal", "Midnight Teal", Color(0xFF1D2A2E), Color(0xFF2A3B40), Color(0xFF7FB3B5), Color(0xFFDCE8E8), true)
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

/** Attack mode: always dark and low-contrast-glare, tinted by her accent colour. */
fun Palette.dimmed(): Palette {
    val base = Color(0xFF14181A)
    return copy(bg = base, card = lerp(base, accent, 0.12f), accent = lerp(accent, base, 0.45f), text = Color(0xFFB4BEC0), dark = true)
}

class PaletteStore(private val s: CompanionSettings) {
    fun current(): Palette {
        if (s.paletteId == "custom") parseCustom()?.let { return it }
        return Palettes.byId(s.paletteId)
    }
    fun select(id: String) { s.paletteId = id }

    /** Colours are hex strings like "#2E2B3A". Invalid input is ignored. */
    fun saveCustom(bg: String, card: String, accent: String, text: String, dark: Boolean): Boolean = try {
        parse(bg); parse(card); parse(accent); parse(text)
        s.customPalette = JSONObject().put("bg", bg).put("card", card).put("accent", accent).put("text", text).put("dark", dark).toString()
        s.paletteId = "custom"; true
    } catch (e: Exception) { false }

    fun customOrDefault(): Palette = parseCustom() ?: Palettes.all.first().copy(id = "custom", name = "Custom")

    private fun parseCustom(): Palette? = try {
        val o = JSONObject(s.customPalette)
        Palette("custom", "Custom", parse(o.getString("bg")), parse(o.getString("card")), parse(o.getString("accent")), parse(o.getString("text")), o.getBoolean("dark"))
    } catch (e: Exception) { null }

    private fun parse(hex: String): Color = Color(android.graphics.Color.parseColor(hex.trim()))
}

val LocalPalette = staticCompositionLocalOf { Palettes.all.first() }

@Composable
fun KanraehTheme(p: Palette, content: @Composable () -> Unit) {
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = p.bg, background = p.bg, onBackground = p.text,
        surface = p.card, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.text,
        secondaryContainer = p.card, onSecondaryContainer = p.text
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.bg, background = p.bg, onBackground = p.text,
        surface = p.card, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.text,
        secondaryContainer = p.card, onSecondaryContainer = p.text
    )
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}

package com.kanraeh.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.kanraeh.app.theme.Palette
import com.kanraeh.app.ui.components.ButtonGrid
import com.kanraeh.app.ui.components.SectionTitle
import com.kanraeh.app.ui.components.SoftButton
import com.kanraeh.app.ui.components.SoftCard

/** MODULE: ui/screens - "Me": colours, avatar, companion settings, permissions, logs, privacy, knowledge, report. Pure UI. */
data class PermState(val label: String, val granted: Boolean, val why: String, val onOpen: () -> Unit)

data class MeState(
    val palettes: List<Palette>, val selectedPaletteId: String, val custom: Palette,
    val companionEnabled: Boolean, val stillMode: Boolean, val dailyCap: Int,
    val lockEnabled: Boolean, val secureScreen: Boolean, val takesEstrogen: Boolean,
    val futureNote: String, val onlineAvatarAllowed: Boolean, val avatarPreview: Bitmap?,
    val permissions: List<PermState>, val kbVersion: String, val kbImported: Boolean, val kbPlaceholder: Boolean,
    val overuseNotes: List<String>, val status: String?
)

data class MeActions(
    val onPalette: (String) -> Unit, val onCustomPalette: (bg: String, card: String, accent: String, text: String, dark: Boolean) -> Unit,
    val onCompanion: (Boolean) -> Unit, val onStill: (Boolean) -> Unit, val onCap: (Int) -> Unit,
    val onLock: (Boolean) -> Unit, val onSecure: (Boolean) -> Unit, val onEstrogen: (Boolean) -> Unit,
    val onFutureNote: (String) -> Unit, val onOnlineAvatar: (Boolean) -> Unit,
    val onPickPhoto: (hairStyle: Int) -> Unit, val onResetAvatar: () -> Unit,
    val onPeriod: () -> Unit, val onAura: () -> Unit, val onCraving: (String) -> Unit,
    val onMeds: () -> Unit, val onLab: () -> Unit, val onHair: () -> Unit, val onAcne: (Int) -> Unit,
    val onBackup: () -> Unit, val onRestore: () -> Unit, val onImportKnowledge: () -> Unit, val onReport: () -> Unit,
    val onVivoHelp: () -> Unit
)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}

@Composable
fun MeScreen(s: MeState, a: MeActions) {
    var hairStyle by remember { mutableStateOf(1) }
    var bg by remember { mutableStateOf(hex(s.custom.bg)) }
    var card by remember { mutableStateOf(hex(s.custom.card)) }
    var accent by remember { mutableStateOf(hex(s.custom.accent)) }
    var text by remember { mutableStateOf(hex(s.custom.text)) }
    var customDark by remember { mutableStateOf(s.custom.dark) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        s.status?.let { SoftCard { Text(it) } }
        s.overuseNotes.forEach { SoftCard { Text(it) } }

        SectionTitle("Log something")
        ButtonGrid(
            listOf("Period started" to a.onPeriod, "Aura" to a.onAura, "Craving sweet" to { a.onCraving("sweet") }, "Craving salt" to { a.onCraving("salt") },
                "Medicine" to a.onMeds, "Lab result" to a.onLab, "Hair check" to a.onHair, "Acne today" to { a.onAcne(2) }),
            2, { it.first }) { it.second() }

        SectionTitle("Colours")
        ButtonGrid(s.palettes, 2, { (if (it.id == s.selectedPaletteId) "✓ " else "") + it.name }) { a.onPalette(it.id) }
        SoftCard {
            Text("Make your own (hex colours)")
            OutlinedTextField(bg, { bg = it }, label = { Text("Background") }, singleLine = true)
            OutlinedTextField(card, { card = it }, label = { Text("Cards") }, singleLine = true)
            OutlinedTextField(accent, { accent = it }, label = { Text("Buttons") }, singleLine = true)
            OutlinedTextField(text, { text = it }, label = { Text("Text") }, singleLine = true)
            SwitchRow("Dark style", customDark) { customDark = it }
            SoftButton("Use my colours", Modifier.fillMaxWidth()) { a.onCustomPalette(bg, card, accent, text, customDark) }
        }

        SectionTitle("My avatar")
        SoftCard {
            s.avatarPreview?.let { Image(it.asImageBitmap(), null, Modifier.height(160.dp)) }
            Text("Hair style")
            ButtonGrid(listOf("Short" to 0, "Long" to 1, "Bun" to 2), 3, { (if (it.second == hairStyle) "✓ " else "") + it.first }) { hairStyle = it.second }
            SwitchRow("Allow one-time online creation (your photo is sent once, then deleted)", s.onlineAvatarAllowed, a.onOnlineAvatar)
            SoftButton("Create from my photo", Modifier.fillMaxWidth()) { a.onPickPhoto(hairStyle) }
            SoftButton("Reset avatar", Modifier.fillMaxWidth(), a.onResetAvatar)
        }

        SectionTitle("Companion")
        SwitchRow("Avatar walks in to ask me things", s.companionEnabled, a.onCompanion)
        SwitchRow("Still mode (no walking or motion)", s.stillMode, a.onStill)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Asks at most ${s.dailyCap} times a day", Modifier.weight(1f))
            SoftButton("-") { a.onCap(s.dailyCap - 1) }; SoftButton("+") { a.onCap(s.dailyCap + 1) }
        }
        SectionTitle("Permissions it needs")
        s.permissions.forEach { p ->
            SoftCard {
                Text("${if (p.granted) "✓" else "○"}  ${p.label}", style = MaterialTheme.typography.titleSmall)
                Text(p.why, style = MaterialTheme.typography.bodySmall)
                if (!p.granted) SoftButton("Open settings", Modifier.fillMaxWidth(), p.onOpen)
            }
        }
        SoftButton("Vivo: keep Kanraeh running (autostart)", Modifier.fillMaxWidth(), a.onVivoHelp)

        SectionTitle("Private")
        SwitchRow("Lock with fingerprint or phone PIN", s.lockEnabled, a.onLock)
        SwitchRow("Hide from screenshots and recent apps", s.secureScreen, a.onSecure)
        SwitchRow("I take an estrogen-containing pill", s.takesEstrogen, a.onEstrogen)
        OutlinedTextField(s.futureNote, a.onFutureNote, label = { Text("A note to myself for hard attacks") }, modifier = Modifier.fillMaxWidth())
        SoftButton("Save encrypted backup file", Modifier.fillMaxWidth(), a.onBackup)
        SoftButton("Restore from backup file", Modifier.fillMaxWidth(), a.onRestore)

        SectionTitle("Knowledge")
        Text("Version: ${s.kbVersion}${if (s.kbImported) " (imported)" else " (built-in)"}${if (s.kbPlaceholder) "  - placeholder content" else ""}")
        SoftButton("Import knowledge file", Modifier.fillMaxWidth(), a.onImportKnowledge)

        SectionTitle("For my doctor")
        SoftButton("Create summary PDF", Modifier.fillMaxWidth(), a.onReport)
        Text("", Modifier.padding(24.dp))
    }
}

private fun hex(c: androidx.compose.ui.graphics.Color): String = "#%06X".format(0xFFFFFF and c.toArgb())

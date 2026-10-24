package com.kanraeh.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanraeh.app.core.Mood
import com.kanraeh.app.cycle.HairAreas
import com.kanraeh.app.food.Food
import com.kanraeh.app.food.FoodDatabase
import com.kanraeh.app.meds.MedClass
import com.kanraeh.app.safety.RedFlag

/** MODULE: ui/components - input dialogs. Each is pure UI: it reports a choice through a lambda. */

@Composable
private fun Pop(title: String, onDismiss: () -> Unit, confirmLabel: String? = null, onConfirm: () -> Unit = {}, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } },
        confirmButton = { if (confirmLabel != null) TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun FoodPickerDialog(
    foods: FoodDatabase, favorites: List<Food>,
    onPick: (String) -> Unit, onCustom: (String, List<String>) -> Unit, onDismiss: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(setOf<String>()) }
    val results = foods.search(q)
    Pop("What did you eat or drink?", onDismiss,
        confirmLabel = if (adding && q.isNotBlank()) "Save" else null, onConfirm = { onCustom(q, tags.toList()) }) {
        OutlinedTextField(q, { q = it; adding = false }, label = { Text("Type a few letters") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (q.isBlank() && favorites.isNotEmpty()) {
            Text("Your usual foods")
            ButtonGrid(favorites, 2, { it.name }) { onPick(it.id) }
        }
        if (q.isNotBlank()) {
            ButtonGrid(results, 2, { it.name }) { onPick(it.id) }
            if (!adding) SoftButton("Not here. Add \"$q\"", Modifier.fillMaxWidth()) { adding = true }
        }
        if (adding) {
            Text("What is it like? (pick any)")
            ButtonGrid(FoodDatabase.TAG_LABELS.entries.toList(), 2, { (if (it.key in tags) "✓ " else "") + it.value }) {
                tags = if (it.key in tags) tags - it.key else tags + it.key
            }
        }
    }
}

@Composable
fun MoodDialog(onPick: (Mood, String?) -> Unit, onDismiss: () -> Unit) {
    var song by remember { mutableStateOf("") }
    Pop("How are you feeling?", onDismiss) {
        ButtonGrid(Mood.values().toList(), 2, { "${it.emoji} ${it.label}" }) { onPick(it, song.ifBlank { null }) }
        OutlinedTextField(song, { song = it }, label = { Text("Song you're listening to (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SleepDialog(onPick: (Double) -> Unit, onDismiss: () -> Unit) {
    Pop("How long did you sleep?", onDismiss) {
        ButtonGrid(listOf("Under 5 h" to 4.0, "5 to 6 h" to 5.5, "7 to 8 h" to 7.5, "9 h or more" to 9.5), 2, { it.first }) { onPick(it.second) }
    }
}

@Composable
fun MedDialog(onSave: (String, MedClass, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    Pop("Medicine taken", onDismiss) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dose, { dose = it }, label = { Text("Dose (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Tap the kind to save")
        ButtonGrid(MedClass.values().toList(), 1, { it.label }) { if (name.isNotBlank()) onSave(name, it, dose) }
    }
}

@Composable
fun LabDialog(onSave: (String, Double, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    Pop("Lab result", onDismiss, confirmLabel = "Save",
        onConfirm = { value.toDoubleOrNull()?.let { if (name.isNotBlank()) onSave(name, it, unit) } }) {
        OutlinedTextField(name, { name = it }, label = { Text("Test (e.g. testosterone, HbA1c)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value, { value = it }, label = { Text("Value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun HairDialog(onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var area by remember { mutableStateOf<String?>(null) }
    Pop(if (area == null) "Which area?" else "How much hair: $area", onDismiss) {
        if (area == null) ButtonGrid(HairAreas.areas, 2, { it }) { area = it }
        else ButtonGrid(listOf("0 none" to 0, "1 light" to 1, "2 some" to 2, "3 clear" to 3, "4 heavy" to 4), 2, { it.first }) { onSave(area!!, it.second) }
    }
}

@Composable
fun RedFlagDialog(onReport: (Set<RedFlag>) -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableStateOf(setOf<RedFlag>()) }
    Pop("Is something different or worse?", onDismiss, confirmLabel = "Continue", onConfirm = { onReport(sel) }) {
        ButtonGrid(RedFlag.values().toList(), 1, { (if (it in sel) "✓ " else "") + it.label }) { sel = if (it in sel) sel - it else sel + it }
    }
}

@Composable
fun PassphraseDialog(title: String, onOk: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var p by remember { mutableStateOf("") }
    Pop(title, onDismiss, confirmLabel = "OK", onConfirm = { if (p.length >= 6) onOk(p.toCharArray()) }) {
        Text("At least 6 characters. If you forget it, the backup cannot be opened.")
        OutlinedTextField(p, { p = it }, label = { Text("Passphrase") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

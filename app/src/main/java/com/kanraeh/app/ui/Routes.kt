package com.kanraeh.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kanraeh.app.avatar.AvatarCreator
import com.kanraeh.app.avatar.AvatarStore
import com.kanraeh.app.companion.Permissions
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.Graph
import com.kanraeh.app.core.Mood
import com.kanraeh.app.core.Time
import com.kanraeh.app.privacy.EncryptedBackup
import com.kanraeh.app.report.DoctorReport
import com.kanraeh.app.theme.Palettes
import com.kanraeh.app.ui.components.FoodPickerDialog
import com.kanraeh.app.ui.components.HairDialog
import com.kanraeh.app.ui.components.LabDialog
import com.kanraeh.app.ui.components.MedDialog
import com.kanraeh.app.ui.components.MoodDialog
import com.kanraeh.app.ui.components.PassphraseDialog
import com.kanraeh.app.ui.components.RedFlagDialog
import com.kanraeh.app.ui.components.SleepDialog
import com.kanraeh.app.ui.screens.AttackActions
import com.kanraeh.app.ui.screens.AttackScreen
import com.kanraeh.app.ui.screens.AttackState
import com.kanraeh.app.ui.screens.BodyScreen
import com.kanraeh.app.ui.screens.MeActions
import com.kanraeh.app.ui.screens.MeScreen
import com.kanraeh.app.ui.screens.MeState
import com.kanraeh.app.ui.screens.PatternsScreen
import com.kanraeh.app.ui.screens.PermState
import com.kanraeh.app.ui.screens.TodayActions
import com.kanraeh.app.ui.screens.TodayScreen
import com.kanraeh.app.ui.screens.TodayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MODULE: ui - "Routes" wire pure screens to the ViewModel and the Android pickers. Antigravity: restyle screens/, leave this file.
 */

@Composable
fun TodayRoute(vm: MainViewModel) {
    val recent by vm.recent.collectAsState()
    val message by vm.message.collectAsState()
    val prompt by vm.prompt.collectAsState()
    val song by vm.songSuggestion.collectAsState()
    val notes by vm.safetyNotes.collectAsState()
    val pending by vm.pendingOpen.collectAsState()
    var showFood by remember { mutableStateOf(false) }
    var showMood by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showMed by remember { mutableStateOf(false) }

    LaunchedEffect(pending) {
        when (pending) { "food" -> showFood = true; "meds" -> showMed = true }
        if (pending != null) vm.pendingOpen.value = null
    }
    val start = Time.startOfDay(System.currentTimeMillis())
    val today = recent.filter { it.ts >= start }
    val state = TodayState(
        message = message, prompt = prompt,
        glassesToday = today.filter { it.type == EventTypes.WATER }.sumOf { it.value ?: 1.0 }.toInt(),
        mealsToday = today.count { it.type == EventTypes.MEAL && it.key != "skipped" },
        lastMood = today.firstOrNull { it.type == EventTypes.MOOD }?.let { Mood.from(it.key)?.label },
        songSuggestion = song, safetyNotes = notes
    )
    TodayScreen(state, TodayActions(
        onMigraine = { vm.startAttack() }, onWater = { vm.logWater() }, onFood = { showFood = true },
        onMood = { showMood = true }, onSleep = { showSleep = true },
        onAnswer = { a -> prompt?.let { vm.answer(it, a) } }
    ))
    if (showFood) FoodPickerDialog(Graph.foods, Graph.foods.favorites(recent),
        onPick = { vm.logFood(it); showFood = false }, onCustom = { n, t -> vm.addCustomFood(n, t); showFood = false }, onDismiss = { showFood = false })
    if (showMood) MoodDialog({ m, s -> vm.logMood(m, s); showMood = false }, { showMood = false })
    if (showSleep) SleepDialog({ vm.logSleep(it); showSleep = false }, { showSleep = false })
    if (showMed) MedDialog({ n, c, d -> vm.logMed(n, c, d); showMed = false }, { showMed = false })
}

@Composable
fun AttackRoute(vm: MainViewModel) {
    val message by vm.message.collectAsState()
    val timeline by vm.attackTimeline.collectAsState()
    val emergency by vm.emergency.collectAsState()
    var showMed by remember { mutableStateOf(false) }
    var showFlags by remember { mutableStateOf(false) }
    val pending by vm.pendingOpen.collectAsState()
    LaunchedEffect(pending) { if (pending == "meds") showMed = true; if (pending != null) vm.pendingOpen.value = null }
    AttackScreen(
        AttackState(message, timeline, emergency),
        AttackActions(
            onLevel = { vm.tapLevel(it) }, onRegion = { vm.tapRegion(it) }, onUndo = { vm.undo() },
            onMedicine = { showMed = true }, onSomethingWrong = { showFlags = true }, onDismissEmergency = { vm.dismissEmergency() }
        )
    )
    if (showMed) MedDialog({ n, c, d -> vm.logMed(n, c, d); showMed = false }, { showMed = false })
    if (showFlags) RedFlagDialog({ vm.reportRedFlags(it); showFlags = false }, { showFlags = false })
}

@Composable
fun BodyRoute(vm: MainViewModel) { BodyScreen(vm.timeline.collectAsState().value) }

@Composable
fun PatternsRoute(vm: MainViewModel) {
    PatternsScreen(vm.patterns.collectAsState().value, vm.stories.collectAsState().value, vm.issueStories.collectAsState().value)
}

@Composable
fun MeRoute(vm: MainViewModel, activity: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Graph.settings
    var v by remember { mutableStateOf(0) }       // bump to re-read settings
    var resume by remember { mutableStateOf(0) }  // bump when returning from system settings
    val status by vm.status.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resume++ }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }

    var showMed by remember { mutableStateOf(false) }
    var showLab by remember { mutableStateOf(false) }
    var showHair by remember { mutableStateOf(false) }
    var passMode by remember { mutableStateOf<String?>(null) }
    var pendingPass by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var hairForPhoto by remember { mutableStateOf(1) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            vm.status.value = "Making your avatar..."
            val msg = withContext(Dispatchers.IO) {
                val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bmp == null) "I couldn't read that picture."
                else AvatarCreator.create(ctx, bmp, s.onlineAvatarAllowed, hairForPhoto).message
            }
            vm.status.value = msg; v++
        }
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = pendingPass
        if (uri != null && pass != null) scope.launch {
            val bytes = EncryptedBackup(Graph.db).export(pass)
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            vm.status.value = "Backup saved. Keep the passphrase safe."
        }
    }
    val restoreOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; passMode = "restore" }
    }
    val kbOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Graph.kb.importFrom(uri) }
            vm.status.value = if (ok) "Knowledge file imported." else "That file isn't a valid knowledge file, so nothing changed."
            v++; vm.refresh()
        }
    }
    val reportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val bytes = DoctorReport.build()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            vm.status.value = "Summary saved as a PDF."
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resume++ }

    val perms = remember(v, resume) {
        listOf(
            PermState("Display over other apps", Permissions.canOverlay(ctx), "Lets your avatar walk in over any app.") { ctx.startActivity(Permissions.overlayIntent(ctx)) },
            PermState("Usage access", Graph.screen.hasAccess(), "Lets Kanraeh notice long scrolling or late-night phone use. It never sees what you read.") { ctx.startActivity(Permissions.usageIntent()) },
            PermState("Notification access", Permissions.listenerGranted(ctx), "Lets Kanraeh see the song title that is playing, so it can remember songs that helped.") { ctx.startActivity(Permissions.listenerIntent()) },
            PermState("Allowed to run in background", Permissions.batteryOk(ctx), "Keeps your avatar able to check in on time.") { ctx.startActivity(Permissions.batteryIntent(ctx)) },
            PermState("Notifications", Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                "Needed for the quiet 'Checking in' notice Android requires while your avatar is on screen.") { if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        )
    }

    val state = remember(v, resume, status) {
        MeState(
            palettes = Palettes.all, selectedPaletteId = s.paletteId, custom = Graph.paletteStore.customOrDefault(),
            companionEnabled = s.enabled, stillMode = s.stillMode, dailyCap = s.dailyCap,
            lockEnabled = s.lockEnabled, secureScreen = s.secureScreen, takesEstrogen = s.takesEstrogen,
            futureNote = s.futureNote, onlineAvatarAllowed = s.onlineAvatarAllowed,
            avatarPreview = AvatarStore.frames(ctx, 160, 240).first(), permissions = perms,
            kbVersion = Graph.kb.version, kbImported = Graph.kb.usingImported,
            kbPlaceholder = Graph.kb.entries.any { it.placeholder }, overuseNotes = vm.safetyNotes.value, status = status
        )
    }
    var note by remember { mutableStateOf(s.futureNote) }

    MeScreen(state.copy(futureNote = note), MeActions(
        onPalette = { Graph.paletteStore.select(it); vm.setPalette(Graph.paletteStore.current()); v++ },
        onCustomPalette = { bg, card, accent, text, dark ->
            if (Graph.paletteStore.saveCustom(bg, card, accent, text, dark)) vm.setPalette(Graph.paletteStore.current()) else vm.status.value = "One of those colours isn't valid. Use a format like #2E2B3A."
            v++
        },
        onCompanion = { s.enabled = it; v++ }, onStill = { s.stillMode = it; v++ }, onCap = { s.dailyCap = it; v++ },
        onLock = { s.lockEnabled = it; v++ },
        onSecure = { s.secureScreen = it; activity.applySecure(); v++ },
        onEstrogen = { s.takesEstrogen = it; v++; vm.refresh() },
        onFutureNote = { note = it; s.futureNote = it },
        onOnlineAvatar = { s.onlineAvatarAllowed = it; v++ },
        onPickPhoto = { hair -> hairForPhoto = hair; photoPicker.launch("image/*") },
        onResetAvatar = { AvatarStore.reset(ctx); v++ },
        onPeriod = { vm.logPeriod(); vm.status.value = "Period start logged." }, onAura = { vm.logAura(); vm.status.value = "Aura logged." },
        onCraving = { vm.logCraving(it); vm.status.value = "Craving logged." },
        onMeds = { showMed = true }, onLab = { showLab = true }, onHair = { showHair = true },
        onAcne = { vm.logAcne(it); vm.status.value = "Acne logged." },
        onBackup = { passMode = "backup" }, onRestore = { restoreOpener.launch(arrayOf("*/*")) },
        onImportKnowledge = { kbOpener.launch(arrayOf("*/*")) },
        onReport = { reportSaver.launch("kanraeh-summary.pdf") },
        onVivoHelp = {
            var opened = false
            for (i in Permissions.vivoAutostartIntents()) {
                try { ctx.startActivity(i); opened = true; break } catch (e: Exception) { }
            }
            if (!opened) ctx.startActivity(Permissions.appDetailsIntent(ctx))
        }
    ))

    if (showMed) MedDialog({ n, c, d -> vm.logMed(n, c, d); showMed = false; vm.status.value = "Medicine logged." }, { showMed = false })
    if (showLab) LabDialog({ n, x, u -> vm.logLab(n, x, u); showLab = false; vm.status.value = "Lab result saved." }, { showLab = false })
    if (showHair) HairDialog({ a, sc -> vm.logHair(a, sc); showHair = false; vm.status.value = "Hair check saved." }, { showHair = false })
    if (passMode != null) PassphraseDialog(
        title = if (passMode == "backup") "Choose a passphrase for the backup" else "Enter the backup passphrase",
        onOk = { p ->
            val mode = passMode; passMode = null
            if (mode == "backup") { pendingPass = p; backupSaver.launch("kanraeh-backup.knrb") }
            else restoreUri?.let { u -> scope.launch {
                val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }
                val ok = bytes != null && EncryptedBackup(Graph.db).import(bytes, p)
                vm.status.value = if (ok) "Backup restored." else "Wrong passphrase or not a Kanraeh backup. Nothing changed."
                vm.refresh()
            } }
        },
        onDismiss = { passMode = null }
    )
}

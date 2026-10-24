package com.kanraeh.app.core

import android.app.Application
import com.kanraeh.app.companion.CompanionBrain
import com.kanraeh.app.companion.CompanionSettings
import com.kanraeh.app.companion.MessageWriter
import com.kanraeh.app.companion.PromptHandler
import com.kanraeh.app.cycle.CycleEngine
import com.kanraeh.app.food.FoodDatabase
import com.kanraeh.app.knowledge.KnowledgeBase
import com.kanraeh.app.meds.MedicineLogger
import com.kanraeh.app.pain.AttackManager
import com.kanraeh.app.patterns.PatternEngine
import com.kanraeh.app.screentime.ScreenTimeReader
import com.kanraeh.app.songs.SongManager
import com.kanraeh.app.stories.StoryBuilder
import com.kanraeh.app.theme.PaletteStore

/** MODULE: core - tiny service locator wiring all modules together. UI talks to modules only through this. */
object Graph {
    const val CHANNEL = "kanraeh_quiet"
    lateinit var app: Application
    val db by lazy { KanraehDatabase.build(app) }
    val settings by lazy { CompanionSettings(app) }
    val paletteStore by lazy { PaletteStore(settings) }
    val foods by lazy { FoodDatabase(app) }
    val kb by lazy { KnowledgeBase(app) }
    val cycle by lazy { CycleEngine(db.events()) }
    val attacks by lazy { AttackManager(db.attacks()) }
    val meds by lazy { MedicineLogger(db.events()) }
    val songs by lazy { SongManager(db.songs()) }
    val screen by lazy { ScreenTimeReader(app) }
    val patterns by lazy { PatternEngine(db, foods) }
    val stories by lazy { StoryBuilder(db, foods, cycle, kb) }
    val messages by lazy { MessageWriter(db, settings) }
    val brain by lazy { CompanionBrain(db, settings, cycle, screen, songs) }
    val handler by lazy { PromptHandler(db, songs) }
}

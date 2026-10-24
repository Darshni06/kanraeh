package com.kanraeh.app.companion

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.Graph
import com.kanraeh.app.screentime.ScreenTimeLogger

/** MODULE: companion - runs about every 15 minutes in the background: tidy attacks, log screen time, maybe send the avatar. */
class CompanionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            Graph.attacks.closeStale()
            ScreenTimeLogger.logToday(Graph.screen, Graph.db.events())
            val now = Clock.now()
            val prompt = Graph.brain.next(now) ?: return Result.success()
            if (!Permissions.canOverlay(applicationContext)) return Result.success()
            Graph.settings.registerAsked(prompt.kind, now)
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, OverlayService::class.java).putExtra("prompt", PromptJson.to(prompt))
            )
        } catch (e: Exception) { /* never crash the background job */ }
        return Result.success()
    }
}

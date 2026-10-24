package com.kanraeh.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kanraeh.app.companion.CompanionWorker
import com.kanraeh.app.core.Graph
import java.util.concurrent.TimeUnit

class KanraehApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.app = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Graph.CHANNEL, "Kanraeh", NotificationManager.IMPORTANCE_MIN)
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "companion", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CompanionWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}

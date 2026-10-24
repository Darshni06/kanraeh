package com.kanraeh.app.songs

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.Mood
import com.kanraeh.app.core.SongDao
import com.kanraeh.app.core.SongMemory

/** MODULE: songs - remembers which song went with which mood change. Never plays music; only suggests. */
object NowPlaying {
    @Volatile var title: String? = null
    @Volatile var artist: String? = null
    @Volatile var ts: Long = 0
    fun recent(maxAgeMs: Long = 15 * 60_000L): String? =
        if (title != null && Clock.now() - ts <= maxAgeMs) listOfNotNull(title, artist).joinToString(" - ") else null
}

/** Needs the user to grant "Notification access". Reads only the media notification's title/artist. */
class NowPlayingListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.category != Notification.CATEGORY_TRANSPORT) return
        val t = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        NowPlaying.title = t
        NowPlaying.artist = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        NowPlaying.ts = Clock.now()
    }
}

data class SongSuggestion(val title: String, val moodBefore: Mood, val moodAfter: Mood)

class SongManager(private val dao: SongDao) {
    suspend fun start(mood: Mood, title: String): Long =
        dao.insert(SongMemory(title = title, moodBefore = mood.code, tsBefore = Clock.now()))

    suspend fun finish(id: Long, moodAfter: String) {
        val s = dao.byId(id) ?: return
        dao.update(s.copy(moodAfter = moodAfter, tsAfter = Clock.now()))
    }

    /** Best song from her own history for the current mood: biggest improvement, then most repeated. */
    suspend fun recommend(current: Mood): SongSuggestion? {
        val good = dao.all().mapNotNull { s ->
            val before = Mood.from(s.moodBefore) ?: return@mapNotNull null
            val after = Mood.from(s.moodAfter) ?: return@mapNotNull null
            if (before == current && after.valence - before.valence >= 1) s.title to after else null
        }
        if (good.isEmpty()) return null
        val best = good.groupBy { it.first }.maxByOrNull { it.value.size }!!
        return SongSuggestion(best.key, current, best.value.last().second)
    }
}

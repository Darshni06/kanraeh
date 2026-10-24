package com.kanraeh.app.core

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** MODULE: core - database tables. One generic event log keeps every tracker analysable the same way. */
@Entity(tableName = "events", indices = [Index("type"), Index("ts")])
data class LogEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val key: String = "",
    val value: Double? = null,
    val text: String = "",
    val ts: Long
)

@Entity(tableName = "attacks")
data class Attack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long,
    val endTs: Long? = null,
    val estimatedEnd: Boolean = false
)

@Entity(tableName = "pain_taps", indices = [Index("attackId")])
data class PainTap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attackId: Long,
    val level: String,
    val region: String? = null,
    val ts: Long
)

@Entity(tableName = "song_memories")
data class SongMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val moodBefore: String,
    val moodAfter: String? = null,
    val tsBefore: Long,
    val tsAfter: Long? = null
)

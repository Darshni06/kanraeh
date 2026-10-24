package com.kanraeh.app.core

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** MODULE: core - data access. */
@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(e: LogEvent): Long
    @Query("DELETE FROM events WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM events WHERE type=:type AND ts BETWEEN :from AND :to") suspend fun deleteTypeBetween(type: String, from: Long, to: Long)
    @Query("SELECT * FROM events WHERE ts BETWEEN :from AND :to ORDER BY ts") suspend fun between(from: Long, to: Long): List<LogEvent>
    @Query("SELECT * FROM events WHERE type=:type ORDER BY ts DESC LIMIT :limit") suspend fun latest(type: String, limit: Int): List<LogEvent>
    @Query("SELECT * FROM events WHERE type=:type AND ts BETWEEN :from AND :to ORDER BY ts") suspend fun typeBetween(type: String, from: Long, to: Long): List<LogEvent>
    @Query("SELECT * FROM events ORDER BY ts DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<LogEvent>>
    @Query("SELECT * FROM events ORDER BY ts") suspend fun all(): List<LogEvent>
}

@Dao
interface AttackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(a: Attack): Long
    @Update suspend fun update(a: Attack)
    @Query("DELETE FROM attacks WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT * FROM attacks WHERE endTs IS NULL ORDER BY startTs DESC LIMIT 1") suspend fun open(): Attack?
    @Query("SELECT * FROM attacks WHERE endTs IS NULL ORDER BY startTs DESC LIMIT 1") fun observeOpen(): Flow<Attack?>
    @Query("SELECT * FROM attacks ORDER BY startTs DESC") suspend fun all(): List<Attack>
    @Query("SELECT * FROM attacks ORDER BY startTs DESC") fun observeAll(): Flow<List<Attack>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTap(t: PainTap): Long
    @Query("DELETE FROM pain_taps WHERE id=:id") suspend fun deleteTap(id: Long)
    @Query("DELETE FROM pain_taps WHERE attackId=:id") suspend fun deleteTapsOf(id: Long)
    @Query("SELECT * FROM pain_taps WHERE attackId=:id ORDER BY ts") suspend fun taps(id: Long): List<PainTap>
    @Query("SELECT * FROM pain_taps WHERE attackId=:id ORDER BY ts") fun observeTaps(id: Long): Flow<List<PainTap>>
    @Query("SELECT * FROM pain_taps ORDER BY ts") suspend fun allTaps(): List<PainTap>
}

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(s: SongMemory): Long
    @Update suspend fun update(s: SongMemory)
    @Query("SELECT * FROM song_memories ORDER BY tsBefore DESC") suspend fun all(): List<SongMemory>
    @Query("SELECT * FROM song_memories WHERE id=:id") suspend fun byId(id: Long): SongMemory?
    @Query("SELECT * FROM song_memories WHERE moodAfter IS NULL AND tsBefore BETWEEN :from AND :to") suspend fun pending(from: Long, to: Long): List<SongMemory>
}

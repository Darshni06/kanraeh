package com.kanraeh.app.core

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

/** MODULE: core - encrypted on-device database (SQLCipher). Key lives in the Android Keystore-backed prefs. No cloud. */
@Database(entities = [LogEvent::class, Attack::class, PainTap::class, SongMemory::class], version = 1, exportSchema = false)
abstract class KanraehDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun attacks(): AttackDao
    abstract fun songs(): SongDao

    companion object {
        fun build(ctx: Context): KanraehDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(ctx, KanraehDatabase::class.java, "kanraeh.db")
                .openHelperFactory(SupportOpenHelperFactory(DbKey.get(ctx)))
                .build()
        }
    }
}

object DbKey {
    fun get(ctx: Context): ByteArray {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val sp = EncryptedSharedPreferences.create(
            ctx, "kanraeh_keys", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        var k = sp.getString("db", null)
        if (k == null) {
            val b = ByteArray(32); SecureRandom().nextBytes(b)
            k = Base64.encodeToString(b, Base64.NO_WRAP)
            sp.edit().putString("db", k).apply()
        }
        return Base64.decode(k, Base64.NO_WRAP)
    }
}

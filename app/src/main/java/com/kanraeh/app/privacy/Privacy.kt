package com.kanraeh.app.privacy

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kanraeh.app.core.Attack
import com.kanraeh.app.core.KanraehDatabase
import com.kanraeh.app.core.LogEvent
import com.kanraeh.app.core.PainTap
import com.kanraeh.app.core.SongMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** MODULE: privacy - app lock (fingerprint or phone PIN) and an encrypted backup file she saves herself. No cloud. */
object AppLock {
    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onFail: () -> Unit = {}) {
        val bp = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
        })
        bp.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Kanraeh").setSubtitle("Unlock")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL).build()
        )
    }
}

class EncryptedBackup(private val db: KanraehDatabase) {
    private val magic = "KNRB1".toByteArray()

    suspend fun export(pass: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("events", JSONArray().also { a -> db.events().all().forEach { a.put(JSONObject().put("id", it.id).put("type", it.type).put("key", it.key).put("value", it.value ?: JSONObject.NULL).put("text", it.text).put("ts", it.ts)) } })
        root.put("attacks", JSONArray().also { a -> db.attacks().all().forEach { a.put(JSONObject().put("id", it.id).put("startTs", it.startTs).put("endTs", it.endTs ?: JSONObject.NULL).put("est", it.estimatedEnd)) } })
        root.put("taps", JSONArray().also { a -> db.attacks().allTaps().forEach { a.put(JSONObject().put("id", it.id).put("attackId", it.attackId).put("level", it.level).put("region", it.region ?: JSONObject.NULL).put("ts", it.ts)) } })
        root.put("songs", JSONArray().also { a -> db.songs().all().forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("before", it.moodBefore).put("after", it.moodAfter ?: JSONObject.NULL).put("tsB", it.tsBefore).put("tsA", it.tsAfter ?: JSONObject.NULL)) } })
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        magic + salt + iv + c.doFinal(root.toString().toByteArray())
    }

    /** Replaces ALL current data with the backup. Returns false if the passphrase or file is wrong. */
    suspend fun import(bytes: ByteArray, pass: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (bytes.size < magic.size + 28 || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) return@withContext false
            val salt = bytes.copyOfRange(magic.size, magic.size + 16)
            val iv = bytes.copyOfRange(magic.size + 16, magic.size + 28)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
            val root = JSONObject(String(c.doFinal(bytes.copyOfRange(magic.size + 28, bytes.size))))
            db.clearAllTables()
            val ev = root.getJSONArray("events")
            for (i in 0 until ev.length()) ev.getJSONObject(i).let { db.events().insert(LogEvent(it.getLong("id"), it.getString("type"), it.getString("key"), if (it.isNull("value")) null else it.getDouble("value"), it.getString("text"), it.getLong("ts"))) }
            val at = root.getJSONArray("attacks")
            for (i in 0 until at.length()) at.getJSONObject(i).let { db.attacks().insert(Attack(it.getLong("id"), it.getLong("startTs"), if (it.isNull("endTs")) null else it.getLong("endTs"), it.getBoolean("est"))) }
            val tp = root.getJSONArray("taps")
            for (i in 0 until tp.length()) tp.getJSONObject(i).let { db.attacks().insertTap(PainTap(it.getLong("id"), it.getLong("attackId"), it.getString("level"), if (it.isNull("region")) null else it.getString("region"), it.getLong("ts"))) }
            val sg = root.getJSONArray("songs")
            for (i in 0 until sg.length()) sg.getJSONObject(i).let { db.songs().insert(SongMemory(it.getLong("id"), it.getString("title"), it.getString("before"), if (it.isNull("after")) null else it.getString("after"), it.getLong("tsB"), if (it.isNull("tsA")) null else it.getLong("tsA"))) }
            true
        } catch (e: Exception) { false }
    }

    private fun key(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(f.generateSecret(PBEKeySpec(pass, salt, 120_000, 256)).encoded, "AES")
    }
}

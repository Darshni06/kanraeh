package com.kanraeh.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.kanraeh.app.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * MODULE: avatar - creation. Two paths:
 *  1) Offline (always works): read skin/hair/clothes colours from her image, build the avatar from the built-in kit.
 *  2) Online, one time, optional: send the image to a service, get back a stylised face, save it. Nothing else ever goes online.
 * The online provider is NOT chosen yet. Configure AVATAR_API_URL / AVATAR_API_KEY in local.properties and adapt
 * OnlineAvatarClient.generate() to that provider's request format (marked below).
 */
object OfflineAvatarBuilder {
    fun fromImage(src: Bitmap, hairStyle: Int = 1): AvatarSpec {
        val b = Bitmap.createScaledBitmap(src, 64, 64, true)
        fun avg(x0: Int, x1: Int, y0: Int, y1: Int): Int {
            var r = 0L; var g = 0L; var bl = 0L; var n = 0
            for (x in x0 until x1) for (y in y0 until y1) { val c = b.getPixel(x, y); r += Color.red(c); g += Color.green(c); bl += Color.blue(c); n++ }
            return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
        }
        val skin = avg(26, 38, 28, 40)     // middle of the face
        val hair = avg(22, 42, 3, 9)       // top band
        val outfit = avg(22, 42, 54, 62)   // lower centre
        val pants = Color.rgb((Color.red(outfit) * 0.6).toInt(), (Color.green(outfit) * 0.6).toInt(), (Color.blue(outfit) * 0.6).toInt())
        return AvatarSpec(skin, hair, outfit, pants, hairStyle)
    }
}

object OnlineAvatarClient {
    fun configured() = BuildConfig.AVATAR_API_URL.isNotBlank()

    /** BLOCKING: call from a background thread. Returns the stylised image, or null on any failure. */
    fun generate(src: Bitmap): Bitmap? = try {
        val out = ByteArrayOutputStream(); src.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val con = URL(BuildConfig.AVATAR_API_URL).openConnection() as HttpURLConnection
        con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 60000
        // ADAPT HERE to the chosen provider: headers, multipart vs raw body, and how the image comes back.
        con.setRequestProperty("Content-Type", "image/jpeg")
        if (BuildConfig.AVATAR_API_KEY.isNotBlank()) con.setRequestProperty("Authorization", "Bearer ${BuildConfig.AVATAR_API_KEY}")
        con.outputStream.use { it.write(out.toByteArray()) }
        if (con.responseCode in 200..299) con.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } else null
    } catch (e: Exception) { null }
}

data class AvatarResult(val usedOnline: Boolean, val message: String)

object AvatarCreator {
    /** BLOCKING. The photo is never stored: only the saved spec and (optional) stylised head are kept. */
    fun create(ctx: Context, photo: Bitmap, allowOnline: Boolean, hairStyle: Int): AvatarResult {
        val spec = OfflineAvatarBuilder.fromImage(photo, hairStyle)
        if (allowOnline && OnlineAvatarClient.configured()) {
            val head = OnlineAvatarClient.generate(photo)
            if (head != null) { AvatarStore.save(ctx, spec, head); return AvatarResult(true, "Your avatar is ready. It now works fully offline.") }
            AvatarStore.save(ctx, spec, null)
            return AvatarResult(false, "The online service didn't respond, so I built your avatar on your phone instead.")
        }
        AvatarStore.save(ctx, spec, null)
        return AvatarResult(false, if (allowOnline) "No online service is set up yet, so I built your avatar on your phone." else "Your avatar is ready, built on your phone.")
    }
}

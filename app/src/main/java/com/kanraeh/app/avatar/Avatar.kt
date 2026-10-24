package com.kanraeh.app.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * MODULE: avatar - her companion. A saved AvatarSpec (colours + hair style) and an optional head image.
 * Walking frames are drawn procedurally, so everything works offline once the avatar exists.
 * Antigravity can restyle ProceduralAvatar.render() or replace it with art assets; keep the 4-frame walk contract.
 */
data class AvatarSpec(
    val skin: Int = Color.parseColor("#D9A77E"),
    val hair: Int = Color.parseColor("#3B2A22"),
    val outfit: Int = Color.parseColor("#7FB3B5"),
    val pants: Int = Color.parseColor("#4B5B66"),
    val hairStyle: Int = 1   // 0 short, 1 long, 2 bun
) {
    fun toJson(): String = JSONObject().put("skin", skin).put("hair", hair).put("outfit", outfit).put("pants", pants).put("style", hairStyle).toString()
    companion object {
        fun fromJson(s: String): AvatarSpec = try {
            val o = JSONObject(s); AvatarSpec(o.getInt("skin"), o.getInt("hair"), o.getInt("outfit"), o.getInt("pants"), o.getInt("style"))
        } catch (e: Exception) { AvatarSpec() }
    }
}

object ProceduralAvatar {
    const val FRAME_COUNT = 4

    /** frame 0..3 = walk cycle (0 is also the standing pose). head = optional image used for the face. */
    fun render(spec: AvatarSpec, frame: Int, head: Bitmap? = null, w: Int = 240, h: Int = 360): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val swing = sin(frame * PI / 2).toFloat() * w * 0.07f
        val cx = w / 2f

        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = w * 0.09f
        p.color = spec.pants                                   // legs
        c.drawLine(cx - w * 0.07f, h * 0.62f, cx - w * 0.07f + swing, h * 0.93f, p)
        c.drawLine(cx + w * 0.07f, h * 0.62f, cx + w * 0.07f - swing, h * 0.93f, p)
        p.color = spec.skin; p.strokeWidth = w * 0.06f         // arms
        c.drawLine(cx - w * 0.20f, h * 0.40f, cx - w * 0.20f - swing * 0.8f, h * 0.58f, p)
        c.drawLine(cx + w * 0.20f, h * 0.40f, cx + w * 0.20f + swing * 0.8f, h * 0.58f, p)

        p.style = Paint.Style.FILL; p.color = spec.outfit      // body
        c.drawRoundRect(RectF(cx - w * 0.19f, h * 0.36f, cx + w * 0.19f, h * 0.66f), w * 0.1f, w * 0.1f, p)

        val headR = w * 0.23f; val headY = h * 0.22f
        if (spec.hairStyle == 1) { p.color = spec.hair; c.drawRoundRect(RectF(cx - headR * 1.05f, headY - headR, cx + headR * 1.05f, headY + headR * 2.1f), headR, headR, p) }
        if (head != null) {
            val clip = Path().apply { addCircle(cx, headY, headR, Path.Direction.CW) }
            c.save(); c.clipPath(clip)
            c.drawBitmap(head, null, RectF(cx - headR, headY - headR, cx + headR, headY + headR), p); c.restore()
        } else {
            p.color = spec.skin; c.drawCircle(cx, headY, headR, p)
            p.color = Color.parseColor("#3A3A3A")
            c.drawCircle(cx - headR * 0.35f, headY - headR * 0.05f, headR * 0.07f, p)
            c.drawCircle(cx + headR * 0.35f, headY - headR * 0.05f, headR * 0.07f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = headR * 0.07f; p.strokeCap = Paint.Cap.ROUND
            c.drawArc(RectF(cx - headR * 0.3f, headY + headR * 0.05f, cx + headR * 0.3f, headY + headR * 0.5f), 20f, 140f, false, p)
            p.style = Paint.Style.FILL
        }
        p.color = spec.hair                                    // hair on top
        c.drawArc(RectF(cx - headR * 1.02f, headY - headR * 1.08f, cx + headR * 1.02f, headY + headR * 0.3f), 180f, 180f, true, p)
        if (spec.hairStyle == 2) c.drawCircle(cx, headY - headR * 1.15f, headR * 0.38f, p)
        return bmp
    }
}

object AvatarStore {
    private fun specFile(c: Context) = File(c.filesDir, "avatar.json")
    private fun headFile(c: Context) = File(c.filesDir, "avatar_head.png")

    fun exists(c: Context) = specFile(c).exists()
    fun spec(c: Context): AvatarSpec = if (specFile(c).exists()) AvatarSpec.fromJson(specFile(c).readText()) else AvatarSpec()
    fun save(c: Context, spec: AvatarSpec, head: Bitmap?) {
        specFile(c).writeText(spec.toJson())
        if (head != null) headFile(c).outputStream().use { head.compress(Bitmap.CompressFormat.PNG, 100, it) } else headFile(c).delete()
    }
    fun head(c: Context): Bitmap? = if (headFile(c).exists()) BitmapFactory.decodeFile(headFile(c).path) else null
    fun frames(c: Context, w: Int = 240, h: Int = 360): List<Bitmap> {
        val s = spec(c); val hd = head(c)
        return (0 until ProceduralAvatar.FRAME_COUNT).map { ProceduralAvatar.render(s, it, hd, w, h) }
    }
    fun reset(c: Context) { specFile(c).delete(); headFile(c).delete() }
}

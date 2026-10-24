package com.kanraeh.app.companion

import android.animation.ValueAnimator
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.compose.ui.graphics.toArgb
import com.kanraeh.app.R
import com.kanraeh.app.avatar.AvatarStore
import com.kanraeh.app.core.Graph
import com.kanraeh.app.theme.dimmed
import com.kanraeh.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * MODULE: companion - the small avatar that walks in over any app, asks ONE question with big buttons, then walks off.
 * Plain Android views (not Compose) so it is light and works from a service. Calm prompts are dimmed and slower.
 * Still mode (Settings) removes all motion. Needs "Display over other apps".
 */
class OverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wm: WindowManager? = null
    private var root: View? = null
    private var frames: List<Bitmap> = emptyList()
    private var walking = false
    private var answered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n: Notification = NotificationCompat.Builder(this, Graph.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("Kanraeh").setContentText("Checking in")
            .setPriority(NotificationCompat.PRIORITY_MIN).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        val prompt = intent?.getStringExtra("prompt")?.let { PromptJson.from(it) }
        if (prompt == null || !Permissions.canOverlay(this)) { stopSelf(); return START_NOT_STICKY }
        removeOverlay()
        show(prompt)
        return START_NOT_STICKY
    }

    private fun show(prompt: Prompt) {
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val pal = Graph.paletteStore.current().let { if (prompt.calm) it.dimmed() else it }
        val still = Graph.settings.stillMode || prompt.calm
        frames = AvatarStore.frames(this, (90 * d).toInt(), (135 * d).toInt())
        val avatarW = frames[0].width

        val avatar = ImageView(this).apply { setImageBitmap(frames[0]); layoutParams = FrameLayout.LayoutParams(avatarW, frames[0].height) }
        val track = FrameLayout(this).apply { addView(avatar) }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.INVISIBLE
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply { setColor(pal.card.toArgb()); cornerRadius = 22 * d }
            elevation = 6 * d
        }
        bubble.addView(TextView(this).apply { text = prompt.text; textSize = 18f; setTextColor(pal.text.toArgb()) })
        prompt.options.chunked(2).forEach { rowOpts ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, (8 * d).toInt(), 0, 0) }
            rowOpts.forEach { o ->
                row.addView(Button(this).apply {
                    text = o.label; isAllCaps = false; textSize = 16f
                    setTextColor(pal.bg.toArgb())
                    background = GradientDrawable().apply { setColor(pal.accent.toArgb()); cornerRadius = 16 * d }
                    layoutParams = LinearLayout.LayoutParams(0, (52 * d).toInt(), 1f).apply { setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0) }
                    setOnClickListener { answer(o.action) }
                })
            }
            bubble.addView(row)
        }
        avatar.setOnClickListener { openApp(null); dismiss(false) }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), 0, (12 * d).toInt(), (24 * d).toInt())
            addView(bubble, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(track, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, frames[0].height))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM; y = (48 * d).toInt() }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm?.addView(column, lp); root = column

        val targetX = 4 * d
        if (still) { avatar.translationX = targetX; bubble.visibility = View.VISIBLE }
        else {
            avatar.translationX = -avatarW.toFloat(); walking = true
            animateFrames(avatar)
            ValueAnimator.ofFloat(-avatarW.toFloat(), targetX).apply {
                duration = 2200
                addUpdateListener { avatar.translationX = it.animatedValue as Float }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) { walking = false; avatar.setImageBitmap(frames[0]); bubble.visibility = View.VISIBLE }
                })
                start()
            }
        }
        handler.postDelayed({ if (!answered) dismiss(true) }, 45_000)   // ignored: counts toward backing off
    }

    private fun animateFrames(img: ImageView) {
        var i = 0
        val r = object : Runnable {
            override fun run() { if (!walking) return; i = (i + 1) % frames.size; img.setImageBitmap(frames[i]); handler.postDelayed(this, 160) }
        }
        handler.post(r)
    }

    private fun answer(a: PromptAction) {
        answered = true
        Graph.settings.ignoredStreak = 0
        scope.launch(Dispatchers.IO) {
            val open = Graph.handler.apply(a)
            launch(Dispatchers.Main) { if (open != null) openApp(open); dismiss(false) }
        }
    }

    private fun openApp(open: String?) {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply { open?.let { putExtra("open", it) } })
    }

    private fun dismiss(ignored: Boolean) {
        if (ignored) Graph.settings.ignoredStreak = Graph.settings.ignoredStreak + 1
        walking = false; handler.removeCallbacksAndMessages(null)
        removeOverlay(); stopSelf()
    }

    private fun removeOverlay() { try { root?.let { wm?.removeView(it) } } catch (e: Exception) { }; root = null }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); scope.cancel(); removeOverlay(); super.onDestroy() }
}

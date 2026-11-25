package com.example.waguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Pal {
    val BG = Color.parseColor("#F1F6F4")
    val SOFT = Color.parseColor("#F1F6F4")
    val DARK = Color.parseColor("#0A1F1C")
    val DARK2 = Color.parseColor("#0F4A41")
    val ACCENT = Color.parseColor("#25D39A")
    val ACCENT_DARK = Color.parseColor("#0E8F69")
    val WHITE = Color.WHITE
    val TEXT = Color.parseColor("#14211F")
    val MUTED = Color.parseColor("#6B7C78")
    val BORDER = Color.parseColor("#DCE6E2")
    val DANGER = Color.parseColor("#D92D3A")
    val DANGER_BG = Color.parseColor("#FDECEC")
    val WARN = Color.parseColor("#8A5A00")
    val WARN_BG = Color.parseColor("#FFF4DB")
    val BIZ = Color.parseColor("#0B6A7D")
    val BIZ_BG = Color.parseColor("#D9F2F7")
    val WA = Color.parseColor("#0E7A3F")
    val WA_BG = Color.parseColor("#DCF6E6")
    val W70 = Color.parseColor("#B3FFFFFF")
    val W40 = Color.parseColor("#66FFFFFF")
    val W12 = Color.parseColor("#1FFFFFFF")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.rounded(fill: Int, radius: Int, stroke: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

fun oval(fill: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(fill)
}

fun ripple(content: Drawable): Drawable =
    RippleDrawable(ColorStateList.valueOf(Color.parseColor("#30808080")), content, null)

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

fun Context.label(text: CharSequence, size: Float = 14f, color: Int = Pal.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT_BOLD)
    }

fun Context.badge(text: String, fg: Int, bgc: Int): TextView =
    label(text, 11f, fg, true).apply {
        setPadding(dp(9), dp(3), dp(9), dp(3))
        background = rounded(bgc, 20)
    }

fun Context.button(
    text: String, fill: Int, fg: Int, stroke: Int = 0, size: Float = 15f, onClick: () -> Unit
): TextView {
    val v = if (size < 14f) 9 else 14
    return label(text, size, fg, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(v), dp(16), dp(v))
        background = ripple(rounded(fill, 14, stroke))
        isClickable = true
        setOnClickListener { onClick() }
    }
}

fun Context.gradientHeader(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val r = dp(28).toFloat()
    background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Pal.DARK2, Pal.DARK)).apply {
        cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
    }
}

fun roundOutline(r: Int): ViewOutlineProvider = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, r.toFloat())
    }
}

fun Activity.darkStatusBar(color: Int = Pal.DARK) {
    window.statusBarColor = color
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility =
        window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
}

fun Activity.footer(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    setPadding(dp(16), dp(10), dp(16), dp(14))
    addView(label(L.t(this@footer, "footer1"), 12f, Pal.MUTED, true).apply { gravity = Gravity.CENTER })
    addView(label(L.t(this@footer, "footer2"), 12f, Pal.ACCENT_DARK).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(2), 0, 0)
        setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:ecom.academy.tg@gmail.com")))
            } catch (_: Exception) {
            }
        }
    })
}

fun mimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "mp4" -> "video/mp4"
    "3gp" -> "video/3gpp"
    "opus", "ogg" -> "audio/ogg"
    "m4a" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "aac" -> "audio/aac"
    else -> "*/*"
}

/** Miniatures chargées en arrière-plan avec cache. */
object Thumbs {
    private val cache = LruCache<String, Bitmap>(40)
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(path: String, video: Boolean, iv: ImageView) {
        iv.tag = path
        val hit = cache.get(path)
        if (hit != null) { iv.setImageBitmap(hit); return }
        iv.setImageDrawable(null)
        pool.execute {
            val bmp = try { if (video) videoFrame(path) else decode(path, 600) } catch (_: Exception) { null }
            if (bmp != null) {
                cache.put(path, bmp)
                main.post { if (iv.tag == path) iv.setImageBitmap(bmp) }
            }
        }
    }

    fun decode(path: String, target: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= target) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun videoFrame(path: String): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            r.getFrameAtTime(0)
        } finally {
            r.release()
        }
    }
}

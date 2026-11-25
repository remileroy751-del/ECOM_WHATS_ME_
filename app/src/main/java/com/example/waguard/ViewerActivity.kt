package com.example.waguard

import android.app.Activity
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView

class ViewerActivity : Activity() {

    private var mp: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkStatusBar(Color.BLACK)
        val path = intent.getStringExtra("path") ?: run { finish(); return }
        val ext = path.substringAfterLast('.').lowercase()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B1412")) }
        when (ext) {
            "mp4", "3gp" -> {
                val v = VideoView(this)
                v.setMediaController(MediaController(this))
                v.setVideoPath(path)
                root.addView(v, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
                v.start()
            }
            "opus", "ogg", "m4a", "mp3", "aac" -> audioUi(root, path)
            else -> {
                val iv = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(Thumbs.decode(path, 1600))
                }
                root.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
            }
        }

        val back = label("←", 22f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = oval(Color.parseColor("#66000000"))
            setOnClickListener { finish() }
        }
        root.addView(
            back,
            FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.START).apply {
                setMargins(dp(12), dp(32), 0, 0)
            }
        )
        setContentView(root)
    }

    private fun audioUi(root: FrameLayout, path: String) {
        val btn: TextView = label("▶", 44f, Pal.DARK, true).apply {
            gravity = Gravity.CENTER
            background = oval(Pal.ACCENT)
        }
        val hint = label("Appuie pour lire / mettre en pause", 13f, Pal.W70).apply { gravity = Gravity.CENTER }
        val player = MediaPlayer()
        var ready = false
        try {
            player.setDataSource(path)
            player.prepare()
            ready = true
        } catch (e: Exception) {
            hint.text = "Lecture impossible sur cet appareil"
        }
        player.setOnCompletionListener { btn.text = "▶" }
        btn.setOnClickListener {
            if (!ready) return@setOnClickListener
            if (player.isPlaying) {
                player.pause(); btn.text = "▶"
            } else {
                player.start(); btn.text = "⏸"
            }
        }
        mp = player
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(btn, lp(dp(130), dp(130)))
            addView(hint, lp(WRAP, WRAP).apply { setMargins(0, dp(20), 0, 0) })
        }
        root.addView(col, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        if (ready) { player.start(); btn.text = "⏸" }
    }

    override fun onDestroy() {
        mp?.release()
        super.onDestroy()
    }
}

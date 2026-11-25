package com.example.waguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView

class LanguageActivity : Activity() {

    private var chosen = "fr"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkStatusBar()
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
        }
        val header = gradientHeader().apply {
            setPadding(dp(26), dp(50), dp(26), dp(30))
            gravity = Gravity.CENTER
        }
        header.addView(label("Ecom Whats Me", 26f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        header.addView(label(L.t(this, "choose_lang"), 15f, Pal.W70).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(20))
        }
        body.addView(label(L.t(this, "choose_lang_sub"), 13f, Pal.MUTED).apply {
            setPadding(0, 0, 0, dp(18))
        })
        body.addView(langCard("🇫🇷", "Français", "fr"))
        body.addView(langCard("🇬🇧", "English", "en"), lp(MATCH, WRAP).apply { setMargins(0, dp(12), 0, 0) })

        body.addView(
            button(L.t(this, "continue"), Pal.ACCENT, Pal.DARK, 0, 16f) {
                Prefs.setLang(this, chosen)
                startActivity(Intent(this, CountryActivity::class.java))
                finish()
            },
            lp(MATCH, WRAP).apply { setMargins(0, dp(30), 0, 0) }
        )
        root.addView(ScrollView(this).apply { addView(body) }, lp(MATCH, 0, 1f))
        setContentView(root)
    }

    private fun langCard(flag: String, name: String, code: String): LinearLayout {
        val sel = chosen == code
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(rounded(if (sel) Pal.ACCENT.let { Color.parseColor("#E8FBF3") } else Pal.WHITE, 16, if (sel) Pal.ACCENT else Pal.BORDER))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            setOnClickListener { chosen = code; render() }
        }
        card.addView(label(flag, 26f))
        card.addView(label(name, 16f, Pal.TEXT, true).apply { setPadding(dp(14), 0, 0, 0) }, lp(0, WRAP, 1f))
        if (sel) card.addView(label("✓", 18f, Pal.ACCENT_DARK, true))
        return card
    }
}

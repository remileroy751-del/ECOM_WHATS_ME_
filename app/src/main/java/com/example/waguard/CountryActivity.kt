package com.example.waguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

class CountryActivity : Activity() {

    private val countries = listOf(
        "Togo" to "TG", "Bénin" to "BJ", "Côte d'Ivoire" to "CI", "Sénégal" to "SN",
        "Ghana" to "GH", "Nigeria" to "NG", "Burkina Faso" to "BF", "Mali" to "ML",
        "Niger" to "NE", "Guinée" to "GN", "Cameroun" to "CM", "Gabon" to "GA",
        "Congo-Kinshasa (RDC)" to "CD", "Congo-Brazzaville" to "CG", "Tchad" to "TD",
        "Maroc" to "MA", "Algérie" to "DZ", "Tunisie" to "TN", "France" to "FR",
        "Belgique" to "BE", "Suisse" to "CH", "Canada" to "CA", "États-Unis" to "US",
        "Royaume-Uni" to "GB", "Allemagne" to "DE", "Afrique du Sud" to "ZA",
        "Kenya" to "KE", "Égypte" to "EG", "Inde" to "IN", "Brésil" to "BR", "Autre" to "XX"
    ).sortedBy { it.first }

    private var filtered = countries
    private var selected: Pair<String, String>? = null
    private lateinit var list: LinearLayout
    private lateinit var goBtn: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkStatusBar()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
        }
        val header = gradientHeader().apply { setPadding(dp(22), dp(46), dp(22), dp(24)) }
        header.addView(label(L.t(this, "choose_country"), 22f, Color.WHITE, true))
        header.addView(label(L.t(this, "choose_country_sub"), 13f, Pal.W70).apply { setPadding(0, dp(6), 0, 0) })
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        val search = EditText(this).apply {
            hint = L.t(this@CountryActivity, "search_country")
            background = rounded(Pal.WHITE, 14, Pal.BORDER)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString()?.trim()?.lowercase() ?: ""
                    filtered = if (q.isEmpty()) countries else countries.filter { it.first.lowercase().contains(q) }
                    renderList()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        body.addView(search)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        body.addView(ScrollView(this).apply { addView(list) }, lp(MATCH, 0, 1f))
        root.addView(body, lp(MATCH, 0, 1f))

        goBtn = button(L.t(this, "continue"), Pal.BORDER, Pal.MUTED, 0, 16f) { proceed() }
        root.addView(goBtn, lp(MATCH, WRAP).apply { setMargins(dp(18), 0, dp(18), dp(20)) })

        setContentView(root)
        renderList()
    }

    private fun renderList() {
        list.removeAllViews()
        for (c in filtered) {
            val sel = selected == c
            val row = label("${flagOf(c.second)}   ${c.first}", 15f, if (sel) Pal.DARK else Pal.TEXT, sel).apply {
                setPadding(dp(14), dp(13), dp(14), dp(13))
                background = ripple(rounded(if (sel) Color.parseColor("#E8FBF3") else Color.TRANSPARENT, 12, if (sel) Pal.ACCENT else 0))
                setOnClickListener {
                    selected = c
                    goBtn.text = L.t(this@CountryActivity, "continue")
                    goBtn.background = ripple(rounded(Pal.ACCENT, 14))
                    goBtn.setTextColor(Pal.DARK)
                    renderList()
                }
            }
            list.addView(row, lp(MATCH, WRAP))
        }
    }

    private fun flagOf(code: String): String {
        if (code.length != 2 || code == "XX") return "🌍"
        val base = 0x1F1E6
        return String(Character.toChars(base + (code[0].uppercaseChar() - 'A'))) +
            String(Character.toChars(base + (code[1].uppercaseChar() - 'A')))
    }

    private fun proceed() {
        val c = selected ?: return
        Prefs.setCountry(this, c.first, c.second)
        Supa.logInstall(
            Prefs.deviceId(this), c.first, c.second, Prefs.lang(this),
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        )
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

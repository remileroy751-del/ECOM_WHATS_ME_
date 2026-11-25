package com.example.waguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesActivity : Activity() {

    private var showAll = false
    private var kind: String? = null
    private var rows: List<Msg> = emptyList()

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var titleView: TextView
    private lateinit var countView: TextView
    private lateinit var toggleBtn: TextView
    private lateinit var chips: LinearLayout

    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = rows[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = buildRow(rows[position])
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkStatusBar()
        showAll = intent.getBooleanExtra("all", false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
        }

        val header = gradientHeader().apply { setPadding(dp(16), dp(34), dp(16), dp(18)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label("←", 26f, Color.WHITE, true).apply {
            setPadding(dp(4), 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        titleView = label("", 21f, Color.WHITE, true)
        top.addView(titleView, lp(0, WRAP, 1f))
        header.addView(top)

        countView = label("", 13f, Pal.W70).apply { setPadding(dp(34), dp(2), 0, dp(12)) }
        header.addView(countView)

        toggleBtn = button("", Color.TRANSPARENT, Color.WHITE, Pal.W40, 13f) {
            showAll = !showAll
            refresh()
        }
        header.addView(toggleBtn, lp(WRAP, WRAP).apply { setMargins(dp(34), 0, 0, 0) })
        root.addView(header)

        chips = LinearLayout(this).apply { setPadding(dp(14), dp(12), dp(14), dp(4)) }
        root.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(chips)
            },
            lp(MATCH, WRAP)
        )

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setPadding(dp(14), dp(8), dp(14), dp(8))
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            adapter = this@MessagesActivity.adapter
        }
        emptyView = label("", 15f, Pal.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(36), dp(36), dp(36), dp(36))
        }
        val body = FrameLayout(this)
        body.addView(listView, FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(emptyView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(body, lp(MATCH, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        rows = Db.get(this).list(!showAll, kind)
        titleView.text = if (showAll) L.t(this, "all_messages") else L.t(this, "deleted_messages")
        toggleBtn.text = if (showAll) "🗑 " + L.t(this, "see_deleted") else L.t(this, "see_all")
        countView.text = "${rows.size} " + L.t(this, "items")
        emptyView.text = if (showAll) L.t(this, "empty_all") else L.t(this, "empty_deleted")
        emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
        buildChips()
    }

    private fun buildChips() {
        chips.removeAllViews()
        val items = listOf<Pair<String, String?>>(
            "Tous" to null,
            "💬 Messages" to "text",
            "📷 Photos" to "photo",
            "🎥 Vidéos" to "video",
            "🎵 Audios" to "audio",
            "🎤 Vocaux" to "voice"
        )
        for ((name, key) in items) {
            val sel = kind == key
            val chip = label(name, 13f, if (sel) Pal.DARK else Pal.TEXT, true).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = ripple(rounded(if (sel) Pal.ACCENT else Pal.WHITE, 20, if (sel) Pal.ACCENT else Pal.BORDER))
                setOnClickListener { kind = key; refresh() }
            }
            chips.addView(chip, lp(WRAP, WRAP).apply { setMargins(0, 0, dp(8), 0) })
        }
    }

    // ------------------------------------------------------------ cartes

    private fun mediaFile(m: Msg): File? = m.media?.let { File(File(filesDir, "media"), it) }

    private fun buildRow(m: Msg): View {
        val wrap = LinearLayout(this).apply { setPadding(0, 0, 0, dp(12)) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Pal.WHITE, 18, Pal.BORDER)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        wrap.addView(card, lp(MATCH, WRAP))

        val file = mediaFile(m)
        val hasFile = file != null && file.exists()

        // ligne 1 : application + statut + date
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(
            if (m.app == "biz") badge("WH Business", Pal.BIZ, Pal.BIZ_BG) else badge("WhatsApp", Pal.WA, Pal.WA_BG)
        )
        if (m.deleted) {
            top.addView(badge("🗑 Supprimé", Pal.DANGER, Pal.DANGER_BG), lp(WRAP, WRAP).apply { setMargins(dp(6), 0, 0, 0) })
        }
        top.addView(View(this), lp(0, 1, 1f))
        top.addView(label(fmt.format(Date(m.time)), 11f, Pal.MUTED))
        card.addView(top)

        // expéditeur
        val who = if (m.orphan || m.sender.isBlank()) L.t(this, "unknown_sender") else m.sender
        card.addView(label("👤 $who", 16f, Pal.TEXT, true).apply { setPadding(0, dp(10), 0, 0) })
        if (!m.orphan && m.chat.isNotBlank() && m.chat != m.sender) {
            card.addView(label("${L.t(this, "in_group")} ${m.chat} »", 12f, Pal.MUTED))
        }

        fun gap() = lp(MATCH, WRAP).apply { setMargins(0, dp(10), 0, 0) }
        when (m.kind) {
            "text" -> card.addView(
                label(m.text, 15f, Pal.TEXT).apply {
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded(if (m.deleted) Pal.DANGER_BG else Pal.SOFT, 12)
                }, gap()
            )
            "photo", "video" -> {
                if (hasFile) {
                    val fl = FrameLayout(this)
                    val iv = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(Pal.DARK2)
                        outlineProvider = roundOutline(dp(12))
                        clipToOutline = true
                    }
                    fl.addView(iv, FrameLayout.LayoutParams(MATCH, dp(200)))
                    Thumbs.load(file!!.absolutePath, m.kind == "video", iv)
                    if (m.kind == "video") {
                        fl.addView(
                            label("▶", 26f, Color.WHITE, true).apply {
                                gravity = Gravity.CENTER
                                background = oval(Color.parseColor("#99000000"))
                            },
                            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
                        )
                    }
                    card.addView(fl, gap())
                } else card.addView(missingNote(m), gap())
            }
            else -> { // audio / voice
                if (hasFile) {
                    val row = LinearLayout(this).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = rounded(Pal.SOFT, 14)
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                    }
                    row.addView(label("▶", 18f, Pal.DARK, true).apply {
                        gravity = Gravity.CENTER
                        background = oval(Pal.ACCENT)
                    }, lp(dp(46), dp(46)))
                    val col = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), 0, 0, 0)
                        addView(label(if (m.kind == "voice") "Message vocal" else "Audio", 15f, Pal.TEXT, true))
                        addView(label("${(file!!.length() / 1024).coerceAtLeast(1)} Ko", 12f, Pal.MUTED))
                    }
                    row.addView(col)
                    card.addView(row, gap())
                } else card.addView(missingNote(m), gap())
            }
        }

        // boutons
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        val canShare = if (m.kind == "text") m.text.isNotBlank() else hasFile
        if (m.kind != "text" && hasFile) {
            val openLabel = when (m.kind) { "photo" -> L.t(this, "view"); "video" -> L.t(this, "play"); else -> L.t(this, "listen") }
            actions.addView(
                button(openLabel, Pal.WHITE, Pal.DARK, Pal.BORDER, 13f) { openMedia(file!!) },
                lp(WRAP, WRAP).apply { setMargins(0, 0, dp(8), 0) }
            )
            card.setOnClickListener { openMedia(file!!) }
        }
        if (canShare) actions.addView(button(L.t(this, "share"), Pal.ACCENT, Pal.DARK, 0, 13f) { share(m) })
        if (actions.childCount > 0) card.addView(actions, gap())
        return wrap
    }

    private fun missingNote(m: Msg): TextView {
        val what = if (m.text.isNotBlank()) m.text else "Média"
        return label(
            "⚠ $what — fichier non récupéré (le téléchargement automatique des médias est peut-être désactivé dans WhatsApp).",
            12f, Pal.WARN
        ).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Pal.WARN_BG, 12)
        }
    }

    private fun openMedia(f: File) {
        startActivity(Intent(this, ViewerActivity::class.java).putExtra("path", f.absolutePath))
    }

    private fun share(m: Msg) {
        val i = Intent(Intent.ACTION_SEND)
        val f = mediaFile(m)
        if (m.kind != "text") {
            if (f == null || !f.exists()) {
                Toast.makeText(this, "Fichier indisponible", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            i.type = mimeOf(f.name)
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, m.text)
        }
        startActivity(Intent.createChooser(i, "Partager via"))
    }
}

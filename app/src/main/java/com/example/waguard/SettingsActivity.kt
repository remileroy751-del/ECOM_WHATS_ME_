package com.example.waguard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkStatusBar()
        render()
    }

    private fun t(k: String) = L.t(this, k)

    private fun render() {
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
        top.addView(label(t("settings"), 20f, Color.WHITE, true), lp(0, WRAP, 1f))
        header.addView(top)
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        // ---- langue ----
        body.addView(sectionCard {
            addView(label(t("language_label"), 15f, Pal.TEXT, true))
            addView(LinearLayout(this@SettingsActivity).apply {
                setPadding(0, dp(10), 0, 0)
                addView(pill("🇫🇷 Français", Prefs.lang(this@SettingsActivity) == "fr") {
                    Prefs.setLang(this@SettingsActivity, "fr"); render()
                })
                addView(pill("🇬🇧 English", Prefs.lang(this@SettingsActivity) == "en") {
                    Prefs.setLang(this@SettingsActivity, "en"); render()
                }, lp(WRAP, WRAP).apply { setMargins(dp(8), 0, 0, 0) })
            })
        })

        // ---- licence ----
        val licensed = Prefs.licenseActive(this)
        body.addView(sectionCard {
            addView(label(t("license_section"), 15f, Pal.TEXT, true))
            addView(
                label(
                    if (licensed) "${Prefs.licenseDaysLeft(this@SettingsActivity)} ${t("license_days_left")}"
                    else "${t("no_license")} — ${Prefs.trialDaysLeft(this@SettingsActivity)} ${t("trial_days_left")}",
                    13f, Pal.MUTED
                ).apply { setPadding(0, dp(6), 0, dp(12)) }
            )
            addView(button(t("activate"), Pal.ACCENT, Pal.DARK, 0, 14f) {
                LicenseFlow.start(this@SettingsActivity) { render() }
            })
        })

        // ---- nettoyage automatique ----
        body.addView(sectionCard {
            val row = LinearLayout(this@SettingsActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            val col = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(t("auto_clean_title"), 15f, Pal.TEXT, true))
                addView(label(t("auto_clean_desc"), 12f, Pal.MUTED).apply { setPadding(0, dp(4), 0, 0) })
            }
            row.addView(col, lp(0, WRAP, 1f))
            row.addView(Switch(this@SettingsActivity).apply {
                isChecked = Prefs.autoCleanEnabled(this@SettingsActivity)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setAutoClean(this@SettingsActivity, checked)
                    if (checked) Prefs.setLastCleanTime(this@SettingsActivity, System.currentTimeMillis())
                }
            })
            addView(row)
        })

        // ---- diagnostic : le service tourne-t-il vraiment en arrière-plan ? ----
        body.addView(sectionCard {
            addView(label("🩺 Diagnostic", 15f, Pal.TEXT, true))
            addView(diagLine("Accès notifications", Perms.notif(this@SettingsActivity)))
            addView(diagLine("Accès fichiers", Perms.files(this@SettingsActivity)))
            addView(diagLine("Arrière-plan autorisé", Perms.battery(this@SettingsActivity)))
            addView(
                label(
                    "La protection se répare normalement toute seule (au retour d'internet, toutes les " +
                        "2h, et à chaque redémarrage du téléphone). Si les dates ci-dessus restent " +
                        "anciennes malgré de nouveaux messages reçus, appuie sur le bouton ci-dessous.",
                    12f, Pal.MUTED
                ).apply { setPadding(0, dp(10), 0, dp(10)) }
            )
            addView(button("🔄 Réactualiser la protection maintenant", Pal.ACCENT, Pal.DARK, 0, 14f) {
                Repair.runNow(this@SettingsActivity)
                Repair.schedulePeriodic(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, "Protection réactualisée.", Toast.LENGTH_SHORT).show()
                render()
            })
            addView(label(agoLabel("Dernière notification WhatsApp reçue", Prefs.lastNotifSeen(this@SettingsActivity)), 12f, Pal.MUTED).apply {
                setPadding(0, dp(10), 0, 0)
            })
            addView(label(agoLabel("Dernier média enregistré", Prefs.lastMediaSeen(this@SettingsActivity)), 12f, Pal.MUTED).apply {
                setPadding(0, dp(4), 0, 0)
            })
            addView(
                label(
                    "Si « Dernière notification WhatsApp reçue » reste à « jamais » alors que vous recevez " +
                        "des messages, cherchez dans les réglages de votre téléphone une option comme " +
                        "« Démarrage automatique », « Apps protégées » ou « Gestion de la batterie » " +
                        "(fréquent sur Xiaomi, Huawei, Oppo, Vivo, Samsung) et autorisez Ecom Whats Me.",
                    12f, Pal.WARN
                ).apply {
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = rounded(Pal.WARN_BG, 10)
                    layoutParams = lp(MATCH, WRAP).apply { setMargins(0, dp(12), 0, 0) }
                }
            )
        })

        // ---- identifiant de l'appareil (utile pour le support / prolongation de licence) ----
        body.addView(sectionCard {
            addView(label("Identifiant de l'appareil", 15f, Pal.TEXT, true))
            addView(
                label(
                    "À communiquer à Ecom Academy si besoin de prolonger votre licence manuellement.",
                    12f, Pal.MUTED
                ).apply { setPadding(0, dp(4), 0, dp(10)) }
            )
            addView(label(Prefs.deviceId(this@SettingsActivity), 13f, Pal.TEXT).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = rounded(Pal.SOFT, 10)
                setOnClickListener {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("device_id", Prefs.deviceId(this@SettingsActivity)))
                    Toast.makeText(this@SettingsActivity, "Copié", Toast.LENGTH_SHORT).show()
                }
            })
        })

        // ---- zone sensible ----
        body.addView(sectionCard {
            addView(label(t("danger_zone"), 15f, Pal.DANGER, true))
            addView(label("${Db.get(this@SettingsActivity).countAll()} ${t("saved_messages")}", 12f, Pal.MUTED).apply {
                setPadding(0, dp(4), 0, dp(12))
            })
            addView(button(t("erase_now"), Pal.DANGER_BG, Pal.DANGER, 0, 14f) {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(t("erase_confirm"))
                    .setPositiveButton(t("erase_now")) { _, _ ->
                        Cleanup.eraseAll(this@SettingsActivity)
                        Toast.makeText(this@SettingsActivity, t("erased"), Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .setNegativeButton(t("cancel"), null)
                    .show()
            })
        })

        root.addView(ScrollView(this).apply { addView(body) }, lp(MATCH, 0, 1f))
        root.addView(footer())
        setContentView(root)
    }

    private fun diagLine(name: String, ok: Boolean): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(MATCH, WRAP).apply { setMargins(0, dp(8), 0, 0) }
            addView(label(if (ok) "✅" else "❌", 14f), lp(dp(26), WRAP))
            addView(label(name, 13f, Pal.TEXT))
        }

    private fun agoLabel(prefix: String, time: Long): String {
        if (time <= 0L) return "$prefix : jamais"
        val mins = (System.currentTimeMillis() - time) / 60000
        val txt = when {
            mins < 1 -> "à l'instant"
            mins < 60 -> "il y a ${mins} min"
            mins < 1440 -> "il y a ${mins / 60} h"
            else -> "il y a ${mins / 1440} j"
        }
        return "$prefix : $txt"
    }

    private fun sectionCard(fill: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Pal.WHITE, 16, Pal.BORDER)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = lp(MATCH, WRAP).apply { setMargins(0, 0, 0, dp(14)) }
            fill()
        }

    private fun pill(text: String, sel: Boolean, onClick: () -> Unit) =
        label(text, 13f, if (sel) Pal.DARK else Pal.TEXT, sel).apply {
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = ripple(rounded(if (sel) Pal.ACCENT else Pal.SOFT, 20, if (sel) Pal.ACCENT else Pal.BORDER))
            setOnClickListener { onClick() }
        }
}

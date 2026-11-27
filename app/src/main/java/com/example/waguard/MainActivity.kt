package com.example.waguard

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

object Perms {
    fun notif(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
            ?.contains(c.packageName) == true

    fun files(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun battery(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
}

class MainActivity : Activity() {

    private var autoLaunched = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkStatusBar()
        // Premier lancement : langue puis pays avant tout le reste.
        if (Prefs.lang(this).isEmpty()) {
            startActivity(Intent(this, LanguageActivity::class.java)); finish(); return
        }
        if (Prefs.country(this).isEmpty()) {
            startActivity(Intent(this, CountryActivity::class.java)); finish(); return
        }
        Cleanup.scheduleIfNeeded(this)
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.lang(this).isEmpty() || Prefs.country(this).isEmpty()) return
        NotifService.instance?.startWatcher()
        Cleanup.runIfDue(this)
        render()
    }

    private fun t(k: String) = L.t(this, k)

    private fun render() {
        val ok = listOf(Perms.notif(this), Perms.files(this), Perms.battery(this))
        val first = ok.indexOf(false)
        when {
            first in 0..1 -> {
                if (autoLaunched != first) { autoLaunched = first; launchStep(first) }
                showGate(ok)
            }
            first == 2 && autoLaunched != 2 -> {
                autoLaunched = 2; launchStep(2); showGate(ok)
            }
            else -> {
                Repair.schedulePeriodic(this)
                showHome(batteryMissing = !ok[2])
            }
        }
    }

    private fun launchStep(i: Int) {
        try {
            when (i) {
                0 -> if (Build.VERSION.SDK_INT >= 30) {
                    val cn = ComponentName(this, NotifService::class.java)
                    startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
                    )
                } else startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                1 -> askFiles()
                2 -> startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            }
        } catch (e: Exception) {
            try {
                if (i == 0) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                else openAppInfo()
            } catch (_: Exception) {
            }
        }
    }

    private fun askFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1
            )
        }
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    // ---------------------------------------------------------------- UI

    private fun logoRow(subtitle: String): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this@MainActivity).apply {
            setImageDrawable(packageManager.getApplicationIcon(packageName))
            outlineProvider = roundOutline(dp(16))
            clipToOutline = true
        }
        addView(logo, lp(dp(60), dp(60)))
        val col = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(label("Ecom Whats Me", 24f, Color.WHITE, true))
            addView(label(subtitle, 13f, Pal.W70))
        }
        addView(col, lp(0, WRAP, 1f))
    }

    private fun showGate(ok: List<Boolean>) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
        }
        val header = gradientHeader().apply { setPadding(dp(22), dp(40), dp(22), dp(26)) }
        header.addView(logoRow(t("app_tagline")))
        header.addView(label(t("activation_title"), 18f, Color.WHITE, true).apply {
            setPadding(0, dp(22), 0, dp(4))
        })
        header.addView(label(t("activation_sub"), 13f, Pal.W70))
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val titles = listOf(t("perm_notif"), t("perm_files"), t("perm_batt"))
        val descs = listOf(t("perm_notif_d"), t("perm_files_d"), t("perm_batt_d"))
        val current = ok.indexOf(false)
        for (i in 0..2) body.addView(stepCard(i, titles[i], descs[i], ok[i], i == current))

        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Pal.WARN_BG, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(label(t("restricted_title"), 13f, Pal.WARN, true))
            addView(label(t("restricted_body"), 12f, Pal.WARN).apply { setPadding(0, dp(4), 0, dp(10)) })
            addView(button(t("open_app_info"), Pal.WHITE, Pal.WARN, Pal.WARN, 13f) { openAppInfo() })
        }, lp(MATCH, WRAP).apply { setMargins(0, dp(6), 0, 0) })

        root.addView(ScrollView(this).apply { addView(body) }, lp(MATCH, 0, 1f))
        root.addView(footer())
        setContentView(root)
    }

    private fun stepCard(i: Int, title: String, desc: String, done: Boolean, active: Boolean): LinearLayout {
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Pal.WHITE, 16, if (active) Pal.ACCENT else Pal.BORDER)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val num = label(if (done) "✓" else "${i + 1}", 16f, if (done) Pal.DARK else Pal.MUTED, true).apply {
            gravity = Gravity.CENTER
            background = oval(if (done) Pal.ACCENT else Pal.SOFT)
        }
        card.addView(num, lp(dp(38), dp(38)))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            addView(label(title, 15f, Pal.TEXT, true))
            addView(label(desc, 12f, Pal.MUTED))
        }
        card.addView(col, lp(0, WRAP, 1f))
        if (!done) card.addView(button(t("allow"), Pal.ACCENT, Pal.DARK, 0, 13f) { launchStep(i) })
        card.layoutParams = lp(MATCH, WRAP).apply { setMargins(0, 0, 0, dp(10)) }
        return card
    }

    private fun showHome(batteryMissing: Boolean) {
        val db = Db.get(this)
        val deleted = db.countDeleted()
        val total = db.countAll()
        val licensed = Prefs.licenseActive(this)
        val trialLeft = Prefs.trialDaysLeft(this)
        val locked = !licensed && trialLeft <= 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
        }
        val header = gradientHeader().apply { setPadding(dp(22), dp(40), dp(22), dp(26)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(logoRow(t("app_tagline")), lp(0, WRAP, 1f))
        top.addView(label("⚙", 24f, Color.WHITE).apply {
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })
        header.addView(top)
        header.addView(label(t("protection_active"), 12f, Pal.ACCENT, true).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(Pal.W12, 20)
        }, lp(WRAP, WRAP).apply { setMargins(0, dp(18), 0, dp(16)) })

        val stats = LinearLayout(this)
        fun stat(num: String, text: String, last: Boolean) {
            val s = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Pal.W12, 16)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(label(num, 26f, Color.WHITE, true))
                addView(label(text, 12f, Pal.W70))
            }
            stats.addView(s, lp(0, WRAP, 1f).apply { if (!last) setMargins(0, 0, dp(10), 0) })
        }
        stat("$deleted", t("deleted_count"), false)
        stat("$total", t("archived_count"), true)
        header.addView(stats)

        // bandeau essai / licence
        val plan = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Pal.W12, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val planText = if (licensed)
            "${Prefs.licenseDaysLeft(this)} ${t("license_days_left")}"
        else
            "$trialLeft ${t("trial_days_left")}"
        plan.addView(label(planText, 14f, Color.WHITE, true), lp(0, WRAP, 1f))
        plan.addView(button(t("activate"), Pal.ACCENT, Pal.DARK, 0, 13f) {
            LicenseFlow.start(this) { render() }
        })
        header.addView(plan, lp(MATCH, WRAP).apply { setMargins(0, dp(14), 0, 0) })
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(10))
        }

        if (locked) {
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Pal.WHITE, 18, Pal.BORDER)
                setPadding(dp(18), dp(20), dp(18), dp(20))
                addView(label("🔒 " + t("trial_expired_title"), 17f, Pal.TEXT, true))
                addView(label(t("trial_expired_body"), 13f, Pal.MUTED).apply { setPadding(0, dp(8), 0, dp(16)) })
                addView(button(t("activate"), Pal.ACCENT, Pal.DARK, 0, 16f) { LicenseFlow.start(this@MainActivity) { render() } })
            }, lp(MATCH, WRAP))
        } else {
            body.addView(button(t("see_deleted"), Pal.ACCENT, Pal.DARK, 0, 17f) { openList(false) }, lp(MATCH, WRAP))
            body.addView(
                button(t("see_all"), Pal.WHITE, Pal.DARK, Pal.BORDER, 15f) { openList(true) },
                lp(MATCH, WRAP).apply { setMargins(0, dp(12), 0, 0) }
            )
            body.addView(
                label(t("home_hint"), 12f, Pal.MUTED).apply { setPadding(dp(4), dp(18), dp(4), 0) }
            )
        }

        if (batteryMissing) {
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(Pal.WARN_BG, 14)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(label(t("batt_warn_title"), 13f, Pal.WARN, true))
                addView(label(t("batt_warn_body"), 12f, Pal.WARN).apply { setPadding(0, dp(4), 0, dp(10)) })
                addView(button(t("allow_now"), Pal.WHITE, Pal.WARN, Pal.WARN, 13f) { launchStep(2) })
            }, lp(MATCH, WRAP).apply { setMargins(0, dp(18), 0, 0) })
        }
        root.addView(ScrollView(this).apply { addView(body) }, lp(MATCH, 0, 1f))
        root.addView(footer())
        setContentView(root)
    }

    private fun openList(all: Boolean) {
        startActivity(Intent(this, MessagesActivity::class.java).putExtra("all", all))
    }
}

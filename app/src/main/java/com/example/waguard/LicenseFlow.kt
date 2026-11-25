package com.example.waguard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/** Un seul écran : champ pour saisir une clé déjà achetée, et 2 boutons pour en acheter une. */
object LicenseFlow {

    private const val SHOP_30 = "https://wldadmwl.mychariow.shop/prd_hv0ptlrs"
    private const val SHOP_90 = "https://wldadmwl.mychariow.shop/prd_t34vacc7"

    fun start(a: Activity, onActivated: () -> Unit) {
        val input = EditText(a).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = L.t(a, "key_hint")
            background = a.rounded(Pal.SOFT, 12, Pal.BORDER)
            setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12))
        }
        val validateBtn = a.button(L.t(a, "validate"), Pal.ACCENT, Pal.DARK, 0, 15f) {}

        val content = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(22), a.dp(18), a.dp(22), a.dp(6))
            addView(a.label(L.t(a, "enter_key"), 15f, Pal.TEXT, true))
            addView(input, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, a.dp(8), 0, a.dp(12)) })
            addView(validateBtn, LinearLayout.LayoutParams(MATCH, WRAP))

            addView(android.view.View(a).apply { setBackgroundColor(Pal.BORDER) },
                LinearLayout.LayoutParams(MATCH, a.dp(1)).apply { setMargins(0, a.dp(20), 0, a.dp(16)) })

            addView(a.label(L.t(a, "no_key_yet"), 13f, Pal.MUTED))
            addView(
                a.button(L.t(a, "plan_30"), Pal.WHITE, Pal.DARK, Pal.BORDER, 14f) {
                    try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHOP_30))) } catch (_: Exception) { }
                },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, a.dp(10), 0, 0) }
            )
            addView(
                a.button(L.t(a, "plan_90"), Pal.WHITE, Pal.DARK, Pal.BORDER, 14f) {
                    try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHOP_90))) } catch (_: Exception) { }
                },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, a.dp(10), 0, 0) }
            )
        }

        val dlg = AlertDialog.Builder(a)
            .setView(content)
            .setNegativeButton(L.t(a, "close"), null)
            .create()

        validateBtn.setOnClickListener {
            val code = input.text.toString().trim()
            if (!looksValid(code)) {
                Toast.makeText(a, L.t(a, "key_err_format"), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            validateBtn.isEnabled = false
            Supa.claimKey(Prefs.deviceId(a), code) { r ->
                validateBtn.isEnabled = true
                when (r) {
                    is Supa.ClaimResult.Ok -> {
                        Prefs.setLicense(a, System.currentTimeMillis() + r.days.toLong() * 86_400_000L, r.type)
                        dlg.dismiss()
                        Toast.makeText(a, L.t(a, "key_ok"), Toast.LENGTH_LONG).show()
                        onActivated()
                    }
                    is Supa.ClaimResult.Err -> Toast.makeText(a, L.t(a, r.reasonKey), Toast.LENGTH_LONG).show()
                }
            }
        }
        dlg.show()
    }

    private fun looksValid(code: String): Boolean {
        val compact = code.replace(" ", "")
        val has9 = code.length == 9 && code.contains(" ")
        val has12 = compact.length == 12 && !code.contains(" ")
        return (has9 || has12) && compact.all { it.isLetterOrDigit() }
    }
}

package com.example.waguard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast

/** Boîtes de dialogue d'achat / activation de clé, réutilisées partout dans l'app. */
object LicenseFlow {

    private const val SHOP_30 = "https://wldadmwl.mychariow.shop/prd_hv0ptlrs"
    private const val SHOP_90 = "https://wldadmwl.mychariow.shop/prd_t34vacc7"

    fun start(a: Activity, onActivated: () -> Unit) {
        val choices = arrayOf(
            "${L.t(a, "plan_30")}\n${L.t(a, "plan_30_sub")}",
            "${L.t(a, "plan_90")}\n${L.t(a, "plan_90_sub")}"
        )
        AlertDialog.Builder(a)
            .setTitle(L.t(a, "choose_plan"))
            .setItems(choices) { _, which ->
                val url = if (which == 0) SHOP_30 else SHOP_90
                try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                askKey(a, onActivated)
            }
            .setNegativeButton(L.t(a, "cancel"), null)
            .show()
    }

    private fun askKey(a: Activity, onActivated: () -> Unit) {
        val input = EditText(a).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = L.t(a, "key_hint")
            setPadding(a.dp(20), a.dp(16), a.dp(20), a.dp(16))
        }
        val dlg = AlertDialog.Builder(a)
            .setTitle(L.t(a, "enter_key"))
            .setView(input)
            .setPositiveButton(L.t(a, "validate"), null)
            .setNegativeButton(L.t(a, "cancel"), null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = normalize(input.text.toString())
                if (!looksValid(code)) {
                    Toast.makeText(a, L.t(a, "key_err_format"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                Supa.claimKey(Prefs.deviceId(a), code) { r ->
                    dlg.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    when (r) {
                        is Supa.ClaimResult.Ok -> {
                            Prefs.setLicense(
                                a, System.currentTimeMillis() + r.days.toLong() * 86_400_000L, r.type
                            )
                            dlg.dismiss()
                            Toast.makeText(a, L.t(a, "key_ok"), Toast.LENGTH_LONG).show()
                            onActivated()
                        }
                        is Supa.ClaimResult.Err -> Toast.makeText(a, L.t(a, r.reasonKey), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dlg.show()
    }

    /** Accepte les deux formats, avec ou sans espace, insensible à la casse des séparateurs. */
    private fun normalize(raw: String): String = raw.trim()

    private fun looksValid(code: String): Boolean {
        val compact = code.replace(" ", "")
        val has9 = code.length == 9 && code.contains(" ")
        val has12 = compact.length == 12 && !code.contains(" ")
        return (has9 || has12) && compact.all { it.isLetterOrDigit() }
    }
}

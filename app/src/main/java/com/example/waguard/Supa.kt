package com.example.waguard

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Client Supabase minimal (REST + RPC), sans dépendance externe.
 * N'utilise que la clé publique ("publishable"), prévue pour être embarquée dans l'app.
 * Toute la logique sensible (vérification des clés, écritures) passe par des fonctions
 * RPC protégées côté base de données — jamais par un accès direct aux tables.
 */
object Supa {
    private const val URL_BASE = "https://dcxfshorvdaypqyugeil.supabase.co"
    private const val KEY = "sb_publishable_YsEsTvgdcnLBFzfxsOm0eg_Nmvt5lYY"

    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    private fun rpc(fn: String, body: JSONObject): JSONObject {
        val conn = URL("$URL_BASE/rest/v1/rpc/$fn").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.setRequestProperty("apikey", KEY)
        conn.setRequestProperty("Authorization", "Bearer $KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        val out = JSONObject()
        out.put("http", code)
        if (text.isNotBlank()) {
            try {
                when {
                    text.trim().startsWith("[") -> out.put("data", JSONArray(text))
                    text.trim().startsWith("{") -> out.put("data", JSONObject(text))
                    else -> out.put("data", text)
                }
            } catch (_: Exception) {
                out.put("raw", text)
            }
        }
        return out
    }

    private fun bg(task: () -> Unit) = pool.execute {
        try { task() } catch (_: Exception) { }
    }

    /** Enregistre l'installation (pays / langue) pour les statistiques admin. Silencieux en cas d'échec. */
    fun logInstall(deviceId: String, country: String, countryCode: String, lang: String, appVersion: String) {
        bg {
            rpc(
                "log_install",
                JSONObject()
                    .put("p_device_id", deviceId)
                    .put("p_country", country)
                    .put("p_country_code", countryCode)
                    .put("p_language", lang)
                    .put("p_app_version", appVersion)
            )
        }
    }

    /** Verrouille/relit le début d'essai côté serveur pour cet appareil. Callback (startedAtMillis ou null si échec). */
    fun registerTrial(hardwareId: String, localHintMillis: Long, cb: (Long?) -> Unit) {
        bg {
            val result: Long? = try {
                val hintIso = java.time.Instant.ofEpochMilli(localHintMillis).toString()
                val r = rpc("register_trial", JSONObject().put("p_hardware_id", hardwareId).put("p_hint", hintIso))
                val data = r.optJSONObject("data")
                val startedAt = data?.optString("started_at")
                if (startedAt.isNullOrBlank()) null
                else java.time.OffsetDateTime.parse(startedAt).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
            main.post { cb(result) }
        }
    }

    sealed class ClaimResult {
        data class Ok(val days: Int, val type: String) : ClaimResult()
        data class Err(val reasonKey: String) : ClaimResult()
    }

    /** Tente d'activer une clé pour cet appareil. Callback appelé sur le thread principal. */
    fun claimKey(deviceId: String, code: String, cb: (ClaimResult) -> Unit) {
        bg {
            val result: ClaimResult = try {
                val r = rpc("claim_license_key", JSONObject().put("p_device_id", deviceId).put("p_code", code))
                val data = r.optJSONObject("data")
                when {
                    r.optInt("http") !in 200..299 || data == null -> ClaimResult.Err("key_err_net")
                    data.optBoolean("ok", false) -> ClaimResult.Ok(data.optInt("days"), data.optString("type"))
                    else -> ClaimResult.Err(data.optString("error", "key_err_unknown"))
                }
            } catch (_: Exception) {
                ClaimResult.Err("key_err_net")
            }
            main.post { cb(result) }
        }
    }
}

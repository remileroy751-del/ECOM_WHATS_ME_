package com.example.waguard

import android.content.Context
import android.provider.Settings
import java.util.UUID

/** Toutes les données locales de l'app (hors messages/médias, qui sont dans Db.kt). */
object Prefs {
    private const val F = "ecom_prefs"
    private fun p(c: Context) = c.getSharedPreferences(F, Context.MODE_PRIVATE)

    // ---- identité de l'appareil ----
    // Basée sur ANDROID_ID : contrairement à un identifiant généré aléatoirement, celui-ci
    // survit à une désinstallation / réinstallation de l'app (il ne change qu'en cas de
    // réinitialisation d'usine du téléphone). Ça empêche de relancer un essai gratuit en
    // réinstallant, et ça permet à une licence déjà achetée de rester valable après
    // réinstallation. Quelques appareils très anciens ou atypiques ne fournissent pas cet
    // identifiant : dans ce cas rare, on retombe sur un identifiant aléatoire stocké localement.
    fun deviceId(c: Context): String {
        try {
            val androidId = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") return androidId
        } catch (_: Exception) {
        }
        val sp = p(c)
        var id = sp.getString("device_id_fallback", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sp.edit().putString("device_id_fallback", id).apply()
        }
        return id
    }

    // ---- premier lancement : langue / pays ----
    fun lang(c: Context): String = p(c).getString("lang", "") ?: ""
    fun setLang(c: Context, v: String) { p(c).edit().putString("lang", v).apply() }

    fun country(c: Context): String = p(c).getString("country", "") ?: ""
    fun setCountry(c: Context, name: String, code: String) {
        p(c).edit().putString("country", name).putString("country_code", code).apply()
    }
    fun countryCode(c: Context): String = p(c).getString("country_code", "") ?: ""

    // ---- essai gratuit ----
    fun installTime(c: Context): Long {
        val sp = p(c)
        var t = sp.getLong("install_time", 0L)
        if (t == 0L) {
            t = System.currentTimeMillis()
            sp.edit().putLong("install_time", t).apply()
        }
        return t
    }

    /** Aligne la date de début d'essai locale sur celle enregistrée côté serveur (voir Supa.registerTrial). */
    fun setInstallTime(c: Context, millis: Long) {
        p(c).edit().putLong("install_time", millis).apply()
    }

    fun trialDaysLeft(c: Context): Int {
        val elapsedDays = (System.currentTimeMillis() - installTime(c)) / 86_400_000L
        return (7 - elapsedDays).toInt().coerceAtLeast(0)
    }
    fun trialActive(c: Context): Boolean = trialDaysLeft(c) > 0

    // ---- licence ----
    fun licenseExpiry(c: Context): Long = p(c).getLong("license_expiry", 0L)
    fun licenseType(c: Context): String = p(c).getString("license_type", "") ?: ""
    fun setLicense(c: Context, expiryMillis: Long, type: String) {
        p(c).edit().putLong("license_expiry", expiryMillis).putString("license_type", type).apply()
    }
    fun licenseActive(c: Context): Boolean = licenseExpiry(c) > System.currentTimeMillis()
    fun licenseDaysLeft(c: Context): Int {
        val ms = licenseExpiry(c) - System.currentTimeMillis()
        return if (ms <= 0) 0 else (ms / 86_400_000L).toInt() + 1
    }

    /** L'application est utilisable (archivage + consultation) tant que l'essai OU la licence est actif. */
    fun canUseApp(c: Context): Boolean = trialActive(c) || licenseActive(c)

    // ---- nettoyage automatique tous les 7 jours ----
    fun autoCleanEnabled(c: Context): Boolean = p(c).getBoolean("auto_clean", false)
    fun setAutoClean(c: Context, v: Boolean) { p(c).edit().putBoolean("auto_clean", v).apply() }
    fun lastCleanTime(c: Context): Long = p(c).getLong("last_clean", installTime(c))
    fun setLastCleanTime(c: Context, v: Long) { p(c).edit().putLong("last_clean", v).apply() }

    // ---- diagnostic : pour vérifier que le service tourne vraiment en arrière-plan ----
    fun lastNotifSeen(c: Context): Long = p(c).getLong("last_notif_seen", 0L)
    fun setLastNotifSeen(c: Context, v: Long) { p(c).edit().putLong("last_notif_seen", v).apply() }
    fun lastMediaSeen(c: Context): Long = p(c).getLong("last_media_seen", 0L)
    fun setLastMediaSeen(c: Context, v: Long) { p(c).edit().putLong("last_media_seen", v).apply() }
}

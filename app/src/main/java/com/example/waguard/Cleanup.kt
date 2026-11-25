package com.example.waguard

import android.content.Context
import java.io.File

/**
 * Nettoyage automatique : si l'option est activée, tous les messages et médias enregistrés
 * sont effacés tous les 7 jours, le compte à rebours étant calculé depuis la date d'installation
 * (et non depuis l'activation de l'option).
 */
object Cleanup {

    private const val PERIOD = 7 * 86_400_000L

    /** Appelé à chaque ouverture de l'app : suffisant car l'app tourne en permanence en tâche de fond
     * grâce au service de notifications ; pas besoin d'alarme système dédiée. */
    fun runIfDue(c: Context) {
        if (!Prefs.autoCleanEnabled(c)) return
        val install = Prefs.installTime(c)
        val now = System.currentTimeMillis()
        val cyclesPassed = (now - install) / PERIOD
        val dueTime = install + cyclesPassed * PERIOD
        if (Prefs.lastCleanTime(c) < dueTime) {
            eraseAll(c)
            Prefs.setLastCleanTime(c, now)
        }
    }

    fun scheduleIfNeeded(c: Context) {
        // Le vrai déclenchement se fait dans runIfDue() à chaque ouverture + via NotifService
        // qui tourne en continu ; cette fonction est gardée pour un futur déclenchement par alarme.
    }

    fun eraseAll(c: Context) {
        c.deleteDatabase("ecomwhatsme.db")
        val dir = File(c.filesDir, "media")
        dir.listFiles()?.forEach { it.delete() }
    }
}

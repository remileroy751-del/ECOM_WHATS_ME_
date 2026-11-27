package com.example.waguard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService

/**
 * Remet en route l'archivage sans rien demander à l'utilisateur.
 * Trois déclencheurs l'appellent : une alarme périodique (toutes les 2h, y compris
 * téléphone verrouillé), un redémarrage du téléphone, et le retour d'internet détecté
 * depuis NotifService. Un bouton "Réactualiser" dans les Paramètres avancés l'appelle aussi
 * manuellement, pour que l'utilisateur ait toujours un recours immédiat.
 */
object Repair {
    private const val REQUEST_CODE = 4821

    fun runNow(c: Context) {
        try {
            NotificationListenerService.requestRebind(ComponentName(c, NotifService::class.java))
        } catch (_: Exception) {
        }
        NotifService.instance?.startWatcher()
    }

    fun schedulePeriodic(c: Context) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                c, REQUEST_CODE, Intent(c, RepairReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR,
                AlarmManager.INTERVAL_HOUR * 2,
                pi
            )
        } catch (_: Exception) {
        }
    }
}

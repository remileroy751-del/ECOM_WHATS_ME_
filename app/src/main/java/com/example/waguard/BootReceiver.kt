package com.example.waguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Relance la protection automatiquement après un redémarrage du téléphone. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Perms.notif(context) && Perms.files(context)) {
                Repair.runNow(context)
                Repair.schedulePeriodic(context)
            }
        }
    }
}

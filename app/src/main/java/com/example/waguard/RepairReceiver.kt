package com.example.waguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Déclenché par l'alarme périodique (toutes les 2h) : force le rebranchement de l'écoute. */
class RepairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Perms.notif(context) && Perms.files(context)) Repair.runNow(context)
    }
}

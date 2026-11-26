package com.example.waguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService

/**
 * Service de fond permanent (notification discrète obligatoire côté Android) dont le seul
 * rôle est de forcer le rebranchement du service d'écoute des notifications WhatsApp
 * (NotifService) et de relancer la surveillance des médias, chaque fois que :
 *  - internet revient après une coupure,
 *  - le téléphone redémarre,
 *  - ou simplement périodiquement, par sécurité.
 * Tout se passe en silence, sans aucune action de l'utilisateur.
 */
class GuardService : Service() {

    private lateinit var cm: ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Le réseau vient de revenir (ou l'app vient de démarrer avec un réseau déjà là) :
                // on force le système à rebrancher l'écoute des notifications, au cas où elle
                // aurait été coupée pendant la panne, puis on relance la surveillance des médias.
                rebind()
            }
        }
        cm.registerNetworkCallback(request, callback!!)
        // sécurité supplémentaire : un rebranchement immédiat au démarrage du service aussi.
        rebind()
    }

    private fun rebind() {
        try {
            NotificationListenerService.requestRebind(ComponentName(this, NotifService::class.java))
        } catch (_: Exception) {
        }
        NotifService.instance?.startWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        rebind()
        return START_STICKY
    }

    override fun onDestroy() {
        try { callback?.let { cm.unregisterNetworkCallback(it) } } catch (_: Exception) { }
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        val channelId = "ecom_guard"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(channelId, "Ecom Whats Me — Protection", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("Ecom Whats Me")
            .setContentText("Protection active en arrière-plan")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notif)
        }
    }

    companion object {
        fun start(c: Context) {
            val i = Intent(c, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
        }
    }
}

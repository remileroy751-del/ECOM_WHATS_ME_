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
import android.os.SystemClock
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
    @Volatile private var lastRecoveryAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // onCapabilitiesChanged confirme ensuite que le réseau est réellement utilisable.
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) rebind()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) rebind()
            }
        }
        // Le réseau par défaut est celui effectivement utilisé par les applications.
        cm.registerDefaultNetworkCallback(callback!!)
        // sécurité supplémentaire : un rebranchement immédiat au démarrage du service aussi.
        rebind()
    }

    @Synchronized
    private fun rebind() {
        // Évite les appels répétés lors des changements de capacités d'un même réseau.
        val now = SystemClock.elapsedRealtime()
        if (lastRecoveryAt != 0L && now - lastRecoveryAt < 3_000L) return
        lastRecoveryAt = now
        try {
            NotificationListenerService.requestRebind(ComponentName(this, NotifService::class.java))
        } catch (_: Exception) { }
        NotifService.instance?.refreshProtection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastRecoveryAt = 0L
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

        fun refresh(c: Context) {
            try { NotificationListenerService.requestRebind(ComponentName(c, NotifService::class.java)) }
            catch (_: Exception) { }
            start(c)
            NotifService.instance?.refreshProtection()
        }
    }
}

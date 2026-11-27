package com.example.waguard

import android.app.Notification
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {

    companion object {
        @Volatile var instance: NotifService? = null
        private val DELETED = listOf(
            "message was deleted", "message a été supprimé", "message supprimé",
            "mensaje eliminado", "mensagem foi apagada", "🚫"
        )
        private val SUMMARY = Regex("^\\d+ (new |nouveaux |nouveau )?(messages?)", RegexOption.IGNORE_CASE)
    }

    private var watcher: MediaWatcher? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onListenerConnected() {
        instance = this
        startWatcher()
        Repair.schedulePeriodic(this)
        registerNetworkWatch()
    }

    override fun onListenerDisconnected() {
        watcher?.stop()
        unregisterNetworkWatch()
        instance = null
    }

    /** Dès qu'internet revient après une coupure, on relance immédiatement la surveillance
     * des fichiers, sans attendre l'alarme périodique ni l'intervention de l'utilisateur. */
    private fun registerNetworkWatch() {
        if (netCallback != null) return
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    startWatcher()
                }
            }
            cm.registerNetworkCallback(request, cb)
            netCallback = cb
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkWatch() {
        try {
            netCallback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }
        netCallback = null
    }

    fun startWatcher() {
        if (watcher == null) watcher = MediaWatcher(applicationContext)
        watcher?.start()
    }

    private fun isDeleted(t: String): Boolean {
        val l = t.lowercase()
        return DELETED.any { l.contains(it) }
    }

    private fun kindOf(t: String): String {
        val s = t.trim()
        val l = s.lowercase()
        return when {
            s.startsWith("📷") || l == "photo" || l == "image" -> "photo"
            s.startsWith("🎥") || s.startsWith("📹") || s.startsWith("🎬") || l == "video" || l == "vidéo" -> "video"
            s.startsWith("🎤") || s.startsWith("🎙") || l.startsWith("voice message") || l.startsWith("message vocal") -> "voice"
            s.startsWith("🎵") || s.startsWith("🎧") || l == "audio" -> "audio"
            else -> "text"
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        val app = if (pkg == "com.whatsapp.w4b") "biz" else "wa"
        // Diagnostic : prouve que le service reçoit bien des notifications WhatsApp,
        // indépendamment de ce qui est ensuite enregistré ou non (visible dans Paramètres avancés).
        Prefs.setLastNotifSeen(this, System.currentTimeMillis())
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString() ?: return
        val db = Db.get(this)

        var handled = false
        @Suppress("DEPRECATION")
        val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (arr != null) {
            for (p in arr) {
                val b = p as? Bundle ?: continue
                val text = b.getCharSequence("text")?.toString() ?: continue
                val time = b.getLong("time", sbn.postTime)
                val sender = b.getCharSequence("sender")?.toString() ?: title
                handle(db, app, title, sender, text, time)
                handled = true
            }
        }
        if (!handled) {
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
            if (SUMMARY.containsMatchIn(text)) return
            handle(db, app, title, title, text, sbn.postTime)
        }
    }

    private fun handle(db: Db, app: String, chat: String, sender: String, text: String, time: Long) {
        if (isDeleted(text)) db.markDeleted(app, chat, time)
        else db.add(app, chat, sender, text, time, kindOf(text))
    }
}

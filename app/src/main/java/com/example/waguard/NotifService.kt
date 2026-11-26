package com.example.waguard

import android.app.Notification
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.ComponentName
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
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private val recoveryAttempts = listOf(1_000L, 5_000L, 15_000L)

    private val recoveryRunnable = object : Runnable {
        override fun run() {
            if (instance != null) return
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(applicationContext, NotifService::class.java)
                )
            } catch (_: Exception) { }
        }
    }

    override fun onListenerConnected() {
        instance = this
        recoveryHandler.removeCallbacksAndMessages(null)
        startWatcher()
    }

    override fun onListenerDisconnected() {
        watcher?.stop()
        instance = null
        // Android recommande explicitement requestRebind après une déconnexion
        // du NotificationListenerService. Plusieurs tentatives couvrent les
        // redémarrages momentanément retardés par le système.
        recoveryAttempts.forEach { delay ->
            recoveryHandler.postDelayed(recoveryRunnable, delay)
        }
    }

    @Synchronized
    fun startWatcher() {
        if (watcher == null) watcher = MediaWatcher(applicationContext)
        watcher?.start()
    }

    /** Relance l'écoute et la surveillance après une reconnexion ou depuis le bouton Actualiser. */
    fun refreshProtection() {
        recoveryHandler.removeCallbacksAndMessages(null)
        try {
            NotificationListenerService.requestRebind(
                ComponentName(applicationContext, NotifService::class.java)
            )
        } catch (_: Exception) { }
        startWatcher()
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

package com.example.waguard

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * app   : "wa" (WhatsApp) ou "biz" (WhatsApp Business)
 * kind  : text | photo | video | audio | voice
 * media : nom du fichier copié dans le stockage de l'app (ou null)
 */
data class Msg(
    val id: Long, val app: String, val chat: String, val sender: String, val text: String,
    val time: Long, val kind: String, val media: String?, val deleted: Boolean, val orphan: Boolean
)

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "ecomwhatsme.db", null, 1) {

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) {
            inst ?: Db(ctx.applicationContext).also { inst = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE msg(id INTEGER PRIMARY KEY AUTOINCREMENT, app TEXT NOT NULL, chat TEXT NOT NULL, " +
                "sender TEXT, text TEXT, time INTEGER, kind TEXT NOT NULL DEFAULT 'text', media TEXT, " +
                "deleted INTEGER DEFAULT 0, orphan INTEGER DEFAULT 0, k TEXT UNIQUE)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Enregistre un message reçu (texte ou annonce de média). */
    @Synchronized
    fun add(app: String, chat: String, sender: String, text: String, time: Long, kind: String) {
        val db = writableDatabase
        val k = "$app|$chat|$time|$text"
        db.rawQuery("SELECT 1 FROM msg WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) return }

        if (kind != "text") {
            // Le fichier est peut-être arrivé avant la notification : on fusionne avec le média "orphelin"
            val t = time.toString()
            db.rawQuery(
                "SELECT id FROM msg WHERE orphan=1 AND app=? AND kind=? AND ABS(time-?)<=120000 " +
                    "ORDER BY ABS(time-?) LIMIT 1",
                arrayOf(app, kind, t, t)
            ).use { c ->
                if (c.moveToFirst()) {
                    val cv = ContentValues().apply {
                        put("chat", chat); put("sender", sender); put("text", text)
                        put("time", time); put("orphan", 0); put("k", k)
                    }
                    db.update("msg", cv, "id=?", arrayOf(c.getLong(0).toString()))
                    return
                }
            }
        }
        val cv = ContentValues().apply {
            put("app", app); put("chat", chat); put("sender", sender); put("text", text)
            put("time", time); put("kind", kind); put("k", k)
        }
        db.insertWithOnConflict("msg", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Un fichier média vient d'être copié : on le rattache au message correspondant. */
    @Synchronized
    fun linkMedia(app: String, kind: String, name: String, ref: Long) {
        val db = writableDatabase
        db.rawQuery("SELECT 1 FROM msg WHERE media=?", arrayOf(name)).use { if (it.moveToFirst()) return }
        db.rawQuery(
            "SELECT id FROM msg WHERE app=? AND kind=? AND media IS NULL AND orphan=0 " +
                "AND time BETWEEN ? AND ? ORDER BY ABS(time-?) LIMIT 1",
            arrayOf(app, kind, (ref - 180000).toString(), (ref + 60000).toString(), ref.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                val cv = ContentValues().apply { put("media", name) }
                db.update("msg", cv, "id=?", arrayOf(c.getLong(0).toString()))
                return
            }
        }
        val cv = ContentValues().apply {
            put("app", app); put("chat", ""); put("sender", ""); put("text", "")
            put("time", ref); put("kind", kind); put("media", name); put("orphan", 1)
            put("k", "media|$name")
        }
        db.insertWithOnConflict("msg", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Le fichier d'origine a disparu de WhatsApp => le média a été supprimé. */
    @Synchronized
    fun markMediaGone(name: String) {
        val cv = ContentValues().apply { put("deleted", 1) }
        writableDatabase.update("msg", cv, "media=?", arrayOf(name))
    }

    /** WhatsApp a affiché "message supprimé". */
    @Synchronized
    fun markDeleted(app: String, chat: String, time: Long) {
        val cv = ContentValues().apply { put("deleted", 1) }
        val n = writableDatabase.update(
            "msg", cv, "app=? AND chat=? AND time=?", arrayOf(app, chat, time.toString())
        )
        if (n == 0) {
            writableDatabase.execSQL(
                "UPDATE msg SET deleted=1 WHERE id=(SELECT id FROM msg WHERE app=? AND chat=? " +
                    "AND deleted=0 ORDER BY time DESC LIMIT 1)",
                arrayOf(app, chat)
            )
        }
    }

    @Synchronized
    fun list(onlyDeleted: Boolean, kind: String?): List<Msg> {
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        if (onlyDeleted) clauses.add("deleted=1")
        if (kind != null) { clauses.add("kind=?"); args.add(kind) }
        val where = if (clauses.isEmpty()) null else clauses.joinToString(" AND ")
        val out = ArrayList<Msg>()
        readableDatabase.query(
            "msg", null, where, if (args.isEmpty()) null else args.toTypedArray(),
            null, null, "time DESC", "1000"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Msg(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("app")),
                        c.getString(c.getColumnIndexOrThrow("chat")) ?: "",
                        c.getString(c.getColumnIndexOrThrow("sender")) ?: "",
                        c.getString(c.getColumnIndexOrThrow("text")) ?: "",
                        c.getLong(c.getColumnIndexOrThrow("time")),
                        c.getString(c.getColumnIndexOrThrow("kind")),
                        c.getString(c.getColumnIndexOrThrow("media")),
                        c.getInt(c.getColumnIndexOrThrow("deleted")) == 1,
                        c.getInt(c.getColumnIndexOrThrow("orphan")) == 1
                    )
                )
            }
        }
        return out
    }

    @Synchronized
    fun countDeleted(): Long = DatabaseUtils.queryNumEntries(readableDatabase, "msg", "deleted=1")

    @Synchronized
    fun countAll(): Long = DatabaseUtils.queryNumEntries(readableDatabase, "msg")
}

package br.com.monitordenoticias.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VideoDb(context: Context) : SQLiteOpenHelper(context, "videos.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE videos(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "source_id TEXT NOT NULL," +
                "source_name TEXT NOT NULL," +
                "published_at INTEGER NOT NULL," +
                "link TEXT UNIQUE NOT NULL," +
                "summary TEXT DEFAULT ''," +
                "matched_term TEXT DEFAULT ''," +
                "matched_demand TEXT DEFAULT ''," +
                "captured_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(items: List<VideoItem>): List<VideoItem> {
        val inserted = mutableListOf<VideoItem>()
        writableDatabase.beginTransaction()
        try {
            items.forEach { item ->
                val values = ContentValues().apply {
                    put("title", item.title)
                    put("source_id", item.sourceId)
                    put("source_name", item.sourceName)
                    put("published_at", item.publishedAt)
                    put("link", item.link)
                    put("summary", item.summary)
                    put("matched_term", item.matchedTerm)
                    put("matched_demand", item.matchedDemand)
                    put("captured_at", item.capturedAt)
                }
                val id = writableDatabase.insertWithOnConflict("videos", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) {
                    inserted += item.copy(id = id)
                } else {
                    // Vídeos já capturados na v2.8 podem passar a corresponder a um
                    // Termo/Demanda na v2.8.1. Atualizamos a classificação sem
                    // contabilizar novamente como conteúdo novo.
                    val update = ContentValues().apply {
                        put("title", item.title)
                        put("source_id", item.sourceId)
                        put("source_name", item.sourceName)
                        put("published_at", item.publishedAt)
                        put("summary", item.summary)
                        if (item.matchedTerm.isNotBlank()) put("matched_term", item.matchedTerm)
                        if (item.matchedDemand.isNotBlank()) put("matched_demand", item.matchedDemand)
                        put("captured_at", item.capturedAt)
                    }
                    writableDatabase.update("videos", update, "link=?", arrayOf(item.link))
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return inserted
    }

    fun listRecent(days: Int = 7, limit: Int = 500): List<VideoItem> {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        return query("published_at>=?", arrayOf(cutoff.toString()), limit)
    }

    fun listAll(limit: Int = 1000): List<VideoItem> = query(null, null, limit)

    fun clear() {
        writableDatabase.delete("videos", null, null)
    }

    private fun query(where: String?, args: Array<String>?, limit: Int): List<VideoItem> {
        val sql = buildString {
            append("SELECT id,title,source_id,source_name,published_at,link,summary,matched_term,matched_demand,captured_at FROM videos")
            if (where != null) append(" WHERE ").append(where)
            append(" ORDER BY published_at DESC, captured_at DESC LIMIT ?")
        }
        val queryArgs = (args?.toList().orEmpty() + limit.toString()).toTypedArray()
        return readableDatabase.rawQuery(sql, queryArgs).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        VideoItem(
                            id = c.getLong(0),
                            title = c.getString(1),
                            sourceId = c.getString(2),
                            sourceName = c.getString(3),
                            publishedAt = c.getLong(4),
                            link = c.getString(5),
                            summary = c.getString(6).orEmpty(),
                            matchedTerm = c.getString(7).orEmpty(),
                            matchedDemand = c.getString(8).orEmpty(),
                            capturedAt = c.getLong(9)
                        )
                    )
                }
            }
        }
    }
}

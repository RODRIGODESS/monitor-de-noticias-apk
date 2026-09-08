package br.com.monitordenoticias.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.net.URI
import java.text.Normalizer

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

    /**
     * Remove resultados antigos da v2.8/v2.8.1 que eram páginas de listagem,
     * como "Todos os vídeos", /videos ou páginas de busca. Eles não são vídeos
     * individuais e por isso não devem aparecer na interface.
     */
    fun removeInvalidListingEntries(): Int {
        val ids = mutableListOf<Long>()
        readableDatabase.rawQuery("SELECT id,title,link FROM videos", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val title = c.getString(1).orEmpty()
                val link = c.getString(2).orEmpty()
                if (isGenericStoredResult(title, link)) ids += id
            }
        }
        if (ids.isEmpty()) return 0
        writableDatabase.beginTransaction()
        try {
            ids.forEach { id -> writableDatabase.delete("videos", "id=?", arrayOf(id.toString())) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return ids.size
    }

    /**
     * Corrige resultados salvos por versões anteriores quando um termo curto foi
     * confundido com parte de outra palavra. Ex.: o termo "FAB" não pode casar
     * com "fábrica". Mantém o vídeo quando ainda existir outro vínculo válido.
     */
    fun repairStoredMatches(): Int {
        data class Repair(val id: Long, val term: String?, val demand: String?, val delete: Boolean)
        val repairs = mutableListOf<Repair>()

        readableDatabase.rawQuery(
            "SELECT id,title,summary,matched_term,matched_demand FROM videos",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val body = "${c.getString(1).orEmpty()} ${c.getString(2).orEmpty()}"
                val matchedTerm = c.getString(3).orEmpty()
                val matchedDemand = c.getString(4).orEmpty()

                val termValid = matchedTerm.isBlank() || phraseMatchesStrict(body, matchedTerm)
                val demandSubject = matchedDemand.substringAfter(" • ", missingDelimiterValue = matchedDemand).trim()
                val demandValid = matchedDemand.isBlank() || (demandSubject.isNotBlank() && phraseMatchesStrict(body, demandSubject))

                if (termValid && demandValid) continue
                val keepTerm = if (termValid) matchedTerm else ""
                val keepDemand = if (demandValid) matchedDemand else ""
                repairs += Repair(
                    id = id,
                    term = keepTerm,
                    demand = keepDemand,
                    delete = keepTerm.isBlank() && keepDemand.isBlank()
                )
            }
        }

        if (repairs.isEmpty()) return 0
        writableDatabase.beginTransaction()
        try {
            repairs.forEach { repair ->
                if (repair.delete) {
                    writableDatabase.delete("videos", "id=?", arrayOf(repair.id.toString()))
                } else {
                    val values = ContentValues().apply {
                        put("matched_term", repair.term.orEmpty())
                        put("matched_demand", repair.demand.orEmpty())
                    }
                    writableDatabase.update("videos", values, "id=?", arrayOf(repair.id.toString()))
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return repairs.size
    }

    fun listRecent(days: Int = 7, limit: Int = 500): List<VideoItem> {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        return query("published_at>=?", arrayOf(cutoff.toString()), limit)
    }

    fun listPeriod(from: Long, to: Long, limit: Int = 1000): List<VideoItem> =
        query(
            "published_at>=? AND published_at<=?",
            arrayOf(from.toString(), to.toString()),
            limit
        )

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

    private fun isGenericStoredResult(title: String, link: String): Boolean {
        val normalizedTitle = normalize(title)
        if (normalizedTitle in GENERIC_TITLES || normalizedTitle.startsWith("todos os videos")) return true

        val uri = runCatching { URI(link) }.getOrNull() ?: return false
        val path = uri.path.orEmpty().lowercase().trimEnd('/')
        if (path in GENERIC_PATHS) return true
        if (path.endsWith("/busca") || path.endsWith("/search")) return true
        if (path.contains("/busca/") || path.contains("/search/")) return true
        return false
    }

    private fun phraseMatchesStrict(text: String, phrase: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(phrase)
        if (wanted.isBlank()) return false

        val hayTokens = haystack.split(' ').filter { it.isNotBlank() }.toSet()
        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }
        if (wantedTokens.isEmpty()) return false
        if (wantedTokens.size == 1) return hayTokens.any { tokenEquivalent(it, wantedTokens.first()) }
        if (" $haystack ".contains(" $wanted ")) return true

        val meaningful = wantedTokens.filter { it.length >= 3 && it !in STOP_WORDS }
        return meaningful.isNotEmpty() && meaningful.all { wantedToken ->
            hayTokens.any { actualToken -> tokenEquivalent(actualToken, wantedToken) }
        }
    }

    private fun tokenEquivalent(actual: String, wanted: String): Boolean {
        if (actual == wanted) return true
        if (actual.length < 5 || wanted.length < 5) return false
        return actual in inflectionVariants(wanted) || wanted in inflectionVariants(actual)
    }

    private fun inflectionVariants(token: String): Set<String> = buildSet {
        add(token)
        when {
            token.endsWith("r") -> add(token + "es")
            token.endsWith("l") -> add(token.dropLast(1) + "is")
            token.endsWith("m") -> add(token.dropLast(1) + "ns")
            token.endsWith("ao") -> {
                add(token.dropLast(2) + "oes")
                add(token.dropLast(2) + "aes")
                add(token.dropLast(2) + "aos")
            }
            !token.endsWith("s") -> add(token + "s")
        }
        when {
            token.endsWith("res") && token.length > 5 -> add(token.dropLast(2))
            token.endsWith("is") && token.length > 5 -> add(token.dropLast(2) + "l")
            token.endsWith("ns") && token.length > 5 -> add(token.dropLast(2) + "m")
            token.endsWith("s") && token.length > 5 -> add(token.dropLast(1))
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
        private val GENERIC_TITLES = setOf(
            "videos", "video", "todos os videos", "todos videos", "ultimos videos", "mais videos",
            "ver videos", "ver todos os videos", "ao vivo", "assistir ao vivo", "carregar mais", "ver mais", "ver tudo"
        )
        private val GENERIC_PATHS = setOf(
            "/videos", "/video", "/ao-vivo", "/busca", "/search", "/categorias/jornalismo"
        )
    }
}

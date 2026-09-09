package br.com.monitordenoticias.android

import android.content.Context
import java.io.File
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager

class VideoDb(context: Context) : AutoCloseable {
    private val connection: Connection

    init {
        Class.forName("org.sqlite.JDBC")
        val dbFile = File(context.filesDir, "videos.db")
        dbFile.parentFile?.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use {
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA busy_timeout=5000")
            it.execute("""CREATE TABLE IF NOT EXISTS videos(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL, source_id TEXT NOT NULL, source_name TEXT NOT NULL,
                published_at INTEGER NOT NULL, link TEXT UNIQUE NOT NULL, summary TEXT DEFAULT '',
                matched_term TEXT DEFAULT '', matched_demand TEXT DEFAULT '', captured_at INTEGER NOT NULL)""")
        }
    }

    @Synchronized
    fun insert(items: List<VideoItem>): List<VideoItem> {
        val inserted = mutableListOf<VideoItem>()
        connection.autoCommit=false
        try {
            items.forEach { item ->
                connection.prepareStatement(
                    "INSERT OR IGNORE INTO videos(title,source_id,source_name,published_at,link,summary,matched_term,matched_demand,captured_at) VALUES(?,?,?,?,?,?,?,?,?)"
                ).use { ps ->
                    ps.setString(1,item.title); ps.setString(2,item.sourceId); ps.setString(3,item.sourceName)
                    ps.setLong(4,item.publishedAt); ps.setString(5,item.link); ps.setString(6,item.summary)
                    ps.setString(7,item.matchedTerm); ps.setString(8,item.matchedDemand); ps.setLong(9,item.capturedAt)
                    if(ps.executeUpdate()>0) {
                        val id=connection.createStatement().use { st -> st.executeQuery("SELECT last_insert_rowid()").use { rs -> if(rs.next())rs.getLong(1) else 0L } }
                        inserted += item.copy(id=id)
                    } else {
                        connection.prepareStatement(
                            """UPDATE videos SET title=?,source_id=?,source_name=?,published_at=?,summary=?,
                               matched_term=CASE WHEN ?<>'' THEN ? ELSE matched_term END,
                               matched_demand=CASE WHEN ?<>'' THEN ? ELSE matched_demand END WHERE link=?""".trimIndent()
                        ).use { up ->
                            up.setString(1,item.title); up.setString(2,item.sourceId); up.setString(3,item.sourceName)
                            up.setLong(4,item.publishedAt); up.setString(5,item.summary)
                            up.setString(6,item.matchedTerm); up.setString(7,item.matchedTerm)
                            up.setString(8,item.matchedDemand); up.setString(9,item.matchedDemand); up.setString(10,item.link)
                            up.executeUpdate()
                        }
                    }
                }
            }
            connection.commit()
        } catch(t:Throwable) {
            connection.rollback()
            throw t
        } finally { connection.autoCommit=true }
        return inserted
    }

    fun removeInvalidListingEntries(): Int {
        val ids=mutableListOf<Long>()
        connection.createStatement().use { st ->
            st.executeQuery("SELECT id,title,link FROM videos").use { rs ->
                while(rs.next()) if(isGenericStoredResult(rs.getString(2).orEmpty(),rs.getString(3).orEmpty())) ids += rs.getLong(1)
            }
        }
        ids.forEach { id -> connection.prepareStatement("DELETE FROM videos WHERE id=?").use { it.setLong(1,id); it.executeUpdate() } }
        return ids.size
    }

    fun repairStoredMatches(): Int {
        data class Repair(val id:Long,val term:String,val demand:String,val delete:Boolean)
        val repairs=mutableListOf<Repair>()
        connection.createStatement().use { st ->
            st.executeQuery("SELECT id,title,summary,matched_term,matched_demand FROM videos").use { rs ->
                while(rs.next()) {
                    val id=rs.getLong(1)
                    val body="${rs.getString(2).orEmpty()} ${rs.getString(3).orEmpty()}"
                    val term=rs.getString(4).orEmpty()
                    val demand=rs.getString(5).orEmpty()
                    val termValid=term.isBlank() || VideoMatchPolicy.phraseMatches(body,term)
                    val subject=demand.substringAfter(" • ",demand).trim()
                    val demandValid=demand.isBlank() || (subject.isNotBlank() && VideoMatchPolicy.phraseMatches(body,subject))
                    if(termValid && demandValid) continue
                    val keepTerm=if(termValid) term else ""
                    val keepDemand=if(demandValid) demand else ""
                    repairs += Repair(id,keepTerm,keepDemand,keepTerm.isBlank() && keepDemand.isBlank())
                }
            }
        }
        repairs.forEach { r ->
            if(r.delete) connection.prepareStatement("DELETE FROM videos WHERE id=?").use { it.setLong(1,r.id); it.executeUpdate() }
            else connection.prepareStatement("UPDATE videos SET matched_term=?,matched_demand=? WHERE id=?").use {
                it.setString(1,r.term); it.setString(2,r.demand); it.setLong(3,r.id); it.executeUpdate()
            }
        }
        return repairs.size
    }

    fun listRecent(days:Int=7,limit:Int=500):List<VideoItem> {
        val cutoff=System.currentTimeMillis()-days*24L*60L*60L*1000L
        return query("published_at>=?", listOf(cutoff),limit)
    }

    fun listPeriod(from:Long,to:Long,limit:Int=1000):List<VideoItem> =
        query("published_at>=? AND published_at<=?", listOf(from,to),limit)

    fun listAll(limit:Int=1000):List<VideoItem> = query(null, emptyList(),limit)

    /**
     * Índice leve e sem limite artificial para a regra visual de vídeo novo.
     *
     * O Set mantém a API já usada pelo controller, mas `contains` trabalha com a
     * identidade canônica da URL. Assim uma entrada histórica `youtu.be/...` é
     * reconhecida quando a busca atual retorna `youtube.com/watch?v=...`, e URLs
     * de portais com parâmetros de rastreamento também não voltam como novas.
     */
    @Synchronized
    fun listKnownLinks(): Set<String> {
        val keys = connection.createStatement().use { st ->
            st.executeQuery("SELECT link FROM videos").use { rs ->
                buildSet {
                    while (rs.next()) {
                        rs.getString(1)
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::canonicalLinkKey)
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
            }
        }
        return object : AbstractSet<String>() {
            override val size: Int get() = keys.size
            override fun iterator(): Iterator<String> = keys.iterator()
            override fun contains(element: String): Boolean = canonicalLinkKey(element) in keys
        }
    }

    /** Cópia explícita das chaves canônicas para diagnóstico e SelfTest. */
    fun listKnownCanonicalLinks(): Set<String> = listKnownLinks().toSet()

    fun canonicalLinkKey(value: String): String = canonicalizeUrl(value)
        .lowercase()
        .trimEnd('/')

    fun clear(){ connection.createStatement().use { it.executeUpdate("DELETE FROM videos") } }

    private fun query(where:String?,args:List<Long>,limit:Int):List<VideoItem> {
        val sql=buildString {
            append("SELECT id,title,source_id,source_name,published_at,link,summary,matched_term,matched_demand,captured_at FROM videos")
            if(where!=null) append(" WHERE ").append(where)
            append(" ORDER BY published_at DESC,captured_at DESC LIMIT ?")
        }
        return connection.prepareStatement(sql).use { ps ->
            var i=1; args.forEach { ps.setLong(i++,it) }; ps.setInt(i,limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while(rs.next()) add(VideoItem(
                        id=rs.getLong(1), title=rs.getString(2), sourceId=rs.getString(3), sourceName=rs.getString(4),
                        publishedAt=rs.getLong(5), link=rs.getString(6), summary=rs.getString(7).orEmpty(),
                        matchedTerm=rs.getString(8).orEmpty(), matchedDemand=rs.getString(9).orEmpty(), capturedAt=rs.getLong(10)
                    ))
                }
            }
        }
    }

    private fun canonicalizeUrl(value:String):String {
        if(value.isBlank()) return ""
        return runCatching {
            val uri=URI(value.trim())
            val host=uri.host.orEmpty().lowercase()
            if(host=="youtu.be" || host.endsWith("youtube.com")) {
                val query=uri.rawQuery.orEmpty()
                val videoId=when {
                    host=="youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
                    uri.path.equals("/watch",ignoreCase=true) ->
                        query.split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=").orEmpty()
                    else -> {
                        val parts=uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
                        if(parts.size>=2 && parts.first().lowercase() in setOf("shorts","live")) parts[1] else ""
                    }
                }
                if(videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else value.trim()
            } else {
                val path=uri.path.orEmpty().ifBlank { "/" }
                URI(uri.scheme ?: "https",uri.userInfo,uri.host,uri.port,path,null,null)
                    .toString()
                    .trimEnd('/')
            }
        }.getOrDefault(value.trim())
    }

    private fun isGenericStoredResult(title:String,link:String):Boolean {
        val normalized=java.text.Normalizer.normalize(title.lowercase(),java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"),"").replace(Regex("[^a-z0-9]+")," ").trim()
        if(normalized in GENERIC_TITLES || normalized.startsWith("todos os videos")) return true
        val uri=runCatching { URI(link) }.getOrNull() ?: return false
        val path=uri.path.orEmpty().lowercase().trimEnd('/')
        return path in GENERIC_PATHS || path.endsWith("/busca") || path.endsWith("/search") ||
            path.contains("/busca/") || path.contains("/search/")
    }

    override fun close(){ runCatching { connection.close() } }

    companion object {
        private val GENERIC_TITLES=setOf("videos","video","todos os videos","todos videos","ultimos videos","mais videos","ver videos","ver todos os videos","ao vivo","assistir ao vivo","carregar mais","ver mais","ver tudo")
        private val GENERIC_PATHS=setOf("/videos","/video","/ao-vivo","/busca","/search","/categorias/jornalismo")
    }
}

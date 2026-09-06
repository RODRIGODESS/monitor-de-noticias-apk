package br.com.monitordenoticias.android

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter

class NewsRepository(private val db: NewsDb) {
    val defaultTerms = listOf("Marinha do Brasil","Capitania dos Portos","Distrito Naval","NAM Atlântico","Cisne Branco","Fragata Marinha do Brasil","Navio-Patrulha Marinha","Programa Nuclear da Marinha")

    suspend fun search(): SearchResult = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        performSearch(cutoff, System.currentTimeMillis())
    }

    suspend fun searchPeriod(from: Long, to: Long): SearchResult = withContext(Dispatchers.IO) {
        performSearch(from, to)
    }

    suspend fun searchBlockingCompatible(): SearchResult = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        performSearch(cutoff, System.currentTimeMillis())
    }

    private fun performSearch(from: Long, to: Long): SearchResult {
        val terms = db.listTerms().ifEmpty { defaultTerms }
        val demands = db.listDemands().filter { it.active }
        var errors = 0
        val raw = mutableListOf<Pair<News,String>>()
        terms.forEach { term ->
            val result = fetchGoogleNews(term)
            if (result == null) errors++ else result.forEach { raw += it to term }
        }
        val classified = raw
            .filter { it.first.date in from..to }
            .groupBy { it.first.link }
            .map { (_, matches) ->
                val base = matches.first().first
                val matchedTerms = matches.map { it.second }.distinct()
                val haystack = "${base.title} ${base.snippet}".lowercase()
                val demandMatch = demands.firstOrNull { d ->
                    val vehicleOk = d.vehicle.isBlank() || base.source.contains(d.vehicle, ignoreCase = true)
                    val subjectOk = haystack.contains(d.subject.lowercase())
                    vehicleOk && subjectOk
                }
                base.copy(
                    important = demandMatch != null,
                    demand = demandMatch != null,
                    matchedTerm = matchedTerms.joinToString(", "),
                    matchedDemand = demandMatch?.let { "${it.vehicle} • ${it.subject}" }.orEmpty(),
                    capturedAt = System.currentTimeMillis()
                )
            }
            .sortedByDescending { it.date }
        val inserted = db.insertNews(classified)
        return SearchResult(
            items = classified,
            foundCount = classified.size,
            newCount = inserted.size,
            newDemandCount = inserted.count { it.demand },
            errors = errors
        )
    }

    private fun fetchGoogleNews(term: String): List<News>? = runCatching {
        val q = URLEncoder.encode(term, "UTF-8")
        val url = URL("https://news.google.com/rss/search?q=$q&hl=pt-BR&gl=BR&ceid=BR:pt-419")
        val con = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 10000; requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 MonitorNoticiasAndroid/2.0")
        }
        try {
            if (con.responseCode !in 200..299) error("HTTP ${con.responseCode}")
            con.inputStream.use { input ->
                val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
                val result = mutableListOf<News>(); var event = parser.eventType
                var title=""; var link=""; var source="Google Notícias"; var date=0L; var desc=""; var inItem=false
                while(event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when(event) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> when(parser.name) {
                            "item" -> { inItem=true; title=""; link=""; source="Google Notícias"; date=0; desc="" }
                            "title" -> if(inItem) title=parser.nextText()
                            "link" -> if(inItem) link=parser.nextText()
                            "pubDate" -> if(inItem) date=parseDate(parser.nextText())
                            "description" -> if(inItem) desc=parser.nextText().replace(Regex("<[^>]*>"), "").trim()
                            "source" -> if(inItem) source=parser.nextText()
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> if(parser.name=="item" && inItem) {
                            if(title.isNotBlank() && link.isNotBlank()) result += News(title=title.trim(),source=source.trim(),date=date,link=link.trim(),snippet=desc.take(500))
                            inItem=false
                        }
                    }
                    event=parser.next()
                }
                result
            }
        } finally { con.disconnect() }
    }.getOrNull()

    private fun parseDate(value: String): Long = runCatching { DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
}

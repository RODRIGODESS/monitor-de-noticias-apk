package br.com.monitordenoticias.android

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.Normalizer
import java.time.Instant
import java.time.format.DateTimeFormatter

class NewsRepository(private val db: NewsDb) {
    val defaultTerms = listOf(
        "Marinha do Brasil","Capitania dos Portos","Distrito Naval","NAM Atlântico",
        "Cisne Branco","Fragata Marinha do Brasil","Navio-Patrulha Marinha","Programa Nuclear da Marinha"
    )

    suspend fun search(selectedSources: List<MediaSource> = emptyList(), searchAllSources: Boolean = true): SearchResult = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        performSearch(cutoff, System.currentTimeMillis(), selectedSources, searchAllSources)
    }

    suspend fun searchPeriod(from: Long, to: Long, selectedSources: List<MediaSource> = emptyList(), searchAllSources: Boolean = true): SearchResult = withContext(Dispatchers.IO) {
        performSearch(from, to, selectedSources, searchAllSources)
    }

    suspend fun searchBlockingCompatible(selectedSources: List<MediaSource> = emptyList(), searchAllSources: Boolean = true): SearchResult = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        performSearch(cutoff, System.currentTimeMillis(), selectedSources, searchAllSources)
    }

    suspend fun searchDemand(demand: Demand): DemandSearchResult = withContext(Dispatchers.IO) {
        performDemandSearch(demand)
    }

    suspend fun searchAllDemands(): DemandSweepResult = withContext(Dispatchers.IO) {
        val active = db.listDemands().filter { it.active }
        val allItems = mutableListOf<News>()
        var found = 0
        var fresh = 0
        var errors = 0
        active.forEach { demand ->
            val result = performDemandSearch(demand)
            found += result.foundCount
            fresh += result.newCount
            if (result.error != null) errors++
            allItems += result.items
        }
        DemandSweepResult(active.size, found, fresh, errors, allItems.distinctBy { it.link }.sortedByDescending { it.date })
    }

    private fun performDemandSearch(demand: Demand): DemandSearchResult {
        val checkedAt = System.currentTimeMillis()
        val query = buildString {
            append('"').append(demand.subject.trim()).append('"')
            if (demand.vehicle.isNotBlank()) append(" \"").append(demand.vehicle.trim()).append('"')
        }
        val fetched = fetchGoogleNews(query)
        if (fetched == null) {
            db.updateDemandStatus(demand.id, checkedAt, 0, 0, "Falha na consulta")
            return DemandSearchResult(demand, emptyList(), 0, 0, "Falha na consulta")
        }

        val cutoff = checkedAt - 24L * 60L * 60L * 1000L
        val matched = fetched
            .filter { it.date >= cutoff }
            .filter { news -> demandVehicleMatches(news.source, demand.vehicle) }
            .filter { news -> subjectMatches("${news.title} ${news.snippet}", demand.subject) }
            .distinctBy { it.link }
            .map { news ->
                news.copy(
                    important = true,
                    demand = true,
                    matchedTerm = demand.subject,
                    matchedDemand = "${demand.vehicle} • ${demand.subject}",
                    capturedAt = checkedAt
                )
            }
            .sortedByDescending { it.date }

        val inserted = db.insertNews(matched)
        db.updateDemandStatus(demand.id, checkedAt, matched.size, inserted.size, "")
        return DemandSearchResult(demand, matched, matched.size, inserted.size)
    }

    private fun performSearch(from: Long, to: Long, selectedSources: List<MediaSource>, searchAllSources: Boolean): SearchResult {
        val terms = db.listTerms().ifEmpty { defaultTerms }
        val demands = db.listDemands().filter { it.active }
        var errors = 0
        val raw = mutableListOf<Pair<News, String>>()

        terms.forEach { term ->
            val result = fetchGoogleNews(term)
            if (result == null) errors++ else result.forEach { raw += it to term }
        }

        if (!searchAllSources && selectedSources.isNotEmpty() && selectedSources.size <= 24) {
            selectedSources.chunked(8).forEach { batch ->
                val sourceClause = batch.joinToString(" OR ") { "\"${it.name}\"" }
                terms.forEach { term ->
                    val query = "\"$term\" ($sourceClause)"
                    val result = fetchGoogleNews(query)
                    if (result == null) errors++ else result.forEach { raw += it to term }
                }
            }
        }

        val classified = raw
            .filter { it.first.date in from..to }
            .filter { pair -> searchAllSources || selectedSources.any { selected -> sourceMatchesStrict(pair.first.source, selected) } }
            .groupBy { it.first.link }
            .map { (_, matches) ->
                val base = matches.first().first
                val matchedTerms = matches.map { it.second }.distinct()
                val demandMatch = demands.firstOrNull { d -> demandVehicleMatches(base.source, d.vehicle) && subjectMatches("${base.title} ${base.snippet}", d.subject) }
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
        return SearchResult(classified, classified.size, inserted.size, inserted.count { it.demand }, errors)
    }

    private fun demandVehicleMatches(actualSource: String, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val actual = normalize(actualSource)
        val actualKey = compact(actualSource)
        val hostKey = publisherHostKey(actualSource)
        val wanted = normalize(vehicle)
        val wantedKey = compact(vehicle)
        if (actual == wanted || actualKey == wantedKey || hostKey == wantedKey) return true
        val tokens = wanted.split(' ').filter { it.length >= 2 && it !in STOP_WORDS }
        return tokens.size >= 2 && wantedKey.length >= 5 && (actualKey.startsWith(wantedKey) || hostKey.startsWith(wantedKey))
    }

    private fun subjectMatches(text: String, subject: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(subject)
        if (wanted.isBlank()) return true
        if (haystack.contains(wanted)) return true
        val tokens = wanted.split(' ').filter { it.length >= 3 && it !in STOP_WORDS }
        return tokens.isNotEmpty() && tokens.all { haystack.contains(it) }
    }

    private fun sourceMatchesStrict(actualSource: String, selected: MediaSource): Boolean {
        val actualNormalized = normalize(actualSource)
        val actualKey = compact(actualSource)
        val hostKey = publisherHostKey(actualSource)
        return (listOf(selected.name) + selected.aliases).any { candidate ->
            val candidateNormalized = normalize(candidate)
            val candidateKey = compact(candidate)
            if (candidateNormalized.isBlank() || candidateKey.isBlank()) false
            else if (actualNormalized == candidateNormalized || actualKey == candidateKey || hostKey == candidateKey) true
            else {
                val meaningfulTokens = candidateNormalized.split(' ').filter { it.length >= 2 && it !in STOP_WORDS }
                meaningfulTokens.size >= 2 && candidateKey.length >= 6 && (actualKey.startsWith(candidateKey) || hostKey.startsWith(candidateKey))
            }
        }
    }

    private fun publisherHostKey(value: String): String {
        val cleaned = value.trim().lowercase()
        return compact(if ('.' in cleaned) cleaned.substringBefore('.') else cleaned)
    }

    private fun compact(value: String): String = normalize(value).replace(" ", "")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun fetchGoogleNews(query: String): List<News>? = runCatching {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://news.google.com/rss/search?q=$q&hl=pt-BR&gl=BR&ceid=BR:pt-419")
        val con = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 10000; requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 MonitorNoticiasAndroid/2.5")
        }
        try {
            if (con.responseCode !in 200..299) error("HTTP ${con.responseCode}")
            con.inputStream.use { input ->
                val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
                val result = mutableListOf<News>()
                var event = parser.eventType
                var title = ""; var link = ""; var source = "Google Notícias"; var date = 0L; var desc = ""; var inItem = false
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                            "item" -> { inItem = true; title = ""; link = ""; source = "Google Notícias"; date = 0; desc = "" }
                            "title" -> if (inItem) title = parser.nextText()
                            "link" -> if (inItem) link = parser.nextText()
                            "pubDate" -> if (inItem) date = parseDate(parser.nextText())
                            "description" -> if (inItem) desc = parser.nextText().replace(Regex("<[^>]*>"), "").trim()
                            "source" -> if (inItem) source = parser.nextText()
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                            if (title.isNotBlank() && link.isNotBlank()) result += News(title=title.trim(), source=source.trim(), date=date, link=link.trim(), snippet=desc.take(500))
                            inItem = false
                        }
                    }
                    event = parser.next()
                }
                result
            }
        } finally { con.disconnect() }
    }.getOrNull()

    private fun parseDate(value: String): Long = runCatching { DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from).toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())

    companion object {
        private val STOP_WORDS = setOf("de","do","da","dos","das","e","em","no","na","nos","nas","a","o","as","os")
    }
}

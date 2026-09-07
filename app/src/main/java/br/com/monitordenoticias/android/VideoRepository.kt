package br.com.monitordenoticias.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI
import java.text.Normalizer
import java.time.Instant

class VideoRepository(
    private val context: Context,
    private val db: VideoDb
) {
    suspend fun search(sources: List<VideoSource>): VideoSearchResult = withContext(Dispatchers.IO) {
        val newsDb = NewsDb(context)
        try {
            val terms = newsDb.listTerms()
            val demands = newsDb.listDemands().filter { it.active }
            val capturedAt = System.currentTimeMillis()
            val collected = mutableListOf<VideoItem>()
            var errors = 0

            sources.forEach { source ->
                val siteItems = runCatching { fetchWebsite(source, capturedAt) }.getOrElse { emptyList() }
                val raw = if (siteItems.isNotEmpty()) {
                    siteItems
                } else if (source.youtubeHandle.isNotBlank()) {
                    runCatching { fetchYoutube(source, capturedAt) }.getOrElse { emptyList() }
                } else {
                    emptyList()
                }
                if (raw.isEmpty()) errors++

                raw.forEach { item ->
                    val body = "${item.title} ${item.summary}"
                    val matchedTerm = terms.firstOrNull { phraseMatches(body, it) }.orEmpty()
                    val demand = demands.firstOrNull { d ->
                        sourceMatchesDemand(source, d.vehicle) && phraseMatches(body, d.subject)
                    }
                    collected += item.copy(
                        matchedTerm = matchedTerm,
                        matchedDemand = demand?.let { "${it.vehicle} • ${it.subject}" }.orEmpty()
                    )
                }
            }

            val items = collected
                .distinctBy { it.link }
                .sortedByDescending { it.publishedAt }
            val inserted = db.insert(items)
            VideoSearchResult(
                items = items,
                foundCount = items.size,
                newCount = inserted.size,
                relevantCount = items.count { it.relevant },
                newRelevantCount = inserted.count { it.relevant },
                errors = errors
            )
        } finally {
            newsDb.close()
        }
    }

    private fun fetchWebsite(source: VideoSource, capturedAt: Long): List<VideoItem> {
        val doc = Jsoup.connect(source.landingUrl)
            .userAgent("Mozilla/5.0 (Linux; Android 14) MonitorNoticias/2.8")
            .referrer("https://www.google.com/")
            .timeout(14_000)
            .followRedirects(true)
            .get()

        val sourceHost = URI(source.landingUrl).host.orEmpty().removePrefix("www.")
        val out = linkedMapOf<String, VideoItem>()

        doc.select("a[href]").forEach { anchor ->
            val absolute = anchor.absUrl("href").trim()
            if (absolute.isBlank() || absolute == source.landingUrl) return@forEach
            val uri = runCatching { URI(absolute) }.getOrNull() ?: return@forEach
            val host = uri.host.orEmpty().removePrefix("www.")
            if (host.isBlank() || !(host == sourceHost || host.endsWith(".$sourceHost") || sourceHost.endsWith(".$host"))) return@forEach

            val path = uri.path.orEmpty()
            if (source.linkHints.isNotEmpty() && source.linkHints.none { hint -> path.contains(hint, ignoreCase = true) }) return@forEach

            val title = sequenceOf(
                anchor.attr("aria-label"),
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                anchor.text()
            ).map { cleanText(it) }.firstOrNull { it.length >= 12 }.orEmpty()

            if (!usefulTitle(title)) return@forEach
            out.putIfAbsent(
                absolute,
                VideoItem(
                    title = title.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = capturedAt,
                    link = absolute,
                    summary = source.group,
                    capturedAt = capturedAt
                )
            )
        }
        return out.values.take(40)
    }

    private fun fetchYoutube(source: VideoSource, capturedAt: Long): List<VideoItem> {
        val handle = source.youtubeHandle.removePrefix("@")
        val channelPage = Jsoup.connect("https://www.youtube.com/@$handle/videos")
            .userAgent("Mozilla/5.0 (Linux; Android 14) MonitorNoticias/2.8")
            .timeout(14_000)
            .get()
            .html()
        val channelId = Regex("\\\"channelId\\\":\\\"(UC[0-9A-Za-z_-]{20,})\\\"")
            .find(channelPage)?.groupValues?.getOrNull(1)
            ?: return emptyList()

        val feed = Jsoup.connect("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            .userAgent("Mozilla/5.0 MonitorNoticias/2.8")
            .timeout(14_000)
            .parser(Parser.xmlParser())
            .get()

        return feed.select("entry").mapNotNull { entry ->
            val title = cleanText(entry.selectFirst("title")?.text().orEmpty())
            val link = entry.selectFirst("link[href]")?.attr("href").orEmpty()
            if (!usefulTitle(title) || link.isBlank()) return@mapNotNull null
            val published = runCatching {
                Instant.parse(entry.selectFirst("published")?.text().orEmpty()).toEpochMilli()
            }.getOrDefault(capturedAt)
            VideoItem(
                title = title.take(220),
                sourceId = source.id,
                sourceName = source.name,
                publishedAt = published,
                link = link,
                summary = "Canal oficial • ${source.group}",
                capturedAt = capturedAt
            )
        }.take(30)
    }

    private fun sourceMatchesDemand(source: VideoSource, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val wanted = normalize(vehicle)
        val wantedCompact = compact(vehicle)
        val candidates = listOf(source.name, source.group) + source.aliases
        return candidates.any { candidate ->
            val actual = normalize(candidate)
            val actualCompact = compact(candidate)
            actual == wanted || actualCompact == wantedCompact ||
                (wantedCompact.length >= 4 && actualCompact.contains(wantedCompact)) ||
                (actualCompact.length >= 4 && wantedCompact.contains(actualCompact))
        }
    }

    private fun phraseMatches(text: String, phrase: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(phrase)
        if (wanted.isBlank()) return true
        if (haystack.contains(wanted)) return true
        val tokens = wanted.split(' ').filter { it.length >= 3 && it !in STOP_WORDS }
        return tokens.isNotEmpty() && tokens.all { haystack.contains(it) }
    }

    private fun usefulTitle(value: String): Boolean {
        if (value.length < 12) return false
        val normalized = normalize(value)
        if (normalized in setOf("carregar mais", "ver mais", "ver tudo", "ultimos videos", "videos", "ao vivo")) return false
        return true
    }

    private fun cleanText(value: String): String = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun compact(value: String): String = normalize(value).replace(" ", "")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
    }
}

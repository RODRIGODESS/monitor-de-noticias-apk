package br.com.monitordenoticias.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.time.Instant

class VideoRepository(
    private val context: Context,
    private val db: VideoDb
) {
    private data class QuerySpec(
        val query: String,
        val term: String = "",
        val demand: Demand? = null
    )

    suspend fun search(
        sources: List<VideoSource>,
        from: Long? = null,
        to: Long? = null
    ): VideoSearchResult = withContext(Dispatchers.IO) {
        val newsDb = NewsDb(context)
        try {
            val terms = newsDb.listTerms().ifEmpty { DEFAULT_TERMS }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy(::normalize)
            val demands = newsDb.listDemands().filter { it.active }
            val capturedAt = System.currentTimeMillis()
            val collected = linkedMapOf<String, VideoItem>()
            var errors = 0

            sources.forEach { source ->
                val specs = buildList {
                    terms.forEach { term -> add(QuerySpec(query = term, term = term)) }
                    demands
                        .filter { demand -> sourceMatchesDemand(source, demand.vehicle) }
                        .forEach { demand -> add(QuerySpec(query = demand.subject, demand = demand)) }
                }.distinctBy { spec -> "${normalize(spec.query)}|${spec.term}|${spec.demand?.id ?: 0L}" }

                var fallbackLoaded = false
                var fallbackItems: List<VideoItem> = emptyList()
                val resolvedCache = mutableMapOf<String, VideoItem?>()

                fun loadFallback(): List<VideoItem> {
                    if (fallbackLoaded) return fallbackItems
                    fallbackLoaded = true
                    val site = runCatching { fetchWebsite(source, capturedAt) }
                        .onFailure { errors++ }
                        .getOrDefault(emptyList())
                    val youtube = if (source.youtubeHandle.isNotBlank()) {
                        runCatching { fetchYoutube(source, capturedAt) }
                            .onFailure { errors++ }
                            .getOrDefault(emptyList())
                    } else emptyList()
                    fallbackItems = (site + youtube).distinctBy { canonicalKey(it.link) }
                    return fallbackItems
                }

                fun resolve(item: VideoItem): VideoItem? {
                    if (isYoutubeUrl(item.link)) {
                        return if (isYoutubeVideoUrl(item.link)) item.copy(link = canonicalizeUrl(item.link)) else null
                    }
                    val cacheKey = canonicalKey(item.link)
                    if (resolvedCache.containsKey(cacheKey)) return resolvedCache[cacheKey]
                    val resolved = runCatching { resolveDirectVideoPage(source, item, capturedAt) }
                        .onFailure { errors++ }
                        .getOrNull()
                    resolvedCache[cacheKey] = resolved
                    return resolved
                }

                specs.forEach { spec ->
                    val searched = if (source.searchUrlTemplate.isNotBlank()) {
                        runCatching { fetchSearchWebsite(source, spec.query, capturedAt) }
                            .onFailure { errors++ }
                            .getOrDefault(emptyList())
                    } else emptyList()

                    val rawCandidates = searched
                        .filter { item -> phraseMatches("${item.title} ${item.summary}", spec.query) }
                        .ifEmpty {
                            loadFallback().filter { item ->
                                phraseMatches("${item.title} ${item.summary}", spec.query)
                            }
                        }

                    val candidates = rawCandidates
                        .asSequence()
                        .take(MAX_RESOLVED_PER_QUERY)
                        .mapNotNull(::resolve)
                        .filter { item -> phraseMatches("${item.title} ${item.summary}", spec.query) }
                        .filter { item -> withinPeriod(item.publishedAt, from, to) }
                        .toList()

                    candidates.forEach { item ->
                        val body = "${item.title} ${item.summary}"
                        val key = canonicalKey(item.link)
                        val previous = collected[key]
                        val matchedTerm = when {
                            spec.term.isNotBlank() && phraseMatches(body, spec.term) -> spec.term
                            previous?.matchedTerm?.isNotBlank() == true -> previous.matchedTerm
                            else -> terms.firstOrNull { phraseMatches(body, it) }.orEmpty()
                        }
                        val demand = spec.demand?.takeIf { phraseMatches(body, it.subject) }
                            ?: demands.firstOrNull { d -> sourceMatchesDemand(source, d.vehicle) && phraseMatches(body, d.subject) }
                        val matchedDemand = demand?.let { "${it.vehicle} • ${it.subject}" }
                            ?: previous?.matchedDemand.orEmpty()

                        if (matchedTerm.isNotBlank() || matchedDemand.isNotBlank()) {
                            collected[key] = item.copy(
                                link = canonicalizeUrl(item.link),
                                matchedTerm = matchedTerm.ifBlank { previous?.matchedTerm.orEmpty() },
                                matchedDemand = matchedDemand,
                                capturedAt = capturedAt
                            )
                        }
                    }
                }
            }

            val items = collected.values
                .filter { it.relevant && isDirectResult(it) }
                .filter { withinPeriod(it.publishedAt, from, to) }
                .distinctBy { canonicalKey(it.link) }
                .sortedByDescending { it.publishedAt }
            val inserted = db.insert(items)
            VideoSearchResult(
                items = items,
                foundCount = items.size,
                newCount = inserted.size,
                relevantCount = items.size,
                newRelevantCount = inserted.count { it.relevant },
                errors = errors
            )
        } finally {
            newsDb.close()
        }
    }

    /**
     * Revalida o histórico com as regras atuais. Remove, por exemplo, o falso
     * positivo FAB -> "fábrica" gravado por versões anteriores.
     */
    suspend fun revalidateStored(): Int = withContext(Dispatchers.IO) {
        val newsDb = NewsDb(context)
        try {
            val terms = newsDb.listTerms().ifEmpty { DEFAULT_TERMS }
            val demands = newsDb.listDemands().filter { it.active }
            var removed = 0
            db.listAll().forEach { item ->
                val body = "${item.title} ${item.summary}"
                val source = VideoSourceCatalog.byId[item.sourceId]
                val term = terms.firstOrNull { phraseMatches(body, it) }.orEmpty()
                val demand = if (source != null) {
                    demands.firstOrNull { d -> sourceMatchesDemand(source, d.vehicle) && phraseMatches(body, d.subject) }
                } else null
                val demandKey = demand?.let { "${it.vehicle} • ${it.subject}" }.orEmpty()
                if (term.isBlank() && demandKey.isBlank()) {
                    db.deleteByLink(item.link)
                    removed++
                } else {
                    db.updateClassification(item.link, term, demandKey)
                }
            }
            removed
        } finally {
            newsDb.close()
        }
    }

    private fun withinPeriod(value: Long, from: Long?, to: Long?): Boolean {
        if (from == null || to == null) return true
        return value in from..to
    }

    private fun fetchSearchWebsite(source: VideoSource, query: String, capturedAt: Long): List<VideoItem> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = source.searchUrlTemplate.replace("{query}", encoded)
        return fetchPageLinks(source, url, capturedAt, "Resultado em ${source.name}")
    }

    private fun fetchWebsite(source: VideoSource, capturedAt: Long): List<VideoItem> {
        val base = fetchPageLinks(source, source.landingUrl, capturedAt, source.group)
        if (!source.id.startsWith("globoplay-") || source.id == "video-globoplay-jornalismo") return base

        // Em páginas/consultas de um telejornal, as primeiras URLs /v/ normalmente
        // levam a edições recentes. Abrimos até duas delas para coletar a seção
        // "Trechos", onde ficam as matérias individuais com título próprio.
        val expanded = base
            .asSequence()
            .take(GLOBOPLAY_EDITION_PAGES_TO_EXPAND)
            .flatMap { edition ->
                runCatching {
                    fetchPageLinks(source, edition.link, capturedAt, source.group)
                }.getOrDefault(emptyList()).asSequence()
            }
            .toList()

        return (base + expanded)
            .distinctBy { canonicalKey(it.link) }
            .take(MAX_FALLBACK_ITEMS)
    }

    private fun fetchPageLinks(source: VideoSource, pageUrl: String, capturedAt: Long, fallbackSummary: String): List<VideoItem> {
        val doc = Jsoup.connect(pageUrl)
            .userAgent("Mozilla/5.0 (Linux; Android 14) MonitorNoticias/2.9.0")
            .referrer("https://www.google.com/")
            .timeout(14_000)
            .followRedirects(true)
            .get()

        val sourceHost = URI(source.landingUrl).host.orEmpty().removePrefix("www.")
        val out = linkedMapOf<String, VideoItem>()

        doc.select("a[href]").forEach { anchor ->
            val absolute = canonicalizeUrl(anchor.absUrl("href").trim())
            if (absolute.isBlank() || samePage(absolute, source.landingUrl) || samePage(absolute, pageUrl)) return@forEach
            val uri = runCatching { URI(absolute) }.getOrNull() ?: return@forEach
            val host = uri.host.orEmpty().removePrefix("www.")
            if (host.isBlank() || !(host == sourceHost || host.endsWith(".$sourceHost") || sourceHost.endsWith(".$host"))) return@forEach
            if (!isSpecificVideoUrl(source, absolute)) return@forEach

            val title = sequenceOf(
                anchor.attr("aria-label"),
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                anchor.text()
            ).map { cleanText(it) }.firstOrNull { usefulTitle(it) }.orEmpty()

            if (!usefulTitle(title)) return@forEach

            val parentText = cleanText(anchor.parent()?.text().orEmpty())
            val summary = when {
                parentText.isBlank() || normalize(parentText) == normalize(title) -> fallbackSummary
                parentText.length > 480 -> fallbackSummary
                else -> parentText.removePrefix(title).trim().ifBlank { fallbackSummary }
            }.take(360)

            out.putIfAbsent(
                canonicalKey(absolute),
                VideoItem(
                    title = title.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = capturedAt,
                    link = absolute,
                    summary = summary,
                    capturedAt = capturedAt
                )
            )
        }
        return out.values.take(50)
    }

    private fun resolveDirectVideoPage(source: VideoSource, candidate: VideoItem, capturedAt: Long): VideoItem? {
        if (!isSpecificVideoUrl(source, candidate.link)) return null

        val doc = Jsoup.connect(candidate.link)
            .userAgent("Mozilla/5.0 (Linux; Android 14) MonitorNoticias/2.9.0")
            .referrer(source.landingUrl)
            .timeout(14_000)
            .followRedirects(true)
            .get()

        val canonical = resolveCanonicalUrl(doc, candidate.link)
        if (!isSpecificVideoUrl(source, canonical)) return null

        val title = sequenceOf(
            doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=twitter:title]")?.attr("content").orEmpty(),
            doc.selectFirst("h1")?.text().orEmpty(),
            candidate.title,
            doc.title()
        ).map(::cleanText).firstOrNull(::usefulTitle).orEmpty()
        if (!usefulTitle(title)) return null

        val description = sequenceOf(
            doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=description]")?.attr("content").orEmpty(),
            candidate.summary
        ).map(::cleanText).firstOrNull { it.isNotBlank() && !isGenericSummary(it) }.orEmpty()

        val publishedAt = parsePublishedAt(doc) ?: candidate.publishedAt.takeIf { it > 0 } ?: capturedAt
        val directPattern = isSpecificVideoUrl(source, canonical)
        val hasVideoSignal = pageHasVideoSignal(doc)
        if (!directPattern && !hasVideoSignal) return null

        return candidate.copy(
            title = title.take(220),
            publishedAt = publishedAt,
            link = canonicalizeUrl(canonical),
            summary = description.take(360)
        )
    }

    private fun resolveCanonicalUrl(doc: Document, fallback: String): String {
        val candidates = listOf(
            doc.selectFirst("link[rel=canonical]")?.absUrl("href").orEmpty(),
            doc.selectFirst("meta[property=og:url]")?.attr("content").orEmpty(),
            fallback
        )
        return candidates.asSequence()
            .map { resolveUrl(fallback, it) }
            .map(::canonicalizeUrl)
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            .orEmpty()
    }

    private fun resolveUrl(base: String, value: String): String {
        if (value.isBlank()) return ""
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    private fun pageHasVideoSignal(doc: Document): Boolean {
        if (doc.selectFirst("video, meta[property=og:video], meta[property=og:video:url], iframe[src*=youtube], iframe[src*=player]") != null) return true
        return doc.select("script").take(40).any { script ->
            val text = script.data().ifBlank { script.html() }
            text.contains("VideoObject", ignoreCase = true) ||
                text.contains("contentUrl", ignoreCase = true) ||
                text.contains("embedUrl", ignoreCase = true)
        }
    }

    private fun parsePublishedAt(doc: Document): Long? {
        val values = buildList {
            add(doc.selectFirst("meta[property=article:published_time]")?.attr("content").orEmpty())
            add(doc.selectFirst("meta[name=date]")?.attr("content").orEmpty())
            add(doc.selectFirst("time[datetime]")?.attr("datetime").orEmpty())
            doc.select("script[type=application/ld+json]").take(8).forEach { script ->
                Regex("\\\"datePublished\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                    .find(script.data().ifBlank { script.html() })
                    ?.groupValues?.getOrNull(1)
                    ?.let(::add)
            }
        }.filter { it.isNotBlank() }
        values.forEach { value ->
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun fetchYoutube(source: VideoSource, capturedAt: Long): List<VideoItem> {
        val handle = source.youtubeHandle.removePrefix("@")
        val channelPage = Jsoup.connect("https://www.youtube.com/@$handle/videos")
            .userAgent("Mozilla/5.0 (Linux; Android 14) MonitorNoticias/2.9.0")
            .timeout(14_000)
            .get()
            .html()
        val channelId = Regex("\\\"channelId\\\":\\\"(UC[0-9A-Za-z_-]{20,})\\\"")
            .find(channelPage)?.groupValues?.getOrNull(1)
            ?: return emptyList()

        val feed = Jsoup.connect("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            .userAgent("Mozilla/5.0 MonitorNoticias/2.9.0")
            .timeout(14_000)
            .parser(Parser.xmlParser())
            .get()

        return feed.select("entry").mapNotNull { entry ->
            val title = cleanText(entry.selectFirst("title")?.text().orEmpty())
            val link = canonicalizeUrl(entry.selectFirst("link[href]")?.attr("href").orEmpty())
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
        }.take(40)
    }

    private fun isSpecificVideoUrl(source: VideoSource, url: String): Boolean {
        if (url.isBlank()) return false
        if (isYoutubeUrl(url)) return isYoutubeVideoUrl(url)
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val path = uri.path.orEmpty()
        val normalizedPath = path.lowercase().trimEnd('/')
        if (normalizedPath.isBlank() || normalizedPath == "/") return false
        if (GENERIC_PATHS.any { normalizedPath == it || normalizedPath.endsWith(it) }) return false
        if (normalizedPath.contains("/busca") || normalizedPath.contains("/search")) return false

        return when {
            source.id == "video-globoplay-jornalismo" || source.id.startsWith("globoplay-") ->
                Regex("/v/[0-9]+/?$", RegexOption.IGNORE_CASE).containsMatchIn(path)
            source.id == "video-r7-record" -> hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")
            source.id == "video-sbt-news" -> hasSpecificSuffix(path, "/videos/")
            source.id == "video-cnn-brasil" -> hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")
            source.id.startsWith("video-band") -> hasSpecificSuffix(path, "/videos/")
            else -> source.linkHints.any { hint -> hasSpecificSuffix(path, hint) }
        }
    }

    private fun hasSpecificSuffix(path: String, marker: String): Boolean {
        val index = path.indexOf(marker, ignoreCase = true)
        if (index < 0) return false
        val suffix = path.substring(index + marker.length).trim('/')
        if (suffix.length < 4) return false
        val normalizedSuffix = normalize(suffix)
        return normalizedSuffix.isNotBlank() && normalizedSuffix !in GENERIC_SLUGS
    }

    private fun isDirectResult(item: VideoItem): Boolean {
        if (!usefulTitle(item.title)) return false
        if (isYoutubeUrl(item.link)) return isYoutubeVideoUrl(item.link)
        val source = VideoSourceCatalog.byId[item.sourceId] ?: return false
        return isSpecificVideoUrl(source, item.link)
    }

    private fun isYoutubeUrl(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "youtu.be" || host.endsWith("youtube.com")
    }

    private fun isYoutubeVideoUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().trim('/')
        if (host == "youtu.be") return path.substringBefore('/').length >= 6
        if (!host.endsWith("youtube.com")) return false

        if (uri.path.equals("/watch", ignoreCase = true)) {
            val videoId = uri.rawQuery.orEmpty()
                .split('&')
                .firstOrNull { it.startsWith("v=") }
                ?.substringAfter("v=")
                .orEmpty()
            return videoId.length >= 6
        }

        val parts = path.split('/').filter { it.isNotBlank() }
        return parts.size >= 2 && parts.first().lowercase() in setOf("shorts", "live") && parts[1].length >= 6
    }

    private fun canonicalizeUrl(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val uri = URI(value.trim())
            val host = uri.host.orEmpty().lowercase()
            if (host == "youtu.be" || host.endsWith("youtube.com")) {
                val query = uri.rawQuery.orEmpty()
                val videoId = when {
                    host == "youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
                    else -> query.split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=").orEmpty()
                }
                if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else value.trim()
            } else {
                val path = uri.path.orEmpty().ifBlank { "/" }
                URI(uri.scheme ?: "https", uri.userInfo, uri.host, uri.port, path, null, null)
                    .toString()
                    .trimEnd('/')
            }
        }.getOrDefault(value.trim())
    }

    private fun canonicalKey(value: String): String = canonicalizeUrl(value)
        .lowercase()
        .trimEnd('/')

    private fun samePage(first: String, second: String): Boolean = canonicalKey(first) == canonicalKey(second)

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

    private fun phraseMatches(text: String, phrase: String): Boolean =
        MediaTextMatcher.matches(text, phrase)

    private fun usefulTitle(value: String): Boolean {
        if (value.length < 8) return false
        val normalized = normalize(value)
        if (normalized in GENERIC_TITLES) return false
        if (normalized.startsWith("todos os videos")) return false
        if (normalized.startsWith("ultimos videos")) return false
        if (normalized.startsWith("mais videos")) return false
        return true
    }

    private fun isGenericSummary(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.startsWith("busca por") || normalized in GENERIC_TITLES
    }

    private fun cleanText(value: String): String = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun compact(value: String): String = normalize(value).replace(" ", "")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private const val MAX_RESOLVED_PER_QUERY = 8
        private const val GLOBOPLAY_EDITION_PAGES_TO_EXPAND = 2
        private const val MAX_FALLBACK_ITEMS = 100
        private val GENERIC_TITLES = setOf(
            "videos", "video", "todos os videos", "todos videos", "ultimos videos", "mais videos",
            "ver videos", "ver todos os videos", "ao vivo", "assistir ao vivo", "carregar mais", "ver mais", "ver tudo"
        )
        private val GENERIC_SLUGS = setOf("videos", "video", "ao vivo", "todos os videos", "ultimos videos")
        private val GENERIC_PATHS = setOf(
            "/videos", "/video", "/ao-vivo", "/busca", "/search", "/categorias/jornalismo"
        )
        private val DEFAULT_TERMS = listOf(
            "Marinha do Brasil", "Capitania dos Portos", "Distrito Naval", "NAM Atlântico",
            "Cisne Branco", "Fragata Marinha do Brasil", "Navio-Patrulha Marinha", "Programa Nuclear da Marinha"
        )
    }
}

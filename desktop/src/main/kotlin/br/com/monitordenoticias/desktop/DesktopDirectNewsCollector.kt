package br.com.monitordenoticias.desktop

import br.com.monitordenoticias.android.Demand
import br.com.monitordenoticias.android.MediaSource
import br.com.monitordenoticias.android.News
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Segunda camada de descoberta exclusiva do Windows.
 *
 * O motor compartilhado continua sendo a busca principal. Este coletor entra
 * como cobertura complementar para fontes que possuam rota direta configurada,
 * reduzindo a dependência da indexação/rotulagem do Google Notícias.
 */
class DesktopDirectNewsCollector {
    data class Diagnostics(
        val sourcesScanned: Int = 0,
        val listingPages: Int = 0,
        val candidateLinks: Int = 0,
        val candidateTermMatches: Int = 0,
        val articlePages: Int = 0,
        val accepted: Int = 0,
        val errors: Int = 0,
        val sourceNames: List<String> = emptyList()
    )

    data class Result(
        val items: List<News>,
        val diagnostics: Diagnostics
    )

    private data class Route(
        val sourceId: String,
        val listingUrls: List<String>,
        val hosts: Set<String>
    )

    private data class Candidate(
        val title: String,
        val snippet: String,
        val link: String
    )

    fun collect(
        selectedSources: List<MediaSource>,
        searchAllSources: Boolean,
        terms: List<String>,
        demands: List<Demand>,
        from: Long,
        to: Long,
        capturedAt: Long = System.currentTimeMillis()
    ): Result {
        val sources = if (searchAllSources) {
            ROUTES.mapNotNull { route -> DesktopSourceCatalog.byId[route.sourceId] }
        } else {
            selectedSources.filter { source -> ROUTES.any { it.sourceId == source.id } }
        }.distinctBy { it.id }

        if (sources.isEmpty()) return Result(emptyList(), Diagnostics())

        val accepted = linkedMapOf<String, News>()
        var listingPages = 0
        var candidateLinks = 0
        var candidateTermMatches = 0
        var articlePages = 0
        var errors = 0

        sources.forEach { source ->
            val route = ROUTES.firstOrNull { it.sourceId == source.id } ?: return@forEach
            val candidates = linkedMapOf<String, Candidate>()

            route.listingUrls.forEach { listingUrl ->
                val listing = runCatching { fetch(listingUrl) }.getOrElse {
                    errors++
                    return@forEach
                }
                listingPages++

                listing.select("a[href]").forEach { anchor ->
                    val link = absoluteUrl(listingUrl, anchor.absUrl("href").ifBlank { anchor.attr("href") })
                        ?: return@forEach
                    if (!hostAllowed(link, route.hosts) || !looksLikeArticle(link)) return@forEach

                    val title = sequenceOf(
                        anchor.attr("aria-label"),
                        anchor.attr("title"),
                        anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                        anchor.text()
                    ).map(::clean).firstOrNull(::usefulTitle).orEmpty()
                    if (!usefulTitle(title)) return@forEach

                    val snippet = clean(anchor.parent()?.text().orEmpty())
                        .removePrefix(title)
                        .trim()
                        .take(800)
                    candidates.putIfAbsent(canonical(link), Candidate(title, snippet, link))
                }
            }

            candidateLinks += candidates.size

            candidates.values.take(MAX_CANDIDATES_PER_SOURCE).forEach { candidate ->
                val listingBody = "${candidate.title} ${candidate.snippet}"
                val listingTerms = terms.filter { subjectMatches(listingBody, it) }
                val listingDemand = demands.firstOrNull { demand ->
                    vehicleMatches(source, demand.vehicle) && subjectMatches(listingBody, demand.subject)
                }

                // Evita abrir dezenas de matérias irrelevantes. Se título/trecho já
                // contiver termo ou demanda, fazemos a validação final na página.
                if (listingTerms.isEmpty() && listingDemand == null) return@forEach
                candidateTermMatches++

                val article = runCatching { fetch(candidate.link) }.getOrElse {
                    errors++
                    return@forEach
                }
                articlePages++

                val title = extractTitle(article).takeIf(::usefulTitle) ?: candidate.title
                val snippet = extractDescription(article).ifBlank { candidate.snippet }
                val body = "$title $snippet"
                val matchedTerms = terms.filter { subjectMatches(body, it) }
                val matchedDemand = demands.firstOrNull { demand ->
                    vehicleMatches(source, demand.vehicle) && subjectMatches(body, demand.subject)
                }
                if (matchedTerms.isEmpty() && matchedDemand == null) return@forEach

                val publishedAt = parsePublishedAt(article, capturedAt) ?: capturedAt
                if (publishedAt !in from..to) return@forEach

                val item = News(
                    title = title.take(300),
                    source = source.name,
                    date = publishedAt,
                    link = candidate.link,
                    snippet = snippet.take(800),
                    important = matchedDemand != null,
                    demand = matchedDemand != null,
                    matchedTerm = matchedTerms.distinct().joinToString(", "),
                    matchedDemand = matchedDemand?.let { "${it.vehicle} • ${it.subject}" }.orEmpty(),
                    capturedAt = capturedAt
                )
                accepted[canonical(item.link)] = item
            }
        }

        val items = accepted.values.sortedByDescending { it.date }
        return Result(
            items,
            Diagnostics(
                sourcesScanned = sources.size,
                listingPages = listingPages,
                candidateLinks = candidateLinks,
                candidateTermMatches = candidateTermMatches,
                articlePages = articlePages,
                accepted = items.size,
                errors = errors,
                sourceNames = sources.map { it.name }
            )
        )
    }

    private fun fetch(url: String): Document = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.6")
        .timeout(REQUEST_TIMEOUT_MS)
        .maxBodySize(MAX_BODY_BYTES)
        .followRedirects(true)
        .get()

    private fun extractTitle(doc: Document): String = sequenceOf(
        doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty(),
        doc.selectFirst("meta[name=twitter:title]")?.attr("content").orEmpty(),
        doc.selectFirst("h1")?.text().orEmpty(),
        doc.title()
    ).map(::clean).firstOrNull(::usefulTitle).orEmpty()

    private fun extractDescription(doc: Document): String = sequenceOf(
        doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
        doc.selectFirst("meta[name=twitter:description]")?.attr("content").orEmpty(),
        doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
    ).map(::clean).firstOrNull { it.isNotBlank() }.orEmpty()

    private fun parsePublishedAt(doc: Document, capturedAt: Long): Long? {
        val candidates = buildList {
            doc.select("meta[property=article:published_time], meta[name=date], meta[itemprop=datePublished], time[datetime]")
                .forEach { element ->
                    add(element.attr("content"))
                    add(element.attr("datetime"))
                }
            DATE_PUBLISHED_REGEX.findAll(doc.html()).take(12).forEach { add(it.groupValues[1]) }
        }
        return candidates.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull(::parseInstant)
            .firstOrNull { it in 1..(capturedAt + FUTURE_TOLERANCE_MS) }
            ?.coerceAtMost(capturedAt)
    }

    private fun parseInstant(value: String): Long? {
        val cleanValue = value.trim()
        return runCatching { Instant.parse(cleanValue).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(cleanValue).toInstant().toEpochMilli() }.getOrNull()
            ?: LOCAL_DATE_TIME_FORMATS.asSequence().mapNotNull { format ->
                runCatching {
                    LocalDateTime.parse(cleanValue, format)
                        .atZone(ZoneId.of("America/Sao_Paulo"))
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull()
            }.firstOrNull()
    }

    private fun vehicleMatches(source: MediaSource, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val wanted = normalize(vehicle)
        val wantedCompact = compact(vehicle)
        return (listOf(source.name) + source.aliases).any { candidate ->
            normalize(candidate) == wanted || compact(candidate) == wantedCompact
        }
    }

    private fun subjectMatches(text: String, subject: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(subject)
        if (wanted.isBlank()) return true
        if (" $haystack ".contains(" $wanted ")) return true
        val hayTokens = haystack.split(' ').filter(String::isNotBlank).toSet()
        val wantedTokens = wanted.split(' ').filter(String::isNotBlank)
        if (wantedTokens.size == 1) return wantedTokens.first() in hayTokens
        val meaningful = wantedTokens.filter { it.length >= 3 && it !in STOP_WORDS }
        return meaningful.isNotEmpty() && meaningful.all { it in hayTokens }
    }

    private fun hostAllowed(url: String, hosts: Set<String>): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase().removePrefix("www.") }.getOrDefault("")
        return host.isNotBlank() && hosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
    }

    private fun looksLikeArticle(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        if (path.isBlank() || path == "/") return false
        if (BLOCKED_PATH_PARTS.any(path::contains)) return false

        val segments = path.trim('/').split('/').filter(String::isNotBlank)
        if (segments.size >= 2) return true
        val slug = segments.firstOrNull().orEmpty()
        return slug.length >= 28 && '-' in slug
    }

    private fun usefulTitle(value: String): Boolean {
        val text = clean(value)
        if (text.length < 18) return false
        return text.count(Char::isLetterOrDigit) >= 12
    }

    private fun absoluteUrl(base: String, value: String): String? = runCatching {
        val resolved = URI(base).resolve(value.trim()).toString()
        resolved.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }.getOrNull()

    private fun canonical(value: String): String = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: "https"
        val host = uri.host?.lowercase()?.removePrefix("www.")
            ?: return@runCatching value.substringBefore('?').substringBefore('#')
        val path = uri.path.orEmpty().trimEnd('/').ifBlank { "/" }
        "$scheme://$host$path"
    }.getOrDefault(value.substringBefore('?').substringBefore('#').trimEnd('/'))

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun compact(value: String): String = normalize(value).replace(" ", "")
    private fun clean(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/152 Safari/537.36 MonitorNoticiasWindows/4.0.3"
        private const val REQUEST_TIMEOUT_MS = 12_000
        private const val MAX_BODY_BYTES = 4_000_000
        private const val MAX_CANDIDATES_PER_SOURCE = 100
        private const val FUTURE_TOLERANCE_MS = 10L * 60L * 1000L

        /**
         * Rotas confirmadas para cobertura complementar. Uma falha em qualquer
         * rota é isolada e aparece no diagnóstico; não interrompe a busca Google.
         */
        private val ROUTES = listOf(
            Route(
                sourceId = "pe-folha-de-pernambuco",
                listingUrls = listOf(
                    "https://www.folhape.com.br/",
                    "https://www.folhape.com.br/economia/"
                ),
                hosts = setOf("folhape.com.br")
            ),
            Route(
                sourceId = "especializada-defesa-em-foco",
                listingUrls = listOf("https://www.defesaemfoco.com.br/"),
                hosts = setOf("defesaemfoco.com.br")
            ),
            Route(
                sourceId = "especializada-defesa-aerea-naval",
                listingUrls = listOf(
                    "https://www.defesaaereanaval.com.br/",
                    "https://www.defesaaereanaval.com.br/naval/"
                ),
                hosts = setOf("defesaaereanaval.com.br")
            ),
            Route(
                sourceId = "especializada-defesanet",
                listingUrls = listOf("https://www.defesanet.com.br/"),
                hosts = setOf("defesanet.com.br")
            ),
            Route(
                sourceId = "especializada-tecnodefesa",
                listingUrls = listOf(
                    "https://tecnodefesa.com.br/",
                    "https://tecnodefesa.com.br/categoria/marinha/"
                ),
                hosts = setOf("tecnodefesa.com.br")
            ),
            Route(
                sourceId = "especializada-zona-militar",
                listingUrls = listOf("https://www.zona-militar.com/pt/"),
                hosts = setOf("zona-militar.com")
            ),
            Route(
                sourceId = "especializada-click-petroleo-gas",
                listingUrls = listOf("https://clickpetroleoegas.com.br/"),
                hosts = setOf("clickpetroleoegas.com.br")
            ),
            Route(
                sourceId = "especializada-poder-naval",
                listingUrls = listOf("https://www.naval.com.br/"),
                hosts = setOf("naval.com.br")
            ),
            Route(
                sourceId = "especializada-gbn-news",
                listingUrls = listOf("https://www.gbnnews.com.br/"),
                hosts = setOf("gbnnews.com.br")
            )
        )

        private val DATE_PUBLISHED_REGEX = Regex(
            "\\\"datePublished\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            RegexOption.IGNORE_CASE
        )

        private val LOCAL_DATE_TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        )

        private val BLOCKED_PATH_PARTS = listOf(
            "/busca", "/search", "/login", "/assine", "/tag/", "/autor/", "/author/",
            "/categoria/", "/category/", "/feed", "/wp-json", "/contato", "/sobre"
        )

        private val STOP_WORDS = setOf(
            "de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os"
        )
    }
}

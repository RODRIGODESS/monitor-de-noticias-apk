package br.com.monitordenoticias.android

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Coleta recente do Globoplay a partir da página real do programa/telejornal.
 *
 * Estratégia:
 * 1. descobre dinamicamente a URL /<programa>/t/<token>/ a partir da busca do Globoplay;
 * 2. acessa /cenas/, que corresponde à aba "Trechos";
 * 3. extrai somente links diretos /v/<id> com o título do card;
 * 4. se a busca não expuser a página do programa, usa até dois vídeos como sementes
 *    para descobrir o /t/<token>/ e tenta novamente.
 *
 * O cruzamento com Termos e Demandas continua no VideoRepository, localmente.
 */
class GloboplayTrechosCollector {

    fun collect(
        source: VideoSource,
        capturedAt: Long,
        onError: () -> Unit
    ): List<VideoItem> {
        if (source.searchPrefix.isBlank()) return emptyList()

        val programPages = linkedMapOf<String, Int>()
        val directLandingProgram = normalizeProgramPage(source.landingUrl)
        if (directLandingProgram != null) {
            programPages[directLandingProgram] = Int.MAX_VALUE
        } else {
            val discoveryUrl = buildDiscoveryUrl(source)
            val discoveryDoc = runCatching { fetchDocument(discoveryUrl) }
                .onFailure { onError() }
                .getOrNull()

            if (discoveryDoc != null) {
                extractProgramPages(source, discoveryDoc, discoveryUrl)
                    .forEach { (url, score) ->
                        programPages[url] = maxOf(programPages[url] ?: Int.MIN_VALUE, score)
                    }

                if (programPages.isEmpty()) {
                    // Algumas versões da busca entregam apenas cards /v/<id> no HTML.
                    // Esses vídeos servem como sementes para descobrir o link do programa.
                    extractDirectVideoLinks(discoveryDoc, discoveryUrl)
                        .take(MAX_SEED_VIDEOS)
                        .forEach { seed ->
                            val seedDoc = runCatching { fetchDocument(seed) }
                                .onFailure { onError() }
                                .getOrNull()
                                ?: return@forEach
                            extractProgramPages(source, seedDoc, seed)
                                .forEach { (url, score) ->
                                    programPages[url] = maxOf(programPages[url] ?: Int.MIN_VALUE, score)
                                }
                        }
                }
            }
        }

        if (programPages.isEmpty()) return emptyList()

        val out = linkedMapOf<String, VideoItem>()
        programPages.entries
            .sortedByDescending { it.value }
            .take(MAX_PROGRAM_PAGES_PER_SOURCE)
            .forEach { (programPage, _) ->
                val scenesUrl = programPage.trimEnd('/') + "/cenas/"
                val scenesDoc = runCatching { fetchDocument(scenesUrl) }
                    .onFailure { onError() }
                    .getOrNull()
                    ?: return@forEach

                extractTrechos(source, scenesDoc, scenesUrl, capturedAt).forEach { item ->
                    out.putIfAbsent(canonicalKey(item.link), item)
                }
            }

        return out.values.take(MAX_TRECHOS_PER_SOURCE)
    }

    private fun buildDiscoveryUrl(source: VideoSource): String {
        if (source.searchUrlTemplate.isBlank()) return source.landingUrl

        val stateQualifier = source.state
            .trim()
            .takeIf { it.length == 2 && !it.equals("BR", ignoreCase = true) }
            .orEmpty()
        val query = listOf(source.searchPrefix.trim(), stateQualifier)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return source.searchUrlTemplate.replace("{query}", URLEncoder.encode(query, "UTF-8"))
    }

    private fun fetchDocument(url: String): Document = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .referrer("https://www.google.com/")
        .timeout(REQUEST_TIMEOUT_MS)
        .followRedirects(true)
        .get()

    private fun extractProgramPages(
        source: VideoSource,
        doc: Document,
        baseUrl: String
    ): List<Pair<String, Int>> {
        val found = linkedMapOf<String, Int>()

        doc.select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { resolveUrl(baseUrl, anchor.attr("href")) }
            val programPage = normalizeProgramPage(href) ?: return@forEach
            val score = programPageScore(source, programPage, anchor.text())
            if (score > 0) found[programPage] = maxOf(found[programPage] ?: Int.MIN_VALUE, score)
        }

        val html = normalizeEmbedded(doc.html())
        PROGRAM_LINK_REGEX.findAll(html).take(MAX_PROGRAM_LINKS_IN_HTML).forEach { match ->
            val programPage = "https://globoplay.globo.com/${match.groupValues[1]}/t/${match.groupValues[2]}"
            val score = programPageScore(source, programPage, "")
            if (score > 0) found[programPage] = maxOf(found[programPage] ?: Int.MIN_VALUE, score)
        }

        return found.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    private fun extractDirectVideoLinks(doc: Document, baseUrl: String): List<String> {
        val out = linkedSetOf<String>()
        doc.select("a[href]").forEach { anchor ->
            val absolute = anchor.absUrl("href").ifBlank { resolveUrl(baseUrl, anchor.attr("href")) }
            normalizeDirectVideoUrl(absolute)?.let(out::add)
        }

        val html = normalizeEmbedded(doc.html())
        VIDEO_LINK_REGEX.findAll(html).take(MAX_VIDEO_LINKS_IN_HTML).forEach { match ->
            out += "https://globoplay.globo.com/v/${match.groupValues[1]}"
        }
        return out.toList()
    }

    private fun extractTrechos(
        source: VideoSource,
        doc: Document,
        pageUrl: String,
        capturedAt: Long
    ): List<VideoItem> {
        val out = linkedMapOf<String, VideoItem>()

        doc.select("a[href]").forEach { anchor ->
            val absolute = anchor.absUrl("href").ifBlank { resolveUrl(pageUrl, anchor.attr("href")) }
            val direct = normalizeDirectVideoUrl(absolute) ?: return@forEach

            val rawTitle = sequenceOf(
                anchor.attr("aria-label"),
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                anchor.text()
            ).map(::cleanText).firstOrNull { it.isNotBlank() }.orEmpty()

            val title = cleanTrechoTitle(rawTitle, source.searchPrefix)
            if (!usefulTitle(title)) return@forEach

            val parentText = cleanText(anchor.parent()?.text().orEmpty())
            val contextual = parentText
                .takeIf { it.isNotBlank() && normalize(it) != normalize(rawTitle) && it.length <= 360 }
                ?.let { cleanTrechoTitle(it, source.searchPrefix) }
                .orEmpty()
            val summary = listOf(source.searchPrefix, contextual)
                .filter { it.isNotBlank() && normalize(it) != normalize(title) }
                .distinctBy(::normalize)
                .joinToString(" • ")
                .take(420)

            out.putIfAbsent(
                canonicalKey(direct),
                VideoItem(
                    title = title.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = capturedAt,
                    link = direct,
                    summary = summary,
                    capturedAt = capturedAt
                )
            )
        }

        // Fallback para cards injetados por JSON/JavaScript. Sem título útil eles não
        // entram aqui: o objetivo desta estratégia é justamente usar os títulos de Trechos
        // para filtrar localmente antes de abrir páginas individuais.
        return out.values.take(MAX_TRECHOS_PER_SOURCE)
    }

    private fun normalizeProgramPage(value: String): String? {
        if (value.isBlank()) return null
        val normalized = normalizeEmbedded(value)
        val match = PROGRAM_LINK_REGEX.find(normalized) ?: return null
        return "https://globoplay.globo.com/${match.groupValues[1]}/t/${match.groupValues[2]}"
    }

    private fun normalizeDirectVideoUrl(value: String): String? {
        if (value.isBlank()) return null
        val normalized = normalizeEmbedded(value)
        val match = VIDEO_LINK_REGEX.find(normalized) ?: return null
        return "https://globoplay.globo.com/v/${match.groupValues[1]}"
    }

    private fun programPageScore(source: VideoSource, url: String, label: String): Int {
        val candidate = normalize("$url $label")
        val desired = normalize(source.searchPrefix)
        if (desired.isBlank()) return 0

        val candidateTokens = candidate.split(' ').filter { it.isNotBlank() }.toSet()
        val desiredTokens = desired.split(' ')
            .filter { it.length >= 2 && it !in STOP_WORDS }
        if (desiredTokens.isEmpty()) return 0

        var score = desiredTokens.count { it in candidateTokens } * 8
        if (desiredTokens.all { it in candidateTokens }) score += 60
        if (candidate.contains(desired)) score += 30

        val state = normalize(source.state)
        if (state.length == 2 && state in candidateTokens) score += 6

        source.aliases.take(8).forEach { alias ->
            val aliasTokens = normalize(alias).split(' ')
                .filter { it.length >= 3 && it !in STOP_WORDS && it !in GENERIC_ALIAS_TOKENS }
            if (aliasTokens.isNotEmpty() && aliasTokens.all { it in candidateTokens }) score += 4
        }
        return score
    }

    private fun cleanTrechoTitle(value: String, program: String): String {
        var title = cleanText(value).replace(DURATION_PREFIX_REGEX, "").trim(' ', '-', '•', '|')
        if (program.isNotBlank() && title.endsWith(program, ignoreCase = true)) {
            title = title.dropLast(program.length).trim(' ', '-', '•', '|')
        }
        return title
    }

    private fun usefulTitle(value: String): Boolean {
        if (value.length < 6) return false
        val normalized = normalize(value)
        return normalized !in GENERIC_TITLES &&
            !normalized.startsWith("edicao de") &&
            !normalized.startsWith("mais vistos")
    }

    private fun canonicalKey(value: String): String = value.lowercase().trim().trimEnd('/')

    private fun resolveUrl(base: String, value: String): String {
        if (value.isBlank()) return ""
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    private fun cleanText(value: String): String = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeEmbedded(value: String): String = value
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u003A", ":", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003D", "=", ignoreCase = true)
        .replace("\\\"", "\"")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) MonitorNoticias/3.0.2"
        private const val REQUEST_TIMEOUT_MS = 14_000
        private const val MAX_PROGRAM_PAGES_PER_SOURCE = 2
        private const val MAX_SEED_VIDEOS = 2
        private const val MAX_TRECHOS_PER_SOURCE = 48
        private const val MAX_PROGRAM_LINKS_IN_HTML = 80
        private const val MAX_VIDEO_LINKS_IN_HTML = 80

        private val PROGRAM_LINK_REGEX = Regex(
            "(?:https?://globoplay\\.globo\\.com)?/([a-z0-9-]+)/t/([A-Za-z0-9_-]{6,})/?",
            RegexOption.IGNORE_CASE
        )
        private val VIDEO_LINK_REGEX = Regex(
            "(?:https?://globoplay\\.globo\\.com)?/v/([0-9]{5,})(?:/|\\?|$)",
            RegexOption.IGNORE_CASE
        )
        private val DURATION_PREFIX_REGEX = Regex(
            "^(?:\\d+\\s*(?:h|min|seg|s)\\s*)+",
            RegexOption.IGNORE_CASE
        )
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
        private val GENERIC_ALIAS_TOKENS = setOf("globo", "globoplay", "telejornal", "regional", "jornalismo")
        private val GENERIC_TITLES = setOf(
            "videos", "video", "trechos", "edicoes", "mais videos", "ver mais", "mostrar mais",
            "assistir agora", "detalhes", "similares", "extras"
        )
    }
}

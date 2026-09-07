package br.com.monitordenoticias.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.Normalizer
import java.time.Instant

/**
 * v3.0.1: scanner híbrido.
 *
 * O Globoplay /busca/ não lista de forma confiável os trechos recentes. Para
 * telejornais regionais, usamos os índices oficiais de vídeos do G1 do próprio
 * programa como fonte de descoberta e convertemos o ID para o link direto
 * https://globoplay.globo.com/v/<id>/. O restante das fontes continua usando o
 * coletor v3.0 já validado.
 *
 * Isso elimina o produto "fonte x todos os termos" para os telejornais
 * regionais: cada telejornal vira uma unidade de varredura e os Termos/Demandas
 * são aplicados localmente aos candidatos encontrados.
 */
class VideoRepositoryV301(
    private val context: Context,
    private val db: VideoDb
) {
    suspend fun search(sources: List<VideoSource>): VideoSearchResult =
        searchInternal(sources, null, null, null)

    suspend fun searchProgressive(
        sources: List<VideoSource>,
        onUpdate: (VideoSearchUpdate) -> Unit
    ): VideoSearchResult = searchInternal(sources, null, null, onUpdate)

    suspend fun searchPeriod(sources: List<VideoSource>, from: Long, to: Long): VideoSearchResult =
        searchInternal(sources, from, to, null)

    suspend fun searchPeriodProgressive(
        sources: List<VideoSource>,
        from: Long,
        to: Long,
        onUpdate: (VideoSearchUpdate) -> Unit
    ): VideoSearchResult = searchInternal(sources, from, to, onUpdate)

    private suspend fun searchInternal(
        sources: List<VideoSource>,
        from: Long?,
        to: Long?,
        onUpdate: ((VideoSearchUpdate) -> Unit)?
    ): VideoSearchResult = withContext(Dispatchers.IO) {
        val regionalGlobo = sources.filter(::isRegionalGloboplay)
        val otherSources = sources.filterNot(::isRegionalGloboplay)
        val newsDb = NewsDb(context)
        try {
            val terms = newsDb.listTerms().ifEmpty { DEFAULT_TERMS }
                .map(String::trim).filter(String::isNotBlank).distinctBy(::normalize)
            val demands = newsDb.listDemands().filter { it.active }
            val startedAt = System.currentTimeMillis()
            val capturedAt = startedAt
            val collected = linkedMapOf<String, VideoItem>()
            val newKeys = linkedSetOf<String>()
            var errors = 0
            var completed = 0
            val total = regionalGlobo.size + if (otherSources.isNotEmpty()) 1 else 0

            fun progress(source: String, query: String, active: Boolean = true) = LiveSearchProgress(
                active = active,
                kind = if (from != null || to != null) "Vídeos • Período" else "Vídeos",
                startedAt = startedAt,
                finishedAt = if (active) 0L else System.currentTimeMillis(),
                completed = completed,
                total = total,
                currentSource = source,
                currentQuery = query,
                found = collected.size,
                newCount = newKeys.size,
                errors = errors
            )

            fun accept(item: VideoItem, sourceLabel: String, queryLabel: String) {
                if (!item.relevant || !inPeriod(item, from, to)) return
                val key = canonicalKey(item.link)
                val previous = collected[key]
                val merged = if (previous == null) item else mergeVideo(previous, item)
                collected[key] = merged
                val inserted = db.insert(listOf(merged))
                if (inserted.isNotEmpty()) newKeys += key
                onUpdate?.invoke(VideoSearchUpdate(progress(sourceLabel, queryLabel), listOf(merged)))
            }

            onUpdate?.invoke(VideoSearchUpdate(progress("Preparando", "")))

            regionalGlobo.forEach { source ->
                val queryLabel = "índice oficial do telejornal"
                onUpdate?.invoke(VideoSearchUpdate(progress(source.name, queryLabel)))
                val scan = runCatching {
                    scanRegionalGloboplay(source, terms, demands, capturedAt, from, to)
                }.getOrElse {
                    RegionalScan(emptyList(), failed = true)
                }
                if (scan.failed) errors++
                scan.items.forEach { accept(it, source.name, queryLabel) }
                completed++
                onUpdate?.invoke(VideoSearchUpdate(progress(source.name, queryLabel)))
            }

            if (otherSources.isNotEmpty()) {
                onUpdate?.invoke(VideoSearchUpdate(progress("Outras fontes", "portais e YouTube")))
                val legacy = VideoRepository(context, db)
                val result = legacy.searchProgressive(otherSources) { update ->
                    update.items.forEach { item ->
                        val key = canonicalKey(item.link)
                        collected[key] = collected[key]?.let { mergeVideo(it, item) } ?: item
                    }
                    onUpdate?.invoke(
                        VideoSearchUpdate(
                            progress(
                                update.progress.currentSource.ifBlank { "Outras fontes" },
                                update.progress.currentQuery.ifBlank { "portais e YouTube" }
                            ),
                            update.items
                        )
                    )
                }
                errors += result.errors
                result.items.forEach { item ->
                    val key = canonicalKey(item.link)
                    collected[key] = collected[key]?.let { mergeVideo(it, item) } ?: item
                }
                // O coletor legado já inseriu esses itens no banco; usamos a
                // contagem final dele sem tentar reinserir para calcular "novos".
                repeat(result.newCount) { index -> newKeys += "legacy-$index-${System.nanoTime()}" }
                completed++
                onUpdate?.invoke(VideoSearchUpdate(progress("Outras fontes", "concluído")))
            }

            val items = collected.values
                .filter { it.relevant && inPeriod(it, from, to) }
                .distinctBy { canonicalKey(it.link) }
                .sortedByDescending { it.publishedAt }

            onUpdate?.invoke(
                VideoSearchUpdate(
                    progress("Concluído", "", active = false).copy(
                        completed = total,
                        found = items.size,
                        newCount = newKeys.size
                    )
                )
            )

            VideoSearchResult(
                items = items,
                foundCount = items.size,
                newCount = newKeys.size,
                relevantCount = items.size,
                newRelevantCount = newKeys.size,
                errors = errors
            )
        } finally {
            newsDb.close()
        }
    }

    private data class RegionalScan(val items: List<VideoItem>, val failed: Boolean)
    private data class G1Candidate(val videoId: String, val title: String, val context: String)

    private fun scanRegionalGloboplay(
        source: VideoSource,
        terms: List<String>,
        demands: List<Demand>,
        capturedAt: Long,
        from: Long?,
        to: Long?
    ): RegionalScan {
        val baseDoc = findG1ProgramDocument(source) ?: return RegionalScan(emptyList(), true)
        val docs = mutableListOf(baseDoc)

        // O G1 pré-carrega vários blocos no índice e expõe um único endpoint
        // "index/feed/pagina-N.ghtml" para a continuação. Um único feed extra
        // cobre trechos mais cedo do mesmo jornal sem varrer páginas infinitas.
        val continuation = baseDoc.select("a[href*=/index/feed/pagina-]")
            .map { it.absUrl("href") }
            .firstOrNull { it.isNotBlank() }
        if (!continuation.isNullOrBlank()) {
            runCatching { fetchDoc(continuation) }.getOrNull()?.let(docs::add)
        }

        val candidates = docs.flatMap(::extractG1Candidates)
            .distinctBy { it.videoId }

        if (candidates.isEmpty()) return RegionalScan(emptyList(), true)

        val results = mutableListOf<VideoItem>()
        val visibleMatches = candidates.filter { candidate ->
            candidateMatches(candidate, terms, demands, source)
        }

        // Além dos candidatos cujo título/contexto já bate, abrimos poucos dos
        // mais recentes para capturar termos que existam só em descrição/tag.
        val toResolve = (visibleMatches + candidates.take(DEEP_METADATA_FALLBACK))
            .distinctBy { it.videoId }
            .take(MAX_DIRECT_RESOLVES_PER_SOURCE)

        toResolve.forEach { candidate ->
            val resolved = runCatching {
                resolveGloboplayVideo(source, candidate.videoId, capturedAt)
            }.getOrNull() ?: return@forEach
            if (!belongsToSource(resolved.title, source)) return@forEach
            if (!inPeriod(resolved, from, to)) return@forEach

            val body = "${resolved.title} ${resolved.summary}"
            val matchedTerms = terms.filter { termMatches(body, it) }
            val demand = demands.firstOrNull { demand ->
                sourceMatchesDemand(source, demand.vehicle) && termMatches(body, demand.subject)
            }
            val item = resolved.copy(
                matchedTerm = matchedTerms.joinToString(", "),
                matchedDemand = demand?.let { "${it.vehicle} • ${it.subject}" }.orEmpty(),
                capturedAt = capturedAt
            )
            if (item.relevant) results += item
        }

        return RegionalScan(results.distinctBy { canonicalKey(it.link) }, failed = false)
    }

    private fun findG1ProgramDocument(source: VideoSource): Document? {
        val root = G1_STATE_ROOT[source.state] ?: return null
        val slugs = programSlugCandidates(source)
        for (slug in slugs.take(MAX_G1_SLUG_ATTEMPTS)) {
            val url = "https://g1.globo.com/$root/videos-$slug/"
            val doc = runCatching { fetchDoc(url) }.getOrNull() ?: continue
            if (extractG1Candidates(doc).isNotEmpty()) return doc
        }
        return null
    }

    private fun programSlugCandidates(source: VideoSource): List<String> {
        val program = source.name.substringAfter("•", source.name).trim()
        val raw = listOf(program) + source.aliases
        val generated = buildList {
            raw.forEach { value ->
                val slug = slugify(value)
                if (slug.isBlank()) return@forEach
                add(slug)
                add(slug.replace("-1a-edicao", "-1-edicao"))
                add(slug.replace("-2a-edicao", "-2-edicao"))
                add(slug.replace("-1-edicao", "-1a-edicao"))
                add(slug.replace("-2-edicao", "-2a-edicao"))
            }
        }
        // URLs do G1 tendem a usar o nome completo (ex.: setv-1-edicao)
        // em vez da sigla curta (ex.: se1).
        return generated.distinct().sortedWith(
            compareByDescending<String> { it.contains("edicao") }
                .thenByDescending { it.count { ch -> ch == '-' } }
        )
    }

    private fun extractG1Candidates(doc: Document): List<G1Candidate> {
        val html = doc.html()
        val out = linkedMapOf<String, G1Candidate>()
        doc.select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val id = G1_VIDEO_ID.find(href)?.groupValues?.getOrNull(1) ?: return@forEach
            val title = sequenceOf(
                anchor.attr("aria-label"), anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(), anchor.text(),
                slugTitle(href)
            ).map(::cleanText).firstOrNull { it.length >= 5 }.orEmpty()
            val pos = html.indexOf(id)
            val nearby = if (pos >= 0) {
                val start = (pos - 900).coerceAtLeast(0)
                val end = (pos + 1200).coerceAtMost(html.length)
                cleanText(Jsoup.parse(html.substring(start, end)).text())
            } else ""
            val parent = cleanText(anchor.parent()?.text().orEmpty())
            out.putIfAbsent(id, G1Candidate(id, title, "$parent $nearby".take(1600)))
        }
        return out.values.toList()
    }

    private fun candidateMatches(
        candidate: G1Candidate,
        terms: List<String>,
        demands: List<Demand>,
        source: VideoSource
    ): Boolean {
        val body = "${candidate.title} ${candidate.context}"
        if (terms.any { termMatches(body, it) }) return true
        return demands.any { sourceMatchesDemand(source, it.vehicle) && termMatches(body, it.subject) }
    }

    private fun resolveGloboplayVideo(source: VideoSource, videoId: String, capturedAt: Long): VideoItem? {
        val url = "https://globoplay.globo.com/v/$videoId/"
        val doc = fetchDoc(url)
        val title = sequenceOf(
            doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=twitter:title]")?.attr("content").orEmpty(),
            doc.selectFirst("h1")?.text().orEmpty(), doc.title()
        ).map(::cleanText).firstOrNull { it.length >= 8 }.orEmpty()
        if (title.isBlank()) return null

        val description = sequenceOf(
            doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty(),
            doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        ).map(::cleanText).firstOrNull { it.isNotBlank() }.orEmpty()
        val tags = extractMetadataTerms(doc)
        val summary = listOf(
            description,
            if (tags.isEmpty()) "" else "Tags: ${tags.take(24).joinToString(", ")}"
        ).filter(String::isNotBlank).joinToString(" • ").take(1100)

        return VideoItem(
            title = title.take(260),
            sourceId = source.id,
            sourceName = source.name,
            publishedAt = parsePublishedAt(doc) ?: capturedAt,
            link = url,
            summary = summary,
            capturedAt = capturedAt
        )
    }

    private fun extractMetadataTerms(doc: Document): List<String> {
        val values = linkedSetOf<String>()
        doc.select("meta[name=keywords], meta[property=article:tag], meta[property=video:tag], meta[name=news_keywords]")
            .forEach { meta -> splitMetadata(meta.attr("content")).forEach(values::add) }
        doc.select("a[href*=/tag/], a[href*=/tags/], [data-tag], [data-tags]").take(50).forEach { element ->
            splitMetadata(element.attr("data-tag")).forEach(values::add)
            splitMetadata(element.attr("data-tags")).forEach(values::add)
            cleanText(element.text()).takeIf { it.length in 2..90 }?.let(values::add)
        }
        val html = doc.html()
        METADATA_JSON.findAll(html).take(60).forEach { match ->
            val value = cleanText(match.groupValues[2].replace("\\\"", "\""))
            splitMetadata(value).forEach(values::add)
        }
        return values.map(::cleanText).filter { it.length in 2..90 }.distinctBy(::normalize).take(50)
    }

    private fun splitMetadata(value: String): List<String> = value
        .replace("[", " ").replace("]", " ").replace("{", " ").replace("}", " ")
        .split(',', ';', '|')
        .map { cleanText(it.trim(' ', '\"', '\'')) }
        .filter { it.length in 2..90 }

    private fun parsePublishedAt(doc: Document): Long? {
        val values = buildList {
            add(doc.selectFirst("meta[property=article:published_time]")?.attr("content").orEmpty())
            add(doc.selectFirst("meta[name=date]")?.attr("content").orEmpty())
            add(doc.selectFirst("time[datetime]")?.attr("datetime").orEmpty())
            DATE_PUBLISHED.find(doc.html())?.groupValues?.getOrNull(1)?.let(::add)
        }.filter(String::isNotBlank)
        values.forEach { raw -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it } }
        return null
    }

    private fun fetchDoc(url: String): Document = Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36 MonitorNoticias/3.0.1")
        .referrer("https://www.google.com/")
        .timeout(18_000)
        .maxBodySize(2_000_000)
        .followRedirects(true)
        .get()

    private fun belongsToSource(title: String, source: VideoSource): Boolean {
        if (source.id.contains("sweep")) return true
        val prefix = title.substringBefore('|').substringBefore(" - ").trim()
        val key = compact(prefix)
        if (key.isBlank()) return true
        val names = listOf(source.name.substringAfter("•", source.name).trim()) + source.aliases
        return names.any { candidate ->
            val c = compact(candidate)
            c.length >= 3 && (key == c || key.contains(c) || c.contains(key))
        }
    }

    private fun termMatches(text: String, term: String): Boolean {
        if (phraseMatches(text, term)) return true
        val wanted = normalize(term)
        val hay = normalize(text)
        // "militares" aparece muitas vezes no metadado, mas o título pode usar
        // "Forças Armadas" ou o nome da Força. Esta equivalência é aplicada só
        // ao conceito militar, não a termos genéricos.
        if (wanted in setOf("militar", "militares")) {
            return MILITARY_EQUIVALENTS.any { phraseMatches(hay, it) }
        }
        return false
    }

    private fun phraseMatches(text: String, phrase: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(phrase)
        if (wanted.isBlank()) return false
        val hayTokens = haystack.split(' ').filter(String::isNotBlank).toSet()
        val wantedTokens = wanted.split(' ').filter(String::isNotBlank)
        if (wantedTokens.isEmpty()) return false
        if (wantedTokens.size == 1) return wantedTokens.first() in hayTokens
        if (" $haystack ".contains(" $wanted ")) return true
        val meaningful = wantedTokens.filter { it.length >= 3 && it !in STOP_WORDS }
        return meaningful.isNotEmpty() && meaningful.all { it in hayTokens }
    }

    private fun sourceMatchesDemand(source: VideoSource, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val wanted = compact(vehicle)
        return (listOf(source.name, source.group) + source.aliases).any { candidate ->
            val actual = compact(candidate)
            actual == wanted || (wanted.length >= 4 && actual.contains(wanted)) ||
                (actual.length >= 4 && wanted.contains(actual))
        }
    }

    private fun isRegionalGloboplay(source: VideoSource): Boolean =
        source.state.isNotBlank() && (source.id.startsWith("globoplay-") || source.landingUrl.contains("globoplay.globo.com"))

    private fun mergeVideo(previous: VideoItem, incoming: VideoItem): VideoItem {
        val terms = (previous.matchedTerm.split(',') + incoming.matchedTerm.split(','))
            .map(String::trim).filter(String::isNotBlank).distinct()
        return incoming.copy(
            matchedTerm = terms.joinToString(", "),
            matchedDemand = incoming.matchedDemand.ifBlank { previous.matchedDemand },
            capturedAt = maxOf(previous.capturedAt, incoming.capturedAt)
        )
    }

    private fun inPeriod(item: VideoItem, from: Long?, to: Long?): Boolean {
        if (from != null && item.publishedAt < from) return false
        if (to != null && item.publishedAt > to) return false
        return true
    }

    private fun canonicalKey(url: String): String = url.substringBefore('?').trimEnd('/').lowercase()

    private fun slugTitle(url: String): String = url.substringAfterLast("/video/")
        .substringBeforeLast('-').replace('-', ' ')

    private fun slugify(value: String): String {
        val prepared = value.lowercase().replace('ª', 'a').replace('º', 'o')
        return Normalizer.normalize(prepared, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    private fun compact(value: String): String = normalize(value).replace(" ", "")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun cleanText(value: String): String = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val MAX_G1_SLUG_ATTEMPTS = 5
        private const val DEEP_METADATA_FALLBACK = 3
        private const val MAX_DIRECT_RESOLVES_PER_SOURCE = 24
        private val G1_VIDEO_ID = Regex("-(\\d{7,9})\\.ghtml(?:[?#].*)?$", RegexOption.IGNORE_CASE)
        private val DATE_PUBLISHED = Regex("\\\"datePublished\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        private val METADATA_JSON = Regex("\\\"(keywords|tags?|subjects?)\\\"\\s*:\\s*\\\"([^\\\"]{2,800})\\\"", RegexOption.IGNORE_CASE)
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
        private val MILITARY_EQUIVALENTS = listOf("forcas armadas", "exercito", "marinha", "aeronautica")
        private val DEFAULT_TERMS = listOf(
            "Marinha do Brasil", "Capitania dos Portos", "Distrito Naval", "NAM Atlântico",
            "Cisne Branco", "Fragata Marinha do Brasil", "Navio-Patrulha Marinha", "Programa Nuclear da Marinha"
        )
        private val G1_STATE_ROOT = mapOf(
            "AC" to "ac/acre", "AP" to "ap/amapa", "AM" to "am/amazonas", "PA" to "pa/para",
            "RO" to "ro/rondonia", "RR" to "rr/roraima", "TO" to "to/tocantins",
            "AL" to "al/alagoas", "BA" to "ba/bahia", "CE" to "ce/ceara", "MA" to "ma/maranhao",
            "PB" to "pb/paraiba", "PE" to "pe/pernambuco", "PI" to "pi/piaui", "RN" to "rn/rio-grande-do-norte",
            "SE" to "se/sergipe", "DF" to "df/distrito-federal", "GO" to "go/goias",
            "MT" to "mt/mato-grosso", "MS" to "ms/mato-grosso-do-sul",
            "ES" to "es/espirito-santo", "MG" to "mg/minas-gerais", "RJ" to "rj/rio-de-janeiro", "SP" to "sp/sao-paulo",
            "PR" to "pr/parana", "SC" to "sc/santa-catarina", "RS" to "rs/rio-grande-do-sul"
        )
    }
}

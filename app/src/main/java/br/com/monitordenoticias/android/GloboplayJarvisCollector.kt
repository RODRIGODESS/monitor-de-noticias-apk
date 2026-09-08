package br.com.monitordenoticias.android

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Camada complementar para os telejornais nacionais do Globoplay.
 *
 * Algumas páginas /cenas/ são preenchidas apenas depois que o JavaScript roda.
 * O Android usa Jsoup e, portanto, não enxerga esses cards recentes no HTML inicial.
 * O Jarvis é o backend usado pelo próprio Globoplay e expõe a busca de vídeos com
 * título, descrição, programa e id direto do vídeo. Fazemos as consultas UMA vez
 * por Termo/Demanda e depois distribuímos localmente os resultados por telejornal.
 */
class GloboplayJarvisCollector {

    data class Outcome(
        val candidatesBySourceId: Map<String, List<VideoItem>>,
        val failedQueries: Int
    )

    fun collect(
        rawQueries: List<String>,
        nationalSources: List<VideoSource>,
        capturedAt: Long
    ): Outcome {
        if (nationalSources.isEmpty()) return Outcome(emptyMap(), 0)

        val queries = expandQueries(rawQueries)
        val buckets = nationalSources.associate { it.id to linkedMapOf<String, VideoItem>() }.toMutableMap()
        var failedQueries = 0

        queries.forEach { query ->
            val response = fetchWithRetry(query) ?: run {
                failedQueries++
                return@forEach
            }

            val resources = response
                .optJSONObject("data")
                ?.optJSONObject("search")
                ?.optJSONObject("videos")
                ?.optJSONArray("resources")
                ?: return@forEach

            for (index in 0 until resources.length()) {
                val video = resources.optJSONObject(index) ?: continue
                val videoId = video.optString("id").trim()
                val headline = video.optString("headline").trim()
                val description = video.optString("description").trim()
                val title = video.optJSONObject("title")
                val programName = title?.optString("headline").orEmpty().trim()
                val originProgramId = title?.optString("originProgramId").orEmpty().trim()

                if (videoId.length < 5 || headline.length < 8 || programName.isBlank()) continue
                val source = nationalSources.firstOrNull { sourceMatchesProgram(it, programName) } ?: continue

                val summary = buildList {
                    if (description.isNotBlank()) add(description)
                    add("Programa: $programName")
                    if (originProgramId.isNotBlank()) add("Programa ID: $originProgramId")
                    add("Descoberto via Globoplay Jarvis")
                }.distinct().joinToString(" • ").take(1400)

                val item = VideoItem(
                    title = headline.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    // A busca do Jarvis não entrega a data nesta coleção. Mantemos 0
                    // para obrigar o repositório a enriquecer a página /v/<id> antes
                    // de aceitar o resultado na janela de 24h/período.
                    publishedAt = 0L,
                    link = "https://globoplay.globo.com/v/$videoId/",
                    summary = summary,
                    capturedAt = capturedAt
                )
                buckets.getValue(source.id).putIfAbsent(videoId, item)
            }
        }

        return Outcome(
            candidatesBySourceId = buckets.mapValues { (_, items) -> items.values.toList() },
            failedQueries = failedQueries
        )
    }

    private fun fetchWithRetry(query: String): JSONObject? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = runCatching { fetch(query) }.getOrNull()
            if (result != null && result.optJSONArray("errors") == null) return result
            if (attempt + 1 < MAX_ATTEMPTS) Thread.sleep(RETRY_DELAY_MS)
        }
        return null
    }

    private fun fetch(query: String): JSONObject {
        val payload = JSONObject()
            .put("operationName", "MonitorGloboplaySearch")
            .put(
                "variables",
                JSONObject()
                    .put("q", query)
                    .put("page", 1)
            )
            .put("query", SEARCH_DOCUMENT)

        val connection = (URL(JARVIS_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("x-platform-id", "web")
            setRequestProperty("x-device-id", "desktop")
            setRequestProperty("x-client-version", "2024.12-5")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299 || body.isBlank()) {
                throw IOException("Jarvis HTTP $status")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun sourceMatchesProgram(source: VideoSource, programName: String): Boolean {
        val program = normalize(programName)
        if (program.isBlank()) return false
        val candidates = listOf(source.name, source.searchPrefix) + source.aliases
        return candidates
            .map(::normalize)
            .filter { it.length >= 4 }
            .any { candidate ->
                program == candidate ||
                    program.startsWith("$candidate ") ||
                    candidate.startsWith("$program ")
            }
    }

    private fun expandQueries(rawQueries: List<String>): List<String> {
        val out = linkedMapOf<String, String>()

        fun add(value: String) {
            val cleaned = value.replace(Regex("\\s+"), " ").trim()
            val key = normalize(cleaned)
            if (cleaned.length >= 3 && key.isNotBlank()) out.putIfAbsent(key, cleaned)
        }

        rawQueries.forEach { raw ->
            val normalized = normalize(raw)
            if (normalized.isBlank()) return@forEach
            val tokens = normalized.split(' ').filter { it.isNotBlank() }
            val tokenSet = tokens.toSet()
            val september7 = "7" in tokenSet && "setembro" in tokenSet

            if (september7) {
                // O backend da Globo mostrou timeout recorrente com "7 de setembro",
                // mas responde de forma estável sem a preposição. Além disso, os cards
                // usam vocabulário diferente (desfiles x comemorações x independência).
                add("7 setembro")
                add("desfiles 7 setembro")
                add("desfiles 7 setembro pais")
                add("comemoracoes 7 setembro")
                add("independencia 7 setembro")
            } else {
                add(raw)
                val compact = tokens.filterNot { it in QUERY_STOP_WORDS }.joinToString(" ")
                if (compact.isNotBlank() && compact != normalized) add(compact)

                // Singular/plural simples para consultas que o serviço às vezes trata
                // de forma muito diferente (ex.: militares -> militar).
                if (tokens.size == 1 && normalized.endsWith("es") && normalized.length >= 7) {
                    add(normalized.dropLast(2))
                } else if (tokens.size == 1 && normalized.endsWith("s") && normalized.length >= 6) {
                    add(normalized.dropLast(1))
                }
            }
        }

        return out.values.take(MAX_QUERIES_PER_SCAN)
    }

    private fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(),
        Normalizer.Form.NFD
    )
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private const val JARVIS_ENDPOINT = "https://cloud-jarvis.globo.com/graphql"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000
        private const val RETRY_DELAY_MS = 350L
        private const val MAX_ATTEMPTS = 2
        private const val MAX_QUERIES_PER_SCAN = 28
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) MonitorNoticias/3.0.9"

        private val QUERY_STOP_WORDS = setOf(
            "de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os"
        )

        private const val SEARCH_DOCUMENT =
            "query MonitorGloboplaySearch(\$q:String!,\$page:Int) { " +
                "search { videos(query:\$q,page:\$page) { " +
                "page nextPage total hasNextPage resources { " +
                "id headline description duration title { headline originProgramId } " +
                "} } } }"
    }
}

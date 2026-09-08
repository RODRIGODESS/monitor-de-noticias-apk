from pathlib import Path


def write(path: str, content: str) -> None:
    Path(path).write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) Globoplay: keep anchors, but also collect cards injected in JSON/JavaScript.
collector_path = Path("app/src/main/java/br/com/monitordenoticias/android/GloboplayTrechosCollector.kt")
collector = collector_path.read_text(encoding="utf-8")
collector = replace_once(
    collector,
    "import java.text.Normalizer\n",
    "import java.text.Normalizer\nimport kotlin.math.abs\n",
    "collector import"
)
start = collector.index("    private fun extractTrechos(")
end = collector.index("    private fun normalizeProgramPage(", start)
new_extract = r'''    private fun extractTrechos(
        source: VideoSource,
        doc: Document,
        pageUrl: String,
        capturedAt: Long
    ): List<VideoItem> {
        val out = linkedMapOf<String, VideoItem>()

        fun addCandidate(direct: String, rawTitle: String, rawSummary: String) {
            val cleanedTitle = cleanTrechoTitle(rawTitle, source.searchPrefix)
            val title = cleanedTitle.takeIf(::usefulTitle)
                ?: "Trecho recente • ${source.searchPrefix.ifBlank { source.name }}"
            val contextual = cleanText(rawSummary)
            val summary = listOf(source.searchPrefix, contextual)
                .filter { it.isNotBlank() && normalize(it) != normalize(title) }
                .distinctBy(::normalize)
                .joinToString(" • ")
                .take(900)

            val candidate = VideoItem(
                title = title.take(220),
                sourceId = source.id,
                sourceName = source.name,
                publishedAt = capturedAt,
                link = direct,
                summary = summary,
                capturedAt = capturedAt
            )
            val key = canonicalKey(direct)
            val previous = out[key]
            val candidateScore = candidateQuality(candidate)
            if (previous == null || candidateScore > candidateQuality(previous)) {
                out[key] = candidate
            }
        }

        // Caminho 1: cards presentes diretamente no HTML.
        doc.select("a[href]").forEach { anchor ->
            val absolute = anchor.absUrl("href").ifBlank { resolveUrl(pageUrl, anchor.attr("href")) }
            val direct = normalizeDirectVideoUrl(absolute) ?: return@forEach

            val rawTitle = sequenceOf(
                anchor.attr("aria-label"),
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
                anchor.text()
            ).map(::cleanText).firstOrNull { it.isNotBlank() }.orEmpty()

            val parentText = cleanText(anchor.parent()?.text().orEmpty())
            addCandidate(direct, rawTitle, parentText)
        }

        // Caminho 2: o Globoplay frequentemente injeta Trechos via JSON/JavaScript.
        // O Jsoup não executa JavaScript, então os cards podem não existir como <a>.
        // Extraímos o /v/<id> do HTML bruto e procuramos título/descrição próximos
        // ao mesmo ID dentro do JSON serializado.
        val html = normalizeEmbedded(doc.html())
        VIDEO_LINK_REGEX.findAll(html).take(MAX_VIDEO_LINKS_IN_HTML).forEach { match ->
            val direct = "https://globoplay.globo.com/v/${match.groupValues[1]}"
            val contextStart = (match.range.first - EMBEDDED_CONTEXT_WINDOW).coerceAtLeast(0)
            val contextEnd = (match.range.last + 1 + EMBEDDED_CONTEXT_WINDOW).coerceAtMost(html.length)
            val context = html.substring(contextStart, contextEnd)
            val center = match.range.first - contextStart

            val embeddedTitle = nearestJsonValue(context, center, EMBEDDED_TITLE_REGEX, 6, 260)
                .takeUnless { normalize(it) == normalize(source.searchPrefix) }
                .orEmpty()
            val embeddedSummary = nearestJsonValue(context, center, EMBEDDED_SUMMARY_REGEX, 8, 900)
            addCandidate(direct, embeddedTitle, embeddedSummary)
        }

        return out.values.take(MAX_TRECHOS_PER_SOURCE)
    }

    private fun candidateQuality(item: VideoItem): Int {
        var score = 0
        if (!item.title.startsWith("Trecho recente •", ignoreCase = true)) score += 30
        if (item.summary.isNotBlank()) score += minOf(item.summary.length / 30, 20)
        return score
    }

    private fun nearestJsonValue(
        context: String,
        center: Int,
        regex: Regex,
        minLength: Int,
        maxLength: Int
    ): String {
        return regex.findAll(context)
            .mapNotNull { match ->
                val value = cleanJsonText(match.groupValues[1])
                if (value.length !in minLength..maxLength) return@mapNotNull null
                if (value.startsWith("http://", true) || value.startsWith("https://", true)) return@mapNotNull null
                if (value.contains("/v/")) return@mapNotNull null
                value to abs(match.range.first - center)
            }
            .minByOrNull { it.second }
            ?.first
            .orEmpty()
    }

    private fun cleanJsonText(value: String): String {
        var decoded = normalizeEmbedded(value)
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
            .replace("\\\\", "\\")
        decoded = UNICODE_ESCAPE_REGEX.replace(decoded) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
        return cleanText(decoded)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
    }

'''
collector = collector[:start] + new_extract + collector[end:]
collector = replace_once(
    collector,
    'private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) MonitorNoticias/3.0.2"',
    'private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) MonitorNoticias/3.0.3"',
    "collector user agent"
)
collector = replace_once(
    collector,
    "        private const val MAX_VIDEO_LINKS_IN_HTML = 80\n",
    "        private const val MAX_VIDEO_LINKS_IN_HTML = 120\n        private const val EMBEDDED_CONTEXT_WINDOW = 1800\n",
    "collector constants"
)
regex_anchor = '''        private val DURATION_PREFIX_REGEX = Regex(\n            "^(?:\\\\d+\\\\s*(?:h|min|seg|s)\\\\s*)+",\n            RegexOption.IGNORE_CASE\n        )\n'''
if regex_anchor not in collector:
    raise SystemExit("collector regex anchor not found")
collector = collector.replace(regex_anchor, regex_anchor + r'''        private val EMBEDDED_TITLE_REGEX = Regex(
            "\\\"(?:title|headline|name|label|episodeTitle)\\\"\\s*:\\s*\\\"([^\\\"]{2,500})\\\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val EMBEDDED_SUMMARY_REGEX = Regex(
            "\\\"(?:description|seoDescription|summary|caption|synopsis)\\\"\\s*:\\s*\\\"([^\\\"]{2,1200})\\\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val UNICODE_ESCAPE_REGEX = Regex("\\\\u([0-9a-fA-F]{4})")
''', 1)
collector_path.write_text(collector, encoding="utf-8")


# 2) Globoplay matching: title/summary hits resolve first; a bounded number of unmatched
# cards are also opened so terms that exist only in detail metadata are not lost.
repo_path = Path("app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt")
repo = repo_path.read_text(encoding="utf-8")
old_loop = r'''                    prioritized.asSequence()
                        .take(resolveLimitFor(source))
                        .forEach { raw ->
                            // A aba Trechos já entrega títulos jornalísticos úteis.
                            // Filtramos localmente antes de abrir /v/<id>, reduzindo requisições e falhas.
                            if (scanMode && isGloboplaySource(source)) {
                                val shallowBody = "${raw.title} ${raw.summary}"
                                val shallowTermMatch = terms.any { phraseMatches(shallowBody, it) }
                                val shallowDemandMatch = demands.any { demand ->
                                    sourceMatchesDemand(source, demand.vehicle) &&
                                        phraseMatches(shallowBody, demand.subject)
                                }
                                if (!shallowTermMatch && !shallowDemandMatch) return@forEach
                            }

                            val item = resolve(raw) ?: return@forEach
'''
new_loop = r'''                    var globoplayDeepFallbacks = 0
                    prioritized.asSequence()
                        .take(resolveLimitFor(source))
                        .forEach { raw ->
                            // O título/JSON de /cenas/ continua sendo o filtro mais barato.
                            // Porém uma quantidade pequena e limitada de cards sem match superficial
                            // também é aberta: no Globoplay o termo pode existir somente na descrição,
                            // tags, keywords ou JSON da página /v/<id>.
                            if (scanMode && isGloboplaySource(source)) {
                                val shallowBody = "${raw.title} ${raw.summary}"
                                val shallowTermMatch = terms.any { phraseMatches(shallowBody, it) }
                                val shallowDemandMatch = demands.any { demand ->
                                    sourceMatchesDemand(source, demand.vehicle) &&
                                        phraseMatches(shallowBody, demand.subject)
                                }
                                if (!shallowTermMatch && !shallowDemandMatch) {
                                    if (globoplayDeepFallbacks >= deepFallbackLimitFor(source)) return@forEach
                                    globoplayDeepFallbacks++
                                }
                            }

                            val item = resolve(raw) ?: return@forEach
'''
repo = replace_once(repo, old_loop, new_loop, "VideoRepository loop")
resolve_fn = r'''    private fun resolveLimitFor(source: VideoSource): Int = when {
        source.youtubeHandle.isNotBlank() -> MAX_YOUTUBE_ITEMS_PER_SCAN
        source.id == "video-globoplay-jornalismo" -> MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN
        source.id in VideoSourceCatalog.globoplayRegionalSweepIds -> MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN
        isGloboplaySource(source) -> MAX_GLOBOPLAY_ITEMS_PER_SCAN
        else -> MAX_RESOLVED_PER_QUERY
    }
'''
repo = replace_once(repo, resolve_fn, resolve_fn + r'''
    private fun deepFallbackLimitFor(source: VideoSource): Int = when {
        source.id == "video-globoplay-jornalismo" -> MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL
        source.id in VideoSourceCatalog.globoplayRegionalSweepIds -> MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL
        isGloboplaySource(source) -> MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE
        else -> 0
    }
''', "VideoRepository deep fallback fn")
repo = repo.replace("MonitorNoticias/3.0.2", "MonitorNoticias/3.0.3")
repo = repo.replace("MonitorNoticiasAndroid/3.0", "MonitorNoticiasAndroid/3.0.3")
repo = replace_once(
    repo,
    "        private const val MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN = 32\n",
    "        private const val MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN = 32\n        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE = 6\n        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL = 10\n",
    "VideoRepository fallback constants"
)
repo_path.write_text(repo, encoding="utf-8")


# 3) UI sync: when automatic workers complete, reload the DB into Home/History.
vm_path = Path("app/src/main/java/br/com/monitordenoticias/android/MonitorViewModel.kt")
vm = vm_path.read_text(encoding="utf-8")
vm = replace_once(
    vm,
    "import android.app.Application\n",
    "import android.app.Application\nimport android.content.SharedPreferences\n",
    "MonitorViewModel import"
)
vm = replace_once(
    vm,
    '    private val locale = Locale("pt", "BR")\n',
    '''    private val locale = Locale("pt", "BR")\n    private val autoRunListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->\n        if (key == AutoRunLog.KEY_NEWS_COMPLETED_AT || key == AutoRunLog.KEY_DEMAND_COMPLETED_AT) {\n            refresh()\n        }\n    }\n''',
    "MonitorViewModel listener"
)
vm = replace_once(
    vm,
    "    init {\n        refresh()\n",
    "    init {\n        prefs.registerOnSharedPreferenceChangeListener(autoRunListener)\n        refresh()\n",
    "MonitorViewModel init listener"
)
vm = replace_once(
    vm,
    "    override fun onCleared() { db.close(); super.onCleared() }\n",
    "    override fun onCleared() {\n        prefs.unregisterOnSharedPreferenceChangeListener(autoRunListener)\n        db.close()\n        super.onCleared()\n    }\n",
    "MonitorViewModel cleanup"
)
vm_path.write_text(vm, encoding="utf-8")


# 4) New APK version. applicationId/signing configuration stay untouched.
gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text(encoding="utf-8")
gradle = replace_once(gradle, "        versionCode = 302\n", "        versionCode = 303\n", "versionCode")
gradle = replace_once(gradle, '        versionName = "3.0.2"\n', '        versionName = "3.0.3"\n', "versionName")
gradle_path.write_text(gradle, encoding="utf-8")


# Guard rails.
checks = {
    "collector JSON fallback": "EMBEDDED_TITLE_REGEX" in collector and "VIDEO_LINK_REGEX.findAll(html)" in collector,
    "bounded deep fallback": "MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE = 6" in repo,
    "automatic news UI listener": "registerOnSharedPreferenceChangeListener(autoRunListener)" in vm,
    "version 303": 'versionCode = 303' in gradle and 'versionName = "3.0.3"' in gradle,
    "application id preserved": 'applicationId = "br.com.monitordenoticias.android"' in gradle,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("guard checks failed: " + ", ".join(failed))

print("v3.0.3 guarded patch applied successfully")

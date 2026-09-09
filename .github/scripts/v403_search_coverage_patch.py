from pathlib import Path

repo = Path('.')
controller_path = repo / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DesktopController.kt'
main_path = repo / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/Main.kt'
build_path = repo / 'desktop/build.gradle.kts'


def once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'PADRAO NAO ENCONTRADO: {label}')
    return text.replace(old, new, 1)


def replace_between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    if a < 0:
        raise SystemExit(f'INICIO NAO ENCONTRADO: {label}')
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f'FIM NAO ENCONTRADO: {label}')
    return text[:a] + replacement.rstrip() + '\n\n' + text[b:]

controller = controller_path.read_text(encoding='utf-8')
controller = once(
    controller,
    'import java.io.File\n',
    'import java.io.File\nimport java.text.Normalizer\n',
    'Normalizer import'
)
controller = once(
    controller,
    '    private val newsRepository = NewsRepository(newsDb)\n    private val videoRepository = VideoRepository(context, videoDb)\n',
    '    private val newsRepository = NewsRepository(newsDb)\n    private val videoRepository = VideoRepository(context, videoDb)\n    private val directNewsCollector = DesktopDirectNewsCollector()\n',
    'direct collector field'
)
controller = once(
    controller,
    '    var videoStatus by mutableStateOf("Pronto")\n        private set\n',
    '    var videoStatus by mutableStateOf("Pronto")\n        private set\n    var newsCoverageDiagnostics by mutableStateOf(DesktopDirectNewsCollector.Diagnostics())\n        private set\n',
    'coverage diagnostics state'
)

new_search = r'''    fun searchNews(from: Long? = null, to: Long? = null, automatic: Boolean = false) {
        if (newsBusy) return
        if (!ensureProxyReady(false)) return
        if (!newsAllSources && selectedNewsSourceIds.isEmpty()) {
            status = "⚠ Selecione pelo menos uma fonte ou ative ‘Buscar em todos os veículos’."
            if (automatic) markAutoFailed("news", status)
            return
        }

        val runStartedAt = System.currentTimeMillis()
        val effectiveFrom = from ?: (runStartedAt - 24L * 60L * 60L * 1000L)
        val effectiveTo = to ?: runStartedAt
        val sourceScope = selectedNewsSources()
        val knownBeforeRun = newsDb.listNews(10_000).mapTo(mutableSetOf()) { it.link }
        newsNewLinks = emptySet()
        newsCoverageDiagnostics = DesktopDirectNewsCollector.Diagnostics()
        newsBusy = true
        if (automatic) markAutoAttempt("news")
        status = if (from == null) "Buscando notícias..." else "Buscando notícias no período..."

        scope.launch {
            try {
                val result = if (from == null || to == null) {
                    newsRepository.searchProgressive(sourceScope, newsAllSources) { update ->
                        newsProgress = update.progress
                        if (update.items.isNotEmpty()) {
                            news = mergeNewsForUi(news, update.items)
                            val currentNew = update.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                            if (currentNew.isNotEmpty()) newsNewLinks = newsNewLinks + currentNew
                        }
                    }
                } else {
                    newsRepository.searchPeriodProgressive(from, to, sourceScope, newsAllSources) { update ->
                        newsProgress = update.progress
                        if (update.items.isNotEmpty()) {
                            news = mergeNewsForUi(news, update.items)
                            val currentNew = update.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                            if (currentNew.isNotEmpty()) newsNewLinks = newsNewLinks + currentNew
                        }
                    }
                }

                // Segunda camada Windows: sites com rota direta configurada. Ela roda
                // depois do motor principal e cobre casos em que o Google Notícias
                // abrevia o publisher ou não entrega a matéria pelo RSS.
                status = "Busca principal concluída • verificando cobertura complementar..."
                val direct = directNewsCollector.collect(
                    selectedSources = sourceScope,
                    searchAllSources = newsAllSources,
                    terms = newsDb.listTerms(),
                    demands = newsDb.listDemands().filter { it.active },
                    from = effectiveFrom,
                    to = effectiveTo,
                    capturedAt = System.currentTimeMillis()
                )
                newsCoverageDiagnostics = direct.diagnostics

                val storedAfterPrimary = newsDb.listNews(10_000)
                val storedByStory = storedAfterPrimary.associateBy(::desktopStoryKey)
                val stableDirect = direct.items.map { incoming ->
                    val previous = storedByStory[desktopStoryKey(incoming)]
                    if (previous == null) incoming else incoming.copy(
                        id = previous.id,
                        link = previous.link,
                        capturedAt = previous.capturedAt,
                        important = previous.important || incoming.important,
                        demand = previous.demand || incoming.demand,
                        matchedTerm = mergeLabels(previous.matchedTerm, incoming.matchedTerm),
                        matchedDemand = mergeLabels(previous.matchedDemand, incoming.matchedDemand)
                    )
                }.distinctBy { it.link }

                val directInserted = newsDb.insertNews(stableDirect)
                val baseNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                newsNewLinks = baseNewLinks + directInserted.map { it.link }

                val combinedStoryKeys = (result.items + stableDirect).map(::desktopStoryKey).toSet()
                val combinedNew = result.newCount + directInserted.size
                val combinedDemandNew = result.newDemandCount + directInserted.count { it.demand }
                val combinedResult = SearchResult(
                    items = (result.items + stableDirect).distinctBy(::desktopStoryKey),
                    foundCount = combinedStoryKeys.size,
                    newCount = combinedNew,
                    newDemandCount = combinedDemandNew,
                    errors = result.errors
                )

                if (automatic) markNewsAutoCompleted(combinedResult)
                refresh()

                val coverageText = if (direct.diagnostics.sourcesScanned > 0) {
                    " • direta ${direct.diagnostics.accepted} aceita(s)"
                } else ""
                val coverageErrors = if (direct.diagnostics.errors > 0) {
                    " • ${direct.diagnostics.errors} falha(s) na cobertura direta"
                } else ""
                status = "✓ $combinedNew nova(s) notícia(s) • $combinedDemandNew demanda(s) • ${result.errors} falha(s)$coverageText$coverageErrors"
                if (combinedNew + combinedDemandNew > 0) notify("Monitor de Notícias", status)
            } catch (t: Throwable) {
                status = "Falha na busca de notícias: ${t.message ?: t.javaClass.simpleName}"
                if (automatic) markAutoFailed("news", t.message ?: t.javaClass.simpleName)
            } finally {
                newsBusy = false
            }
        }
    }'''
controller = replace_between(controller, '    fun searchNews(', '    fun searchDemand(', new_search, 'searchNews')

controller = once(
    controller,
    '    private fun selectedVideoSources(): List<VideoSource> = VideoSourceCatalog.selected(selectedVideoSourceIds)\n',
    '''    private fun desktopStoryKey(news: News): String =
        "${normalizeStoryText(DesktopSourceCatalog.canonicalName(news.source))}|${normalizeStoryText(news.title)}"

    private fun normalizeStoryText(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun mergeLabels(previous: String, incoming: String): String = when {
        previous.isBlank() -> incoming
        incoming.isBlank() -> previous
        normalizeStoryText(previous).contains(normalizeStoryText(incoming)) -> previous
        normalizeStoryText(incoming).contains(normalizeStoryText(previous)) -> incoming
        else -> "$previous, $incoming"
    }

    private fun selectedVideoSources(): List<VideoSource> = VideoSourceCatalog.selected(selectedVideoSourceIds)
''',
    'story helpers'
)
controller_path.write_text(controller, encoding='utf-8')

main = main_path.read_text(encoding='utf-8')
main = once(
    main,
    '        StatusStrip(c.status, c.newsBusy)\n\n        val shown = c.news.filter {',
    '''        StatusStrip(c.status, c.newsBusy)
        if (c.newsCoverageDiagnostics.sourcesScanned > 0) {
            CoverageDiagnosticsStrip(c.newsCoverageDiagnostics)
        }

        val shown = c.news.filter {''',
    'coverage strip placement'
)
main = once(
    main,
    '@Composable private fun StatusStrip(text: String, busy: Boolean) {',
    '''@Composable
private fun CoverageDiagnosticsStrip(d: DesktopDirectNewsCollector.Diagnostics) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AppGoldSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppGold.copy(alpha = .45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Radar, null, tint = AppGold, modifier = Modifier.size(18.dp))
            Text("Cobertura complementar", fontWeight = FontWeight.Bold, color = AppNavy)
            Text("${d.sourcesScanned} fonte(s)", color = AppMuted)
            Text("${d.candidateLinks} candidatos", color = AppMuted)
            Text("${d.candidateTermMatches} com termo", color = AppMuted)
            Text("${d.accepted} aceitos", color = AppGreen, fontWeight = FontWeight.SemiBold)
            if (d.errors > 0) Text("${d.errors} falha(s)", color = AppRed, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(d.sourceNames.joinToString(" • ").take(90), color = AppMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun StatusStrip(text: String, busy: Boolean) {''',
    'coverage composable'
)
main = main.replace('Windows Portable • v4.0.2', 'Windows Portable • v4.0.3')
main = main.replace('Windows Portable v4.0.2', 'Windows Portable v4.0.3')
main = main.replace('Edição Windows v4.0.2', 'Edição Windows v4.0.3')
main_path.write_text(main, encoding='utf-8')

build = build_path.read_text(encoding='utf-8')
build = build.replace('version = "4.0.2"', 'version = "4.0.3"', 1)
build = build.replace('packageVersion = "4.0.2"', 'packageVersion = "4.0.3"', 1)
build = build.replace('Monitor de Notícias v4.0.2 para Windows', 'Monitor de Notícias v4.0.3 para Windows')
build_path.write_text(build, encoding='utf-8')

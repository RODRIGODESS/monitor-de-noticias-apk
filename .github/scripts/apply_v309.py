from pathlib import Path


def rep(path, old, new, count=1):
    p = Path(path)
    s = p.read_text()
    actual = s.count(old)
    if actual < count:
        raise SystemExit(f"{path}: expected at least {count} occurrence(s), got {actual}: {old[:120]!r}")
    s = s.replace(old, new, count)
    p.write_text(s)

# --- Video models: expose diagnostic details to UI ---
rep(
    "app/src/main/java/br/com/monitordenoticias/android/VideoModels.kt",
    '''data class VideoSearchResult(\n    val items: List<VideoItem>,\n    val foundCount: Int,\n    val newCount: Int,\n    val relevantCount: Int,\n    val newRelevantCount: Int,\n    val errors: Int\n)''',
    '''data class VideoSourceIssue(\n    val sourceId: String,\n    val sourceName: String,\n    val failureCount: Int,\n    val stage: String\n)\n\ndata class VideoSearchResult(\n    val items: List<VideoItem>,\n    val foundCount: Int,\n    val newCount: Int,\n    val relevantCount: Int,\n    val newRelevantCount: Int,\n    val errors: Int,\n    val unstableSources: List<VideoSourceIssue> = emptyList()\n)'''
)
rep(
    "app/src/main/java/br/com/monitordenoticias/android/VideoModels.kt",
    '''    val searchProgress: LiveSearchProgress = LiveSearchProgress(),\n    val totalStored: Int = 0,''',
    '''    val searchProgress: LiveSearchProgress = LiveSearchProgress(),\n    val unstableSources: List<VideoSourceIssue> = emptyList(),\n    val totalStored: Int = 0,'''
)

# --- Repository: unique source diagnostics with stage/failure count ---
repo = "app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt"
rep(
    repo,
    '''            val unstableSourceIds = linkedSetOf<String>()\n            var errors = 0''',
    '''            val unstableSourceIds = linkedSetOf<String>()\n            val unstableDetails = linkedMapOf<String, VideoSourceIssue>()\n            var errors = 0'''
)
rep(
    repo,
    '''            fun markUnstable(source: VideoSource) {\n                unstableSourceIds += source.id\n                errors = unstableSourceIds.size\n            }''',
    '''            fun markUnstable(source: VideoSource, failureCount: Int, stage: String) {\n                unstableSourceIds += source.id\n                unstableDetails[source.id] = VideoSourceIssue(\n                    sourceId = source.id,\n                    sourceName = source.name,\n                    failureCount = failureCount.coerceAtLeast(1),\n                    stage = stage.ifBlank { "HTTP/rede" }\n                )\n                errors = unstableSourceIds.size\n            }'''
)
rep(
    repo,
    '''                var sourceRequestFailures = 0\n\n                fun sourceError() {\n                    sourceRequestFailures++\n                }''',
    '''                var sourceRequestFailures = 0\n                val sourceFailureStages = linkedMapOf<String, Int>()\n\n                fun sourceError(stage: String = "HTTP/rede") {\n                    sourceRequestFailures++\n                    sourceFailureStages[stage] = (sourceFailureStages[stage] ?: 0) + 1\n                }\n\n                fun markCurrentSourceUnstable() {\n                    val primaryStage = sourceFailureStages.maxByOrNull { it.value }?.key ?: "HTTP/rede"\n                    markUnstable(source, sourceRequestFailures, primaryStage)\n                }'''
)
rep(
    repo,
    '''                    collectRecentBySource(source, capturedAt, effectiveFrom, effectiveTo) { sourceError() }''',
    '''                    collectRecentBySource(source, capturedAt, effectiveFrom, effectiveTo) { stage -> sourceError(stage) }'''
)
rep(repo, '''                    markUnstable(source)''', '''                    markCurrentSourceUnstable()''', count=1)
rep(
    repo,
    '''                            if (!globoplay) sourceError()''',
    '''                            if (!globoplay) sourceError("Abrir página do vídeo")'''
)
rep(repo, '''                        markUnstable(source)''', '''                        markCurrentSourceUnstable()''', count=1)
rep(
    repo,
    '''                        collectCandidatesForQuery(source, spec.query, capturedAt) { sourceError() }''',
    '''                        collectCandidatesForQuery(source, spec.query, capturedAt) { sourceError("Busca por termo") }'''
)
rep(
    repo,
    '''                newRelevantCount = newKeys.size,\n                errors = errors\n            )''',
    '''                newRelevantCount = newKeys.size,\n                errors = errors,\n                unstableSources = unstableDetails.values.sortedBy { it.sourceName.lowercase() }\n            )'''
)

# --- Repository: stage-aware collection and national Edição + Trechos merge ---
rep(
    repo,
    '''        onError: () -> Unit\n    ): List<VideoItem> {\n        if (source.youtubeHandle.isNotBlank()) {''',
    '''        onError: (String) -> Unit\n    ): List<VideoItem> {\n        if (source.youtubeHandle.isNotBlank()) {'''
)
rep(repo, '''                .onFailure { onError() }''', '''                .onFailure { onError("YouTube • feed/canal") }''', count=1)
rep(repo, '''                .onFailure { onError() }''', '''                .onFailure { onError("YouTube • página") }''', count=1)
old_globo = '''        if (isGloboplaySource(source)) {\n            // Estratégia principal da v3.0.7: programa -> Edição recente -> Trechos da Edição.\n            // A aba /cenas/ depende de JavaScript em vários telejornais e fica apenas como fallback.\n            val editionTrechos = globoplayEditionCollector.collect(source, capturedAt, from, to, onError)\n            if (editionTrechos.isNotEmpty()) {\n                return editionTrechos.distinctBy { canonicalKey(it.link) }\n            }\n\n            val trechos = globoplayTrechosCollector.collect(source, capturedAt, onError)\n            if (trechos.isNotEmpty()) {\n                return trechos.distinctBy { canonicalKey(it.link) }\n            }\n'''
new_globo = '''        if (isGloboplaySource(source)) {\n            // Regionais normalmente expõem Trechos dentro da própria Edição. JH/JN e\n            // outros nacionais podem expor apenas \"Mais Vídeos\" na Edição enquanto os\n            // Trechos reais ficam em /cenas/. Nos cinco nacionais, portanto, combinamos\n            // os dois grafos em vez de encerrar no primeiro que devolver qualquer link.\n            val editionTrechos = globoplayEditionCollector.collect(source, capturedAt, from, to) {\n                onError("Globoplay • Edições")\n            }\n            if (source.id in CORE_NATIONAL_GLOBOPLAY_IDS) {\n                val sceneTrechos = globoplayTrechosCollector.collect(source, capturedAt) {\n                    onError("Globoplay • Trechos")\n                }\n                val combined = (editionTrechos + sceneTrechos)\n                    .distinctBy { canonicalKey(it.link) }\n                if (combined.isNotEmpty()) return combined\n            } else {\n                if (editionTrechos.isNotEmpty()) {\n                    return editionTrechos.distinctBy { canonicalKey(it.link) }\n                }\n                val trechos = globoplayTrechosCollector.collect(source, capturedAt) {\n                    onError("Globoplay • Trechos")\n                }\n                if (trechos.isNotEmpty()) {\n                    return trechos.distinctBy { canonicalKey(it.link) }\n                }\n            }\n'''
rep(repo, old_globo, new_globo)
# Remaining Globoplay/website errors in this method: label them instead of bare callback.
# Replace only within collectRecentBySource tail by targeted patterns.
rep(
    repo,
    '''                    runCatching { fetchSearchWebsite(source, "", capturedAt) }\n                        .onFailure { onError() }''',
    '''                    runCatching { fetchSearchWebsite(source, "", capturedAt) }\n                        .onFailure { onError("Globoplay • busca fallback") }''',
    count=2
)
rep(
    repo,
    '''                    runCatching { fetchWebsite(source, capturedAt) }\n                        .onFailure { onError() }''',
    '''                    runCatching { fetchWebsite(source, capturedAt) }\n                        .onFailure { onError("Globoplay • página fallback") }''',
    count=1
)
rep(
    repo,
    '''                return runCatching { fetchWebsite(source, capturedAt) }\n                    .onFailure { onError() }''',
    '''                return runCatching { fetchWebsite(source, capturedAt) }\n                    .onFailure { onError("Globoplay • página fallback") }'''
)
rep(
    repo,
    '''        return runCatching { fetchWebsite(source, capturedAt) }\n            .onFailure { onError() }''',
    '''        return runCatching { fetchWebsite(source, capturedAt) }\n            .onFailure { onError("Portal • página") }'''
)

# --- Event-aware matcher, intentionally scoped to 7 de Setembro ---
rep(
    repo,
    '''        val wanted = normalize(phrase)\n        if (wanted.isBlank()) return true\n\n        val hayTokens = haystack.split(' ').filter { it.isNotBlank() }.toSet()''',
    '''        val wanted = normalize(phrase)\n        if (wanted.isBlank()) return true\n        if (matchesSeptember7Event(haystack, wanted)) return true\n\n        val hayTokens = haystack.split(' ').filter { it.isNotBlank() }.toSet()'''
)
rep(
    repo,
    '''    private fun tokenEquivalent(actual: String, wanted: String): Boolean {''',
    '''    private fun matchesSeptember7Event(haystack: String, wanted: String): Boolean {\n        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }.toSet()\n        if ("7" !in wantedTokens || "setembro" !in wantedTokens) return false\n        if (wantedTokens.none { it in SEPTEMBER_7_EVENT_TOKENS }) return false\n\n        val hayTokens = haystack.split(' ').filter { it.isNotBlank() }.toSet()\n        return "7" in hayTokens &&\n            "setembro" in hayTokens &&\n            hayTokens.any { it in SEPTEMBER_7_EVENT_TOKENS }\n    }\n\n    private fun tokenEquivalent(actual: String, wanted: String): Boolean {'''
)
rep(
    repo,
    '''        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")''',
    '''        private val SEPTEMBER_7_EVENT_TOKENS = setOf(\n            "desfile", "desfiles", "comemoracao", "comemoracoes", "independencia"\n        )\n        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")'''
)

# --- VideoViewModel: expose/reset diagnostics and use correct wording ---
vm = "app/src/main/java/br/com/monitordenoticias/android/VideoViewModel.kt"
rep(
    vm,
    '''            status = "Buscando vídeos das últimas 24h...",\n            searchProgress = LiveSearchProgress(active = true, kind = "Vídeos", startedAt = started)''',
    '''            status = "Buscando vídeos das últimas 24h...",\n            searchProgress = LiveSearchProgress(active = true, kind = "Vídeos", startedAt = started),\n            unstableSources = emptyList()'''
)
rep(
    vm,
    '''                result.errors > 0 && result.foundCount == 0 -> "⚠ Busca concluída sem vídeos diretos • ${result.errors} consulta(s) falharam"''',
    '''                result.errors > 0 && result.foundCount == 0 -> "⚠ Busca concluída sem vídeos diretos • ${result.errors} fonte(s) instável(is)"'''
)
rep(
    vm,
    '''                searchProgress = _state.value.searchProgress.copy(active = false, finishedAt = finished)\n            )''',
    '''                searchProgress = _state.value.searchProgress.copy(active = false, finishedAt = finished),\n                unstableSources = result.unstableSources\n            )''',
    count=1
)
rep(
    vm,
    '''            status = "Pesquisando vídeos no período em tempo real...",\n            searchProgress = LiveSearchProgress(active = true, kind = "Vídeos • Período", startedAt = started)''',
    '''            status = "Pesquisando vídeos no período em tempo real...",\n            searchProgress = LiveSearchProgress(active = true, kind = "Vídeos • Período", startedAt = started),\n            unstableSources = emptyList()'''
)
rep(
    vm,
    '''                result.errors > 0 && periodItems.isEmpty() -> "⚠ Pesquisa do período concluída sem vídeos • ${result.errors} consulta(s) falharam"''',
    '''                result.errors > 0 && periodItems.isEmpty() -> "⚠ Pesquisa do período concluída sem vídeos • ${result.errors} fonte(s) instável(is)"'''
)
rep(
    vm,
    '''                searchProgress = _state.value.searchProgress.copy(active = false, finishedAt = finished)\n            )''',
    '''                searchProgress = _state.value.searchProgress.copy(active = false, finishedAt = finished),\n                unstableSources = result.unstableSources\n            )''',
    count=1
)

# --- V30 UI: source diagnostics + equal-size news actions ---
v30 = "app/src/main/java/br/com/monitordenoticias/android/V30Screens.kt"
rep(v30, '''import androidx.compose.foundation.horizontalScroll''', '''import androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.verticalScroll''')
rep(
    v30,
    '''    var showPeriod by remember { mutableStateOf(false) }\n    var confirmClear by remember { mutableStateOf(false) }''',
    '''    var showPeriod by remember { mutableStateOf(false) }\n    var confirmClear by remember { mutableStateOf(false) }\n    var showUnstable by remember { mutableStateOf(false) }'''
)
# Insert diagnostics button before filter chips.
needle = '''        item {\n            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {\n                V30Chip("Todos", s.filter == VideoFilter.ALL)'''
replacement = '''        if (!s.busy && s.unstableSources.isNotEmpty()) {\n            item {\n                OutlinedButton(\n                    onClick = { showUnstable = true },\n                    modifier = Modifier.fillMaxWidth().height(42.dp),\n                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Amber),\n                    border = BorderStroke(1.dp, V30Amber.copy(alpha = .38f))\n                ) {\n                    Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(7.dp))\n                    Text("Ver fontes instáveis (${s.unstableSources.size})")\n                }\n            }\n        }\n\n        item {\n            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {\n                V30Chip("Todos", s.filter == VideoFilter.ALL)'''
rep(v30, needle, replacement)
# Add dialog after clear dialog block, before function closes.
needle = '''    if (confirmClear) {\n        AlertDialog(\n            onDismissRequest = { confirmClear = false },\n            icon = { Icon(Icons.Outlined.DeleteSweep, null, tint = V30Red) },\n            title = { Text("Limpar vídeos?") },\n            text = { Text("Isso apaga o histórico de vídeos detectados. Termos, Demandas e fontes selecionadas serão mantidos.") },\n            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Limpar", color = V30Red) } },\n            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },\n            containerColor = V30Surface2\n        )\n    }\n}'''
replacement = '''    if (confirmClear) {\n        AlertDialog(\n            onDismissRequest = { confirmClear = false },\n            icon = { Icon(Icons.Outlined.DeleteSweep, null, tint = V30Red) },\n            title = { Text("Limpar vídeos?") },\n            text = { Text("Isso apaga o histórico de vídeos detectados. Termos, Demandas e fontes selecionadas serão mantidos.") },\n            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Limpar", color = V30Red) } },\n            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },\n            containerColor = V30Surface2\n        )\n    }\n\n    if (showUnstable) {\n        AlertDialog(\n            onDismissRequest = { showUnstable = false },\n            icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = V30Amber) },\n            title = { Text("Fontes instáveis (${s.unstableSources.size})") },\n            text = {\n                Column(\n                    Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),\n                    verticalArrangement = Arrangement.spacedBy(8.dp)\n                ) {\n                    Text(\n                        "Uma fonte entra aqui quando não conseguiu completar sua rota principal. O número de falhas e a etapa ajudam a identificar URLs fora do ar, bloqueios e timeouts.",\n                        color = V30Text2,\n                        fontSize = 10.8.sp\n                    )\n                    s.unstableSources.forEach { issue ->\n                        Surface(\n                            color = V30Amber.copy(alpha = .07f),\n                            shape = RoundedCornerShape(10.dp),\n                            border = BorderStroke(1.dp, V30Amber.copy(alpha = .18f)),\n                            modifier = Modifier.fillMaxWidth()\n                        ) {\n                            Column(Modifier.padding(9.dp)) {\n                                Text(issue.sourceName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)\n                                Text(\n                                    "${issue.failureCount} falha(s) • ${issue.stage}",\n                                    color = V30Text2,\n                                    fontSize = 10.sp\n                                )\n                            }\n                        }\n                    }\n                }\n            },\n            confirmButton = { TextButton(onClick = { showUnstable = false }) { Text("Fechar") } },\n            containerColor = V30Surface2\n        )\n    }\n}'''
rep(v30, needle, replacement)
# Equal-height V30NewsCard buttons (only the news card instances with these labels).
rep(
    v30,
    '''                    modifier = Modifier.weight(1f)\n                ) {\n                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(6.dp))\n                    Text("Abrir notícia")''',
    '''                    modifier = Modifier.weight(1f).height(50.dp),\n                    contentPadding = PaddingValues(horizontal = 8.dp)\n                ) {\n                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(6.dp))\n                    Text("Abrir notícia", maxLines = 1, fontSize = 10.8.sp)'''
)
rep(
    v30,
    '''                    modifier = Modifier.weight(1f),\n                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)\n                ) {\n                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(6.dp))\n                    Text("WhatsApp")''',
    '''                    modifier = Modifier.weight(1f).height(50.dp),\n                    contentPadding = PaddingValues(horizontal = 8.dp),\n                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)\n                ) {\n                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(6.dp))\n                    Text("WhatsApp", maxLines = 1, fontSize = 10.8.sp)''',
    count=1
)

# V28 history/period/demand cards: same action dimensions.
v28 = "app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt"
rep(
    v28,
    '''                    modifier = Modifier.weight(1f)\n                ) {\n                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(6.dp))\n                    Text("Abrir notícia")''',
    '''                    modifier = Modifier.weight(1f).height(50.dp),\n                    contentPadding = PaddingValues(horizontal = 8.dp)\n                ) {\n                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(6.dp))\n                    Text("Abrir notícia", maxLines = 1, fontSize = 10.8.sp)'''
)
rep(
    v28,
    '''                    modifier = Modifier.weight(1f),\n                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V28Mint)\n                ) {\n                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(6.dp))\n                    Text("WhatsApp")''',
    '''                    modifier = Modifier.weight(1f).height(50.dp),\n                    contentPadding = PaddingValues(horizontal = 8.dp),\n                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V28Mint)\n                ) {\n                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))\n                    Spacer(Modifier.width(6.dp))\n                    Text("WhatsApp", maxLines = 1, fontSize = 10.8.sp)'''
)

# Version bump (release workflow is updated separately by connector).
rep("app/build.gradle.kts", '''        versionCode = 308\n        versionName = "3.0.8"''', '''        versionCode = 309\n        versionName = "3.0.9"''')

# Sanity checks.
checks = {
    "app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt": [
        "source.id in CORE_NATIONAL_GLOBOPLAY_IDS",
        "Globoplay • Trechos",
        "matchesSeptember7Event",
        "unstableSources = unstableDetails.values",
    ],
    "app/src/main/java/br/com/monitordenoticias/android/V30Screens.kt": [
        "Ver fontes instáveis",
        "heightIn(max = 430.dp)",
        "Abrir notícia\", maxLines = 1",
    ],
    "app/build.gradle.kts": ["versionCode = 309", 'versionName = "3.0.9"'],
}
for path, needles in checks.items():
    text = Path(path).read_text()
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"sanity check failed: {path}: {needle}")

print("V309_PATCH_OK=true")

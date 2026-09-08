from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

root = Path("app/src/main/java/br/com/monitordenoticias/android")
video_models = root / "VideoModels.kt"
video_vm = root / "VideoViewModel.kt"
video_repo = root / "VideoRepository.kt"
news_repo = root / "NewsRepository.kt"
main = root / "MainActivityV28.kt"
build = Path("app/build.gradle.kts")
workflow = Path(".github/workflows/release.yml")

# 1) VideoState passa a expor a lista independente de termos.
replace_once(
    video_models,
    "    val unstableSources: List<VideoSourceIssue> = emptyList(),\n    val totalStored: Int = 0,\n    val capturedToday: Int = 0\n)",
    "    val unstableSources: List<VideoSourceIssue> = emptyList(),\n    val videoTerms: List<String> = emptyList(),\n    val totalStored: Int = 0,\n    val capturedToday: Int = 0\n)"
)

# 2) VideoViewModel: migração única dos termos atuais e edição independente.
replace_once(
    video_vm,
    "    private val initialStats = currentStats()\n",
    "    private val initialStats = currentStats()\n    private val initialVideoTerms = loadVideoTerms()\n"
)
replace_once(
    video_vm,
    "            capturedToday = initialStats.second\n",
    "            capturedToday = initialStats.second,\n            videoTerms = initialVideoTerms\n"
)
replace_once(
    video_vm,
    "                capturedToday = stats.second\n",
    "                capturedToday = stats.second,\n                videoTerms = loadVideoTerms()\n",
)
marker = "    fun setFilter(filter: VideoFilter) {\n"
text = video_vm.read_text(encoding="utf-8")
if text.count(marker) != 1:
    raise SystemExit("VideoViewModel setFilter marker not unique")
methods = '''    fun addVideoTerm(value: String) {\n        val next = VideoTermStore.add(getApplication(), value, newsTermSeed())\n        _state.value = _state.value.copy(videoTerms = next, status = "✓ Termo de vídeo adicionado")\n    }\n\n    fun removeVideoTerm(value: String) {\n        val next = VideoTermStore.remove(getApplication(), value, newsTermSeed())\n        _state.value = _state.value.copy(videoTerms = next, status = "✓ Termo de vídeo removido")\n    }\n\n    private fun loadVideoTerms(): List<String> = VideoTermStore.load(getApplication(), newsTermSeed())\n\n    private fun newsTermSeed(): List<String> {\n        val newsDb = NewsDb(getApplication())\n        return try {\n            newsDb.listTerms().ifEmpty { DEFAULT_VIDEO_TERM_SEED }\n        } finally {\n            newsDb.close()\n        }\n    }\n\n'''
video_vm.write_text(text.replace(marker, methods + marker, 1), encoding="utf-8")
replace_once(
    video_vm,
    "        private val V284_STARTER_IDS = setOf(\n",
    "        private val DEFAULT_VIDEO_TERM_SEED = listOf(\n            \"Marinha do Brasil\", \"Capitania dos Portos\", \"Distrito Naval\", \"NAM Atlântico\",\n            \"Cisne Branco\", \"Fragata Marinha do Brasil\", \"Navio-Patrulha Marinha\", \"Programa Nuclear da Marinha\"\n        )\n\n        private val V284_STARTER_IDS = setOf(\n"
)

# 3) Vídeos deixam de ler diretamente os termos de Notícias.
replace_once(
    video_repo,
    "            val terms = newsDb.listTerms().ifEmpty { DEFAULT_TERMS }\n                .map { it.trim() }\n",
    "            val newsTermsForMigration = newsDb.listTerms().ifEmpty { DEFAULT_TERMS }\n            val terms = VideoTermStore.load(context, newsTermsForMigration)\n                .map { it.trim() }\n"
)

# 4) Notícias: Google continua como descoberta e páginas 'Últimas' entram como segunda rota.
replace_once(
    news_repo,
    "class NewsRepository(private val db: NewsDb) {\n",
    "class NewsRepository(private val db: NewsDb) {\n    private val latestCollector = NewsLatestCollector()\n"
)
replace_once(
    news_repo,
    "        val startedAt = System.currentTimeMillis()\n        val tasks = buildList {\n",
    "        val startedAt = System.currentTimeMillis()\n        val directSources = if (from >= startedAt - DIRECT_SCAN_MAX_WINDOW_MS && to >= startedAt - DIRECT_SCAN_RECENCY_TOLERANCE_MS) {\n            latestCollector.supportedSources(selectedSources, searchAllSources)\n        } else {\n            emptyList()\n        }\n        val tasks = buildList {\n"
)
replace_once(news_repo, "            total = tasks.size,\n", "            total = tasks.size + directSources.size,\n")
insert_marker = "        val items = collected.values.sortedByDescending { it.date }\n"
text = news_repo.read_text(encoding="utf-8")
if text.count(insert_marker) != 1:
    raise SystemExit("NewsRepository items marker not unique")
direct_block = '''        directSources.forEach { source ->\n            val label = "${source.name} • Últimas notícias"\n            onUpdate?.invoke(NewsSearchUpdate(progress(label, "Todos os termos")))\n            val outcome = latestCollector.collect(source, terms, demands, from, to, System.currentTimeMillis())\n            if (!outcome.failed && outcome.items.isNotEmpty()) {\n                val inserts = mutableListOf<News>()\n                val updates = mutableListOf<News>()\n                outcome.items.forEach { incoming ->\n                    val duplicate = collected.values.firstOrNull { storyKey(it) == storyKey(incoming) }\n                    if (duplicate == null) {\n                        collected[incoming.link] = incoming\n                        inserts += incoming\n                        updates += incoming\n                    } else {\n                        val merged = mergeNews(duplicate, incoming.copy(link = duplicate.link, source = duplicate.source))\n                        collected[duplicate.link] = merged\n                        updates += merged\n                    }\n                }\n                val inserted = db.insertNews(inserts)\n                inserted.forEach { newLinks += it.link }\n                if (updates.isNotEmpty()) {\n                    onUpdate?.invoke(NewsSearchUpdate(progress(label, "Todos os termos"), updates))\n                }\n            }\n            completed++\n            onUpdate?.invoke(NewsSearchUpdate(progress(label, "Todos os termos")))\n        }\n\n'''
news_repo.write_text(text.replace(insert_marker, direct_block + insert_marker, 1), encoding="utf-8")
replace_once(
    news_repo,
    "                    completed = tasks.size,\n",
    "                    completed = tasks.size + directSources.size,\n"
)
helper_marker = "    private fun mergeNews(previous: News, incoming: News): News {\n"
text = news_repo.read_text(encoding="utf-8")
if text.count(helper_marker) != 1:
    raise SystemExit("NewsRepository mergeNews marker not unique")
helper = '''    private fun storyKey(news: News): String {\n        val sourceKey = normalize(news.source).replace(" noticias", "").replace(" jornal", "").trim()\n        val titleKey = normalize(news.title)\n        return "$sourceKey|$titleKey"\n    }\n\n'''
news_repo.write_text(text.replace(helper_marker, helper + helper_marker, 1), encoding="utf-8")
replace_once(
    news_repo,
    "    companion object {\n        private val STOP_WORDS",
    "    companion object {\n        private const val DIRECT_SCAN_MAX_WINDOW_MS = 48L * 60L * 60L * 1000L\n        private const val DIRECT_SCAN_RECENCY_TOLERANCE_MS = 2L * 60L * 60L * 1000L\n        private val STOP_WORDS"
)

# 5) UI ativa: duas listas de termos independentes.
replace_once(main, "                V28Section.TERMS -> V28Terms(news, newsVm)\n", "                V28Section.TERMS -> V28Terms(news, newsVm, videos, videoVm)\n")
replace_once(main, "        V28Section.TERMS -> \"Palavras usadas no monitoramento\"\n", "        V28Section.TERMS -> \"Termos separados para Notícias e Vídeos\"\n")
replace_once(main, "            V28MoreItem(Icons.Outlined.ManageSearch, \"Termos\", \"Palavras monitoradas\")", "            V28MoreItem(Icons.Outlined.ManageSearch, \"Termos\", \"Listas separadas de Notícias e Vídeos\")")
text = main.read_text(encoding="utf-8")
start = text.index("@Composable\nprivate fun V28Terms(")
end = text.index("@Composable\nprivate fun V28Settings(", start)
new_terms = '''@Composable\nprivate fun V28Terms(s: AppState, vm: MonitorViewModel, videos: VideoState, videoVm: VideoViewModel) {\n    var newsTerm by remember { mutableStateOf("") }\n    var videoTerm by remember { mutableStateOf("") }\n\n    LazyColumn(\n        modifier = Modifier.fillMaxSize(),\n        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),\n        verticalArrangement = Arrangement.spacedBy(8.dp)\n    ) {\n        item {\n            Text("Termos de Notícias", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)\n            Text("Usados pelo Google Notícias e pelas varreduras diretas de Últimas notícias.", color = V28Text2, fontSize = 11.sp)\n        }\n        item {\n            Surface(color = V28Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {\n                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    OutlinedTextField(newsTerm, { newsTerm = it }, label = { Text("Novo termo de Notícias") }, singleLine = true, modifier = Modifier.weight(1f))\n                    FilledIconButton(onClick = { if (newsTerm.isNotBlank()) { vm.addTerm(newsTerm); newsTerm = "" } }) {\n                        Icon(Icons.Outlined.Add, "Adicionar termo de Notícias")\n                    }\n                }\n            }\n        }\n        if (s.terms.isEmpty()) {\n            item { V28Empty("Nenhum termo de Notícias", "Adicione um termo para acompanhar matérias.") }\n        } else {\n            items(s.terms, key = { "news-term-$it" }) { value ->\n                Surface(color = V28Surface, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {\n                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {\n                        Icon(Icons.Outlined.Article, null, tint = V28Accent, modifier = Modifier.size(18.dp))\n                        Spacer(Modifier.width(9.dp))\n                        Text(value, modifier = Modifier.weight(1f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)\n                        IconButton(onClick = { vm.removeTerm(value) }) { Icon(Icons.Outlined.DeleteOutline, "Remover", tint = V28Red) }\n                    }\n                }\n            }\n        }\n\n        item {\n            Spacer(Modifier.height(6.dp))\n            HorizontalDivider(color = V28Divider)\n            Spacer(Modifier.height(6.dp))\n            Text("Termos de Vídeos", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = V28Purple)\n            Text("Independentes dos termos de Notícias. Demandas continuam valendo para os dois monitores.", color = V28Text2, fontSize = 11.sp)\n        }\n        item {\n            Surface(color = V28Purple.copy(alpha = .08f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V28Purple.copy(alpha = .22f)), modifier = Modifier.fillMaxWidth()) {\n                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    OutlinedTextField(videoTerm, { videoTerm = it }, label = { Text("Novo termo de Vídeos") }, singleLine = true, modifier = Modifier.weight(1f))\n                    FilledIconButton(onClick = { if (videoTerm.isNotBlank()) { videoVm.addVideoTerm(videoTerm); videoTerm = "" } }) {\n                        Icon(Icons.Outlined.Add, "Adicionar termo de Vídeos")\n                    }\n                }\n            }\n        }\n        if (videos.videoTerms.isEmpty()) {\n            item { V28Empty("Nenhum termo de Vídeos", "Os vídeos ainda podem ser encontrados pelas Demandas ativas.") }\n        } else {\n            items(videos.videoTerms, key = { "video-term-$it" }) { value ->\n                Surface(color = V28Surface, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {\n                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {\n                        Icon(Icons.Outlined.SmartDisplay, null, tint = V28Purple, modifier = Modifier.size(18.dp))\n                        Spacer(Modifier.width(9.dp))\n                        Text(value, modifier = Modifier.weight(1f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)\n                        IconButton(onClick = { videoVm.removeVideoTerm(value) }) { Icon(Icons.Outlined.DeleteOutline, "Remover", tint = V28Red) }\n                    }\n                }\n            }\n        }\n        item {\n            Text("Na primeira abertura da v4.0, os termos antigos são copiados para Vídeos uma única vez. Depois, as listas são totalmente independentes.", color = V28Text2, fontSize = 10.5.sp)\n        }\n    }\n}\n\n'''
main.write_text(text[:start] + new_terms + text[end:], encoding="utf-8")

# 6) Versionamento v4.0.
replace_once(build, '        versionCode = 312\n        versionName = "3.0.12"', '        versionCode = 400\n        versionName = "4.0"')

# 7) Workflow de release v4.0; preserva o certificado permanente.
w = workflow.read_text(encoding="utf-8")
release_start = w.index("      - name: Prepare v3.0.12 release asset")
release_tail = '''      - name: Prepare v4.0 release asset\n        if: github.event_name != 'pull_request'\n        run: cp app/build/outputs/apk/release/app-release.apk monitor-de-noticias-v4.0.apk\n\n      - name: Point v4.0 tag to validated main commit\n        if: github.event_name == 'push' && github.ref == 'refs/heads/main'\n        run: |\n          set -euo pipefail\n          git config user.name "github-actions[bot]"\n          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"\n          git tag -f v4.0 "$GITHUB_SHA"\n          git push --force origin refs/tags/v4.0\n\n      - name: Publish GitHub Release\n        if: github.event_name == 'push' && github.ref == 'refs/heads/main'\n        uses: softprops/action-gh-release@v2\n        with:\n          tag_name: v4.0\n          target_commitish: ${{ github.sha }}\n          name: Monitor de Notícias Android v4.0\n          body: |\n            Monitor de Notícias Android v4.0 — nova arquitetura de termos e descoberta direta de notícias.\n\n            Notícias:\n            - Google Notícias continua como rota principal de descoberta;\n            - páginas de Últimas notícias de veículos nacionais passam a ser varridas uma vez por veículo;\n            - primeira leva: CNN Brasil, Metrópoles, Folha de S.Paulo, R7, Jovem Pan e O Globo;\n            - títulos e resumos recentes são cruzados localmente com todos os Termos e Demandas;\n            - resultados do Google e dos sites diretos são deduplicados por matéria para evitar cards repetidos;\n            - a rota direta é complementar: falha isolada de um site não derruba a busca do Google.\n\n            Termos:\n            - Termos de Notícias e Termos de Vídeos passam a ser listas independentes;\n            - os termos existentes continuam como Termos de Notícias;\n            - na primeira execução, eles são copiados uma única vez para Termos de Vídeos para preservar o comportamento anterior;\n            - depois da migração, adicionar ou remover termo em uma lista não altera a outra;\n            - Demandas continuam válidas para Notícias e Vídeos.\n\n            Compatibilidade:\n            - versionCode 400 / versionName 4.0;\n            - mesmo applicationId br.com.monitordenoticias.android;\n            - mesma assinatura permanente validada pelo certificado SHA-256 esperado;\n            - atualização direta sobre v3.0.12 preservando banco, notícias, vídeos, Demandas, fontes, histórico e configurações.\n          files: monitor-de-noticias-v4.0.apk\n          overwrite_files: true\n          make_latest: true\n'''
workflow.write_text(w[:release_start] + release_tail, encoding="utf-8")

print("v4.0 patch applied")

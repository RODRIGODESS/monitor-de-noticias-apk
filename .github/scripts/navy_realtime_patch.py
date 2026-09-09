from pathlib import Path

repo = Path('.')
controller_path = repo / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/DesktopController.kt'
main_path = repo / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/Main.kt'
auto_path = repo / 'desktop/src/main/kotlin/br/com/monitordenoticias/desktop/AutomationSettingsCard.kt'


def once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'PADRAO NAO ENCONTRADO: {label}')
    return text.replace(old, new, 1)


def replace_block(text, start_marker, end_marker, replacement, label):
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f'INICIO NAO ENCONTRADO: {label}')
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f'FIM NAO ENCONTRADO: {label}')
    return text[:start] + replacement.rstrip() + '\n\n' + text[end:]

# ---------- Controller: estado realmente observável + NOVO por execução ----------
controller = controller_path.read_text(encoding='utf-8')
controller = once(
    controller,
    'import androidx.compose.runtime.mutableStateOf\n',
    'import androidx.compose.runtime.mutableIntStateOf\nimport androidx.compose.runtime.mutableStateOf\n',
    'import mutableIntStateOf'
)

controller = once(
    controller,
    '''    var capturedTodayVideos by mutableStateOf(0)\n        private set\n\n''',
    '''    var capturedTodayVideos by mutableStateOf(0)\n        private set\n\n    // Coleções usadas diretamente pelas telas precisam ser estado Compose.\n    // Assim limpar histórico, buscas e mutações aparecem sem trocar de aba.\n    var newsHistory: List<News> by mutableStateOf(emptyList())\n        private set\n    var videoHistory: List<VideoItem> by mutableStateOf(emptyList())\n        private set\n\n    // NOVO agora significa inserido na execução corrente, não apenas capturado há <24h.\n    var newsNewLinks: Set<String> by mutableStateOf(emptySet())\n        private set\n    var videoNewLinks: Set<String> by mutableStateOf(emptySet())\n        private set\n\n    var uiRevision by mutableIntStateOf(0)\n        private set\n\n    private fun touchUi() { uiRevision++ }\n\n''',
    'observable histories'
)

controller = once(
    controller,
    '''    fun refresh() {\n        news = newsDb.listRecent(24, 1000)\n        videos = videoDb.listRecent(7, 1500)\n        terms = newsDb.listTerms()\n        demands = newsDb.listDemands()\n        videoTerms = VideoTermStore.load(context, terms)\n        val stored = videoDb.listAll(1000).filter { it.relevant }\n''',
    '''    fun refresh() {\n        news = newsDb.listRecent(24, 1000)\n        videos = videoDb.listRecent(7, 1500)\n        newsHistory = newsDb.listNews(5000)\n        videoHistory = videoDb.listAll(5000)\n        terms = newsDb.listTerms()\n        demands = newsDb.listDemands()\n        videoTerms = VideoTermStore.load(context, terms)\n        val stored = videoHistory.filter { it.relevant }\n''',
    'refresh histories'
)
controller = once(
    controller,
    '''        totalStoredVideos = stored.size\n        capturedTodayVideos = stored.count { it.capturedAt >= startOfDay }\n    }\n''',
    '''        totalStoredVideos = stored.size\n        capturedTodayVideos = stored.count { it.capturedAt >= startOfDay }\n        touchUi()\n    }\n''',
    'refresh revision'
)

controller = once(
    controller,
    '''        newsBusy = true\n        if (automatic) markAutoAttempt("news")\n''',
    '''        val knownBeforeRun = newsDb.listNews(10_000).mapTo(mutableSetOf()) { it.link }\n        newsNewLinks = emptySet()\n        newsBusy = true\n        if (automatic) markAutoAttempt("news")\n''',
    'news snapshot'
)
news_cb_old = '''                        newsProgress = update.progress\n                        if (update.items.isNotEmpty()) news = mergeNewsForUi(news, update.items)\n'''
news_cb_new = '''                        newsProgress = update.progress\n                        if (update.items.isNotEmpty()) {\n                            news = mergeNewsForUi(news, update.items)\n                            val currentNew = update.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()\n                            if (currentNew.isNotEmpty()) newsNewLinks = newsNewLinks + currentNew\n                        }\n'''
if controller.count(news_cb_old) != 2:
    raise SystemExit(f'Esperava 2 callbacks de notícia, achei {controller.count(news_cb_old)}')
controller = controller.replace(news_cb_old, news_cb_new)
controller = once(
    controller,
    '''                if (automatic) markNewsAutoCompleted(result)\n                refresh()\n''',
    '''                if (automatic) markNewsAutoCompleted(result)\n                newsNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()\n                refresh()\n''',
    'news final new set'
)

controller = once(
    controller,
    '''    fun searchDemand(demand: Demand) {\n        if (demandBusy) return\n        if (!ensureProxyReady(false)) return\n        demandBusy = true\n''',
    '''    fun searchDemand(demand: Demand) {\n        if (demandBusy) return\n        if (!ensureProxyReady(false)) return\n        val knownBeforeRun = newsDb.listNews(10_000).mapTo(mutableSetOf()) { it.link }\n        newsNewLinks = emptySet()\n        demandBusy = true\n''',
    'single demand snapshot'
)
controller = once(
    controller,
    '''                val result = newsRepository.searchDemand(demand)\n                refresh()\n''',
    '''                val result = newsRepository.searchDemand(demand)\n                newsNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()\n                refresh()\n''',
    'single demand new set'
)
controller = once(
    controller,
    '''        demandBusy = true\n        if (automatic) markAutoAttempt("demand")\n        status = "Buscando todas as demandas..."\n''',
    '''        val knownBeforeRun = newsDb.listNews(10_000).mapTo(mutableSetOf()) { it.link }\n        newsNewLinks = emptySet()\n        demandBusy = true\n        if (automatic) markAutoAttempt("demand")\n        status = "Buscando todas as demandas..."\n''',
    'all demand snapshot'
)
controller = once(
    controller,
    '''                val result = newsRepository.searchAllDemands()\n                if (automatic) markDemandAutoCompleted(result)\n                refresh()\n''',
    '''                val result = newsRepository.searchAllDemands()\n                if (automatic) markDemandAutoCompleted(result)\n                newsNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()\n                refresh()\n''',
    'all demand new set'
)

controller = once(
    controller,
    '''        videoBusy = true\n        if (automatic) markAutoAttempt("video")\n''',
    '''        val knownBeforeRun = videoDb.listAll(10_000).mapTo(mutableSetOf()) { it.link }\n        videoNewLinks = emptySet()\n        videoBusy = true\n        if (automatic) markAutoAttempt("video")\n''',
    'video snapshot'
)
video_cb_old = '''                        videoProgress = update.progress\n                        if (update.items.isNotEmpty()) videos = mergeVideosForUi(videos, update.items)\n'''
video_cb_new = '''                        videoProgress = update.progress\n                        if (update.items.isNotEmpty()) {\n                            videos = mergeVideosForUi(videos, update.items)\n                            val currentNew = update.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()\n                            if (currentNew.isNotEmpty()) videoNewLinks = videoNewLinks + currentNew\n                        }\n'''
if controller.count(video_cb_old) != 2:
    raise SystemExit(f'Esperava 2 callbacks de vídeo, achei {controller.count(video_cb_old)}')
controller = controller.replace(video_cb_old, video_cb_new)
controller = once(
    controller,
    '''                unstableVideoSources = result.unstableSources\n                if (automatic) markVideoAutoCompleted(result)\n                refresh()\n''',
    '''                unstableVideoSources = result.unstableSources\n                if (automatic) markVideoAutoCompleted(result)\n                videoNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()\n                refresh()\n''',
    'video final new set'
)

controller = once(
    controller,
    '''    fun clearNewsHistory() { newsDb.clearHistory(); refresh() }\n    fun clearVideoHistory() { videoDb.clear(); refresh() }\n''',
    '''    fun clearNewsHistory() {\n        newsDb.clearHistory()\n        news = emptyList()\n        newsHistory = emptyList()\n        newsNewLinks = emptySet()\n        status = "✓ Histórico de notícias limpo"\n        refresh()\n    }\n    fun clearVideoHistory() {\n        videoDb.clear()\n        videos = emptyList()\n        videoHistory = emptyList()\n        videoNewLinks = emptySet()\n        status = "✓ Histórico de vídeos limpo"\n        videoStatus = status\n        refresh()\n    }\n''',
    'clear history reactive'
)

# Relatórios automáticos são prefs; force uma revisão observável quando mudarem.
controller = once(
    controller,
    '''        prefs.edit()\n            .putLong(keyAttempt, System.currentTimeMillis())\n            .putString(keyError, "")\n            .apply()\n    }\n''',
    '''        prefs.edit()\n            .putLong(keyAttempt, System.currentTimeMillis())\n            .putString(keyError, "")\n            .apply()\n        touchUi()\n    }\n''',
    'auto attempt revision'
)
controller = once(
    controller,
    '''        prefs.edit().putString(key, error.take(180)).apply()\n    }\n''',
    '''        prefs.edit().putString(key, error.take(180)).apply()\n        touchUi()\n    }\n''',
    'auto fail revision'
)
for label, tail in [
    ('news auto completed revision', 'if (result.errors > 0) "${result.errors} consulta(s) com falha" else "")'),
    ('demand auto completed revision', 'if (result.errors > 0) "${result.errors} demanda(s) com falha" else "")'),
    ('video auto completed revision', 'if (result.errors > 0) "${result.errors} fonte(s) instável(is)" else "")'),
]:
    old = f'''            .putString(KEY_{'NEWS_ERROR' if 'news ' in label else 'DEMAND_ERROR' if 'demand ' in label else 'VIDEO_ERROR'}, {tail}\n            .apply()\n    }}\n'''
    new = f'''            .putString(KEY_{'NEWS_ERROR' if 'news ' in label else 'DEMAND_ERROR' if 'demand ' in label else 'VIDEO_ERROR'}, {tail}\n            .apply()\n        touchUi()\n    }}\n'''
    controller = once(controller, old, new, label)

controller_path.write_text(controller, encoding='utf-8')

# ---------- Main: tema naval + estado de Histórico/NOVO ----------
main = main_path.read_text(encoding='utf-8')
main = once(
    main,
    '''private val AppBlue = Color(0xFF155EEF)\nprivate val AppBlueStrong = Color(0xFF0B4AB8)\nprivate val AppBlueSoft = Color(0xFFEAF2FF)\nprivate val AppNavy = Color(0xFF102A43)\nprivate val AppMuted = Color(0xFF66788A)\nprivate val AppBg = Color(0xFFF4F7FB)\nprivate val AppPanel = Color(0xFFFFFFFF)\nprivate val AppLine = Color(0xFFD9E2EC)\nprivate val AppGreen = Color(0xFF128A4B)\nprivate val AppRed = Color(0xFFD92D20)\nprivate val AppOrange = Color(0xFFE56A13)\nprivate val AppSlate = Color(0xFF334E68)\n''',
    '''// Paleta inspirada no universo naval brasileiro: azul-marinho, azul oceânico, branco e ouro.\n// Não utiliza brasões nem identidade oficial; é uma linguagem visual própria do Monitor.\nprivate val AppBlue = Color(0xFF0B5EA8)\nprivate val AppBlueStrong = Color(0xFF063B6F)\nprivate val AppBlueSoft = Color(0xFFE8F1F8)\nprivate val AppNavy = Color(0xFF061D34)\nprivate val AppNavyMid = Color(0xFF0A2D50)\nprivate val AppNavySoft = Color(0xFF123F6C)\nprivate val AppGold = Color(0xFFC7A347)\nprivate val AppGoldSoft = Color(0xFFF7F0DF)\nprivate val AppMuted = Color(0xFF60758A)\nprivate val AppBg = Color(0xFFF2F5F8)\nprivate val AppPanel = Color(0xFFFFFFFF)\nprivate val AppLine = Color(0xFFD3DEE8)\nprivate val AppGreen = Color(0xFF168357)\nprivate val AppRed = Color(0xFFC93C36)\nprivate val AppOrange = Color(0xFFB97824)\nprivate val AppSlate = Color(0xFF334E68)\n''',
    'navy palette'
)
main = once(
    main,
    '''    secondary = Color(0xFF526785),\n''',
    '''    secondary = AppGold,\n    onSecondary = AppNavy,\n''',
    'theme secondary gold'
)

new_sidebar = r'''@Composable
private fun Sidebar(selected: Section, onSection: (Section) -> Unit, c: DesktopController) {
    Surface(
        modifier = Modifier.width(258.dp).fillMaxHeight(),
        color = AppNavy,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = AppGold
                ) {
                    Icon(
                        Icons.Default.Newspaper,
                        contentDescription = null,
                        tint = AppNavy,
                        modifier = Modifier.padding(11.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("MONITOR", fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
                    Text("Inteligência de mídia", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .68f))
                }
            }

            Text(
                "CENTRAL DE MONITORAMENTO",
                style = MaterialTheme.typography.labelSmall,
                color = AppGold,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp, top = 16.dp, bottom = 8.dp)
            )

            Section.entries.forEach { item ->
                val active = selected == item
                Surface(
                    color = if (active) Color.White.copy(alpha = .11f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSection(item) }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.width(4.dp).height(44.dp)
                                .background(if (active) AppGold else Color.Transparent, RoundedCornerShape(0.dp, 4.dp, 4.dp, 0.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (active) AppGold else Color.White.copy(alpha = .70f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(11.dp))
                        Text(
                            item.label,
                            color = if (active) Color.White else Color.White.copy(alpha = .82f),
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        val badge = when (item) {
                            Section.NEWS -> c.newsNewLinks.size
                            Section.DEMANDS -> c.demands.count { it.active }
                            else -> 0
                        }
                        if (badge > 0) {
                            Surface(shape = CircleShape, color = if (active) AppGold else Color.White.copy(alpha = .10f)) {
                                Text(
                                    badge.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) AppNavy else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = .07f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(if (c.automaticMonitoring) Color(0xFF51D88A) else AppGold))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (c.automaticMonitoring) "Sistema operacional" else "Automação pausada",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Text("Dados locais • modo portátil", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .62f))
                    Text(
                        if (DesktopProxyManager.load(c.context).enabled) {
                            if (DesktopProxyManager.isReady(c.context)) "Proxy autenticado • pronto" else "Proxy autenticado • configurar"
                        } else "Conexão direta • proxy desativado",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (DesktopProxyManager.isReady(c.context)) Color.White.copy(alpha = .66f) else AppGold
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .10f))
                    Text("Windows Portable • v4.0.2", style = MaterialTheme.typography.labelSmall, color = AppGold)
                }
            }
        }
    }
}'''
main = replace_block(main, '@Composable\nprivate fun Sidebar', '@Composable\nprivate fun ModernTopBar', new_sidebar, 'sidebar')

new_topbar = r'''@Composable
private fun ModernTopBar(c: DesktopController, section: Section, nowMs: Long) {
    val proxy = DesktopProxyManager.load(c.context)
    val proxyReady = DesktopProxyManager.isReady(c.context)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(if (section == Section.NEWS) 84.dp else 96.dp).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(4.dp).height(44.dp).background(AppGold, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "CENTRAL DE INTELIGÊNCIA DE MÍDIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppGold,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (section == Section.HOME) "Monitor de Notícias" else section.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppNavy,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    when (section) {
                        Section.HOME -> "Visão operacional de notícias, vídeos, demandas e fontes em tempo real"
                        Section.NEWS -> "Busca e acompanhamento de matérias com atualização contínua"
                        Section.VIDEOS -> "Vídeos, telejornais, Globoplay e fontes oficiais monitoradas"
                        Section.TERMS -> "Termos independentes para inteligência de notícias e vídeos"
                        Section.DEMANDS -> "Assuntos prioritários acompanhados por veículo"
                        Section.SOURCES -> "Fontes nacionais, regionais e mídias especializadas"
                        Section.HISTORY -> "Arquivo local, exportação e gestão do histórico monitorado"
                        Section.SETTINGS -> "Automação, proxy, inicialização e operação do aplicativo"
                    },
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TopStatusPill(
                icon = Icons.Default.Security,
                text = when {
                    !proxy.enabled -> "Proxy desativado"
                    proxyReady -> "Proxy pronto"
                    else -> "Proxy requer configuração"
                },
                active = !proxy.enabled || proxyReady,
                warning = proxy.enabled && !proxyReady
            )
            Spacer(Modifier.width(10.dp))
            TopStatusPill(
                icon = Icons.Default.Radar,
                text = if (c.automaticMonitoring) "Automação ativa" else "Automação pausada",
                active = c.automaticMonitoring,
                warning = !c.automaticMonitoring
            )
            Spacer(Modifier.width(18.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(SimpleDateFormat("EEE, dd 'de' MMMM", Locale("pt", "BR")).format(Date(nowMs)), color = AppMuted, style = MaterialTheme.typography.labelMedium)
                Text(SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(nowMs)), color = AppNavy, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            }
        }
        HorizontalDivider(color = AppGold.copy(alpha = .32f))
    }
}'''
main = replace_block(main, '@Composable\nprivate fun ModernTopBar', '@Composable\nprivate fun TopStatusPill', new_topbar, 'topbar')

# NOVO por execução nas telas.
main = once(main, 'private fun NewsCard(n: News) {', 'private fun NewsCard(n: News, isNew: Boolean = false) {', 'NewsCard signature')
main = once(main, 'if (System.currentTimeMillis() - n.capturedAt < 24L * 60 * 60 * 1000)', 'if (isNew)', 'news new condition')
main = once(main, 'private fun VideoCard(v: VideoItem) {', 'private fun VideoCard(v: VideoItem, isNew: Boolean = false) {', 'VideoCard signature')
main = once(main, 'if (System.currentTimeMillis() - v.capturedAt < 24L * 60 * 60 * 1000)', 'if (isNew)', 'video new condition')
main = main.replace('NewsCard(it)', 'NewsCard(it, it.link in c.newsNewLinks)')
main = main.replace('VideoCard(it)', 'VideoCard(it, it.link in c.videoNewLinks)')
main = once(main, 'Section.NEWS -> c.news.count { System.currentTimeMillis() - it.capturedAt < 24L * 60 * 60 * 1000 }', 'Section.NEWS -> c.newsNewLinks.size', 'sidebar new badge old fallback') if 'Section.NEWS -> c.news.count { System.currentTimeMillis() - it.capturedAt < 24L * 60 * 60 * 1000 }' in main else main
main = main.replace('val matches = c.newsDb.listNews(5000).filter', 'val matches = c.newsHistory.filter')

new_history = r'''@Composable
private fun HistoryScreen(c: DesktopController) {
    var tab by remember { mutableStateOf(0) }
    val newsAll = c.newsHistory
    val videoAll = c.videoHistory
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(compact = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Arquivo monitorado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppNavy)
                    Text("Alterações no histórico aparecem imediatamente nesta tela.", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                }
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Notícias (${newsAll.size})") }, leadingIcon = { Icon(Icons.Default.Article, null) })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Vídeos (${videoAll.size})") }, leadingIcon = { Icon(Icons.Default.PlayCircle, null) })
                if (tab == 0) {
                    OutlinedButton(onClick = { val f = c.exportNewsHistoryCsv(); openFolder(f.parentFile) }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.IosShare, null); Spacer(Modifier.width(5.dp)); Text("Exportar CSV")
                    }
                }
                OutlinedButton(
                    onClick = { if (tab == 0) c.clearNewsHistory() else c.clearVideoHistory() },
                    enabled = if (tab == 0) newsAll.isNotEmpty() else videoAll.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppRed)
                ) {
                    Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Limpar histórico")
                }
            }
        }
        StatusStrip(c.status, false)
        if (tab == 0) {
            if (newsAll.isEmpty()) {
                EmptyHistoryState("Nenhuma notícia armazenada", "As próximas matérias capturadas aparecerão aqui em tempo real.", Icons.Default.Article)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(newsAll, key = { it.link }) { NewsCard(it, it.link in c.newsNewLinks) }
                }
            }
        } else {
            if (videoAll.isEmpty()) {
                EmptyHistoryState("Nenhum vídeo armazenado", "Os próximos vídeos relevantes aparecerão aqui em tempo real.", Icons.Default.PlayCircle)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(videoAll, key = { it.link }) { VideoCard(it, it.link in c.videoNewLinks) }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyHistoryState(title: String, subtitle: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine)
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = AppBlueSoft, modifier = Modifier.size(70.dp)) {
                Icon(icon, null, tint = AppBlue, modifier = Modifier.padding(18.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppNavy)
            Text(subtitle, color = AppMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}'''
main = replace_block(main, '@Composable\nprivate fun HistoryScreen', '@Composable\nprivate fun SettingsScreen', new_history, 'history screen')

# Relatórios em Configurações também recebem revisão observável das prefs.
main = once(main, 'Section.SETTINGS -> SettingsScreen(c)', 'Section.SETTINGS -> SettingsScreen(c, c.uiRevision)', 'settings call revision')
main = once(main, 'private fun SettingsScreen(c: DesktopController) {', 'private fun SettingsScreen(c: DesktopController, revision: Int) {\n    @Suppress("UNUSED_VARIABLE") val liveRevision = revision', 'settings revision')

# Refinamentos dos cards no tema naval.
main = main.replace('shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)', 'shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)')
main = main.replace('private fun Panel(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable () -> Unit) { Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)', 'private fun Panel(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable () -> Unit) { Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)')
main = main.replace('MetricCard("Fontes", DesktopSourceCatalog.all.size.toString(), "${DesktopSourceCatalog.specialized.size} especializadas", Icons.Default.Storage, AppBlueStrong', 'MetricCard("Fontes", DesktopSourceCatalog.all.size.toString(), "${DesktopSourceCatalog.specialized.size} especializadas", Icons.Default.Storage, AppGold')
main = main.replace('MetricCard("Demandas", c.demands.size.toString(), "${c.demands.count { it.active }} ativas", Icons.Default.Assignment, AppOrange', 'MetricCard("Demandas", c.demands.size.toString(), "${c.demands.count { it.active }} ativas", Icons.Default.Assignment, AppGold')

main_path.write_text(main, encoding='utf-8')

# ---------- Cartão de automação alinhado ao novo tema ----------
auto = auto_path.read_text(encoding='utf-8')
auto = once(auto, 'private val AutoNews = Color(0xFF155EEF)\nprivate val AutoDemand = Color(0xFFE56A13)\nprivate val AutoVideo = Color(0xFF6941C6)\nprivate val AutoGreen = Color(0xFF128A4B)\nprivate val AutoMuted = Color(0xFF66788A)\nprivate val AutoLine = Color(0xFFD9E2EC)\n', 'private val AutoNews = Color(0xFF0B5EA8)\nprivate val AutoDemand = Color(0xFFC7A347)\nprivate val AutoVideo = Color(0xFF2C7DA0)\nprivate val AutoGreen = Color(0xFF168357)\nprivate val AutoMuted = Color(0xFF60758A)\nprivate val AutoLine = Color(0xFFD3DEE8)\n', 'automation navy palette')
auto = auto.replace('color = Color(0xFFEAF2FF)', 'color = Color(0xFFE8F1F8)')
auto = auto.replace('color = Color(0xFFFAFCFF)', 'color = Color(0xFFF8FAFC)')
auto_path.write_text(auto, encoding='utf-8')

print('PATCH_OK')

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt"
V30 = ROOT / "app/src/main/java/br/com/monitordenoticias/android/V30Screens.kt"


def replace_between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    if a < 0:
        raise SystemExit(f"missing start marker: {label}")
    b = text.find(end, a + len(start))
    if b < 0:
        raise SystemExit(f"missing end marker: {label}")
    return text[:a] + replacement.rstrip() + "\n\n" + text[b:]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing marker: {label}")
    return text.replace(old, new, 1)


main = MAIN.read_text()

if "import androidx.compose.ui.graphics.Brush" not in main:
    main = replace_once(
        main,
        "import androidx.compose.ui.graphics.Color\n",
        "import androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color\n",
        "Brush import",
    )

main = main.replace("private val V28Bg = Color(0xFF07111F)", "private val V28Bg = Color(0xFF03111F)")
main = main.replace("private val V28Surface = Color(0xFF0C1828)", "private val V28Surface = Color(0xFF071D31)")
main = main.replace("private val V28Surface2 = Color(0xFF12243A)", "private val V28Surface2 = Color(0xFF0B2943)")
main = main.replace("private val V28Selected = Color(0xFF153B60)", "private val V28Selected = Color(0xFF0C3D67)")
main = main.replace("private val V28Accent = Color(0xFF58A6FF)", "private val V28Accent = Color(0xFF35A7FF)")
main = main.replace("private val V28Mint = Color(0xFF35CFA0)", "private val V28Mint = Color(0xFF24E0B3)")
main = main.replace("private val V28Amber = Color(0xFFF0B35D)", "private val V28Amber = Color(0xFFFFB657)")
main = main.replace("private val V28Purple = Color(0xFF9B8CFF)", "private val V28Purple = Color(0xFFA77BFF)")
main = main.replace("private val V28Divider = Color(0xFF203449)", "private val V28Divider = Color(0xFF174563)")

main = replace_once(
    main,
    '            if (status.isNotBlank() && status != "Pronto") V28StatusStrip(status)\n            if (news.searchProgress.active && section != V28Section.HOME && section != V28Section.VIDEOS) V30CompactProgress(news.searchProgress)\n',
    '            if (status.isNotBlank() && status != "Pronto") V28StatusStrip(status)\n            if (section != V28Section.HOME && section != V28Section.VIDEOS) V42NewsPulse(news)\n            if (news.searchProgress.active && section != V28Section.HOME && section != V28Section.VIDEOS) V30CompactProgress(news.searchProgress)\n',
    "cross-screen pulse",
)

TOP = r'''@Composable
private fun V28TopBar(section: V28Section) {
    val title = when (section) {
        V28Section.HOME -> "Monitor de Notícias"
        V28Section.VIDEOS -> "Monitor de Vídeos"
        V28Section.SOURCES -> "Fontes"
        V28Section.DEMANDS -> "Demandas"
        V28Section.PERIOD -> "Período"
        V28Section.HISTORY -> "Histórico"
        V28Section.TERMS -> "Termos"
        V28Section.SETTINGS -> "Configurações"
    }
    val subtitle = when (section) {
        V28Section.HOME -> "Inteligência de mídia em tempo real"
        V28Section.VIDEOS -> "TV, portais, YouTube e conteúdo audiovisual"
        V28Section.SOURCES -> "Notícias e fontes de vídeo"
        V28Section.DEMANDS -> "Alertas por veículo e assunto"
        V28Section.PERIOD -> "Defina o intervalo da pesquisa"
        V28Section.HISTORY -> "Arquivo das matérias capturadas"
        V28Section.TERMS -> "Termos separados para Notícias e Vídeos"
        V28Section.SETTINGS -> "Saúde, automação e preferências"
    }
    Surface(color = V28Bg, modifier = Modifier.statusBarsPadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(V28Surface, V28Selected.copy(alpha = .82f), V28Bg)
                    )
                )
        ) {
            Icon(
                Icons.Outlined.Radar,
                null,
                tint = V28Accent.copy(alpha = .055f),
                modifier = Modifier.size(112.dp).align(Alignment.CenterEnd).offset(x = 12.dp)
            )
            Row(
                Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = V28Accent.copy(alpha = .10f),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, V28Accent.copy(alpha = .55f))
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            if (section == V28Section.VIDEOS) Icons.Outlined.PlayCircle else Icons.Outlined.Radar,
                            null,
                            tint = V28Accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = V28Text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(
                    color = V28Accent.copy(alpha = .08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, V28Accent.copy(alpha = .28f))
                ) {
                    Text("v${BuildConfig.VERSION_NAME}", color = V28Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                }
            }
            Text(
                "VIGILÂNCIA  •  ANÁLISE  •  INFORMAÇÃO  •  DECISÃO",
                color = V28Accent.copy(alpha = .92f),
                fontSize = 8.5.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp)
            )
            Text(
                "INFORMAÇÃO QUE FORTALECE DECISÕES",
                color = V28Text2.copy(alpha = .72f),
                fontSize = 7.2.sp,
                letterSpacing = .8.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp)
            )
        }
    }
}'''
main = replace_between(main, "@Composable\nprivate fun V28TopBar(section: V28Section) {", "@Composable\nprivate fun V28BottomBar", TOP, "top bar")

BOTTOM = r'''@Composable
private fun V28BottomBar(section: V28Section, onSection: (V28Section) -> Unit, onMore: () -> Unit) {
    val nav = listOf(
        V28NavItem(V28Section.HOME, "Início", Icons.Outlined.Home),
        V28NavItem(V28Section.VIDEOS, "Vídeos", Icons.Outlined.SmartDisplay),
        V28NavItem(V28Section.SOURCES, "Fontes", Icons.Outlined.Layers),
        V28NavItem(V28Section.DEMANDS, "Demandas", Icons.Outlined.NotificationsNone)
    )
    val moreSelected = section in setOf(V28Section.PERIOD, V28Section.HISTORY, V28Section.TERMS, V28Section.SETTINGS)
    Surface(
        color = V28Surface.copy(alpha = .98f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, V28Accent.copy(alpha = .28f)),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 5.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            nav.forEach { item ->
                V28NavButton(item.label, item.icon, section == item.section, Modifier.weight(1f)) { onSection(item.section) }
            }
            V28NavButton("Mais", Icons.Outlined.MoreHoriz, moreSelected, Modifier.weight(1f), onMore)
        }
    }
}'''
main = replace_between(main, "@Composable\nprivate fun V28BottomBar", "@Composable\nprivate fun V28NavButton", BOTTOM, "bottom bar")

NAV = r'''@Composable
private fun V28NavButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) V28Accent.copy(alpha = .12f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(1.dp, V28Accent.copy(alpha = .48f)) else null,
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = if (selected) V28Accent else V28Text2, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (selected) V28Accent else V28Text2, fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}'''
main = replace_between(main, "@Composable\nprivate fun V28NavButton", "@Composable\nprivate fun V28StatusStrip", NAV, "nav button")

PULSE = r'''@Composable
private fun V42NewsPulse(s: AppState) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
    val started = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)
    val completed = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)
    val newCount = s.news.count { v401SettingsInRun(it.capturedAt, started, completed) }
    val label = if (newCount > 0) "$newCount nova(s) notícia(s) encontrada(s)" else "${s.news.size} notícia(s) no escopo atual"
    Surface(
        color = V28Mint.copy(alpha = .055f),
        border = BorderStroke(1.dp, V28Mint.copy(alpha = .24f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(V28Mint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Article, null, tint = V28Mint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = if (newCount > 0) V28Mint else V28Text2, fontSize = 11.5.sp, fontWeight = if (newCount > 0) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
            if (completed > 0L) Text(v28DateTime(completed), color = V28Text2, fontSize = 9.5.sp)
        }
    }
}'''
main = replace_once(main, "@Composable\nprivate fun V28StatusStrip", PULSE + "\n\n@Composable\nprivate fun V28StatusStrip", "pulse helper")

SETTINGS = r'''@Composable
private fun V28Settings(s: AppState, vm: MonitorViewModel, videos: VideoState) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var autoConfig by remember { mutableStateOf(AutoSearchSettings.read(context)) }
    val prefs = remember(refreshKey) { context.getSharedPreferences(BackgroundMonitor.PREFS, 0) }
    val newsAttempt = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)
    val newsCompleted = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)
    val newsNewVisible = s.news.count { v401SettingsInRun(it.capturedAt, newsAttempt, newsCompleted) }
    val demandAttempt = prefs.getLong(AutoRunLog.KEY_DEMAND_ATTEMPT_AT, 0L)
    val demandCompleted = prefs.getLong(AutoRunLog.KEY_DEMAND_COMPLETED_AT, 0L)
    val videoAttempt = prefs.getLong(VideoAutoRunLog.KEY_ATTEMPT_AT, 0L)
    val videoCompleted = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)
    val videoFound = prefs.getInt(VideoAutoRunLog.KEY_FOUND, 0)
    val videoNew = videos.items.count { v401SettingsInRun(it.capturedAt, videoAttempt, videoCompleted) }
    val videoRelevant = prefs.getInt(VideoAutoRunLog.KEY_NEW_RELEVANT, 0)
    val videoErrors = prefs.getInt(VideoAutoRunLog.KEY_ERRORS, 0)
    val videoError = prefs.getString(VideoAutoRunLog.KEY_ERROR_TEXT, "").orEmpty()
    val powerManager = context.getSystemService(PowerManager::class.java)
    val unrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val latestAttempt = maxOf(newsAttempt, demandAttempt, videoAttempt)
    val stale = latestAttempt > 0 && System.currentTimeMillis() - latestAttempt > 2L * 60L * 60L * 1000L

    fun reschedule() {
        BackgroundMonitor.scheduleAll(context)
        VideoBackgroundMonitor.scheduleAll(context)
        autoConfig = AutoSearchSettings.read(context)
        refreshKey++
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = if (stale) V28Amber.copy(alpha = .08f) else V28Mint.copy(alpha = .07f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, if (stale) V28Amber.copy(alpha = .42f) else V28Mint.copy(alpha = .34f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(V28Mint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(if (stale) Icons.Outlined.WarningAmber else Icons.Outlined.Radar, null, tint = if (stale) V28Amber else V28Mint, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        V28Badge(if (stale) "VERIFICAR AUTOMAÇÃO" else "MONITOR ATIVO", if (stale) V28Amber else V28Mint)
                        Spacer(Modifier.height(6.dp))
                        Text(if (stale) "Atenção ao monitor" else "Monitor funcionando", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (latestAttempt > 0) "Último disparo: ${v28DateTime(latestAttempt)}" else "Aguardando primeira execução automática", color = V28Text2, fontSize = 11.5.sp)
                    }
                    FilledIconButton(onClick = { refreshKey++; autoConfig = AutoSearchSettings.read(context) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Refresh, "Atualizar")
                    }
                }
            }
        }

        item {
            Column {
                Text("Busca automática", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Configure cada monitor separadamente. Alterações são salvas e reagendadas na hora.", color = V28Text2, fontSize = 11.sp)
            }
        }
        item {
            V42AutomationCard(
                title = "Notícias automáticas",
                subtitle = "Google Notícias e varreduras diretas",
                icon = Icons.Outlined.Article,
                color = V28Accent,
                enabled = autoConfig.newsEnabled,
                intervalMinutes = autoConfig.newsIntervalMinutes,
                lastAttempt = newsAttempt,
                onEnabledChange = {
                    AutoSearchSettings.setNewsEnabled(context, it)
                    reschedule()
                },
                onIntervalChange = {
                    AutoSearchSettings.setNewsInterval(context, it)
                    vm.setInterval(it)
                    reschedule()
                }
            )
        }
        item {
            V42AutomationCard(
                title = "Demandas automáticas",
                subtitle = "Executa todas as Demandas cadastradas",
                icon = Icons.Outlined.NotificationsActive,
                color = V28Amber,
                enabled = autoConfig.demandsEnabled,
                intervalMinutes = autoConfig.demandsIntervalMinutes,
                lastAttempt = demandAttempt,
                onEnabledChange = {
                    AutoSearchSettings.setDemandsEnabled(context, it)
                    reschedule()
                },
                onIntervalChange = {
                    AutoSearchSettings.setDemandsInterval(context, it)
                    reschedule()
                }
            )
        }
        item {
            V42AutomationCard(
                title = "Vídeos automáticos",
                subtitle = "TV, portais, YouTube e fontes selecionadas",
                icon = Icons.Outlined.SmartDisplay,
                color = V28Purple,
                enabled = autoConfig.videosEnabled,
                intervalMinutes = autoConfig.videosIntervalMinutes,
                lastAttempt = videoAttempt,
                onEnabledChange = {
                    AutoSearchSettings.setVideosEnabled(context, it)
                    reschedule()
                },
                onIntervalChange = {
                    AutoSearchSettings.setVideosInterval(context, it)
                    reschedule()
                }
            )
        }

        item {
            Column {
                Text("Últimos ciclos", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("Resumo das execuções automáticas mais recentes.", color = V28Text2, fontSize = 10.8.sp)
            }
        }
        item { V28ReportCard("Notícias", Icons.Outlined.Article, V28Accent, newsAttempt, newsCompleted, "${prefs.getInt(AutoRunLog.KEY_NEWS_FOUND, 0)} resultado(s) • $newsNewVisible nova(s)", prefs.getString(AutoRunLog.KEY_NEWS_ERROR_TEXT, "").orEmpty()) }
        item { V28ReportCard("Demandas", Icons.Outlined.NotificationsActive, V28Amber, demandAttempt, demandCompleted, "${prefs.getInt(AutoRunLog.KEY_DEMAND_CHECKED, 0)} demanda(s) • ${prefs.getInt(AutoRunLog.KEY_DEMAND_NEW, 0)} nova(s)", prefs.getString(AutoRunLog.KEY_DEMAND_ERROR_TEXT, "").orEmpty()) }
        item { V28ReportCard("Vídeos", Icons.Outlined.SmartDisplay, V28Purple, videoAttempt, videoCompleted, "$videoFound detectado(s) • $videoNew novo(s) • $videoRelevant relevante(s) • $videoErrors falha(s)", videoError) }

        item {
            Surface(color = V28Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, V28Divider), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background((if (unrestricted) V28Mint else V28Amber).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.BatterySaver, null, tint = if (unrestricted) V28Mint else V28Amber)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bateria e segundo plano", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(if (unrestricted) "Sem restrição de bateria detectada" else "O Android pode atrasar tarefas em repouso", color = V28Text2, fontSize = 11.5.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.SettingsSuggest, null); Spacer(Modifier.width(7.dp)); Text("Abrir ajustes do aplicativo")
                    }
                }
            }
        }
        item {
            Surface(color = V28Accent.copy(alpha = .06f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, V28Accent.copy(alpha = .20f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Monitor de Notícias ${BuildConfig.VERSION_NAME}", color = V28Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Central integrada de notícias, demandas e vídeos. ${videos.selectedSourceIds.size} fonte(s) de vídeo ativa(s).", color = V28Text2, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}'''
main = replace_between(main, "@Composable\nprivate fun V28Settings", "@Composable\nprivate fun V28ReportCard", SETTINGS, "settings")

REPORTS = r'''@Composable
private fun V28ReportCard(title: String, icon: ImageVector, color: Color, attemptAt: Long, completedAt: Long, summary: String, error: String) {
    Surface(color = V28Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, color.copy(alpha = .22f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(21.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (error.isNotBlank()) Icons.Outlined.ErrorOutline else if (completedAt > 0) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, null, tint = if (error.isNotBlank()) V28Red else if (completedAt > 0) V28Mint else V28Text2)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = V28Surface2.copy(alpha = .65f), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Última tentativa", color = V28Text2, fontSize = 9.5.sp)
                        Text(if (attemptAt > 0) v28DateTime(attemptAt) else "—", fontSize = 11.2.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(color = V28Surface2.copy(alpha = .65f), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Última conclusão", color = V28Text2, fontSize = 9.5.sp)
                        Text(if (completedAt > 0) v28DateTime(completedAt) else "—", fontSize = 11.2.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (completedAt > 0) { Spacer(Modifier.height(8.dp)); Text(summary, color = color, fontSize = 11.4.sp, fontWeight = FontWeight.Bold) }
            if (error.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(error, color = V28Red, fontSize = 10.8.sp) }
        }
    }
}

@Composable
private fun V42AutomationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    intervalMinutes: Int,
    lastAttempt: Long,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    Surface(
        color = V28Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = .42f) else V28Divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = V28Text2, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                V28Badge(if (enabled) "ATIVO" else "PAUSADO", if (enabled) V28Mint else V28Text2)
                Spacer(Modifier.width(8.dp))
                Text("A cada ${v42IntervalLabel(intervalMinutes)}", color = color, fontSize = 11.2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (lastAttempt > 0) Text("último ${v28DateTime(lastAttempt)}", color = V28Text2, fontSize = 9.3.sp)
            }
            Spacer(Modifier.height(9.dp))
            Text("Intervalo", color = V28Text2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AutoSearchSettings.intervalOptions.forEach { minutes ->
                    V28Chip(v42IntervalLabel(minutes), intervalMinutes == minutes) { onIntervalChange(minutes) }
                }
            }
            if (!enabled) {
                Spacer(Modifier.height(7.dp))
                Text("A busca manual continua disponível mesmo com a automação pausada.", color = V28Text2, fontSize = 9.8.sp)
            }
        }
    }
}

private fun v42IntervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}'''
main = replace_between(main, "@Composable\nprivate fun V28ReportCard", "@Composable\nprivate fun V28NewsCard", REPORTS, "report cards")

MAIN.write_text(main)

v30 = V30.read_text()
v30 = v30.replace("private val V30Bg = Color(0xFF07111F)", "private val V30Bg = Color(0xFF03111F)")
v30 = v30.replace("private val V30Surface = Color(0xFF0C1828)", "private val V30Surface = Color(0xFF071D31)")
v30 = v30.replace("private val V30Surface2 = Color(0xFF12243A)", "private val V30Surface2 = Color(0xFF0B2943)")
v30 = v30.replace("private val V30Selected = Color(0xFF153B60)", "private val V30Selected = Color(0xFF0C3D67)")
v30 = v30.replace("private val V30Accent = Color(0xFF58A6FF)", "private val V30Accent = Color(0xFF35A7FF)")
v30 = v30.replace("private val V30Mint = Color(0xFF35CFA0)", "private val V30Mint = Color(0xFF24E0B3)")
v30 = v30.replace("private val V30Amber = Color(0xFFF0B35D)", "private val V30Amber = Color(0xFFFFB657)")
v30 = v30.replace("private val V30Purple = Color(0xFF9B8CFF)", "private val V30Purple = Color(0xFFA77BFF)")
v30 = v30.replace("private val V30Divider = Color(0xFF203449)", "private val V30Divider = Color(0xFF174563)")
V30.write_text(v30)

print("v4.2.0 UI patch applied")

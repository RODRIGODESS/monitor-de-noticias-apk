package br.com.monitordenoticias.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import br.com.monitordenoticias.android.*
import kotlinx.coroutines.delay
import java.awt.Desktop as AwtDesktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Paleta inspirada no universo naval brasileiro: azul-marinho, azul oceânico, branco e ouro.
// Não utiliza brasões nem identidade oficial; é uma linguagem visual própria do Monitor.
private val AppBlue = Color(0xFF0B5EA8)
private val AppBlueStrong = Color(0xFF063B6F)
private val AppBlueSoft = Color(0xFFE8F1F8)
private val AppNavy = Color(0xFF061D34)
private val AppNavyMid = Color(0xFF0A2D50)
private val AppNavySoft = Color(0xFF123F6C)
private val AppGold = Color(0xFFC7A347)
private val AppGoldSoft = Color(0xFFF7F0DF)
private val AppMuted = Color(0xFF60758A)
private val AppBg = Color(0xFFF2F5F8)
private val AppPanel = Color(0xFFFFFFFF)
private val AppLine = Color(0xFFD3DEE8)
private val AppGreen = Color(0xFF168357)
private val AppRed = Color(0xFFC93C36)
private val AppOrange = Color(0xFFB97824)
private val AppSlate = Color(0xFF334E68)

private val ModernLightScheme = lightColorScheme(
    primary = AppBlue,
    onPrimary = Color.White,
    primaryContainer = AppBlueSoft,
    onPrimaryContainer = AppNavy,
    secondary = AppGold,
    onSecondary = AppNavy,
    background = AppBg,
    onBackground = AppNavy,
    surface = AppPanel,
    onSurface = AppNavy,
    surfaceVariant = Color(0xFFF0F5FB),
    onSurfaceVariant = AppMuted,
    outline = AppLine,
    error = AppRed
)

private enum class Section(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home),
    NEWS("Notícias", Icons.Default.Article),
    VIDEOS("Vídeos", Icons.Default.PlayCircle),
    TERMS("Termos", Icons.Default.Search),
    DEMANDS("Demandas", Icons.Default.NotificationsActive),
    SOURCES("Fontes", Icons.Default.Public),
    HISTORY("Histórico", Icons.Default.History),
    SETTINGS("Configurações", Icons.Default.Settings)
}

fun main() = application {
    val trayState = rememberTrayState()
    var windowVisible by remember { mutableStateOf(true) }
    val appIcon = painterResource("monitor_icon.svg")
    val controller = remember {
        DesktopController { title, message ->
            runCatching { trayState.sendNotification(Notification(title, message)) }
        }
    }

    Tray(
        state = trayState,
        icon = appIcon,
        tooltip = "Monitor de Notícias v4.0.2",
        menu = {
            Item("Abrir Monitor de Notícias", onClick = { windowVisible = true })
            Separator()
            Item("Buscar notícias agora", onClick = { controller.searchNews() })
            Item("Buscar vídeos agora", onClick = { controller.searchVideos() })
            Item("Buscar demandas agora", onClick = { controller.searchAllDemands() })
            Separator()
            Item("Sair", onClick = { controller.close(); exitApplication() })
        }
    )

    Window(
        visible = windowVisible,
        onCloseRequest = { windowVisible = false },
        title = "Monitor de Notícias • Windows Portable v4.0.2",
        icon = appIcon,
        state = rememberWindowState(width = 1600.dp, height = 960.dp)
    ) {
        MaterialTheme(colorScheme = ModernLightScheme) {
            App(controller)
        }
    }
}

@Composable
private fun App(c: DesktopController) {
    var section by remember { mutableStateOf(Section.HOME) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    Surface(Modifier.fillMaxSize(), color = AppBg) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(section, onSection = { section = it }, c = c)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ModernTopBar(c, section, nowMs)
                Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
                    when (section) {
                        Section.HOME -> HomeScreen(c, nowMs)
                        Section.NEWS -> NewsScreen(c, nowMs)
                        Section.VIDEOS -> VideosScreen(c, nowMs)
                        Section.TERMS -> TermsScreen(c)
                        Section.DEMANDS -> DemandsScreen(c, nowMs)
                        Section.SOURCES -> SourcesScreen(c)
                        Section.HISTORY -> HistoryScreen(c)
                        Section.SETTINGS -> SettingsScreen(c, c.uiRevision)
                    }
                }
            }
        }
    }
}

@Composable
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
}

@Composable
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
}

@Composable
private fun TopStatusPill(icon: ImageVector, text: String, active: Boolean, warning: Boolean = false) {
    val bg = when {
        warning -> Color(0xFFFFF4E8)
        active -> Color(0xFFEAF8EF)
        else -> Color(0xFFF0F4F8)
    }
    val fg = when {
        warning -> AppOrange
        active -> AppGreen
        else -> AppSlate
    }
    Surface(shape = RoundedCornerShape(12.dp), color = bg) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeScreen(c: DesktopController, nowMs: Long) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Notícias 24h", c.news.size.toString(), "na janela atual", Icons.Default.Article, AppBlue, Modifier.weight(1f))
                MetricCard("Vídeos armazenados", c.totalStoredVideos.toString(), "relevantes na base", Icons.Default.PlayCircle, Color(0xFF6441D8), Modifier.weight(1f))
                MetricCard("Vídeos hoje", c.capturedTodayVideos.toString(), "capturados hoje", Icons.Default.SmartDisplay, AppGreen, Modifier.weight(1f))
                MetricCard("Demandas", c.demands.size.toString(), "${c.demands.count { it.active }} ativas", Icons.Default.Assignment, AppGold, Modifier.weight(1f))
                MetricCard("Fontes", DesktopSourceCatalog.all.size.toString(), "${DesktopSourceCatalog.specialized.size} especializadas", Icons.Default.Storage, AppGold, Modifier.weight(1f))
            }
        }

        item {
            val activeProgress = when {
                c.newsBusy -> "Notícias" to c.newsProgress
                c.demandBusy -> "Demandas" to c.demandProgress
                c.videoBusy -> "Vídeos" to c.videoProgress
                c.newsProgress.startedAt > 0L -> "Notícias" to c.newsProgress
                c.videoProgress.startedAt > 0L -> "Vídeos" to c.videoProgress
                else -> null
            }
            if (activeProgress != null) LiveSearchHero(activeProgress.first, activeProgress.second, nowMs)
            else WelcomePanel(c)
        }

        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ações operacionais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Execute varreduras prioritárias sem interromper o acompanhamento", color = AppMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { c.searchNews() }, enabled = !c.newsBusy, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (c.newsBusy) "Buscando..." else "Buscar notícias")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { c.searchVideos() }, enabled = !c.videoBusy, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(6.dp)); Text(if (c.videoBusy) "Buscando..." else "Buscar vídeos")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { c.searchAllDemands() }, enabled = !c.demandBusy, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Assignment, null); Spacer(Modifier.width(6.dp)); Text(if (c.demandBusy) "Buscando..." else "Buscar demandas")
                    }
                }
            }
        }

        if (c.unstableVideoSources.isNotEmpty()) item {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, null, tint = AppOrange); Spacer(Modifier.width(8.dp)); Text("Fontes de vídeo instáveis", fontWeight = FontWeight.Bold)
                    }
                    c.unstableVideoSources.take(8).forEach { Text("• ${it.sourceName} — ${it.stage} (${it.failureCount})", color = AppMuted) }
                }
            }
        }

        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = AppBlue); Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Agendamento automático", fontWeight = FontWeight.Bold)
                        Text(
                            "Notícias: ${if (c.newsAutomaticEnabled) "a cada ${c.newsIntervalMinutes} min" else "pausadas"} • " +
                                "Demandas: ${if (c.demandAutomaticEnabled) "a cada ${c.demandIntervalMinutes} min" else "pausadas"} • " +
                                "Vídeos: ${if (c.videoAutomaticEnabled) c.videoAutoTimes.joinToString(", ") else "pausados"}",
                            color = AppMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePanel(c: DesktopController) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = AppBlueSoft, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Default.CellTower, null, tint = AppBlue, modifier = Modifier.padding(18.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Central pronta para monitorar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("As buscas e os resultados agora são atualizados na própria tela, em tempo real.", color = AppMuted)
                Spacer(Modifier.height(5.dp))
                Text("Status: ${c.status}", color = AppBlue, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent.copy(alpha = .85f)))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = accent.copy(alpha = .10f), modifier = Modifier.size(50.dp)) {
                    Icon(icon, null, tint = accent, modifier = Modifier.padding(13.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, color = AppMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Text(value, color = AppNavy, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = AppMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LiveSearchHero(title: String, p: LiveSearchProgress, nowMs: Long) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = AppBlueSoft, modifier = Modifier.size(94.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.CellTower, null, tint = AppBlue, modifier = Modifier.size(40.dp)) }
            }
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (p.active) "Buscando ${title.lowercase()}..." else "$title • última execução concluída", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(p.currentSource.ifBlank { if (p.active) "Preparando fontes e filtros" else "Execução finalizada" }, color = AppMuted)
                LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = AppBlue, trackColor = Color(0xFFDCE8F7))
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    SearchStat("${p.found}", "encontrados", AppNavy); SearchStat("${p.newCount}", "novos", AppGreen); SearchStat("${p.errors}", "falhas", AppRed); SearchStat("${p.completed}/${p.total}", "etapas", AppBlue)
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                MetaLine("Fonte atual", p.currentSource.ifBlank { "—" }, Icons.Default.Public)
                MetaLine("Termo", p.currentQuery.ifBlank { "—" }, Icons.Default.Search)
                val end = if (p.active) nowMs else p.finishedAt
                val sec = ((end - p.startedAt).coerceAtLeast(0L) / 1000)
                MetaLine("Tempo", "%02d:%02d".format(sec / 60, sec % 60), Icons.Default.Timer)
            }
        }
    }
}

@Composable
private fun CompactCompletedProgress(title: String, p: LiveSearchProgress) {
    val sec = ((p.finishedAt - p.startedAt).coerceAtLeast(0L) / 1000)
    Panel(compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = Color(0xFFEAF8EF), modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = AppGreen, modifier = Modifier.padding(10.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$title • última execução concluída", fontWeight = FontWeight.Bold, color = AppNavy)
                LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = AppGreen, trackColor = Color(0xFFDCE8F7))
            }
            SearchStat("${p.found}", "encontrados", AppNavy)
            SearchStat("${p.newCount}", "novos", AppGreen)
            SearchStat("${p.errors}", "falhas", AppRed)
            SearchStat("${p.completed}/${p.total}", "etapas", AppBlue)
            MetaLine("Tempo", "%02d:%02d".format(sec / 60, sec % 60), Icons.Default.Timer)
        }
    }
}

@Composable private fun SearchStat(value: String, label: String, color: Color) { Column { Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text(label, color = AppMuted, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun MetaLine(label: String, value: String, icon: ImageVector) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color(0xFF6D83A5), modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Column(horizontalAlignment = Alignment.End) { Text(label, style = MaterialTheme.typography.labelSmall, color = AppMuted); Text(value.take(34), style = MaterialTheme.typography.labelLarge, color = AppNavy, maxLines = 1) } } }
@Composable private fun ProgressPanel(title: String, p: LiveSearchProgress, nowMs: Long, compactWhenCompleted: Boolean = false) { if (!p.active && p.startedAt == 0L) return; if (compactWhenCompleted && !p.active) CompactCompletedProgress(title, p) else LiveSearchHero(title, p, nowMs) }

@Composable
private fun NewsScreen(c: DesktopController, nowMs: Long) {
    var onlyDemands by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showCustomPeriod by remember { mutableStateOf(false) }
    var sd by remember { mutableStateOf("") }
    var st by remember { mutableStateOf("00:00") }
    var ed by remember { mutableStateOf("") }
    var et by remember { mutableStateOf("23:59") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Panel(compact = true) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Buscar nas notícias (título, fonte, termo...)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { c.searchNews() }, enabled = !c.newsBusy, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (c.newsBusy) "Buscando..." else "Buscar últimas 24h")
                    }
                    FilterChip(
                        selected = onlyDemands,
                        onClick = { onlyDemands = !onlyDemands },
                        label = { Text("Só demandas") },
                        leadingIcon = { Icon(Icons.Default.FilterAlt, null) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PeriodPresets(c) { p ->
                        sd = p.startDate; st = p.startTime; ed = p.endDate; et = p.endTime
                        c.parsePeriod(sd, st, ed, et)?.let { c.searchNews(it.first, it.second) }
                    }
                    OutlinedButton(onClick = { showCustomPeriod = !showCustomPeriod }, shape = RoundedCornerShape(10.dp)) {
                        Icon(if (showCustomPeriod) Icons.Default.ExpandLess else Icons.Default.DateRange, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (showCustomPeriod) "Ocultar período" else "Período personalizado")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${c.news.size} matéria(s) na janela", color = AppMuted, style = MaterialTheme.typography.labelMedium)
                }
                if (showCustomPeriod) {
                    PeriodFields(sd, { sd = it }, st, { st = it }, ed, { ed = it }, et, { et = it }) {
                        c.parsePeriod(sd, st, ed, et)?.let { c.searchNews(it.first, it.second) }
                    }
                }
            }
        }

        ProgressPanel("Notícias", c.newsProgress, nowMs, compactWhenCompleted = true)
        StatusStrip(c.status, c.newsBusy)

        val shown = c.news.filter {
            (!onlyDemands || it.demand) &&
                (query.isBlank() || "${it.title} ${it.source} ${it.snippet} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Notícias encontradas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("A lista ocupa o espaço principal e continua sendo atualizada durante a busca.", color = AppMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text("${shown.size} exibida(s)", color = AppMuted, style = MaterialTheme.typography.labelLarge)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(shown, key = { it.link }) { NewsCard(it, it.link in c.newsNewLinks) }
        }
    }
}

@Composable private fun PeriodPresets(c: DesktopController, onPreset: (DesktopController.PeriodPreset) -> Unit) { listOf(0 to "Hoje", 1 to "24 horas", 7 to "7 dias", 30 to "30 dias").forEach { (days, label) -> AssistChip(onClick = { onPreset(c.periodPreset(days)) }, label = { Text(label) }) } }
@Composable private fun PeriodFields(sd: String, setSd: (String) -> Unit, st: String, setSt: (String) -> Unit, ed: String, setEd: (String) -> Unit, et: String, setEt: (String) -> Unit, search: () -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(sd, setSd, label = { Text("Data inicial") }, placeholder = { Text("dd/MM/aaaa") }, singleLine = true, modifier = Modifier.width(160.dp)); OutlinedTextField(st, setSt, label = { Text("Hora") }, singleLine = true, modifier = Modifier.width(92.dp)); OutlinedTextField(ed, setEd, label = { Text("Data final") }, placeholder = { Text("dd/MM/aaaa") }, singleLine = true, modifier = Modifier.width(160.dp)); OutlinedTextField(et, setEt, label = { Text("Hora") }, singleLine = true, modifier = Modifier.width(92.dp)); OutlinedButton(onClick = search, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(5.dp)); Text("Buscar período") } } }

@Composable
private fun NewsCard(n: News, isNew: Boolean = false) {
    var copied by remember(n.link) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = AppBlueSoft, modifier = Modifier.size(76.dp)) { Icon(Icons.Default.Article, null, tint = AppBlue, modifier = Modifier.padding(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { if (isNew) { Surface(shape = RoundedCornerShape(20.dp), color = AppBlue) { Text("Nova", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)) }; Spacer(Modifier.width(7.dp)) }; Text("${n.source} • ${formatDate(n.date)}", color = AppMuted, style = MaterialTheme.typography.labelMedium) }
                Text(n.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (n.snippet.isNotBlank()) Text(n.snippet, color = Color(0xFF405372), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (n.matchedTerm.isNotBlank()) Tag(n.matchedTerm); if (n.matchedDemand.isNotBlank()) Tag(n.matchedDemand) }
            }
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { openUrl(n.link) }, shape = RoundedCornerShape(9.dp)) { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir notícia") }
                OutlinedButton(onClick = { shareWhatsApp(n.title, n.link) }, shape = RoundedCornerShape(9.dp)) { Icon(Icons.Default.Share, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("WhatsApp") }
                OutlinedButton(onClick = { copyToClipboard(n.link); copied = true }, shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (copied) AppGreen else AppBlue)) { Icon(if (copied) Icons.Default.Check else Icons.Default.Link, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(if (copied) "Copiado" else "Copiar link") }
            }
        }
    }
}

@Composable
private fun VideosScreen(c: DesktopController, nowMs: Long) {
    var filter by remember { mutableStateOf(VideoFilter.ALL) }; var query by remember { mutableStateOf("") }
    var sd by remember { mutableStateOf("") }; var st by remember { mutableStateOf("00:00") }; var ed by remember { mutableStateOf("") }; var et by remember { mutableStateOf("23:59") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(compact = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Buscar nos vídeos (título, fonte, termo...)") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { c.searchVideos() }, enabled = !c.videoBusy, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (c.videoBusy) "Buscando..." else "Buscar vídeos") }
                    VideoFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(when (f) { VideoFilter.ALL -> "Todos"; VideoFilter.RELEVANT -> "Relevantes"; VideoFilter.DEMANDS -> "Demandas" }) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { PeriodPresets(c) { p -> sd = p.startDate; st = p.startTime; ed = p.endDate; et = p.endTime } }
                PeriodFields(sd, { sd = it }, st, { st = it }, ed, { ed = it }, et, { et = it }) { c.parsePeriod(sd, st, ed, et)?.let { c.searchVideos(it.first, it.second) } }
            }
        }
        ProgressPanel("Vídeos", c.videoProgress, nowMs); StatusStrip(c.videoStatus, c.videoBusy)
        val shown = c.videos.filter { (filter == VideoFilter.ALL || filter == VideoFilter.RELEVANT && it.relevant || filter == VideoFilter.DEMANDS && it.demand) && (query.isBlank() || "${it.title} ${it.sourceName} ${it.summary} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true)) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Vídeos encontrados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${shown.size} exibido(s)", color = AppMuted) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) { items(shown, key = { it.link }) { VideoCard(it, it.link in c.videoNewLinks) } }
    }
}

@Composable
private fun VideoCard(v: VideoItem, isNew: Boolean = false) {
    var copied by remember(v.link) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFF1ECFF), modifier = Modifier.size(76.dp)) { Icon(Icons.Default.PlayCircle, null, tint = Color(0xFF6542D6), modifier = Modifier.padding(19.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { if (isNew) { Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF6542D6)) { Text("Novo", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)) }; Spacer(Modifier.width(7.dp)) }; Text("${v.sourceName} • ${formatDate(v.publishedAt)}", color = AppMuted, style = MaterialTheme.typography.labelMedium) }
                Text(v.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (v.summary.isNotBlank()) Text(v.summary, color = Color(0xFF405372), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (v.matchedTerm.isNotBlank()) Tag(v.matchedTerm); if (v.matchedDemand.isNotBlank()) Tag(v.matchedDemand) }
            }
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { openUrl(v.link) }, shape = RoundedCornerShape(9.dp)) { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir vídeo") }
                OutlinedButton(onClick = { copyToClipboard(v.link); copied = true }, shape = RoundedCornerShape(9.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (copied) AppGreen else AppBlue)) { Icon(if (copied) Icons.Default.Check else Icons.Default.Link, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(if (copied) "Copiado" else "Copiar link") }
            }
        }
    }
}

@Composable private fun TermsScreen(c: DesktopController) { var nt by remember { mutableStateOf("") }; var vt by remember { mutableStateOf("") }; Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) { TermColumn("Termos de Notícias", "Usados na varredura de matérias", c.terms, nt, { nt = it }, { c.addTerm(nt); nt = "" }, c::removeTerm, Modifier.weight(1f)); TermColumn("Termos de Vídeos", "Lista independente para vídeos", c.videoTerms, vt, { vt = it }, { c.addVideoTerm(vt); vt = "" }, c::removeVideoTerm, Modifier.weight(1f)) } }
@Composable private fun TermColumn(title: String, subtitle: String, terms: List<String>, value: String, onValue: (String) -> Unit, add: () -> Unit, remove: (String) -> Unit, modifier: Modifier) { Panel(modifier = modifier.fillMaxHeight()) { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(subtitle, color = AppMuted); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value, onValue, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Novo termo") }, singleLine = true, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Button(onClick = add, enabled = value.isNotBlank(), shape = RoundedCornerShape(10.dp)) { Text("Adicionar") } }; Text("${terms.size} termo(s)", color = AppMuted, style = MaterialTheme.typography.labelLarge); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(terms) { term -> Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF4F7FB), modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Tag, null, tint = AppBlue, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text(term, modifier = Modifier.weight(1f)); IconButton(onClick = { remove(term) }) { Icon(Icons.Default.DeleteOutline, null, tint = AppRed) } } } } } } } }

@Composable
private fun DemandsScreen(c: DesktopController, nowMs: Long) {
    var vehicle by remember { mutableStateOf("") }; var subject by remember { mutableStateOf("") }; var selectedDemand by remember { mutableStateOf<Demand?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(compact = true) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, singleLine = true, modifier = Modifier.width(270.dp)); OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, singleLine = true, modifier = Modifier.weight(1f)); Button(onClick = { c.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank(), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Adicionar") }; OutlinedButton(onClick = { c.searchAllDemands() }, enabled = !c.demandBusy, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text(if (c.demandBusy) "Buscando..." else "Buscar todas") } } }
        ProgressPanel("Demandas", c.demandProgress, nowMs); StatusStrip(c.status, c.demandBusy)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) { items(c.demands, key = { it.id }) { d -> Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Surface(shape = CircleShape, color = Color(0xFFFFF2E6), modifier = Modifier.size(42.dp)) { Icon(Icons.Default.Assignment, null, tint = AppOrange, modifier = Modifier.padding(10.dp)) }; Column(Modifier.weight(1f)) { Text("${d.vehicle} • ${d.subject}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("Última busca: ${if (d.lastCheckedAt > 0) formatDate(d.lastCheckedAt) else "nunca"} • encontrados ${d.lastFoundCount} • novos ${d.lastNewCount}", color = AppMuted, style = MaterialTheme.typography.labelMedium); if (d.lastError.isNotBlank()) Text(d.lastError, color = AppRed, style = MaterialTheme.typography.labelMedium) }; OutlinedButton(onClick = { selectedDemand = d }, shape = RoundedCornerShape(9.dp)) { Text("Resultados") }; OutlinedButton(onClick = { c.searchDemand(d) }, enabled = !c.demandBusy, shape = RoundedCornerShape(9.dp)) { Text("Buscar") }; IconButton(onClick = { c.removeDemand(d.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = AppRed) } } } } }
    }
    selectedDemand?.let { d -> val expected = "${d.vehicle} • ${d.subject}"; val matches = c.newsHistory.filter { it.matchedDemand.equals(expected, true) || (it.matchedDemand.contains(d.vehicle, true) && it.matchedDemand.contains(d.subject, true)) }; AlertDialog(onDismissRequest = { selectedDemand = null }, confirmButton = { TextButton(onClick = { selectedDemand = null }) { Text("Fechar") } }, title = { Text("Resultados da demanda") }, text = { Column(Modifier.widthIn(min = 760.dp, max = 1040.dp).heightIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${d.vehicle} • ${d.subject} • ${matches.size} matéria(s)", color = AppMuted); if (matches.isEmpty()) Text("Nenhum resultado armazenado para esta demanda.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(matches, key = { it.link }) { NewsCard(it, it.link in c.newsNewLinks) } } } }) }
}

@Composable
private fun SourcesScreen(c: DesktopController) {
    var videos by remember { mutableStateOf(false) }; var query by remember { mutableStateOf("") }; var region by remember { mutableStateOf(SourceCatalog.ALL_REGION) }; var state by remember { mutableStateOf("") }; var specializedOnly by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(compact = true) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !videos, onClick = { videos = false }, label = { Text("Notícias") }, leadingIcon = { Icon(Icons.Default.Article, null) })
                    FilterChip(selected = videos, onClick = { videos = true; specializedOnly = false }, label = { Text("Vídeos") }, leadingIcon = { Icon(Icons.Default.PlayCircle, null) })
                    if (!videos) FilterChip(selected = specializedOnly, onClick = { specializedOnly = !specializedOnly; region = SourceCatalog.ALL_REGION; state = "" }, label = { Text("Mídias especializadas (${DesktopSourceCatalog.specialized.size})") }, leadingIcon = { Icon(Icons.Default.Verified, null) })
                    Spacer(Modifier.weight(1f)); OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Pesquisar fonte") }, singleLine = true, modifier = Modifier.width(340.dp))
                }
                if (!specializedOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { SourceCatalog.regions.forEach { r -> FilterChip(selected = region == r, onClick = { region = r; state = "" }, label = { Text(r) }) } }
                    if (region != SourceCatalog.NATIONAL_REGION) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { FilterChip(selected = state.isBlank(), onClick = { state = "" }, label = { Text("Todos") }); SourceCatalog.states.filter { region == SourceCatalog.ALL_REGION || it.third == region }.forEach { s -> FilterChip(selected = state == s.first, onClick = { state = s.first }, label = { Text(s.first) }) } }
                }
            }
        }
        val visibleIds: Set<String>; val rows: List<Triple<String, String, String>>
        if (videos) { val visible = VideoSourceCatalog.all.filter { (region == SourceCatalog.ALL_REGION || it.region == region) && (state.isBlank() || it.state == state) && (query.isBlank() || (listOf(it.name, it.group, it.region, it.state) + it.aliases).any { text -> text.contains(query, true) }) }; visibleIds = visible.map { it.id }.toSet(); rows = visible.map { Triple(it.id, it.name, "${it.group}${if (it.state.isNotBlank()) " • ${it.region} • ${it.state}" else ""}") } }
        else { val visible = DesktopSourceCatalog.all.filter { (!specializedOnly || it.group == DesktopSourceCatalog.SPECIALIZED_GROUP) && (specializedOnly || region == SourceCatalog.ALL_REGION || it.region == region) && (specializedOnly || state.isBlank() || it.state == state) && (query.isBlank() || (listOf(it.name, it.group, it.region, it.state, it.stateName) + it.aliases).any { text -> text.contains(query, true) }) }; visibleIds = visible.map { it.id }.toSet(); rows = visible.map { Triple(it.id, it.name, "${it.group} • ${it.region} • ${it.state}") } }
        Panel(compact = true) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { Text("${rows.size} fonte(s) visível(is)", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold); OutlinedButton(onClick = { if (videos) c.setVideoSources(visibleIds, true) else c.setNewsSources(visibleIds, true) }) { Text("Selecionar visíveis") }; OutlinedButton(onClick = { if (videos) c.setVideoSources(visibleIds, false) else c.setNewsSources(visibleIds, false) }) { Text("Limpar visíveis") }; OutlinedButton(onClick = { if (videos) c.selectAllVideoSources() else c.selectAllNewsSources() }) { Text("Todas") }; OutlinedButton(onClick = { if (videos) c.clearVideoSources() else c.clearNewsSources() }) { Text("Nenhuma") } } }
        if (videos && c.selectedVideoSourceIds.isEmpty()) StatusStrip("⚠ Nenhuma fonte de vídeo selecionada; a busca ficará bloqueada.", false)
        if (!videos && c.newsAllSources) StatusStrip("Busca em todos os veículos ativa — inclui também as mídias especializadas Windows.", false)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { items(rows, key = { it.first }) { row -> val checked = if (videos) row.first in c.selectedVideoSourceIds else c.newsAllSources || row.first in c.selectedNewsSourceIds; Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, AppLine), modifier = Modifier.fillMaxWidth().clickable { if (videos) c.setVideoSource(row.first, !checked) else c.setNewsSource(row.first, !checked) }) { Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onCheckedChange = { v -> if (videos) c.setVideoSource(row.first, v) else c.setNewsSource(row.first, v) }); Column(Modifier.weight(1f)) { Text(row.second, fontWeight = FontWeight.SemiBold); Text(row.third, style = MaterialTheme.typography.labelSmall, color = AppMuted) }; if (!videos && row.first.startsWith("especializada-")) Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFEAF8EF)) { Text("Especializada", color = AppGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) } } } } }
    }
}

@Composable
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
}

@Composable
private fun SettingsScreen(c: DesktopController, revision: Int) {
    @Suppress("UNUSED_VARIABLE") val liveRevision = revision
    val nr = c.newsAutoReport(); val dr = c.demandAutoReport(); val vr = c.videoAutoReport()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ProxySettingsCard(c) }
        item { AutomationSettingsCard(c) }
        item { AutoReportCard("Notícias automáticas", nr, "${nr.found} resultado(s) • ${nr.newCount} nova(s)") }
        item { AutoReportCard("Demandas automáticas", dr, "${dr.checked} demanda(s) • ${dr.found} resultado(s) • ${dr.newCount} nova(s)") }
        item { AutoReportCard("Vídeos automáticos", vr, "${vr.found} detectado(s) • ${vr.newCount} novo(s) • ${vr.relevant} relevante(s) • ${vr.errors} falha(s)") }
        item { Panel { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Dados portáteis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Banco, termos, demandas, histórico, exportações e preferências ficam em:", color = AppMuted); SelectionContainer { Text(c.context.filesDir.absolutePath, color = AppBlue) }; Text("Copiar a pasta inteira do programa preserva os dados desta edição Windows.", color = AppMuted) } } }
        item { Panel { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Edição Windows v4.0.2", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Motor de Notícias/Vídeos, Globoplay, Jarvis, YouTube, telejornais, Termos/Demandas, histórico, deduplicação e regra de NOVO preservados.", color = AppMuted); Text("Fontes especializadas exclusivas desta edição Windows: ${DesktopSourceCatalog.specialized.joinToString { it.name }}.", color = AppMuted) } } }
    }
}

@Composable private fun AutoReportCard(title: String, r: DesktopController.AutoReport, summary: String) { Panel { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = if (r.error.isNotBlank()) Color(0xFFFFECEA) else Color(0xFFEAF8EF), modifier = Modifier.size(44.dp)) { Icon(if (r.error.isNotBlank()) Icons.Default.ErrorOutline else if (r.completedAt > 0) Icons.Default.CheckCircle else Icons.Default.Schedule, null, tint = if (r.error.isNotBlank()) AppRed else AppGreen, modifier = Modifier.padding(10.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Última tentativa: ${if (r.attemptAt > 0) formatDate(r.attemptAt) else "—"}", color = AppMuted); Text("Última conclusão: ${if (r.completedAt > 0) formatDate(r.completedAt) else "—"}", color = AppMuted) }; Column(horizontalAlignment = Alignment.End) { if (r.completedAt > 0) Text(summary, fontWeight = FontWeight.SemiBold); if (r.error.isNotBlank()) Text(r.error, color = AppRed) } } } }
@Composable private fun SettingSwitch(value: Boolean, onChange: (Boolean) -> Unit, text: String) { Row(verticalAlignment = Alignment.CenterVertically) { Switch(value, onCheckedChange = onChange); Spacer(Modifier.width(10.dp)); Text(text) } }
@Composable private fun StatusStrip(text: String, busy: Boolean) { Surface(shape = RoundedCornerShape(10.dp), color = when { text.startsWith("✓") -> Color(0xFFEAF8EF); text.startsWith("⚠") || text.startsWith("Falha") -> Color(0xFFFFF0EE); busy -> AppBlueSoft; else -> Color(0xFFF3F6FA) }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { if (busy) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) } else { Icon(when { text.startsWith("✓") -> Icons.Default.CheckCircle; text.startsWith("⚠") || text.startsWith("Falha") -> Icons.Default.WarningAmber; else -> Icons.Default.Info }, null, modifier = Modifier.size(17.dp), tint = when { text.startsWith("✓") -> AppGreen; text.startsWith("⚠") || text.startsWith("Falha") -> AppRed; else -> AppBlue }); Spacer(Modifier.width(8.dp)) }; Text(text, style = MaterialTheme.typography.labelLarge) } } }
@Composable private fun Tag(text: String) { Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFEDF2F8)) { Text(text.take(52), color = Color(0xFF5D6F8B), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) } }
@Composable private fun Panel(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable () -> Unit) { Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, AppLine), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Box(Modifier.padding(if (compact) 13.dp else 18.dp)) { content() } } }

private fun copyToClipboard(text: String) { runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) } }
private fun openUrl(url: String) { runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().browse(URI(url)) } }
private fun openFolder(folder: File?) { if (folder == null) return; runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().open(folder) } }
private fun shareWhatsApp(title: String, link: String) { val text = URLEncoder.encode("$title\n$link", StandardCharsets.UTF_8); openUrl("https://wa.me/?text=$text") }
private fun formatDate(ms: Long): String = if (ms <= 0L) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))

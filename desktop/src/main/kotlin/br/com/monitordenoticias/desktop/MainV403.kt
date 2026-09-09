package br.com.monitordenoticias.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val V403Navy = Color(0xFF03294A)
private val V403Navy2 = Color(0xFF063B6F)
private val V403Blue = Color(0xFF0B67D1)
private val V403BlueSoft = Color(0xFFEAF3FC)
private val V403Gold = Color(0xFFE4AA18)
private val V403GoldSoft = Color(0xFFFFF5D8)
private val V403Bg = Color(0xFFF3F8FD)
private val V403Line = Color(0xFFD6E3EF)
private val V403Muted = Color(0xFF5F7895)
private val V403Text = Color(0xFF08234B)
private val V403Green = Color(0xFF10914B)
private val V403Red = Color(0xFFD62222)
private val V403Orange = Color(0xFFD88A00)
private val V403Purple = Color(0xFF6E48E5)

private val V403Scheme = lightColorScheme(
    primary = V403Blue,
    onPrimary = Color.White,
    primaryContainer = V403BlueSoft,
    onPrimaryContainer = V403Text,
    secondary = V403Gold,
    onSecondary = V403Navy,
    background = V403Bg,
    onBackground = V403Text,
    surface = Color.White,
    onSurface = V403Text,
    surfaceVariant = Color(0xFFF4F8FC),
    onSurfaceVariant = V403Muted,
    outline = V403Line,
    error = V403Red
)

private enum class V403Section(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home),
    NEWS("Notícias", Icons.Default.Article),
    VIDEOS("Vídeos", Icons.Default.PlayCircle),
    DEMANDS("Demandas", Icons.Default.Assignment),
    SOURCES("Fontes", Icons.Default.Storage),
    HISTORY("Histórico", Icons.Default.History),
    TERMS("Termos de busca", Icons.Default.Search),
    SETTINGS("Configurações", Icons.Default.Settings)
}

private enum class V403Sort(val label: String) {
    NEWEST("Mais recentes"),
    OLDEST("Mais antigos"),
    SOURCE("Por fonte"),
    TITLE("A-Z")
}

fun main() = application {
    val trayState = rememberTrayState()
    var windowVisible by remember { mutableStateOf(true) }
    val icon = painterResource("monitor_icon.svg")
    val controller = remember {
        DesktopController { title, message ->
            runCatching { trayState.sendNotification(Notification(title, message)) }
        }
    }

    Tray(
        state = trayState,
        icon = icon,
        tooltip = "Monitor de Notícias v4.0.3",
        menu = {
            Item("Abrir Monitor de Notícias") { windowVisible = true }
            Separator()
            Item("Buscar notícias agora") { controller.searchNews() }
            Item("Buscar vídeos agora") { controller.searchVideos() }
            Item("Buscar demandas agora") { controller.searchAllDemands() }
            Separator()
            Item("Sair") { controller.close(); exitApplication() }
        }
    )

    Window(
        visible = windowVisible,
        onCloseRequest = { windowVisible = false },
        title = "Monitor de Notícias - Windows Portable v4.0.3",
        icon = icon,
        state = rememberWindowState(width = 1600.dp, height = 960.dp)
    ) {
        MaterialTheme(colorScheme = V403Scheme) {
            V403App(controller)
        }
    }
}

@Composable
private fun V403App(c: DesktopController) {
    var section by remember { mutableStateOf(V403Section.HOME) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    Row(Modifier.fillMaxSize().background(V403Bg)) {
        V403Sidebar(c, section) { section = it }
        Column(Modifier.fillMaxHeight().weight(1f)) {
            V403TopBar(c, section, now)
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                when (section) {
                    V403Section.HOME -> V403Home(c, now)
                    V403Section.NEWS -> V403News(c, now)
                    V403Section.VIDEOS -> V403Videos(c, now)
                    V403Section.DEMANDS -> V403Demands(c, now)
                    V403Section.SOURCES -> V403Sources(c)
                    V403Section.HISTORY -> V403History(c)
                    V403Section.TERMS -> V403Terms(c)
                    V403Section.SETTINGS -> V403Settings(c, c.uiRevision)
                }
            }
        }
    }
}

@Composable
private fun V403Sidebar(c: DesktopController, selected: V403Section, onSelect: (V403Section) -> Unit) {
    Surface(
        modifier = Modifier.width(264.dp).fillMaxHeight(),
        color = V403Navy,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = V403Gold, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Newspaper, null, tint = V403Navy, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("MONITOR", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("DE NOTÍCIAS", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Inteligência de mídia", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(18.dp))
            V403Section.entries.forEach { item ->
                val active = item == selected
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) V403Blue else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelect(item) }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            item.icon,
                            null,
                            tint = if (active) Color.White else Color(0xFFD3E4F7),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.label,
                            color = Color.White,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        val badge = when (item) {
                            V403Section.NEWS -> c.newsNewLinks.size
                            V403Section.DEMANDS -> c.demands.count { it.active }
                            else -> 0
                        }
                        if (badge > 0) {
                            Surface(shape = CircleShape, color = if (active) V403Gold else Color.White.copy(alpha = .12f)) {
                                Text(
                                    badge.toString(),
                                    color = if (active) V403Navy else Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = .05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .13f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(if (c.automaticMonitoring) Color(0xFF42D67D) else V403Gold)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (c.automaticMonitoring) "Sistema operacional" else "Automação pausada",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Text("Dados locais • modo portátil", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.labelSmall)
                    val proxyEnabled = DesktopProxyManager.load(c.context).enabled
                    Text(
                        when {
                            !proxyEnabled -> "Proxy desativado"
                            DesktopProxyManager.isReady(c.context) -> "Proxy autenticado • pronto"
                            else -> "Proxy autenticado • configurar"
                        },
                        color = if (!proxyEnabled || DesktopProxyManager.isReady(c.context)) Color(0xFF68E89A) else V403Gold,
                        style = MaterialTheme.typography.labelSmall
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = .10f))
                    Text("Windows Portable • v4.0.3", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun V403TopBar(c: DesktopController, section: V403Section, now: Long) {
    val proxy = DesktopProxyManager.load(c.context)
    val proxyReady = DesktopProxyManager.isReady(c.context)
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(4.dp).height(48.dp).background(V403Gold, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "CENTRAL DE INTELIGÊNCIA DE MÍDIA",
                    color = V403Gold,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    if (section == V403Section.HOME) "Monitor de Notícias" else section.label,
                    color = V403Text,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    when (section) {
                        V403Section.HOME -> "Acompanhe notícias, vídeos, demandas e fontes em tempo real"
                        V403Section.NEWS -> "Busca e acompanhamento de matérias com atualização contínua"
                        V403Section.VIDEOS -> "Monitoramento audiovisual com termos e demandas independentes"
                        V403Section.DEMANDS -> "Assuntos prioritários acompanhados por veículo"
                        V403Section.SOURCES -> "Fontes nacionais, regionais e mídias especializadas"
                        V403Section.HISTORY -> "Arquivo local e gestão do histórico monitorado"
                        V403Section.TERMS -> "Termos independentes para inteligência de notícias e vídeos"
                        V403Section.SETTINGS -> "Automação, proxy, inicialização e operação do aplicativo"
                    },
                    color = V403Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (section == V403Section.HOME) {
                V403GlobalSearch(c)
                Spacer(Modifier.width(12.dp))
                V403NotificationButton(c)
                Spacer(Modifier.width(12.dp))
                Surface(shape = CircleShape, color = V403Navy2, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.padding(10.dp))
                }
            } else {
                V403StatusPill(
                    Icons.Default.Security,
                    when {
                        !proxy.enabled -> "Proxy desativado"
                        proxyReady -> "Proxy pronto"
                        else -> "Proxy requer configuração"
                    },
                    proxyReady || !proxy.enabled,
                    proxy.enabled && !proxyReady
                )
                Spacer(Modifier.width(10.dp))
                V403StatusPill(
                    Icons.Default.Radar,
                    if (c.automaticMonitoring) "Automação ativa" else "Automação pausada",
                    c.automaticMonitoring,
                    !c.automaticMonitoring
                )
            }

            Spacer(Modifier.width(22.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(Date(now)),
                    color = V403Muted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(now)),
                    color = V403Text,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@Composable
private fun V403GlobalSearch(c: DesktopController) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val clean = query.trim()
    val newsMatches = if (clean.length >= 2) c.newsHistory.filter {
        "${it.title} ${it.source} ${it.matchedTerm} ${it.matchedDemand}".contains(clean, true)
    }.take(5) else emptyList()
    val videoMatches = if (clean.length >= 2) c.videoHistory.filter {
        "${it.title} ${it.sourceName} ${it.matchedTerm} ${it.matchedDemand}".contains(clean, true)
    }.take(4) else emptyList()
    val demandMatches = if (clean.length >= 2) c.demands.filter {
        "${it.vehicle} ${it.subject}".contains(clean, true)
    }.take(3) else emptyList()

    Box(Modifier.width(470.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = it.trim().length >= 2
            },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Buscar notícias, vídeos ou demandas...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded && clean.length >= 2,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(560.dp).heightIn(max = 440.dp)
        ) {
            if (newsMatches.isEmpty() && videoMatches.isEmpty() && demandMatches.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Nenhum resultado local para “$clean”") },
                    onClick = { expanded = false },
                    leadingIcon = { Icon(Icons.Default.SearchOff, null) }
                )
            }
            newsMatches.forEach { n ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(n.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("Notícia • ${n.source}", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = { v403Open(n.link); expanded = false },
                    leadingIcon = { Icon(Icons.Default.Article, null, tint = V403Blue) }
                )
            }
            videoMatches.forEach { v ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("Vídeo • ${v.sourceName}", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = { v403Open(v.link); expanded = false },
                    leadingIcon = { Icon(Icons.Default.PlayCircle, null, tint = V403Purple) }
                )
            }
            demandMatches.forEach { d ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(d.subject, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("Demanda • ${d.vehicle}", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = { c.searchDemand(d); expanded = false },
                    leadingIcon = { Icon(Icons.Default.Assignment, null, tint = V403Gold) }
                )
            }
        }
    }
}

@Composable
private fun V403NotificationButton(c: DesktopController) {
    var expanded by remember { mutableStateOf(false) }
    val newNews = c.news.filter { it.link in c.newsNewLinks }.take(5)
    val newVideos = c.videos.filter { it.link in c.videoNewLinks }.take(3)
    val count = newNews.size + newVideos.size

    Box {
        BadgedBox(
            badge = {
                if (count > 0) Badge(containerColor = V403Red) {
                    Text(count.coerceAtMost(99).toString(), color = Color.White)
                }
            }
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.NotificationsNone, null, tint = V403Navy2)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(430.dp).heightIn(max = 430.dp)
        ) {
            if (count == 0) {
                DropdownMenuItem(
                    text = { Text("Nenhum item novo na execução atual") },
                    onClick = { expanded = false },
                    leadingIcon = { Icon(Icons.Default.NotificationsNone, null) }
                )
            }
            newNews.forEach { n ->
                DropdownMenuItem(
                    text = { Text(n.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = { v403Open(n.link); expanded = false },
                    leadingIcon = { Icon(Icons.Default.Article, null, tint = V403Blue) }
                )
            }
            newVideos.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = { v403Open(v.link); expanded = false },
                    leadingIcon = { Icon(Icons.Default.PlayCircle, null, tint = V403Purple) }
                )
            }
        }
    }
}

@Composable
private fun V403StatusPill(icon: ImageVector, text: String, ok: Boolean, warning: Boolean = false) {
    val bg = if (warning) Color(0xFFFFF4E2) else if (ok) Color(0xFFE9F8EF) else Color(0xFFF0F4F8)
    val fg = if (warning) V403Orange else if (ok) V403Green else V403Muted
    Surface(shape = RoundedCornerShape(12.dp), color = bg) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, color = fg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun V403Home(c: DesktopController, now: Long) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                V403Metric("Notícias 24h", c.news.size.toString(), "na janela atual", Icons.Default.Article, V403Blue, Modifier.weight(1f))
                V403Metric("Vídeos armazenados", c.totalStoredVideos.toString(), "relevantes na base", Icons.Default.PlayCircle, V403Purple, Modifier.weight(1f))
                V403Metric("Vídeos hoje", c.capturedTodayVideos.toString(), "capturados hoje", Icons.Default.SmartDisplay, V403Green, Modifier.weight(1f))
                V403Metric("Demandas", c.demands.size.toString(), "${c.demands.count { it.active }} ativas", Icons.Default.Assignment, V403Gold, Modifier.weight(1f))
                V403Metric("Fontes", DesktopSourceCatalog.all.size.toString(), "${DesktopSourceCatalog.specialized.size} especializadas", Icons.Default.Storage, Color(0xFFC2185B), Modifier.weight(1f))
            }
        }

        item {
            val active = when {
                c.newsBusy -> Triple("Notícias", c.newsProgress, c.status)
                c.videoBusy -> Triple("Vídeos", c.videoProgress, c.videoStatus)
                c.demandBusy -> Triple("Demandas", c.demandProgress, c.status)
                else -> null
            }
            if (active != null) {
                V403ProgressPanel(active.first, active.second, now, active.third)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    V403HomeHero(c, Modifier.weight(1.9f).height(245.dp))
                    V403QuickActions(c, Modifier.weight(1f).height(245.dp))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                V403SchedulePanel(c, now, Modifier.weight(1.1f).height(215.dp))
                V403DailySummary(c, now, Modifier.weight(1f).height(215.dp))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                V403TopSources(c, Modifier.weight(1f).height(230.dp))
                V403RecentActivities(c, now, Modifier.weight(1f).height(230.dp))
                V403Tips(Modifier.weight(.95f).height(230.dp))
            }
        }
    }
}

@Composable
private fun V403HomeHero(c: DesktopController, modifier: Modifier) {
    V403Panel(modifier = modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = V403BlueSoft, modifier = Modifier.size(70.dp)) {
                        Icon(Icons.Default.CellTower, null, tint = V403Blue, modifier = Modifier.padding(17.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Central pronta para monitorar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("As buscas e os resultados são atualizados na própria tela, em tempo real.", color = V403Muted)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFEAF8EF), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(V403Green))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Status: ${if (c.newsBusy || c.videoBusy || c.demandBusy) "Em execução" else "Pronto"}", color = V403Green, fontWeight = FontWeight.ExtraBold)
                            Text(c.status.take(120), color = V403Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF0F6FD),
                modifier = Modifier.weight(.8f).fillMaxHeight()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.size(122.dp)) {
                            Icon(Icons.Default.Dashboard, null, tint = V403Blue, modifier = Modifier.padding(28.dp))
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("INTELIGÊNCIA DE MÍDIA", color = V403Navy2, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun V403QuickActions(c: DesktopController, modifier: Modifier) {
    V403Panel(modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = V403Blue)
                Spacer(Modifier.width(7.dp))
                Column {
                    Text("Ações rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Execute varreduras prioritárias sem interromper o acompanhamento.", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            V403ActionButton("Buscar notícias", "Iniciar varredura agora", Icons.Default.Search, V403Blue, !c.newsBusy) { c.searchNews() }
            V403ActionButton("Buscar vídeos", "Pesquisar novos vídeos", Icons.Default.PlayCircle, V403Purple, !c.videoBusy) { c.searchVideos() }
            V403ActionButton("Buscar demandas", "Consultar demandas ativas", Icons.Default.Assignment, V403Gold, !c.demandBusy) { c.searchAllDemands() }
        }
    }
}

@Composable
private fun V403ActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = if (accent == V403Blue) V403Blue else Color(0xFFF7FAFD),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (accent == V403Blue) V403Blue else V403Line),
        modifier = Modifier.fillMaxWidth().weight(1f).clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (accent == V403Blue) Color.White else accent)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (accent == V403Blue) Color.White else V403Text, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = if (accent == V403Blue) Color.White.copy(alpha = .80f) else V403Muted, style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.ChevronRight, null, tint = if (accent == V403Blue) Color.White else V403Blue)
        }
    }
}

@Composable
private fun V403SchedulePanel(c: DesktopController, now: Long, modifier: Modifier) {
    V403Panel(modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = V403BlueSoft, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Schedule, null, tint = V403Blue, modifier = Modifier.padding(9.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Agendamento automático", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Próximas execuções conforme as configurações atuais.", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                V403ScheduleMini("Notícias", if (c.newsAutomaticEnabled) "a cada ${c.newsIntervalMinutes} min" else "pausadas", v403ShortDate(c.nextNewsAutoAt(now)), Icons.Default.Article, V403Blue, Modifier.weight(1f))
                V403ScheduleMini("Demandas", if (c.demandAutomaticEnabled) "a cada ${c.demandIntervalMinutes} min" else "pausadas", v403ShortDate(c.nextDemandAutoAt(now)), Icons.Default.Assignment, V403Gold, Modifier.weight(1f))
                V403ScheduleMini("Vídeos", if (c.videoAutomaticEnabled) c.videoAutoTimes.joinToString(", ") else "pausados", v403ShortDate(c.nextVideoAutoAt(now)), Icons.Default.PlayCircle, V403Purple, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun V403ScheduleMini(
    title: String,
    detail: String,
    next: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier
) {
    Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFF7FAFD), border = androidx.compose.foundation.BorderStroke(1.dp, V403Line), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            Text(title, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = V403Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("Próxima: $next", color = V403Text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun V403DailySummary(c: DesktopController, now: Long, modifier: Modifier) {
    val cutoff = now - 24L * 60L * 60L * 1000L
    val buckets = IntArray(12)
    c.newsHistory.asSequence().filter { it.date >= cutoff }.forEach { n ->
        val hoursAgo = ((now - n.date).coerceAtLeast(0L) / (2L * 60L * 60L * 1000L)).toInt().coerceIn(0, 11)
        buckets[11 - hoursAgo]++
    }
    c.videoHistory.asSequence().filter { it.publishedAt >= cutoff }.forEach { v ->
        val hoursAgo = ((now - v.publishedAt).coerceAtLeast(0L) / (2L * 60L * 60L * 1000L)).toInt().coerceIn(0, 11)
        buckets[11 - hoursAgo]++
    }
    val max = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)

    V403Panel(modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, null, tint = V403Blue)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Resumo do dia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Panorama geral das últimas 24 horas.", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                }
                Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFFF4F8FC)) {
                    Text("Últimas 24 horas", color = V403Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                buckets.forEachIndexed { index, value ->
                    val height = 10 + (72 * value / max)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.width(12.dp).height(height.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (value > 0) V403Blue else V403Line)
                        )
                        if (index % 2 == 0) {
                            Spacer(Modifier.height(3.dp))
                            Text("${index * 2}h", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                V403Legend(V403Blue, "Notícias + vídeos")
                V403Legend(V403Gold, "Demandas: ${c.demands.count { it.active }} ativas")
            }
        }
    }
}

@Composable
private fun V403Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, color = V403Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun V403TopSources(c: DesktopController, modifier: Modifier) {
    val top = c.newsHistory
        .groupingBy { DesktopSourceCatalog.canonicalName(it.source) }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(5)

    V403Panel(modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, null, tint = V403Blue)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Fontes mais relevantes", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Maior presença no histórico local.", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (top.isEmpty()) {
                Spacer(Modifier.weight(1f))
                Text("Ainda não há histórico suficiente para calcular o ranking.", color = V403Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
            } else {
                top.forEachIndexed { index, entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = V403BlueSoft, modifier = Modifier.size(25.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text((index + 1).toString(), color = V403Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(V403Green))
                        Spacer(Modifier.width(8.dp))
                        Text(entry.key, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.value.toString(), color = V403Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun V403RecentActivities(c: DesktopController, now: Long, modifier: Modifier) {
    val newsReport = c.newsAutoReport()
    val demandReport = c.demandAutoReport()
    val videoReport = c.videoAutoReport()
    V403Panel(modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = V403Blue)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Últimas atividades", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Histórico recente de ações no sistema.", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            V403ActivityRow(Icons.Default.CheckCircle, V403Green, "Sistema ativo", c.status.take(70), v403Time(now))
            V403ActivityRow(Icons.Default.Article, V403Blue, "Notícias automáticas", v403ReportSummary(newsReport), v403Time(newsReport.completedAt))
            V403ActivityRow(Icons.Default.Assignment, V403Gold, "Demandas automáticas", v403ReportSummary(demandReport), v403Time(demandReport.completedAt))
            V403ActivityRow(Icons.Default.PlayCircle, V403Purple, "Vídeos automáticos", v403ReportSummary(videoReport), v403Time(videoReport.completedAt))
        }
    }
}

@Composable
private fun V403ActivityRow(icon: ImageVector, color: Color, title: String, subtitle: String, time: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text(subtitle, color = V403Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(time, color = V403Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun V403Tips(modifier: Modifier) {
    val tips = listOf(
        "Use termos de busca específicos" to "Quanto mais específico o termo, mais relevantes tendem a ser os resultados.",
        "Combine fontes e períodos" to "Selecione as fontes prioritárias e use períodos personalizados para auditorias pontuais.",
        "Acompanhe a cobertura" to "Na aba Notícias, confira o diagnóstico para entender o que veio do Google e da coleta direta."
    )
    var index by remember { mutableIntStateOf(0) }
    V403Panel(modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, null, tint = V403Gold)
                Spacer(Modifier.width(7.dp))
                Text("Dicas", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { index = (index - 1 + tips.size) % tips.size }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronLeft, null)
                }
                Text("${index + 1}/${tips.size}", color = V403Muted, style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { index = (index + 1) % tips.size }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
            Surface(shape = RoundedCornerShape(13.dp), color = V403BlueSoft, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .65f), modifier = Modifier.size(54.dp)) {
                        Icon(Icons.Default.School, null, tint = V403Blue, modifier = Modifier.padding(13.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tips[index].first, color = V403Text, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text(tips[index].second, color = V403Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                tips.indices.forEach { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (i == index) 9.dp else 7.dp)
                            .clip(CircleShape).background(if (i == index) V403Blue else V403Line)
                    )
                }
            }
        }
    }
}

@Composable
private fun V403Metric(title: String, value: String, subtitle: String, icon: ImageVector, accent: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, V403Line),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent.copy(alpha = .11f), modifier = Modifier.size(48.dp)) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, color = V403Muted, style = MaterialTheme.typography.labelMedium)
                Text(value, color = V403Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = V403Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun V403News(c: DesktopController, now: Long) {
    var onlyDemands by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(V403Sort.NEWEST) }
    var sd by remember { mutableStateOf("") }
    var st by remember { mutableStateOf("00:00") }
    var ed by remember { mutableStateOf("") }
    var et by remember { mutableStateOf("23:59") }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        V403SearchToolbar(
            query = query,
            onQuery = { query = it },
            placeholder = "Buscar nas notícias (título, fonte, termo...)",
            busy = c.newsBusy,
            searchLabel = "Buscar últimas 24h",
            onSearch = { c.searchNews() },
            onlyDemands = onlyDemands,
            onOnlyDemands = { onlyDemands = !onlyDemands },
            onPreset = { days ->
                val p = c.periodPreset(days)
                sd = p.startDate
                st = p.startTime
                ed = p.endDate
                et = p.endTime
                c.parsePeriod(sd, st, ed, et)?.let { c.searchNews(it.first, it.second) }
            },
            customVisible = showCustom,
            onToggleCustom = { showCustom = !showCustom },
            totalText = "${c.news.size} matéria(s) na janela"
        )
        if (showCustom) {
            V403PeriodFields(sd, { sd = it }, st, { st = it }, ed, { ed = it }, et, { et = it }) {
                c.parsePeriod(sd, st, ed, et)?.let { c.searchNews(it.first, it.second) }
            }
        }

        if (c.newsProgress.startedAt > 0L) V403ProgressPanel("Notícias", c.newsProgress, now, c.status)
        else V403StatusStrip(c.status, c.newsBusy)

        val diagnostics = DesktopSearchDiagnosticsStore.snapshot
        if (diagnostics.startedAt > 0L) V403SearchDiagnostics(diagnostics)

        val base = c.news.filter {
            (!onlyDemands || it.demand) &&
                (query.isBlank() || "${it.title} ${it.source} ${it.snippet} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true))
        }
        val shown = when (sort) {
            V403Sort.NEWEST -> base.sortedByDescending { it.date }
            V403Sort.OLDEST -> base.sortedBy { it.date }
            V403Sort.SOURCE -> base.sortedBy { it.source.lowercase() }
            V403Sort.TITLE -> base.sortedBy { it.title.lowercase() }
        }
        V403ResultsHeader(
            "Notícias encontradas",
            "A lista ocupa o espaço principal e continua sendo atualizada durante a busca.",
            shown.size,
            sort
        ) { sort = it }
        if (shown.isEmpty()) {
            V403EmptyState(Icons.Default.Article, "Nenhuma notícia encontrada", "Ajuste o período, os termos ou as fontes e execute uma nova busca.")
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                items(shown, key = { it.link }) { V403NewsCard(it, it.link in c.newsNewLinks) }
            }
        }
    }
}

@Composable
private fun V403SearchDiagnostics(d: DesktopSearchDiagnosticsStore.Snapshot) {
    V403Panel(compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = V403GoldSoft, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Radar, null, tint = V403Gold, modifier = Modifier.padding(9.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Cobertura da busca", color = V403Text, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (d.active) "Auditoria sendo atualizada durante a execução." else "Diagnóstico da última execução de notícias.",
                    color = V403Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            V403DiagStat("${d.googleQueries}", "consultas Google", V403Blue)
            V403DiagStat("${d.googleRawItems}", "itens brutos", V403Blue)
            V403DiagStat("${d.googleInPeriod}", "no período", V403Navy2)
            V403DiagStat("${d.googleSourceAccepted}", "aceitos por fonte", V403Green)
            V403DiagStat("${d.directAccepted}", "cobertura direta", V403Gold)
            if (d.googleErrors + d.directErrors > 0) {
                V403DiagStat("${d.googleErrors + d.directErrors}", "falhas", V403Red)
            }
        }
    }
}

@Composable
private fun V403DiagStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold)
        Text(label, color = V403Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun V403Videos(c: DesktopController, now: Long) {
    var onlyDemands by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(V403Sort.NEWEST) }
    var sd by remember { mutableStateOf("") }
    var st by remember { mutableStateOf("00:00") }
    var ed by remember { mutableStateOf("") }
    var et by remember { mutableStateOf("23:59") }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        V403SearchToolbar(
            query = query,
            onQuery = { query = it },
            placeholder = "Buscar nos vídeos (título, fonte, termo...)",
            busy = c.videoBusy,
            searchLabel = "Buscar últimas 24h",
            onSearch = { c.searchVideos() },
            onlyDemands = onlyDemands,
            onOnlyDemands = { onlyDemands = !onlyDemands },
            onPreset = { days ->
                val p = c.periodPreset(days)
                sd = p.startDate
                st = p.startTime
                ed = p.endDate
                et = p.endTime
                c.parsePeriod(sd, st, ed, et)?.let { c.searchVideos(it.first, it.second) }
            },
            customVisible = showCustom,
            onToggleCustom = { showCustom = !showCustom },
            totalText = "${c.videos.size} vídeo(s) na janela"
        )
        if (showCustom) {
            V403PeriodFields(sd, { sd = it }, st, { st = it }, ed, { ed = it }, et, { et = it }) {
                c.parsePeriod(sd, st, ed, et)?.let { c.searchVideos(it.first, it.second) }
            }
        }
        if (c.videoProgress.startedAt > 0L) V403ProgressPanel("Vídeos", c.videoProgress, now, c.videoStatus)
        else V403StatusStrip(c.videoStatus, c.videoBusy)

        val base = c.videos.filter {
            (!onlyDemands || it.demand) &&
                (query.isBlank() || "${it.title} ${it.sourceName} ${it.summary} ${it.matchedTerm} ${it.matchedDemand}".contains(query, true))
        }
        val shown = when (sort) {
            V403Sort.NEWEST -> base.sortedByDescending { it.publishedAt }
            V403Sort.OLDEST -> base.sortedBy { it.publishedAt }
            V403Sort.SOURCE -> base.sortedBy { it.sourceName.lowercase() }
            V403Sort.TITLE -> base.sortedBy { it.title.lowercase() }
        }
        V403ResultsHeader(
            "Vídeos encontrados",
            "A lista ocupa o espaço principal e continua sendo atualizada durante a busca.",
            shown.size,
            sort
        ) { sort = it }
        if (shown.isEmpty()) {
            V403EmptyState(Icons.Default.PlayCircle, "Nenhum vídeo encontrado", "Selecione fontes de vídeo, ajuste os termos ou amplie o período.")
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                items(shown, key = { it.link }) { V403VideoCard(it, it.link in c.videoNewLinks) }
            }
        }
    }
}

@Composable
private fun V403SearchToolbar(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    busy: Boolean,
    searchLabel: String,
    onSearch: () -> Unit,
    onlyDemands: Boolean,
    onOnlyDemands: () -> Unit,
    onPreset: (Int) -> Unit,
    customVisible: Boolean,
    onToggleCustom: () -> Unit,
    totalText: String
) {
    V403Panel(compact = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query,
                    onQuery,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onSearch, enabled = !busy, shape = RoundedCornerShape(9.dp)) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "Buscando..." else searchLabel)
                }
                FilterChip(
                    selected = onlyDemands,
                    onClick = onOnlyDemands,
                    label = { Text("Só demandas") },
                    leadingIcon = { Icon(Icons.Default.FilterAlt, null) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(0 to "Hoje", 1 to "24 horas", 7 to "7 dias", 30 to "30 dias").forEach { (d, label) ->
                    AssistChip(onClick = { onPreset(d) }, label = { Text(label) })
                }
                OutlinedButton(onClick = onToggleCustom, shape = RoundedCornerShape(9.dp)) {
                    Icon(if (customVisible) Icons.Default.ExpandLess else Icons.Default.CalendarMonth, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (customVisible) "Ocultar período" else "Período personalizado")
                }
                Spacer(Modifier.weight(1f))
                Text(totalText, color = V403Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun V403PeriodFields(
    sd: String,
    setSd: (String) -> Unit,
    st: String,
    setSt: (String) -> Unit,
    ed: String,
    setEd: (String) -> Unit,
    et: String,
    setEt: (String) -> Unit,
    onSearch: () -> Unit
) {
    V403Panel(compact = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(sd, setSd, label = { Text("Data inicial") }, singleLine = true, modifier = Modifier.width(170.dp))
            OutlinedTextField(st, setSt, label = { Text("Hora") }, singleLine = true, modifier = Modifier.width(100.dp))
            OutlinedTextField(ed, setEd, label = { Text("Data final") }, singleLine = true, modifier = Modifier.width(170.dp))
            OutlinedTextField(et, setEt, label = { Text("Hora") }, singleLine = true, modifier = Modifier.width(100.dp))
            Button(onClick = onSearch) {
                Icon(Icons.Default.CalendarMonth, null)
                Spacer(Modifier.width(5.dp))
                Text("Buscar período")
            }
        }
    }
}

@Composable
private fun V403ProgressPanel(title: String, p: LiveSearchProgress, now: Long, status: String) {
    val end = if (p.active) now else p.finishedAt
    val seconds = ((end - p.startedAt).coerceAtLeast(0L) / 1000L)
    V403Panel {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (p.active) V403BlueSoft else Color(0xFFE9F8EF),
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(
                        if (p.active) Icons.Default.CellTower else Icons.Default.CheckCircle,
                        null,
                        tint = if (p.active) V403Blue else V403Green,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (p.active) "$title • execução em andamento" else "$title • última execução concluída",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (p.active) p.currentSource.ifBlank { "Preparando fontes e filtros" }
                        else "Busca finalizada. Os resultados estão disponíveis abaixo.",
                        color = V403Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(
                        progress = { p.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = if (p.active) V403Blue else V403Green,
                        trackColor = Color(0xFFE0EAF3)
                    )
                }
                Spacer(Modifier.width(12.dp))
                V403MiniStat(p.found.toString(), "encontrados", Icons.Default.Article, V403Blue)
                V403MiniStat(p.newCount.toString(), "novos", Icons.Default.AddCircle, V403Green)
                V403MiniStat(p.errors.toString(), "falhas", Icons.Default.Error, V403Red)
                V403MiniStat("${p.completed}/${p.total}", "etapas", Icons.Default.Settings, V403Blue)
                V403MiniStat("%02d:%02d".format(seconds / 60, seconds % 60), "tempo", Icons.Default.Schedule, V403Blue)
            }
            V403StatusStrip(status, p.active)
        }
    }
}

@Composable
private fun V403MiniStat(value: String, label: String, icon: ImageVector, color: Color) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = Color(0xFFF8FBFE),
        border = androidx.compose.foundation.BorderStroke(1.dp, V403Line),
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(value, color = V403Text, fontWeight = FontWeight.ExtraBold)
                Text(label, color = V403Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun V403ResultsHeader(
    title: String,
    subtitle: String,
    count: Int,
    sort: V403Sort,
    onSort: (V403Sort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = V403Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("$count exibida(s)", color = V403Muted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(12.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(9.dp)) {
                Text(sort.label)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                V403Sort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = { onSort(s); expanded = false },
                        leadingIcon = { if (s == sort) Icon(Icons.Default.Check, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun V403NewsCard(n: News, isNew: Boolean) {
    var copied by remember(n.link) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    Card(
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, V403Line),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = V403BlueSoft, modifier = Modifier.size(76.dp)) {
                Icon(Icons.Default.Article, null, tint = V403Blue, modifier = Modifier.padding(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${n.source} • ${v403Date(n.date)}", color = V403Muted, style = MaterialTheme.typography.labelMedium)
                    if (isNew) {
                        Spacer(Modifier.width(8.dp))
                        V403NewBadge("Nova", V403Blue)
                    }
                }
                Text(
                    n.title,
                    color = V403Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (n.snippet.isNotBlank()) {
                    Text(n.snippet, color = V403Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (n.matchedTerm.isNotBlank()) V403Tag(n.matchedTerm)
                    if (n.matchedDemand.isNotBlank()) V403Tag(n.matchedDemand)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { v403Open(n.link) }) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Abrir notícia")
                }
                OutlinedButton(onClick = { v403Whatsapp(n.title, n.link) }) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(17.dp), tint = V403Green)
                    Spacer(Modifier.width(5.dp))
                    Text("WhatsApp")
                }
                OutlinedButton(onClick = { v403Copy(n.link); copied = true }) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.Link, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (copied) "Copiado" else "Copiar link")
                }
            }
        }
    }
}

@Composable
private fun V403VideoCard(v: VideoItem, isNew: Boolean) {
    var copied by remember(v.link) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    Card(
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, V403Line),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFEDE8FF), modifier = Modifier.size(76.dp)) {
                Icon(Icons.Default.PlayCircle, null, tint = V403Purple, modifier = Modifier.padding(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${v.sourceName} • ${v403Date(v.publishedAt)}", color = V403Muted, style = MaterialTheme.typography.labelMedium)
                    if (isNew) {
                        Spacer(Modifier.width(8.dp))
                        V403NewBadge("Novo", V403Blue)
                    }
                }
                Text(
                    v.title,
                    color = V403Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (v.summary.isNotBlank()) {
                    Text(v.summary, color = V403Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (v.matchedTerm.isNotBlank()) V403Tag(v.matchedTerm)
                    if (v.matchedDemand.isNotBlank()) V403Tag(v.matchedDemand)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { v403Open(v.link) }) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Abrir vídeo")
                }
                OutlinedButton(onClick = { v403Copy(v.link); copied = true }) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.Link, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (copied) "Copiado" else "Copiar link")
                }
            }
        }
    }
}

@Composable
private fun V403Terms(c: DesktopController) {
    var nt by remember { mutableStateOf("") }
    var vt by remember { mutableStateOf("") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        V403TermColumn(
            "Termos de Notícias",
            "Usados na varredura de matérias",
            Icons.Default.Article,
            V403Blue,
            c.terms,
            nt,
            { nt = it },
            { c.addTerm(nt); nt = "" },
            c::removeTerm,
            Modifier.weight(1f)
        )
        V403TermColumn(
            "Termos de Vídeos",
            "Lista independente para vídeos",
            Icons.Default.PlayCircle,
            V403Purple,
            c.videoTerms,
            vt,
            { vt = it },
            { c.addVideoTerm(vt); vt = "" },
            c::removeVideoTerm,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun V403TermColumn(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    values: List<String>,
    value: String,
    onValue: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier
) {
    V403Panel(modifier = modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = .10f), modifier = Modifier.size(56.dp)) {
                    Icon(icon, null, tint = accent, modifier = Modifier.padding(14.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = V403Muted)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = V403BlueSoft) {
                    Text("${values.size} termo(s)", color = V403Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value,
                    onValue,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Novo termo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onAdd, enabled = value.isNotBlank()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Adicionar")
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(values, key = { it }) { term ->
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF5F8FB), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(9.dp), color = V403BlueSoft, modifier = Modifier.size(34.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("#", color = V403Blue, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(term, modifier = Modifier.weight(1f), color = V403Text)
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFE8E8)) {
                                IconButton(onClick = { onRemove(term) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.DeleteOutline, null, tint = V403Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V403Demands(c: DesktopController, now: Long) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var vehicleMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Demand?>(null) }
    val vehicles = remember { DesktopSourceCatalog.all.map { it.name }.distinct().sorted() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V403Panel(compact = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(430.dp)) {
                    OutlinedTextField(
                        vehicle,
                        { vehicle = it },
                        label = { Text("Veículo") },
                        leadingIcon = { Icon(Icons.Default.Public, null) },
                        trailingIcon = {
                            IconButton(onClick = { vehicleMenu = true }) {
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                        },
                        placeholder = { Text("Selecione ou digite o veículo...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = vehicleMenu,
                        onDismissRequest = { vehicleMenu = false },
                        modifier = Modifier.heightIn(max = 420.dp)
                    ) {
                        vehicles.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { vehicle = name; vehicleMenu = false })
                        }
                    }
                }
                OutlinedTextField(
                    subject,
                    { subject = it },
                    label = { Text("Assunto") },
                    leadingIcon = { Icon(Icons.Default.Article, null) },
                    placeholder = { Text("Digite o assunto da demanda...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { c.addDemand(vehicle, subject); vehicle = ""; subject = "" },
                    enabled = vehicle.isNotBlank() && subject.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Adicionar")
                }
                OutlinedButton(onClick = { c.searchAllDemands() }, enabled = !c.demandBusy) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (c.demandBusy) "Buscando..." else "Buscar todas")
                }
            }
        }

        if (c.demandProgress.startedAt > 0L) V403ProgressPanel("Demandas", c.demandProgress, now, c.status)
        else V403StatusStrip(c.status, c.demandBusy)

        if (c.demands.isEmpty()) {
            V403EmptyState(
                Icons.Default.NotificationsActive,
                "Nenhuma demanda carregada no momento",
                "Adicione uma nova demanda ou utilize os controles acima para começar.",
                Modifier.weight(1f)
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(c.demands, key = { it.id }) { d ->
                    Card(
                        shape = RoundedCornerShape(13.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, V403Line),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = V403GoldSoft, modifier = Modifier.size(46.dp)) {
                                Icon(Icons.Default.Assignment, null, tint = V403Gold, modifier = Modifier.padding(11.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${d.vehicle} • ${d.subject}", fontWeight = FontWeight.ExtraBold, color = V403Text)
                                Text(
                                    "Última busca: ${if (d.lastCheckedAt > 0) v403Date(d.lastCheckedAt) else "nunca"} • encontrados ${d.lastFoundCount} • novos ${d.lastNewCount}",
                                    color = V403Muted,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (d.lastError.isNotBlank()) Text(d.lastError, color = V403Red, style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = { selected = d }) { Text("Resultados") }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { c.searchDemand(d) }, enabled = !c.demandBusy) { Text("Buscar") }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { c.removeDemand(d.id) }) {
                                Icon(Icons.Default.DeleteOutline, null, tint = V403Red)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { d ->
        val expected = "${d.vehicle} • ${d.subject}"
        val matches = c.newsHistory.filter {
            it.matchedDemand.equals(expected, true) ||
                (it.matchedDemand.contains(d.vehicle, true) && it.matchedDemand.contains(d.subject, true))
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Fechar") } },
            title = { Text("Resultados da demanda") },
            text = {
                Column(
                    Modifier.widthIn(min = 760.dp, max = 1040.dp).heightIn(max = 650.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${d.vehicle} • ${d.subject} • ${matches.size} matéria(s)", color = V403Muted)
                    if (matches.isEmpty()) {
                        Text("Nenhum resultado armazenado para esta demanda.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(matches, key = { it.link }) { V403NewsCard(it, it.link in c.newsNewLinks) }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun V403Sources(c: DesktopController) {
    var videos by remember { mutableStateOf(false) }
    var specializedOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(SourceCatalog.ALL_REGION) }
    var state by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V403Panel(compact = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !videos && !specializedOnly,
                        onClick = { videos = false; specializedOnly = false },
                        label = { Text("Notícias") },
                        leadingIcon = { Icon(Icons.Default.Article, null) }
                    )
                    FilterChip(
                        selected = videos,
                        onClick = { videos = true; specializedOnly = false },
                        label = { Text("Vídeos") },
                        leadingIcon = { Icon(Icons.Default.PlayCircle, null) }
                    )
                    FilterChip(
                        selected = specializedOnly,
                        onClick = { videos = false; specializedOnly = true; region = SourceCatalog.ALL_REGION; state = "" },
                        label = { Text("Mídias especializadas (${DesktopSourceCatalog.specialized.size})") },
                        leadingIcon = { Icon(Icons.Default.Verified, null) }
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedTextField(
                        query,
                        { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Pesquisar fonte...") },
                        singleLine = true,
                        modifier = Modifier.width(430.dp)
                    )
                }

                if (!specializedOnly) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        item { Text("Região", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp)) }
                        items(SourceCatalog.regions, key = { it }) { r ->
                            FilterChip(selected = region == r, onClick = { region = r; state = "" }, label = { Text(r) })
                        }
                    }
                    val visibleStates = SourceCatalog.states.filter { region == SourceCatalog.ALL_REGION || it.third == region }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        item { Text("Estado", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp)) }
                        item { FilterChip(selected = state.isBlank(), onClick = { state = "" }, label = { Text("Todos") }) }
                        items(visibleStates, key = { it.first }) { s ->
                            FilterChip(selected = state == s.first, onClick = { state = s.first }, label = { Text(s.first) })
                        }
                    }
                }
            }
        }

        val rows: List<Triple<String, String, String>>
        val visibleIds: Set<String>
        if (videos) {
            val visible = VideoSourceCatalog.all.filter {
                (region == SourceCatalog.ALL_REGION || it.region == region) &&
                    (state.isBlank() || it.state == state) &&
                    (query.isBlank() || (listOf(it.name, it.group, it.region, it.state) + it.aliases).any { x -> x.contains(query, true) })
            }
            visibleIds = visible.map { it.id }.toSet()
            rows = visible.map { Triple(it.id, it.name, "${it.group} • ${it.region}${if (it.state.isNotBlank()) " • ${it.state}" else ""}") }
        } else {
            val visible = DesktopSourceCatalog.all.filter {
                (!specializedOnly || it.group == DesktopSourceCatalog.SPECIALIZED_GROUP) &&
                    (specializedOnly || region == SourceCatalog.ALL_REGION || it.region == region) &&
                    (specializedOnly || state.isBlank() || it.state == state) &&
                    (query.isBlank() || (listOf(it.name, it.group, it.region, it.state, it.stateName) + it.aliases).any { x -> x.contains(query, true) })
            }
            visibleIds = visible.map { it.id }.toSet()
            rows = visible.map { Triple(it.id, it.name, "${it.group} • ${it.region} • ${it.state}") }
        }

        V403Panel(compact = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = V403BlueSoft, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Default.Storage, null, tint = V403Blue, modifier = Modifier.padding(11.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("${rows.size} fonte(s) visível(is)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (!videos && c.newsAllSources) "Busca em todos os veículos ativa — inclui também as mídias especializadas Windows."
                        else "Selecione as fontes que participarão das varreduras.",
                        color = V403Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = { if (videos) c.setVideoSources(visibleIds, true) else c.setNewsSources(visibleIds, true) }) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Selecionar visíveis")
                }
                OutlinedButton(onClick = { if (videos) c.setVideoSources(visibleIds, false) else c.setNewsSources(visibleIds, false) }) {
                    Icon(Icons.Default.DeleteOutline, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Limpar visíveis")
                }
                OutlinedButton(onClick = { if (videos) c.selectAllVideoSources() else c.selectAllNewsSources() }) { Text("Todas") }
                OutlinedButton(onClick = { if (videos) c.clearVideoSources() else c.clearNewsSources() }) { Text("Nenhuma") }
            }
        }

        if (videos && c.selectedVideoSourceIds.isEmpty()) {
            V403StatusStrip("⚠ Nenhuma fonte de vídeo selecionada; a busca ficará bloqueada.", false)
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(rows, key = { it.first }) { row ->
                val checked = if (videos) row.first in c.selectedVideoSourceIds else c.newsAllSources || row.first in c.selectedNewsSourceIds
                val setChecked: (Boolean) -> Unit = { value ->
                    if (videos) {
                        c.setVideoSource(row.first, value)
                    } else if (c.newsAllSources && !value) {
                        c.selectAllNewsSources()
                        c.setNewsSource(row.first, false)
                    } else {
                        c.setNewsSource(row.first, value)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, V403Line),
                    modifier = Modifier.fillMaxWidth().clickable { setChecked(!checked) }
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, onCheckedChange = setChecked)
                        Surface(shape = RoundedCornerShape(9.dp), color = V403BlueSoft, modifier = Modifier.width(68.dp).height(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(v403Initials(row.second), color = V403Navy2, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.second, color = V403Text, fontWeight = FontWeight.ExtraBold)
                            Text(row.third, color = V403Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (!videos && row.first.startsWith("especializada-")) {
                            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE9F8EF)) {
                                Text("Especializada", color = V403Green, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = V403Blue)
                    }
                }
            }
        }
    }
}

@Composable
private fun V403History(c: DesktopController) {
    var tab by remember { mutableStateOf(0) }
    val news = c.newsHistory
    val videos = c.videoHistory
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        V403Panel(compact = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Arquivo monitorado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Alterações aparecem imediatamente nesta tela.", color = V403Muted, style = MaterialTheme.typography.bodySmall)
                }
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Notícias (${news.size})") }, leadingIcon = { Icon(Icons.Default.Article, null) })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Vídeos (${videos.size})") }, leadingIcon = { Icon(Icons.Default.PlayCircle, null) })
                if (tab == 0) {
                    OutlinedButton(onClick = { val f = c.exportNewsHistoryCsv(); v403OpenFolder(f.parentFile) }) {
                        Icon(Icons.Default.IosShare, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Exportar CSV")
                    }
                }
                OutlinedButton(
                    onClick = { if (tab == 0) c.clearNewsHistory() else c.clearVideoHistory() },
                    enabled = if (tab == 0) news.isNotEmpty() else videos.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V403Red)
                ) {
                    Icon(Icons.Default.DeleteOutline, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Limpar histórico")
                }
            }
        }
        V403StatusStrip(c.status, false)
        if (tab == 0) {
            if (news.isEmpty()) {
                V403EmptyState(
                    Icons.Default.Article,
                    "Nenhuma notícia armazenada",
                    "As próximas matérias capturadas aparecerão aqui em tempo real.",
                    Modifier.weight(1f)
                )
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(news, key = { it.link }) { V403NewsCard(it, it.link in c.newsNewLinks) }
                }
            }
        } else {
            if (videos.isEmpty()) {
                V403EmptyState(
                    Icons.Default.PlayCircle,
                    "Nenhum vídeo armazenado",
                    "Os próximos vídeos relevantes aparecerão aqui em tempo real.",
                    Modifier.weight(1f)
                )
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(videos, key = { it.link }) { V403VideoCard(it, it.link in c.videoNewLinks) }
                }
            }
        }
    }
}

@Composable
private fun V403Settings(c: DesktopController, revision: Int) {
    @Suppress("UNUSED_VARIABLE") val live = revision
    val nr = c.newsAutoReport()
    val dr = c.demandAutoReport()
    val vr = c.videoAutoReport()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item { ProxySettingsCard(c) }
        item { AutomationSettingsCard(c) }
        item { V403AutoReport("Notícias automáticas", nr, "${nr.found} resultado(s) • ${nr.newCount} nova(s)") }
        item { V403AutoReport("Demandas automáticas", dr, "${dr.checked} demanda(s) • ${dr.found} resultado(s) • ${dr.newCount} nova(s)") }
        item { V403AutoReport("Vídeos automáticos", vr, "${vr.found} detectado(s) • ${vr.newCount} novo(s) • ${vr.relevant} relevante(s)") }
        item {
            V403Panel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dados portáteis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Banco, termos, demandas, histórico, exportações e preferências ficam em:", color = V403Muted)
                    SelectionContainer { Text(c.context.filesDir.absolutePath, color = V403Blue) }
                }
            }
        }
        item {
            V403Panel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Edição Windows v4.0.3", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Inclui busca em camadas, aliases regionais inteligentes, cobertura direta complementar, diagnóstico de busca e interface operacional redesenhada. A versão Android permanece inalterada.",
                        color = V403Muted
                    )
                }
            }
        }
    }
}

@Composable
private fun V403AutoReport(title: String, r: DesktopController.AutoReport, summary: String) {
    V403Panel(compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (r.error.isNotBlank()) Color(0xFFFFEAEA) else Color(0xFFE9F8EF),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    if (r.error.isNotBlank()) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                    null,
                    tint = if (r.error.isNotBlank()) V403Red else V403Green,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Última tentativa: ${if (r.attemptAt > 0) v403Date(r.attemptAt) else "—"} • conclusão: ${if (r.completedAt > 0) v403Date(r.completedAt) else "—"}",
                    color = V403Muted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(summary, fontWeight = FontWeight.SemiBold)
            if (r.error.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(r.error, color = V403Red, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun V403Panel(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, V403Line),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Box(Modifier.padding(if (compact) 13.dp else 18.dp)) { content() }
    }
}

@Composable
private fun V403StatusStrip(text: String, busy: Boolean) {
    val warning = text.startsWith("⚠") || text.startsWith("Falha")
    val ok = text.startsWith("✓")
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = when {
            warning -> Color(0xFFFFEEEE)
            ok -> Color(0xFFE9F8EF)
            busy -> V403BlueSoft
            else -> Color(0xFFF5F8FB)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    when {
                        warning -> Icons.Default.WarningAmber
                        ok -> Icons.Default.CheckCircle
                        else -> Icons.Default.Info
                    },
                    null,
                    tint = when {
                        warning -> V403Red
                        ok -> V403Green
                        else -> V403Blue
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(text, color = V403Text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun V403EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier.fillMaxWidth().height(260.dp)
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, V403Line),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = V403BlueSoft, modifier = Modifier.size(82.dp)) {
                Icon(icon, null, tint = V403Blue, modifier = Modifier.padding(20.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = V403Text)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = V403Muted)
        }
    }
}

@Composable
private fun V403NewBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun V403Tag(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFEAF1FA)) {
        Text(
            text.take(56),
            color = V403Muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun v403Copy(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

private fun v403Open(url: String) {
    runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().browse(URI(url)) }
}

private fun v403OpenFolder(folder: File?) {
    if (folder == null) return
    runCatching { if (AwtDesktop.isDesktopSupported()) AwtDesktop.getDesktop().open(folder) }
}

private fun v403Whatsapp(title: String, link: String) {
    val text = URLEncoder.encode("$title\n$link", StandardCharsets.UTF_8)
    v403Open("https://wa.me/?text=$text")
}

private fun v403Date(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))

private fun v403ShortDate(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))

private fun v403Time(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(ms))

private fun v403ReportSummary(r: DesktopController.AutoReport): String = when {
    r.completedAt <= 0L && r.error.isBlank() -> "Sem execução concluída"
    r.error.isNotBlank() -> r.error.take(70)
    else -> "${r.found} encontrado(s) • ${r.newCount} novo(s)"
}

private fun v403Initials(name: String): String = name
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "FN" }

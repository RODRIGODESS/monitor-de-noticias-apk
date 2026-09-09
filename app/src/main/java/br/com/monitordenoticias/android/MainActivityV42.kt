package br.com.monitordenoticias.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val V42Bg = Color(0xFF03111F)
private val V42Surface = Color(0xFF071D31)
private val V42Surface2 = Color(0xFF0B2943)
private val V42Selected = Color(0xFF0C3D67)
private val V42Accent = Color(0xFF35A7FF)
private val V42Mint = Color(0xFF24E0B3)
private val V42Amber = Color(0xFFFFB657)
private val V42Purple = Color(0xFFA77BFF)
private val V42Red = Color(0xFFFF6B7A)
private val V42Text = Color(0xFFF5F8FC)
private val V42Text2 = Color(0xFFA9BAD0)
private val V42Divider = Color(0xFF174563)

private val V42Colors = darkColorScheme(
    primary = V42Accent,
    secondary = V42Mint,
    background = V42Bg,
    surface = V42Surface,
    surfaceVariant = V42Surface2,
    onPrimary = Color(0xFF04121F),
    onBackground = V42Text,
    onSurface = V42Text,
    onSurfaceVariant = V42Text2
)

class MainActivityV42 : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
        AutoSearchSettings.migrate(this)
        BackgroundMonitor.scheduleAll(this)
        VideoBackgroundMonitor.scheduleAll(this)

        setContent {
            MaterialTheme(colorScheme = V42Colors) {
                V42App()
            }
        }
    }
}

private enum class V42Section { HOME, VIDEOS, SOURCES, DEMANDS, PERIOD, HISTORY, TERMS, SETTINGS }
private data class V42NavItem(val section: V42Section, val label: String, val icon: ImageVector)

@Composable
private fun V42App(
    newsVm: MonitorViewModel = viewModel(),
    videoVm: VideoViewModel = viewModel()
) {
    val news by newsVm.state.collectAsState()
    val videos by videoVm.state.collectAsState()
    var section by remember { mutableStateOf(V42Section.HOME) }
    var showMore by remember { mutableStateOf(false) }
    var sourceTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = V42Bg,
        topBar = { V42TopBar(section) },
        bottomBar = { V42BottomBar(section, onSection = { section = it }, onMore = { showMore = true }) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(V42Bg)
        ) {
            val status = if (section == V42Section.VIDEOS) videos.status else news.status
            if (status.isNotBlank() && status != "Pronto") V42StatusStrip(status)
            if (section != V42Section.HOME && section != V42Section.VIDEOS) V42NewsPulse(news)
            if (news.searchProgress.active && section != V42Section.HOME && section != V42Section.VIDEOS) {
                V30CompactProgress(news.searchProgress)
            }
            when (section) {
                V42Section.HOME -> V30Home(news, newsVm, videos) { section = V42Section.VIDEOS }
                V42Section.VIDEOS -> V30Videos(videos, videoVm) { sourceTab = 1; section = V42Section.SOURCES }
                V42Section.SOURCES -> V30SourcesHub(news, newsVm, videos, videoVm, sourceTab) { sourceTab = it }
                V42Section.DEMANDS -> V42Demands(news, newsVm)
                V42Section.PERIOD -> V42Period(news, newsVm)
                V42Section.HISTORY -> V42History(news, newsVm)
                V42Section.TERMS -> V42Terms(news, newsVm, videos, videoVm)
                V42Section.SETTINGS -> V42Settings(news, newsVm, videos)
            }
        }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }, containerColor = V42Surface2) {
            Text("Central de opções", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            V42MoreItem(Icons.Outlined.DateRange, "Período", "Pesquisa por data e hora") { section = V42Section.PERIOD; showMore = false }
            V42MoreItem(Icons.Outlined.History, "Histórico", "Arquivo das matérias capturadas") { section = V42Section.HISTORY; showMore = false }
            V42MoreItem(Icons.Outlined.ManageSearch, "Termos", "Listas separadas de Notícias e Vídeos") { section = V42Section.TERMS; showMore = false }
            V42MoreItem(Icons.Outlined.Settings, "Configurações", "Automação independente, bateria e relatórios") { section = V42Section.SETTINGS; showMore = false }
            Spacer(Modifier.navigationBarsPadding().height(14.dp))
        }
    }
}

@Composable
private fun V42TopBar(section: V42Section) {
    val title = when (section) {
        V42Section.HOME -> "Monitor de Notícias"
        V42Section.VIDEOS -> "Monitor de Vídeos"
        V42Section.SOURCES -> "Fontes"
        V42Section.DEMANDS -> "Demandas"
        V42Section.PERIOD -> "Período"
        V42Section.HISTORY -> "Histórico"
        V42Section.TERMS -> "Termos"
        V42Section.SETTINGS -> "Configurações"
    }
    val subtitle = when (section) {
        V42Section.HOME -> "Inteligência de mídia em tempo real"
        V42Section.VIDEOS -> "TV, portais, YouTube e conteúdo audiovisual"
        V42Section.SOURCES -> "Notícias e fontes de vídeo"
        V42Section.DEMANDS -> "Alertas por veículo e assunto"
        V42Section.PERIOD -> "Defina o intervalo da pesquisa"
        V42Section.HISTORY -> "Arquivo das matérias capturadas"
        V42Section.TERMS -> "Termos separados para Notícias e Vídeos"
        V42Section.SETTINGS -> "Saúde, automação e preferências"
    }

    Surface(color = V42Bg, modifier = Modifier.statusBarsPadding()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(105.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(V42Surface, V42Selected.copy(alpha = .82f), V42Bg)
                    )
                )
        ) {
            Icon(
                Icons.Outlined.Radar,
                null,
                tint = V42Accent.copy(alpha = .055f),
                modifier = Modifier.size(120.dp).align(Alignment.CenterEnd).offset(x = 16.dp)
            )
            Row(
                Modifier.fillMaxWidth().height(75.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = V42Accent.copy(alpha = .10f),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, V42Accent.copy(alpha = .60f))
                ) {
                    Box(Modifier.size(49.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            if (section == V42Section.VIDEOS) Icons.Outlined.PlayCircle else Icons.Outlined.Radar,
                            null,
                            tint = V42Accent,
                            modifier = Modifier.size(29.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = V42Text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(
                    color = V42Accent.copy(alpha = .08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, V42Accent.copy(alpha = .30f))
                ) {
                    Text("v${BuildConfig.VERSION_NAME}", color = V42Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                }
            }
            Text(
                "VIGILÂNCIA  •  ANÁLISE  •  INFORMAÇÃO  •  DECISÃO",
                color = V42Accent.copy(alpha = .95f),
                fontSize = 8.4.sp,
                letterSpacing = 1.15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 10.dp)
            )
            Text(
                "INFORMAÇÃO QUE FORTALECE DECISÕES",
                color = V42Text2.copy(alpha = .72f),
                fontSize = 7.sp,
                letterSpacing = .75.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun V42BottomBar(section: V42Section, onSection: (V42Section) -> Unit, onMore: () -> Unit) {
    val nav = listOf(
        V42NavItem(V42Section.HOME, "Início", Icons.Outlined.Home),
        V42NavItem(V42Section.VIDEOS, "Vídeos", Icons.Outlined.SmartDisplay),
        V42NavItem(V42Section.SOURCES, "Fontes", Icons.Outlined.Layers),
        V42NavItem(V42Section.DEMANDS, "Demandas", Icons.Outlined.NotificationsNone)
    )
    val moreSelected = section in setOf(V42Section.PERIOD, V42Section.HISTORY, V42Section.TERMS, V42Section.SETTINGS)

    Surface(
        color = V42Surface.copy(alpha = .99f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, V42Accent.copy(alpha = .30f)),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(73.dp).padding(horizontal = 5.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            nav.forEach { item ->
                V42NavButton(item.label, item.icon, section == item.section, Modifier.weight(1f)) { onSection(item.section) }
            }
            V42NavButton("Mais", Icons.Outlined.MoreHoriz, moreSelected, Modifier.weight(1f), onMore)
        }
    }
}

@Composable
private fun V42NavButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) V42Accent.copy(alpha = .12f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(1.dp, V42Accent.copy(alpha = .50f)) else null,
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = if (selected) V42Accent else V42Text2, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (selected) V42Accent else V42Text2, fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun V42StatusStrip(text: String) {
    val warning = text.startsWith("⚠")
    Surface(color = if (warning) V42Amber.copy(alpha = .08f) else V42Accent.copy(alpha = .06f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info, null, tint = if (warning) V42Amber else V42Accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(text.removePrefix("⚠ "), color = V42Text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V42NewsPulse(s: AppState) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
    val autoStarted = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)
    val autoCompleted = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)
    val manualIsNews = s.searchProgress.kind.startsWith("Notícias")
    val manualStarted = if (manualIsNews) s.searchProgress.startedAt else 0L
    val manualCompleted = if (!manualIsNews) 0L else s.searchProgress.finishedAt.takeIf { it > 0L }
        ?: if (s.searchProgress.active) System.currentTimeMillis() else 0L
    val useManual = manualStarted > autoStarted
    val started = if (useManual) manualStarted else autoStarted
    val completed = if (useManual) manualCompleted else autoCompleted
    val newCount = s.news.count { v42InRun(it.capturedAt, started, completed) }
    val label = if (newCount > 0) "$newCount nova(s) notícia(s) encontrada(s)" else "${s.news.size} notícia(s) no escopo atual"
    Surface(
        color = V42Mint.copy(alpha = .055f),
        border = BorderStroke(1.dp, V42Mint.copy(alpha = .24f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(V42Mint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Article, null, tint = V42Mint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = if (newCount > 0) V42Mint else V42Text2, fontSize = 11.5.sp, fontWeight = if (newCount > 0) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
            if (completed > 0L) Text(v42DateTime(completed), color = V42Text2, fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun V42Demands(s: AppState, vm: MonitorViewModel) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var resultsDemand by remember { mutableStateOf<Demand?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(color = V42Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, V42Accent.copy(alpha = .40f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(V42Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.AddAlert, null, tint = V42Accent, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("Nova demanda", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Crie um alerta para monitorar um veículo e assunto", color = V42Text2, fontSize = 10.8.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, placeholder = { Text("Ex.: Band News, Globo, Folha de S.Paulo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, placeholder = { Text("Ex.: Amazônia, PROSUB, exercícios militares") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Outlined.AddAlert, null); Spacer(Modifier.width(7.dp)); Text("Adicionar demanda")
                    }
                }
            }
        }
        item {
            Button(onClick = vm::searchAllDemandsNow, enabled = !s.demandSearchBusy && s.demands.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Outlined.ManageSearch, null); Spacer(Modifier.width(7.dp)); Text(if (s.demandSearchBusy) "Pesquisando demandas..." else "Buscar demandas agora")
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.demands.size} demanda(s)", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Busca manual e automática", color = V42Text2, fontSize = 10.sp)
            }
        }
        if (s.demands.isEmpty()) {
            item { V42Empty("Nenhuma demanda", "Adicione veículo e assunto para criar um alerta.") }
        } else {
            items(s.demands, key = { it.id }) { d ->
                val demandKey = "${d.vehicle} • ${d.subject}"
                val resultItems = s.history.filter { it.matchedDemand == demandKey }.sortedByDescending { it.date }
                Surface(color = V42Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, V42Amber.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(V42Amber.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.NotificationsActive, null, tint = V42Amber, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.vehicle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(d.subject, color = V42Text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { vm.searchDemandNow(d.id) }, enabled = s.demandBusyId == null && !s.demandSearchBusy) { Icon(Icons.Outlined.Search, "Buscar", tint = V42Accent) }
                            IconButton(onClick = { vm.removeDemand(d.id) }) { Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = V42Red) }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 9.dp), color = V42Divider)
                        Text(if (d.lastCheckedAt > 0) "Última busca: ${v42DateTime(d.lastCheckedAt)}" else "Ainda não pesquisada", color = V42Text2, fontSize = 10.8.sp)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.lastFoundCount} resultado(s)  •  ${d.lastNewCount} novo(s)", color = if (d.lastNewCount > 0) V42Mint else V42Text2, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (d.lastFoundCount > 0) TextButton(onClick = { resultsDemand = d }) { Text("Ver resultados", fontSize = 10.5.sp) }
                        }
                        if (d.lastFoundCount > 0 && resultItems.isEmpty()) Text("Toque na lupa para atualizar e vincular os resultados desta demanda.", color = V42Amber, fontSize = 9.8.sp)
                        if (d.lastError.isNotBlank()) Text(d.lastError, color = V42Red, fontSize = 10.5.sp)
                    }
                }
            }
        }
    }

    resultsDemand?.let { demand ->
        val key = "${demand.vehicle} • ${demand.subject}"
        val matched = s.history.filter { it.matchedDemand == key }.sortedByDescending { it.date }
        ModalBottomSheet(onDismissRequest = { resultsDemand = null }, containerColor = V42Surface2) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Resultados da demanda", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("${demand.vehicle} • ${demand.subject}", color = V42Text2, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                if (matched.isEmpty()) V42Empty("Resultado ainda não vinculado", "Toque na lupa da demanda e abra novamente esta lista.")
                else LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(matched, key = { it.link }) { V42NewsCard(it) }
                }
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
            }
        }
    }
}

@Composable
private fun V42Period(s: AppState, vm: MonitorViewModel) {
    val from = v42ParseDateTime(s.periodStartDate, s.periodStartTime)
    val to = v42ParseDateTime(s.periodEndDate, s.periodEndTime)
    val valid = from != null && to != null && from < to

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Hoje" to 0, "24 horas" to 1, "7 dias" to 7, "30 dias" to 30).forEach { (label, days) -> V42Chip(label, false) { vm.applyPeriodPreset(days) } }
                }
            }
            item {
                Surface(color = V42Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, V42Accent.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DateRange, null, tint = V42Accent)
                            Spacer(Modifier.width(8.dp)); Text("Início", color = V42Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(s.periodStartDate, vm::setPeriodStartDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.45f))
                            OutlinedTextField(s.periodStartTime, vm::setPeriodStartTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = V42Divider)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DateRange, null, tint = V42Mint)
                            Spacer(Modifier.width(8.dp)); Text("Fim", color = V42Mint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(s.periodEndDate, vm::setPeriodEndDate, label = { Text("Data") }, singleLine = true, modifier = Modifier.weight(1.45f))
                            OutlinedTextField(s.periodEndTime, vm::setPeriodEndTime, label = { Text("Hora") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Surface(color = if (valid) V42Mint.copy(alpha = .08f) else V42Amber.copy(alpha = .08f), shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, if (valid) V42Mint.copy(alpha = .35f) else V42Amber.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (valid) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, null, tint = if (valid) V42Mint else V42Amber)
                        Spacer(Modifier.width(8.dp)); Text(if (valid) "Período válido e salvo automaticamente" else "Revise as datas e horários", color = if (valid) V42Mint else V42Amber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (s.news.isNotEmpty()) {
                item { Text("Resultados", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                items(s.news, key = { it.link }) { V42NewsCard(it) }
            }
        }
        Surface(color = V42Bg, tonalElevation = 8.dp, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            Button(onClick = vm::searchSavedPeriod, enabled = valid && !s.busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)) {
                Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp)); Text(if (s.busy) "Pesquisando..." else "Pesquisar período", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun V42History(s: AppState, vm: MonitorViewModel) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { v42ExportCsv(context, s.history) }, enabled = s.history.isNotEmpty(), modifier = Modifier.weight(1f).height(50.dp)) { Icon(Icons.Outlined.IosShare, null); Spacer(Modifier.width(6.dp)); Text("Exportar") }
                OutlinedButton(onClick = vm::clearHistory, enabled = s.history.isNotEmpty(), modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = V42Red)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("Limpar") }
            }
        }
        item { Text("${s.history.size} matéria(s)", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (s.history.isEmpty()) item { V42Empty("Histórico vazio", "As matérias encontradas aparecerão aqui.") }
        else items(s.history, key = { it.link }) { V42NewsCard(it) }
    }
}

@Composable
private fun V42Terms(s: AppState, vm: MonitorViewModel, videos: VideoState, videoVm: VideoViewModel) {
    var newsTerm by remember { mutableStateOf("") }
    var videoTerm by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(color = V42Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, V42Accent.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(V42Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Article, null, tint = V42Accent) }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("Termos de Notícias", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Google Notícias e varreduras diretas", color = V42Text2, fontSize = 10.8.sp)
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(newsTerm, { newsTerm = it }, label = { Text("Novo termo de Notícias") }, singleLine = true, modifier = Modifier.weight(1f))
                        FilledIconButton(onClick = { if (newsTerm.isNotBlank()) { vm.addTerm(newsTerm); newsTerm = "" } }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Add, "Adicionar") }
                    }
                }
            }
        }
        if (s.terms.isEmpty()) item { V42Empty("Nenhum termo de Notícias", "Adicione um termo para acompanhar matérias.") }
        else items(s.terms, key = { "news-term-$it" }) { value -> V42TermRow(value, Icons.Outlined.Article, V42Accent) { vm.removeTerm(value) } }

        item {
            Spacer(Modifier.height(4.dp)); HorizontalDivider(color = V42Divider); Spacer(Modifier.height(4.dp))
            Surface(color = V42Purple.copy(alpha = .07f), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, V42Purple.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(V42Purple.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SmartDisplay, null, tint = V42Purple) }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("Termos de Vídeos", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = V42Purple)
                            Text("Lista independente para o monitor audiovisual", color = V42Text2, fontSize = 10.8.sp)
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(videoTerm, { videoTerm = it }, label = { Text("Novo termo de Vídeos") }, singleLine = true, modifier = Modifier.weight(1f))
                        FilledIconButton(onClick = { if (videoTerm.isNotBlank()) { videoVm.addVideoTerm(videoTerm); videoTerm = "" } }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Add, "Adicionar") }
                    }
                }
            }
        }
        if (videos.videoTerms.isEmpty()) item { V42Empty("Nenhum termo de Vídeos", "Os vídeos ainda podem ser encontrados pelas Demandas ativas.") }
        else items(videos.videoTerms, key = { "video-term-$it" }) { value -> V42TermRow(value, Icons.Outlined.SmartDisplay, V42Purple) { videoVm.removeVideoTerm(value) } }

        item { Text("Os termos pré-estabelecidos da v4.1 são mantidos. Notícias e Vídeos continuam independentes e editáveis.", color = V42Text2, fontSize = 10.5.sp) }
    }
}

@Composable
private fun V42TermRow(value: String, icon: ImageVector, color: Color, onDelete: () -> Unit) {
    Surface(color = V42Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V42Divider), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = .11f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp)); Text(value, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Remover", tint = V42Red) }
        }
    }
}

@Composable
private fun V42Settings(s: AppState, vm: MonitorViewModel, videos: VideoState) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var autoConfig by remember { mutableStateOf(AutoSearchSettings.read(context)) }
    val prefs = remember(refreshKey) { context.getSharedPreferences(BackgroundMonitor.PREFS, 0) }
    val newsAttempt = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)
    val newsCompleted = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)
    val newsNewVisible = s.news.count { v42InRun(it.capturedAt, newsAttempt, newsCompleted) }
    val demandAttempt = prefs.getLong(AutoRunLog.KEY_DEMAND_ATTEMPT_AT, 0L)
    val demandCompleted = prefs.getLong(AutoRunLog.KEY_DEMAND_COMPLETED_AT, 0L)
    val videoAttempt = prefs.getLong(VideoAutoRunLog.KEY_ATTEMPT_AT, 0L)
    val videoCompleted = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)
    val videoFound = prefs.getInt(VideoAutoRunLog.KEY_FOUND, 0)
    val videoNew = videos.items.count { v42InRun(it.capturedAt, videoAttempt, videoCompleted) }
    val videoRelevant = prefs.getInt(VideoAutoRunLog.KEY_NEW_RELEVANT, 0)
    val videoErrors = prefs.getInt(VideoAutoRunLog.KEY_ERRORS, 0)
    val videoError = prefs.getString(VideoAutoRunLog.KEY_ERROR_TEXT, "").orEmpty()
    val powerManager = context.getSystemService(PowerManager::class.java)
    val unrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val latestAttempt = maxOf(newsAttempt, demandAttempt, videoAttempt)
    val now = System.currentTimeMillis()
    fun overdue(enabled: Boolean, lastAttempt: Long, intervalMinutes: Int): Boolean {
        if (!enabled || lastAttempt <= 0L) return false
        val tolerance = 30L * 60L * 1000L
        return now - lastAttempt > intervalMinutes.coerceAtLeast(15) * 60_000L + tolerance
    }
    val stale = overdue(autoConfig.newsEnabled, newsAttempt, autoConfig.newsIntervalMinutes) ||
        overdue(autoConfig.demandsEnabled, demandAttempt, autoConfig.demandsIntervalMinutes) ||
        overdue(autoConfig.videosEnabled, videoAttempt, autoConfig.videosIntervalMinutes)
    val anyEnabled = autoConfig.newsEnabled || autoConfig.demandsEnabled || autoConfig.videosEnabled
    val monitorColor = when {
        stale -> V42Amber
        anyEnabled -> V42Mint
        else -> V42Text2
    }
    val monitorBadge = when {
        stale -> "VERIFICAR AUTOMAÇÃO"
        anyEnabled -> "MONITOR ATIVO"
        else -> "AUTOMAÇÃO PAUSADA"
    }
    val monitorTitle = when {
        stale -> "Atenção ao monitor"
        anyEnabled -> "Monitor funcionando"
        else -> "Buscas automáticas pausadas"
    }

    fun reschedule() {
        BackgroundMonitor.scheduleAll(context)
        VideoBackgroundMonitor.scheduleAll(context)
        autoConfig = AutoSearchSettings.read(context)
        refreshKey++
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(color = monitorColor.copy(alpha = .07f), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, monitorColor.copy(alpha = .38f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(monitorColor.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(if (stale) Icons.Outlined.WarningAmber else if (anyEnabled) Icons.Outlined.Radar else Icons.Outlined.PauseCircleOutline, null, tint = monitorColor, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        V42Badge(monitorBadge, monitorColor)
                        Spacer(Modifier.height(6.dp))
                        Text(monitorTitle, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                latestAttempt > 0 -> "Último disparo: ${v42DateTime(latestAttempt)}"
                                anyEnabled -> "Aguardando primeira execução automática"
                                else -> "As buscas manuais continuam disponíveis"
                            },
                            color = V42Text2,
                            fontSize = 11.5.sp
                        )
                    }
                    FilledIconButton(onClick = { refreshKey++; autoConfig = AutoSearchSettings.read(context) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Refresh, "Atualizar") }
                }
            }
        }

        item {
            Text("Busca automática", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text("Cada monitor tem liga/desliga e intervalo próprios. A alteração é salva e reagendada imediatamente.", color = V42Text2, fontSize = 11.sp)
        }
        item {
            V42AutomationCard(
                "Notícias automáticas", "Google Notícias e varreduras diretas", Icons.Outlined.Article, V42Accent,
                autoConfig.newsEnabled, autoConfig.newsIntervalMinutes, newsAttempt,
                onEnabledChange = { AutoSearchSettings.setNewsEnabled(context, it); reschedule() },
                onIntervalChange = { AutoSearchSettings.setNewsInterval(context, it); vm.setInterval(it); reschedule() }
            )
        }
        item {
            V42AutomationCard(
                "Demandas automáticas", "Executa todas as Demandas cadastradas", Icons.Outlined.NotificationsActive, V42Amber,
                autoConfig.demandsEnabled, autoConfig.demandsIntervalMinutes, demandAttempt,
                onEnabledChange = { AutoSearchSettings.setDemandsEnabled(context, it); reschedule() },
                onIntervalChange = { AutoSearchSettings.setDemandsInterval(context, it); reschedule() }
            )
        }
        item {
            V42AutomationCard(
                "Vídeos automáticos", "TV, portais, YouTube e fontes selecionadas", Icons.Outlined.SmartDisplay, V42Purple,
                autoConfig.videosEnabled, autoConfig.videosIntervalMinutes, videoAttempt,
                onEnabledChange = { AutoSearchSettings.setVideosEnabled(context, it); reschedule() },
                onIntervalChange = { AutoSearchSettings.setVideosInterval(context, it); reschedule() }
            )
        }

        item { Text("Últimos ciclos", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
        item { V42ReportCard("Notícias", Icons.Outlined.Article, V42Accent, newsAttempt, newsCompleted, "${prefs.getInt(AutoRunLog.KEY_NEWS_FOUND, 0)} resultado(s) • $newsNewVisible nova(s)", prefs.getString(AutoRunLog.KEY_NEWS_ERROR_TEXT, "").orEmpty()) }
        item { V42ReportCard("Demandas", Icons.Outlined.NotificationsActive, V42Amber, demandAttempt, demandCompleted, "${prefs.getInt(AutoRunLog.KEY_DEMAND_CHECKED, 0)} demanda(s) • ${prefs.getInt(AutoRunLog.KEY_DEMAND_NEW, 0)} nova(s)", prefs.getString(AutoRunLog.KEY_DEMAND_ERROR_TEXT, "").orEmpty()) }
        item { V42ReportCard("Vídeos", Icons.Outlined.SmartDisplay, V42Purple, videoAttempt, videoCompleted, "$videoFound detectado(s) • $videoNew novo(s) • $videoRelevant relevante(s) • $videoErrors falha(s)", videoError) }

        item {
            Surface(color = V42Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, V42Divider), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background((if (unrestricted) V42Mint else V42Amber).copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.BatterySaver, null, tint = if (unrestricted) V42Mint else V42Amber) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bateria e segundo plano", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(if (unrestricted) "Sem restrição de bateria detectada" else "O Android pode atrasar tarefas em repouso", color = V42Text2, fontSize = 11.5.sp)
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
            Surface(color = V42Accent.copy(alpha = .06f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, V42Accent.copy(alpha = .22f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Monitor de Notícias ${BuildConfig.VERSION_NAME}", color = V42Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Central integrada de notícias, demandas e vídeos. ${videos.selectedSourceIds.size} fonte(s) de vídeo ativa(s).", color = V42Text2, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
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
    Surface(color = V42Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (enabled) color.copy(alpha = .44f) else V42Divider), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = V42Text2, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                V42Badge(if (enabled) "ATIVO" else "PAUSADO", if (enabled) V42Mint else V42Text2)
                Spacer(Modifier.width(8.dp))
                Text("A cada ${v42IntervalLabel(intervalMinutes)}", color = color, fontSize = 11.2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (lastAttempt > 0) Text("último ${v42DateTime(lastAttempt)}", color = V42Text2, fontSize = 9.3.sp)
            }
            Spacer(Modifier.height(9.dp))
            Text("Intervalo", color = V42Text2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AutoSearchSettings.intervalOptions.forEach { minutes -> V42Chip(v42IntervalLabel(minutes), intervalMinutes == minutes) { onIntervalChange(minutes) } }
            }
            if (!enabled) {
                Spacer(Modifier.height(7.dp))
                Text("A busca manual continua disponível mesmo com a automação pausada.", color = V42Text2, fontSize = 9.8.sp)
            }
        }
    }
}

@Composable
private fun V42ReportCard(title: String, icon: ImageVector, color: Color, attemptAt: Long, completedAt: Long, summary: String, error: String) {
    Surface(color = V42Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, color.copy(alpha = .24f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(21.dp)) }
                Spacer(Modifier.width(10.dp)); Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (error.isNotBlank()) Icons.Outlined.ErrorOutline else if (completedAt > 0) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, null, tint = if (error.isNotBlank()) V42Red else if (completedAt > 0) V42Mint else V42Text2)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V42RunTimeBox("Última tentativa", attemptAt, Modifier.weight(1f))
                V42RunTimeBox("Última conclusão", completedAt, Modifier.weight(1f))
            }
            if (completedAt > 0) { Spacer(Modifier.height(8.dp)); Text(summary, color = color, fontSize = 11.4.sp, fontWeight = FontWeight.Bold) }
            if (error.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(error, color = V42Red, fontSize = 10.8.sp) }
        }
    }
}

@Composable
private fun V42RunTimeBox(label: String, value: Long, modifier: Modifier) {
    Surface(color = V42Surface2.copy(alpha = .68f), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = V42Text2, fontSize = 9.5.sp)
            Text(if (value > 0) v42DateTime(value) else "—", fontSize = 11.2.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun V42NewsCard(n: News) {
    val context = LocalContext.current
    val openNews = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) }; Unit }
    Surface(color = V42Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, V42Accent.copy(alpha = .28f)), modifier = Modifier.fillMaxWidth().clickable(onClick = openNews)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(V42Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Article, null, tint = V42Accent, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(9.dp))
                Text(n.source, color = V42Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v42DateTime(n.date), color = V42Text2, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(n.title, fontSize = 14.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            val snippet = v42Clean(n.snippet)
            if (snippet.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(snippet, color = V42Text2, fontSize = 11.2.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(8.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (n.demand) V42Badge("DEMANDA", V42Amber)
                    if (n.matchedTerm.isNotBlank()) V42Badge(n.matchedTerm, V42Accent)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = openNews, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Abrir", maxLines = 1, fontSize = 10.5.sp)
                }
                OutlinedButton(
                    onClick = {
                        context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Link da notícia", n.link))
                        android.widget.Toast.makeText(context, "Link copiado", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = V42Accent)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Copiar link", maxLines = 1, fontSize = 10.2.sp)
                }
                OutlinedButton(onClick = { v42ShareWhatsApp(context, n.title, n.link) }, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = V42Mint)) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("WhatsApp", maxLines = 1, fontSize = 10.2.sp)
                }
            }
        }
    }
}

@Composable
private fun V42Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) V42Accent.copy(alpha = .14f) else V42Surface, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, if (selected) V42Accent.copy(alpha = .50f) else V42Divider), modifier = Modifier.height(35.dp).clickable(onClick = onClick)) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(label, color = if (selected) V42Accent else V42Text2, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) }
    }
}

@Composable
private fun V42Badge(label: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = .32f))) {
        Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun V42Empty(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(V42Surface), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SearchOff, null, tint = V42Accent, modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.height(10.dp)); Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(3.dp)); Text(subtitle, color = V42Text2, fontSize = 11.5.sp)
    }
}

@Composable
private fun V42MoreItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(subtitle, color = V42Text2) },
        leadingContent = { Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(V42Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = V42Accent) } },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun v42IntervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private fun v42InRun(capturedAt: Long, startedAt: Long, completedAt: Long): Boolean {
    if (capturedAt <= 0L || startedAt <= 0L || completedAt < startedAt) return false
    return capturedAt in startedAt..(completedAt + 5_000L)
}

private fun v42DateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
private fun v42ParseDateTime(date: String, time: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply { isLenient = false }.parse("$date $time")?.time }.getOrNull()
private fun v42Clean(value: String): String = value.replace("&nbsp;", " ", ignoreCase = true).replace("&amp;", "&", ignoreCase = true).replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

private fun v42ShareWhatsApp(context: android.content.Context, title: String, link: String) {
    val message = listOf(title.trim(), link.trim()).filter { it.isNotBlank() }.joinToString("\n")
    if (message.isBlank()) return
    fun shareIntent(packageName: String? = null) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        packageName?.let(::setPackage)
    }
    if (runCatching { context.startActivity(shareIntent("com.whatsapp")) }.isSuccess) return
    if (runCatching { context.startActivity(shareIntent("com.whatsapp.w4b")) }.isSuccess) return
    runCatching { context.startActivity(Intent.createChooser(shareIntent(), "Compartilhar link")) }
}

private fun v42ExportCsv(context: android.content.Context, items: List<News>) {
    if (items.isEmpty()) return
    val header = "data;fonte;titulo;link\n"
    val body = items.joinToString("\n") { item ->
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(item.date))
        listOf(date, item.source, item.title, item.link).joinToString(";") { field -> "\"${field.replace("\"", "\"\"")}\"" }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Histórico - Monitor de Notícias")
        putExtra(Intent.EXTRA_TEXT, header + body)
    }
    context.startActivity(Intent.createChooser(intent, "Exportar histórico"))
}

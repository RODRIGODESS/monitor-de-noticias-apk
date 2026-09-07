package br.com.monitordenoticias.android

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

private val AppBg = Color(0xFF0D0E13)
private val SurfaceDark = Color(0xFF171920)
private val SurfaceRaised = Color(0xFF20232C)
private val Accent = Color(0xFF6EA8FE)
private val Mint = Color(0xFF5BD8B2)
private val Amber = Color(0xFFFFB86B)
private val TextSecondary = Color(0xFFB7BAC5)

private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF10213B),
    secondary = Mint,
    background = AppBg,
    surface = SurfaceDark,
    surfaceVariant = SurfaceRaised,
    onBackground = Color(0xFFF5F6FA),
    onSurface = Color(0xFFF5F6FA),
    onSurfaceVariant = TextSecondary
)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
        setContent { MaterialTheme(colorScheme = AppColors) { MonitorApp() } }
    }
}

private data class AppTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorApp(vm: MonitorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val tabs = listOf(
        AppTab("Notícias", Icons.Outlined.Article),
        AppTab("Período", Icons.Outlined.DateRange),
        AppTab("Demandas", Icons.Outlined.NotificationsActive),
        AppTab("Histórico", Icons.Outlined.History),
        AppTab("Termos", Icons.Outlined.ManageSearch),
        AppTab("Fontes", Icons.Outlined.Public),
        AppTab("Config.", Icons.Outlined.Settings)
    )

    Scaffold(
        containerColor = AppBg,
        topBar = {
            Column(Modifier.background(AppBg)) {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppBg),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Monitor de Notícias", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                            Text("Painel inteligente de monitoramento", color = TextSecondary, fontSize = 11.sp)
                        }
                    },
                    actions = {
                        Surface(
                            color = Accent.copy(alpha = .12f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text("2.2.0", color = Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                    }
                )
                ScrollableTabRow(
                    selectedTabIndex = state.selectedTab,
                    containerColor = AppBg,
                    contentColor = Accent,
                    edgePadding = 10.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = state.selectedTab == index,
                            onClick = { vm.setTab(index) },
                            text = { Text(tab.label, fontSize = 12.sp, fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(20.dp)) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(AppBg)) {
            if (state.status.isNotBlank() && state.status != "Pronto") {
                Surface(color = Color(0xFF14161C), modifier = Modifier.fillMaxWidth()) {
                    Text(state.status, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp))
                }
            }
            when (state.selectedTab) {
                0 -> NewsScreen(state, vm)
                1 -> PeriodScreen(state, vm)
                2 -> DemandScreen(state, vm)
                3 -> HistoryScreen(state, vm)
                4 -> TermsScreen(state, vm)
                5 -> SourcesScreen(state, vm)
                else -> SettingsScreen(state, vm)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Accent.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun NewsScreen(s: AppState, vm: MonitorViewModel) {
    val shown = if (s.showOnlyDemands) s.news.filter { it.demand } else s.news
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader("Últimas notícias", "Monitoramento automático das últimas 24 horas", Icons.Outlined.Radar)
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF1B2740), Color(0xFF171920)))).padding(19.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(Accent.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.ManageSearch, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Painel de monitoramento", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                            Text(
                                if (s.searchAllSources) "Busca aberta em qualquer veículo" else "${s.selectedSourceIds.size} fonte(s) selecionada(s)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = vm::search, enabled = !s.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), contentPadding = PaddingValues(vertical = 13.dp)) {
                        Icon(if (s.busy) Icons.Outlined.HourglassTop else Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.busy) "Buscando..." else "Buscar agora", fontWeight = FontWeight.Bold)
                    }
                    s.lastUpdatedAt?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Última atualização: ${dateText(it)}", color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Notícias", s.news.size.toString(), Accent, Modifier.weight(1f))
            MetricCard("Demandas", s.news.count { it.demand }.toString(), Amber, Modifier.weight(1f))
            MetricCard("Fontes", s.news.map { it.source }.distinct().size.toString(), Mint, Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !s.showOnlyDemands, onClick = { vm.setDemandFilter(false) }, label = { Text("Todas") })
            FilterChip(selected = s.showOnlyDemands, onClick = { vm.setDemandFilter(true) }, label = { Text("Demandas") })
        }
        Spacer(Modifier.height(7.dp))
        NewsList(shown)
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun NewsList(items: List<News>) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(68.dp).clip(CircleShape).background(SurfaceDark), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ManageSearch, contentDescription = null, tint = Accent, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(13.dp))
                Text("Nenhuma notícia encontrada", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Ajuste as fontes ou execute uma nova busca.", color = TextSecondary, fontSize = 11.sp)
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(items, key = { it.link }) { n ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(19.dp),
                    modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(n.link))) }
                ) {
                    Column(Modifier.padding(15.dp)) {
                        if (n.demand) {
                            Surface(color = Amber.copy(alpha = .14f), shape = RoundedCornerShape(20.dp)) {
                                Text("DEMANDA", color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Spacer(Modifier.height(7.dp))
                        }
                        Text(n.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("${n.source} • ${dateText(n.date)}", color = TextSecondary, fontSize = 10.sp)
                        if (n.matchedTerm.isNotBlank()) Text("Termo: ${n.matchedTerm}", color = Accent, fontSize = 10.sp)
                        if (n.matchedDemand.isNotBlank()) Text("Demanda: ${n.matchedDemand}", color = Amber, fontSize = 10.sp)
                        if (n.snippet.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(n.snippet, color = TextSecondary, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodScreen(s: AppState, vm: MonitorViewModel) {
    val fromMs = parseDateTimeUi(s.periodStartDate, s.periodStartTime)
    val toMs = parseDateTimeUi(s.periodEndDate, s.periodEndTime)
    val valid = fromMs != null && toMs != null && fromMs < toMs

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader("Período", "Datas e horários ficam salvos automaticamente", Icons.Outlined.EventAvailable)
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Atalhos rápidos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(7.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AssistChip(onClick = { vm.applyPeriodPreset(0) }, label = { Text("Hoje") }, leadingIcon = { Icon(Icons.Outlined.Today, null, Modifier.size(17.dp)) })
                    AssistChip(onClick = { vm.applyPeriodPreset(1) }, label = { Text("24 horas") })
                    AssistChip(onClick = { vm.applyPeriodPreset(7) }, label = { Text("7 dias") })
                    AssistChip(onClick = { vm.applyPeriodPreset(30) }, label = { Text("30 dias") })
                }

                Spacer(Modifier.height(14.dp))
                Text("Início", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = s.periodStartDate,
                        onValueChange = vm::setPeriodStartDate,
                        label = { Text("Data") },
                        placeholder = { Text("dd/MM/aaaa") },
                        singleLine = true,
                        modifier = Modifier.weight(1.6f)
                    )
                    OutlinedTextField(
                        value = s.periodStartTime,
                        onValueChange = vm::setPeriodStartTime,
                        label = { Text("Hora") },
                        placeholder = { Text("HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(11.dp))
                Text("Fim", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = s.periodEndDate,
                        onValueChange = vm::setPeriodEndDate,
                        label = { Text("Data") },
                        placeholder = { Text("dd/MM/aaaa") },
                        singleLine = true,
                        modifier = Modifier.weight(1.6f)
                    )
                    OutlinedTextField(
                        value = s.periodEndTime,
                        onValueChange = vm::setPeriodEndTime,
                        label = { Text("Hora") },
                        placeholder = { Text("HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(11.dp))
                Surface(color = if (valid) Mint.copy(alpha = .08f) else Amber.copy(alpha = .10f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (valid) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, null, tint = if (valid) Mint else Amber, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (valid) "Período válido e salvo" else "Confira data/hora: o início deve ser anterior ao fim",
                            color = if (valid) Mint else Amber,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::searchSavedPeriod, enabled = valid && !s.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    Icon(Icons.Outlined.Search, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Pesquisar neste período", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        NewsList(s.news)
    }
}

@Composable
fun DemandScreen(s: AppState, vm: MonitorViewModel) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader("Demandas", "Alertas direcionados por veículo e assunto", Icons.Outlined.NotificationsActive)
        Spacer(Modifier.height(15.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(15.dp)) {
                OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = { vm.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Adicionar demanda") }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.demands, key = { it.id }) { d ->
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(d.vehicle, fontWeight = FontWeight.Bold); Text(d.subject, color = TextSecondary, fontSize = 11.sp) }
                        TextButton(onClick = { vm.removeDemand(d.id) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(s: AppState, vm: MonitorViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader("Histórico", "${s.history.size} matéria(s) armazenada(s)", Icons.Outlined.History)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportCsv(context, s.history) }, enabled = s.history.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Exportar CSV") }
            OutlinedButton(onClick = vm::clearHistory, enabled = s.history.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Limpar") }
        }
        Spacer(Modifier.height(8.dp))
        NewsList(s.history)
    }
}

@Composable
fun TermsScreen(s: AppState, vm: MonitorViewModel) {
    var term by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader("Termos monitorados", "Palavras e expressões usadas em todas as buscas", Icons.Outlined.ManageSearch)
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(21.dp)) {
            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(term, { term = it }, label = { Text("Novo termo") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(7.dp))
                Button(onClick = { vm.addTerm(term); term = "" }, enabled = term.isNotBlank(), shape = RoundedCornerShape(14.dp)) { Text("+") }
            }
        }
        Spacer(Modifier.height(9.dp))
        LazyColumn {
            items(s.terms) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tag, null, tint = Accent)
                    Spacer(Modifier.width(8.dp))
                    Text(t, Modifier.weight(1f))
                    TextButton(onClick = { vm.removeTerm(t) }) { Text("Excluir") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(s: AppState, vm: MonitorViewModel) {
    var sourceTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("Todas") }
    var stateCode by remember { mutableStateOf("Todos") }
    var stateExpanded by remember { mutableStateOf(false) }

    val stateOptions = SourceCatalog.states.filter { region == "Todas" || it.third == region }
    val baseSources = when (sourceTab) {
        0 -> SourceCatalog.national
        1 -> SourceCatalog.byState
        else -> emptyList()
    }
    val filteredSources = baseSources.filter { source ->
        val regionOk = sourceTab != 1 || region == "Todas" || source.region == region
        val stateOk = sourceTab != 1 || stateCode == "Todos" || source.state == stateCode
        val searchOk = query.isBlank() || listOf(source.name, source.group, source.stateName, source.region).any { it.contains(query, ignoreCase = true) }
        regionOk && stateOk && searchOk
    }
    val visibleIds = filteredSources.map { it.id }.toSet()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader("Fontes", "Escolha onde o monitor deve concentrar a pesquisa", Icons.Outlined.Public)
        Spacer(Modifier.height(13.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = if (s.searchAllSources) Color(0xFF182A2A) else SurfaceDark),
            border = BorderStroke(1.dp, if (s.searchAllSources) Mint.copy(alpha = .65f) else Color.Transparent),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().clickable { vm.setSearchAllSources(!s.searchAllSources) }
        ) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Mint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Language, null, tint = Mint)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Buscar em todos os veículos", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Busca aberta: inclui portais grandes, locais e veículos menores", color = TextSecondary, fontSize = 10.sp)
                }
                Switch(checked = s.searchAllSources, onCheckedChange = vm::setSearchAllSources)
            }
        }

        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = sourceTab, containerColor = SurfaceDark, contentColor = Accent, divider = {}) {
            Tab(selected = sourceTab == 0, onClick = { sourceTab = 0 }, text = { Text("Nacionais") }, icon = { Icon(Icons.Outlined.Newspaper, null) })
            Tab(selected = sourceTab == 1, onClick = { sourceTab = 1 }, text = { Text("Estados") }, icon = { Icon(Icons.Outlined.Map, null) })
            Tab(selected = sourceTab == 2, onClick = { sourceTab = 2 }, text = { Text("Qualquer") }, icon = { Icon(Icons.Outlined.TravelExplore, null) })
        }

        if (sourceTab == 2) {
            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.TravelExplore, null, tint = Accent, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Busca aberta em qualquer fonte", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Use este modo quando quiser encontrar matérias de qualquer veículo indexado, inclusive sites pequenos que não estejam no catálogo.", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(13.dp))
                    Button(onClick = { vm.setSearchAllSources(true) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Search, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Ativar busca em qualquer veículo")
                    }
                }
            }
            return@Column
        }

        Spacer(Modifier.height(11.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null) } },
            label = { Text("Pesquisar veículo ou grupo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (sourceTab == 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceCatalog.regions.filter { it != "Nacional" }.forEach { item ->
                    FilterChip(
                        selected = region == item,
                        onClick = { region = item; stateCode = "Todos" },
                        label = { Text(item) }
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            ExposedDropdownMenuBox(expanded = stateExpanded, onExpandedChange = { stateExpanded = !stateExpanded }) {
                OutlinedTextField(
                    value = if (stateCode == "Todos") "Todos os estados" else SourceCatalog.states.firstOrNull { it.first == stateCode }?.second ?: stateCode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Estado") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = stateExpanded, onDismissRequest = { stateExpanded = false }) {
                    DropdownMenuItem(text = { Text("Todos os estados") }, onClick = { stateCode = "Todos"; stateExpanded = false })
                    stateOptions.forEach { st ->
                        DropdownMenuItem(text = { Text("${st.second} (${st.first})") }, onClick = { stateCode = st.first; stateExpanded = false })
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${filteredSources.size} veículo(s) visível(is)", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.setVisibleSources(visibleIds, true) }, enabled = visibleIds.isNotEmpty()) { Text("Selecionar visíveis") }
            TextButton(onClick = { vm.setVisibleSources(visibleIds, false) }, enabled = visibleIds.isNotEmpty()) { Text("Desmarcar") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(filteredSources, key = { it.id }) { source ->
                SourceSelectionCard(source, s.selectedSourceIds.contains(source.id)) { selected ->
                    vm.setSourceSelected(source.id, selected)
                }
            }
        }
    }
}

@Composable
private fun SourceSelectionCard(source: MediaSource, selected: Boolean, onSelected: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF1D2B42) else SurfaceDark),
        border = BorderStroke(if (selected) 1.4.dp else 1.dp, if (selected) Accent else Color(0xFF2A2D36)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable { onSelected(!selected) }
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) Accent.copy(alpha = .16f) else SurfaceRaised),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Newspaper, null, tint = if (selected) Accent else TextSecondary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text(source.group, color = TextSecondary, fontSize = 10.sp)
            }
            Checkbox(checked = selected, onCheckedChange = onSelected)
        }
    }
}

@Composable
fun SettingsScreen(s: AppState, vm: MonitorViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader("Configurações", "Preferências persistentes do monitor", Icons.Outlined.Settings)
        Spacer(Modifier.height(15.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Monitoramento automático", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Intervalo salvo: ${s.intervalMinutes} minutos", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(15, 30, 45, 60).forEach { m ->
                        FilterChip(selected = s.intervalMinutes == m, onClick = { vm.setInterval(m) }, label = { Text("$m min") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("A configuração permanece após fechar ou reiniciar o aplicativo.", color = Mint, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Fontes ativas", fontWeight = FontWeight.Bold)
                Text(
                    if (s.searchAllSources) "Qualquer veículo" else "${s.selectedSourceIds.size} fonte(s) selecionada(s)",
                    color = Accent,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("Período salvo: ${s.periodStartDate} ${s.periodStartTime} → ${s.periodEndDate} ${s.periodEndTime}", color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

fun dateText(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))

private fun parseDateTimeUi(date: String, time: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).apply { isLenient = false }.parse("$date $time")?.time
}.getOrNull()

fun exportCsv(context: android.content.Context, items: List<News>) {
    fun clean(v: String) = v.replace(";", ",").replace("\n", " ")
    val csv = buildString {
        append("Publicação;Captura;Veículo;Título;Termo;Demanda;Link\n")
        items.forEach {
            append(dateText(it.date)).append(';')
                .append(dateText(it.capturedAt)).append(';')
                .append(clean(it.source)).append(';')
                .append(clean(it.title)).append(';')
                .append(clean(it.matchedTerm)).append(';')
                .append(clean(it.matchedDemand)).append(';')
                .append(it.link).append('\n')
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(Intent.createChooser(send, "Exportar CSV"))
}

package br.com.monitordenoticias.android

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val V25Bg = Color(0xFF0C0E13)
private val V25Surface = Color(0xFF151820)
private val V25Selected = Color(0xFF1D2940)
private val V25Accent = Color(0xFF6EA8FE)
private val V25Mint = Color(0xFF5BD8B2)
private val V25Amber = Color(0xFFFFB86B)
private val V25Text2 = Color(0xFFAEB4C2)
private val V25Divider = Color(0xFF262A35)

private val V25Colors = darkColorScheme(
    primary = V25Accent,
    background = V25Bg,
    surface = V25Surface,
    onBackground = Color(0xFFF4F6FA),
    onSurface = Color(0xFFF4F6FA)
)

class MainActivityV25 : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        setContent { MaterialTheme(colorScheme = V25Colors) { MonitorAppV25() } }
    }
}

private data class V25Tab(val label: String, val icon: ImageVector)

@Composable
private fun MonitorAppV25(vm: MonitorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val tabs = remember {
        listOf(
            V25Tab("Notícias", Icons.Outlined.Article),
            V25Tab("Período", Icons.Outlined.DateRange),
            V25Tab("Demandas", Icons.Outlined.NotificationsActive),
            V25Tab("Histórico", Icons.Outlined.History),
            V25Tab("Termos", Icons.Outlined.ManageSearch),
            V25Tab("Fontes", Icons.Outlined.Public),
            V25Tab("Config.", Icons.Outlined.Settings)
        )
    }

    Scaffold(containerColor = V25Bg, topBar = {
        Surface(color = V25Bg, modifier = Modifier.statusBarsPadding()) {
            Column {
                Row(
                    Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Radar, null, tint = V25Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Monitor de Notícias", fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("2.5.0", color = V25Accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 7.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val active = state.selectedTab == index
                        Surface(
                            color = if (active) V25Selected else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            border = if (active) BorderStroke(1.dp, V25Accent.copy(alpha = .3f)) else null,
                            modifier = Modifier.height(31.dp).clickable { vm.setTab(index) }
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(tab.icon, tab.label, tint = if (active) V25Accent else V25Text2, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tab.label, fontSize = 9.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                HorizontalDivider(color = V25Divider)
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(V25Bg)) {
            if (state.status.isNotBlank() && state.status != "Pronto") {
                Text(
                    state.status,
                    color = V25Text2,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().background(V25Accent.copy(alpha = .05f)).padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            when (state.selectedTab) {
                0 -> NewsScreen(state, vm)
                1 -> PeriodScreen(state, vm)
                2 -> DemandScreenV25(state, vm)
                3 -> HistoryScreen(state, vm)
                4 -> TermsScreen(state, vm)
                5 -> SourcesScreen(state, vm)
                else -> SettingsScreen(state, vm)
            }
        }
    }
}

@Composable
private fun DemandScreenV25(s: AppState, vm: MonitorViewModel) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    val checked = s.demands.count { it.lastCheckedAt > 0 }
    val lastRun = s.demands.maxOfOrNull { it.lastCheckedAt } ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Demandas", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Busca independente automática a cada 1 hora", color = V25Text2, fontSize = 9.5.sp)
                }
                Button(
                    onClick = vm::searchAllDemandsNow,
                    enabled = !s.demandSearchBusy && s.demands.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (s.demandSearchBusy) "Buscando" else "Buscar todas", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Surface(color = V25Surface, shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = V25Accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$checked/${s.demands.size} demanda(s) já verificadas", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (lastRun > 0) "Última execução: ${demandDateTime(lastRun)}" else "Ainda não houve execução",
                            color = V25Text2,
                            fontSize = 8.5.sp
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    vehicle, { vehicle = it }, label = { Text("Veículo", fontSize = 8.5.sp) },
                    singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    subject, { subject = it }, label = { Text("Assunto", fontSize = 8.5.sp) },
                    singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp), modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = { vm.addDemand(vehicle, subject); vehicle = ""; subject = "" },
                    enabled = vehicle.isNotBlank() && subject.isNotBlank(), modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Outlined.Add, "Adicionar", modifier = Modifier.size(17.dp)) }
            }
        }

        if (s.demands.isEmpty()) {
            item { Text("Nenhuma demanda cadastrada.", color = V25Text2, fontSize = 10.sp) }
        } else {
            items(s.demands, key = { it.id }) { d ->
                val busy = s.demandSearchBusy || s.demandBusyId == d.id
                Surface(color = V25Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = V25Amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.vehicle, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                Text(d.subject, color = V25Text2, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { vm.searchDemandNow(d.id) }, enabled = !busy, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.Search, "Buscar agora", tint = V25Accent, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { vm.removeDemand(d.id) }, enabled = !busy, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Outlined.DeleteOutline, "Excluir", modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = V25Divider)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when {
                                busy -> V25Accent
                                d.lastError.isNotBlank() -> Color(0xFFFF7777)
                                d.lastCheckedAt > 0 -> V25Mint
                                else -> V25Text2
                            }
                            Icon(
                                when {
                                    busy -> Icons.Outlined.HourglassTop
                                    d.lastError.isNotBlank() -> Icons.Outlined.ErrorOutline
                                    d.lastCheckedAt > 0 -> Icons.Outlined.CheckCircle
                                    else -> Icons.Outlined.Schedule
                                }, null, tint = statusColor, modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                when {
                                    busy -> "Pesquisando agora..."
                                    d.lastError.isNotBlank() -> "Falha em ${demandDateTime(d.lastCheckedAt)}"
                                    d.lastCheckedAt > 0 -> "${demandDateTime(d.lastCheckedAt)} • ${d.lastFoundCount} resultado(s) • ${d.lastNewCount} nova(s)"
                                    else -> "Ainda não pesquisada"
                                },
                                color = statusColor,
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun demandDateTime(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))

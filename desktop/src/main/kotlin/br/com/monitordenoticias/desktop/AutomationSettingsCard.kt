package br.com.monitordenoticias.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AutoNews = Color(0xFF155EEF)
private val AutoDemand = Color(0xFFE56A13)
private val AutoVideo = Color(0xFF6941C6)
private val AutoGreen = Color(0xFF128A4B)
private val AutoMuted = Color(0xFF66788A)
private val AutoLine = Color(0xFFD9E2EC)

@Composable
fun AutomationSettingsCard(c: DesktopController) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var newVideoTime by remember { mutableStateOf("") }
    var videoTimeError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = Color(0xFFEAF2FF)
                ) {
                    Icon(Icons.Default.Schedule, null, tint = AutoNews, modifier = Modifier.padding(11.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Buscas automáticas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Controle Notícias, Demandas e Vídeos de forma independente.",
                        color = AutoMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Controle geral", style = MaterialTheme.typography.labelMedium, color = AutoMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (c.automaticMonitoring) "Ativo" else "Pausado",
                            fontWeight = FontWeight.SemiBold,
                            color = if (c.automaticMonitoring) AutoGreen else AutoMuted
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = c.automaticMonitoring, onCheckedChange = { c.automaticMonitoring = it })
                    }
                }
            }

            HorizontalDivider(color = AutoLine)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Computer, null, tint = AutoMuted, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Iniciar Monitor de Notícias com o Windows", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(checked = c.startWithWindows, onCheckedChange = { c.startWithWindows = it })
            }

            AutoModuleCard(
                title = "Notícias",
                subtitle = "Varredura automática das fontes de notícias selecionadas",
                icon = Icons.Default.Article,
                accent = AutoNews,
                enabled = c.newsAutomaticEnabled,
                masterEnabled = c.automaticMonitoring,
                onEnabledChange = { c.newsAutomaticEnabled = it },
                lastAt = c.newsAutoReport().completedAt,
                nextAt = c.nextNewsAutoAt(nowMs),
                busy = c.newsBusy,
                onRunNow = { c.searchNews() }
            ) {
                Text("Frequência", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(15, 30, 45, 60, 120).forEach { minutes ->
                        FilterChip(
                            selected = c.newsIntervalMinutes == minutes,
                            onClick = { c.newsIntervalMinutes = minutes },
                            enabled = c.newsAutomaticEnabled,
                            label = { Text(if (minutes < 60) "$minutes min" else if (minutes == 60) "1 hora" else "2 horas") }
                        )
                    }
                }
            }

            AutoModuleCard(
                title = "Demandas",
                subtitle = "Pesquisa automática de todas as demandas ativas",
                icon = Icons.Default.Assignment,
                accent = AutoDemand,
                enabled = c.demandAutomaticEnabled,
                masterEnabled = c.automaticMonitoring,
                onEnabledChange = { c.demandAutomaticEnabled = it },
                lastAt = c.demandAutoReport().completedAt,
                nextAt = c.nextDemandAutoAt(nowMs),
                busy = c.demandBusy,
                onRunNow = { c.searchAllDemands() }
            ) {
                Text("Frequência", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(30, 60, 120, 180, 360).forEach { minutes ->
                        FilterChip(
                            selected = c.demandIntervalMinutes == minutes,
                            onClick = { c.demandIntervalMinutes = minutes },
                            enabled = c.demandAutomaticEnabled,
                            label = {
                                Text(
                                    when (minutes) {
                                        30 -> "30 min"
                                        60 -> "1 hora"
                                        120 -> "2 horas"
                                        180 -> "3 horas"
                                        else -> "6 horas"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            AutoModuleCard(
                title = "Vídeos",
                subtitle = "Execução automática nos horários definidos abaixo",
                icon = Icons.Default.PlayCircle,
                accent = AutoVideo,
                enabled = c.videoAutomaticEnabled,
                masterEnabled = c.automaticMonitoring,
                onEnabledChange = { c.videoAutomaticEnabled = it },
                lastAt = c.videoAutoReport().completedAt,
                nextAt = c.nextVideoAutoAt(nowMs),
                busy = c.videoBusy,
                onRunNow = { c.searchVideos() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Horários ativos", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { c.resetVideoAutoTimes() }, enabled = c.videoAutomaticEnabled) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Restaurar padrão")
                    }
                }

                if (c.videoAutoTimes.isEmpty()) {
                    Text("Nenhum horário configurado. A busca automática de vídeos não será executada.", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(c.videoAutoTimes.toList(), key = { it }) { time ->
                            OutlinedButton(
                                onClick = { c.removeVideoAutoTime(time) },
                                enabled = c.videoAutomaticEnabled,
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(time)
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Default.Close, "Remover horário", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newVideoTime,
                        onValueChange = {
                            newVideoTime = it.take(5)
                            videoTimeError = false
                        },
                        label = { Text("Novo horário") },
                        placeholder = { Text("HH:mm") },
                        singleLine = true,
                        isError = videoTimeError,
                        enabled = c.videoAutomaticEnabled,
                        modifier = Modifier.width(150.dp)
                    )
                    Button(
                        onClick = {
                            val ok = c.addVideoAutoTime(newVideoTime)
                            videoTimeError = !ok
                            if (ok) newVideoTime = ""
                        },
                        enabled = c.videoAutomaticEnabled && newVideoTime.isNotBlank(),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Adicionar")
                    }
                    if (videoTimeError) Text("Use o formato HH:mm, por exemplo 14:30.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }

            if (!DesktopProxyManager.isReady(c.context)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF4E8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, null, tint = AutoDemand, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("As automações ficam aguardando até o proxy estar configurado e pronto.", color = AutoDemand, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    masterEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    lastAt: Long,
    nextAt: Long,
    busy: Boolean,
    onRunNow: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFAFCFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(11.dp), color = accent.copy(alpha = .10f), modifier = Modifier.size(42.dp)) {
                    Icon(icon, null, tint = accent, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                !masterEnabled -> Color(0xFFF0F4F8)
                                enabled -> Color(0xFFEAF8EF)
                                else -> Color(0xFFFFF4E8)
                            }
                        ) {
                            Text(
                                when {
                                    !masterEnabled -> "controle geral pausado"
                                    enabled -> "automático ativo"
                                    else -> "automático pausado"
                                },
                                color = when {
                                    !masterEnabled -> AutoMuted
                                    enabled -> AutoGreen
                                    else -> AutoDemand
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(subtitle, color = AutoMuted, style = MaterialTheme.typography.bodySmall)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Automático", style = MaterialTheme.typography.labelSmall, color = AutoMuted)
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                AutoTimeInfo("Última automática", formatAutoDate(lastAt), Icons.Default.History)
                AutoTimeInfo(
                    "Próxima execução",
                    if (!masterEnabled || !enabled) "Pausada" else formatAutoDate(nextAt),
                    Icons.Default.Schedule
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onRunNow, enabled = !busy, shape = RoundedCornerShape(9.dp)) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "Executando..." else "Executar agora")
                }
            }

            HorizontalDivider(color = AutoLine)
            content()
        }
    }
}

@Composable
private fun AutoTimeInfo(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AutoMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AutoMuted)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatAutoDate(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))

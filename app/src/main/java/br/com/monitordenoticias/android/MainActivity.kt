package br.com.monitordenoticias.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

private val AppBg = Color(0xFF0F0E14)
private val SurfaceDark = Color(0xFF19181F)
private val Accent = Color(0xFF6EA8FE)
private val Mint = Color(0xFF61D6B1)
private val TextSecondary = Color(0xFFB9B4C3)

private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF14223C),
    background = AppBg,
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF22212A),
    onBackground = Color(0xFFF5F2FA),
    onSurface = Color(0xFFF5F2FA),
    onSurfaceVariant = TextSecondary
)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        setContent { MaterialTheme(colorScheme = AppColors) { MonitorApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorApp(vm: MonitorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val tabs = listOf(
        Triple("Notícias", Icons.Outlined.Article, 0),
        Triple("Período", Icons.Outlined.CalendarMonth, 1),
        Triple("Demandas", Icons.Outlined.NotificationsActive, 2),
        Triple("Histórico", Icons.Outlined.History, 3),
        Triple("Termos", Icons.Outlined.ManageSearch, 4),
        Triple("Fontes", Icons.Outlined.Public, 5),
        Triple("Config.", Icons.Outlined.Settings, 6)
    )
    Scaffold(
        containerColor = AppBg,
        topBar={
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppBg),
                title={
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Monitor de Notícias", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Monitoramento inteligente", color = TextSecondary, fontSize = 12.sp)
                    }
                },
                actions={ Text("2.1.0", color = TextSecondary, fontSize = 12.sp, modifier=Modifier.padding(end=14.dp)) }
            )
        },
        bottomBar={
            NavigationBar(containerColor = SurfaceDark, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected=state.selectedTab==tab.third,
                        onClick={vm.setTab(tab.third)},
                        icon={Icon(tab.second, contentDescription=tab.first)},
                        label={Text(tab.first, fontSize=9.sp, maxLines=2)},
                        colors=NavigationBarItemDefaults.colors(
                            selectedIconColor=Accent,
                            selectedTextColor=Color.White,
                            indicatorColor=Color(0xFF353244),
                            unselectedIconColor=TextSecondary,
                            unselectedTextColor=TextSecondary
                        )
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(AppBg)) {
            if(state.status.isNotBlank() && state.status != "Pronto") {
                Surface(color=Color(0xFF171720), modifier=Modifier.fillMaxWidth()) {
                    Text(state.status, color=TextSecondary, fontSize=11.sp, modifier=Modifier.padding(horizontal=18.dp,vertical=7.dp))
                }
            }
            when(state.selectedTab) {
                0->NewsScreen(state,vm)
                1->PeriodScreen(state,vm)
                2->DemandScreen(state,vm)
                3->HistoryScreen(state,vm)
                4->TermsScreen(state,vm)
                5->SourcesScreen(state)
                else->SettingsScreen(state,vm)
            }
        }
    }
}

@Composable
fun NewsScreen(s:AppState,vm:MonitorViewModel) {
    val shown = if (s.showOnlyDemands) s.news.filter { it.demand } else s.news
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Últimas notícias", fontWeight=FontWeight.ExtraBold, fontSize=30.sp)
        Text("Monitoramento automático das últimas 24 horas", color=TextSecondary, fontSize=14.sp)
        Spacer(Modifier.height(18.dp))
        Card(
            shape=RoundedCornerShape(28.dp),
            colors=CardDefaults.cardColors(containerColor=Color.Transparent),
            modifier=Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier.background(Brush.linearGradient(listOf(Color(0xFF1D2230),Color(0xFF18171E)))).padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(Accent.copy(alpha=.13f)),
                            contentAlignment=Alignment.Center
                        ) {
                            Icon(Icons.Outlined.ManageSearch, contentDescription=null, tint=Accent, modifier=Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Painel de monitoramento", fontWeight=FontWeight.Bold, fontSize=20.sp)
                            Text("${s.news.size} matéria(s) nas últimas 24h", color=TextSecondary, fontSize=12.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick=vm::search,
                        enabled=!s.busy,
                        modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(18.dp)
                    ) { Text(if(s.busy) "Buscando..." else "Buscar agora", fontWeight=FontWeight.Bold) }
                    s.lastUpdatedAt?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Última atualização: ${dateText(it)}", color=TextSecondary, fontSize=10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Notícias", s.news.size.toString(), Modifier.weight(1f))
            SummaryCard("Demandas", s.news.count { it.demand }.toString(), Modifier.weight(1f))
            SummaryCard("Fontes", s.news.map { it.source }.distinct().size.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!s.showOnlyDemands,onClick={vm.setDemandFilter(false)},label={Text("Todas")})
            FilterChip(selected=s.showOnlyDemands,onClick={vm.setDemandFilter(true)},label={Text("Demandas")})
        }
        Spacer(Modifier.height(8.dp))
        NewsList(shown)
    }
}

@Composable
fun SummaryCard(label:String,value:String,modifier:Modifier=Modifier) {
    Card(modifier, colors=CardDefaults.cardColors(containerColor=SurfaceDark), shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal=12.dp,vertical=10.dp)) {
            Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Accent)
            Text(label,color=TextSecondary,fontSize=10.sp)
        }
    }
}

@Composable
fun NewsList(items:List<News>) {
    val context=LocalContext.current
    if(items.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.size(68.dp).clip(CircleShape).background(SurfaceDark),contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.ManageSearch,contentDescription=null,tint=Accent,modifier=Modifier.size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Nenhuma notícia encontrada",fontWeight=FontWeight.Bold,fontSize=19.sp)
            Spacer(Modifier.height(4.dp))
            Text("Use “Buscar agora” para atualizar o monitor.",color=TextSecondary,fontSize=12.sp)
        }
    }
    else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
        items(items,key={it.link}) { n ->
            Card(Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(n.link))) }) {
                Column(Modifier.padding(14.dp)) {
                    if(n.demand) AssistChip(onClick={},label={Text("DEMANDA")})
                    Text(n.title,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp)); Text("${n.source} • ${dateText(n.date)}",style=MaterialTheme.typography.labelSmall)
                    if(n.matchedTerm.isNotBlank()) Text("Termo: ${n.matchedTerm}",style=MaterialTheme.typography.labelSmall)
                    if(n.matchedDemand.isNotBlank()) Text("Demanda: ${n.matchedDemand}",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold)
                    if(n.snippet.isNotBlank()){Spacer(Modifier.height(5.dp));Text(n.snippet,style=MaterialTheme.typography.bodySmall,maxLines=3)}
                }
            }
        }
    }
}

@Composable
fun PeriodScreen(s:AppState,vm:MonitorViewModel) {
    var from by remember { mutableStateOf(todayMinus(7)) }; var to by remember { mutableStateOf(today()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pesquisa por período",fontWeight=FontWeight.Bold)
        Text("A pesquisa externa depende do conteúdo disponível no Google Notícias. O histórico local permanece armazenado.",style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp)); OutlinedTextField(from,{from=it},label={Text("De")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(to,{to=it},label={Text("Até")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp)); Button(onClick={parseDate(from)?.let{a->parseDate(to)?.let{b->vm.searchPeriod(a,b+86_399_000)}}},enabled=!s.busy){Text("Pesquisar período")}
        Spacer(Modifier.height(10.dp)); NewsList(s.news)
    }
}

@Composable
fun DemandScreen(s:AppState,vm:MonitorViewModel) {
    var vehicle by remember{mutableStateOf("")}; var subject by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Monitoramento de Demandas",fontWeight=FontWeight.Bold)
        Text("Uma notícia vira DEMANDA quando o veículo e o assunto cadastrados correspondem ao resultado.",style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp)); OutlinedTextField(vehicle,{vehicle=it},label={Text("Veículo")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(subject,{subject=it},label={Text("Assunto")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp)); Button(onClick={vm.addDemand(vehicle,subject);vehicle="";subject=""},enabled=vehicle.isNotBlank()&&subject.isNotBlank()){Text("Adicionar demanda")}
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.demands, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.vehicle, fontWeight = FontWeight.Bold)
                            Text(d.subject, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.removeDemand(d.id) }) {
                            Text("Excluir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(s:AppState,vm:MonitorViewModel) {
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("Histórico (${s.history.size})",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));OutlinedButton(onClick=vm::clearHistory,enabled=s.history.isNotEmpty()){Text("Limpar")}}
        Spacer(Modifier.height(8.dp)); OutlinedButton(onClick={exportCsv(context,s.history)},enabled=s.history.isNotEmpty()){Text("Exportar CSV completo")}; Spacer(Modifier.height(8.dp)); NewsList(s.history)
    }
}

@Composable
fun TermsScreen(s:AppState,vm:MonitorViewModel) {
    var term by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Termos monitorados",fontWeight=FontWeight.ExtraBold,fontSize=28.sp)
        Text("Palavras e expressões usadas na busca automática",color=TextSecondary,fontSize=13.sp)
        Spacer(Modifier.height(16.dp))
        Card(colors=CardDefaults.cardColors(containerColor=SurfaceDark),shape=RoundedCornerShape(22.dp)) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(term,{term=it},label={Text("Novo termo")},singleLine=true,modifier=Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick={vm.addTerm(term);term=""},enabled=term.isNotBlank(),shape=RoundedCornerShape(14.dp)){Text("+",fontSize=20.sp)}
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn {
            items(s.terms){t->
                Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Outlined.ManageSearch,contentDescription=null,tint=Accent)
                    Spacer(Modifier.width(8.dp))
                    Text(t,Modifier.weight(1f))
                    TextButton(onClick={vm.removeTerm(t)}){Text("Excluir")}
                }
            }
        }
    }
}

@Composable
fun SourcesScreen(s:AppState) {
    Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Fontes",fontWeight=FontWeight.ExtraBold,fontSize=28.sp)
        Text("Canais utilizados para localizar novas matérias",color=TextSecondary,fontSize=13.sp)
        Spacer(Modifier.height(16.dp))
        SourceCard("Google Notícias","RSS de pesquisa • Brasil • Português",true)
        Spacer(Modifier.height(10.dp))
        SourceCard("Fontes detectadas","${s.history.map{it.source}.filter{it.isNotBlank()}.distinct().size} veículo(s) no histórico",s.history.isNotEmpty())
    }
}

@Composable
fun SourceCard(title:String,subtitle:String,active:Boolean) {
    Card(colors=CardDefaults.cardColors(containerColor=SurfaceDark),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(alpha=.12f)),contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.Public,contentDescription=null,tint=Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title,fontWeight=FontWeight.Bold)
                Text(subtitle,color=TextSecondary,fontSize=11.sp)
            }
            Text(if(active)"ATIVA" else "VAZIA",color=if(active)Mint else TextSecondary,fontWeight=FontWeight.Bold,fontSize=9.sp)
        }
    }
}

@Composable
fun SettingsScreen(s:AppState,vm:MonitorViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configurações",fontWeight=FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Intervalo de monitoramento automático: ${s.intervalMinutes} min")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=10.dp)){listOf(15,30,45,60).forEach{m->FilterChip(selected=s.intervalMinutes==m,onClick={vm.setInterval(m)},label={Text("$m min")})}}
        Text("O intervalo fica salvo mesmo após fechar o aplicativo.",style=MaterialTheme.typography.labelSmall)
        HorizontalDivider(Modifier.padding(vertical=12.dp)); Text("Termos monitorados",fontWeight=FontWeight.Bold)
        var term by remember{mutableStateOf("")}; Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(term,{term=it},label={Text("Novo termo")},singleLine=true,modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));Button(onClick={vm.addTerm(term);term=""},enabled=term.isNotBlank()){Text("+")}}
        LazyColumn{items(s.terms){t->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Text(t,Modifier.weight(1f));TextButton(onClick={vm.removeTerm(t)}){Text("Excluir")}}}}
    }
}

fun today():String=SimpleDateFormat("dd/MM/yyyy",Locale("pt","BR")).format(Date())
fun todayMinus(days:Int):String=SimpleDateFormat("dd/MM/yyyy",Locale("pt","BR")).format(Date(System.currentTimeMillis()-days*86_400_000L))
fun parseDate(s:String):Long?=runCatching{SimpleDateFormat("dd/MM/yyyy",Locale("pt","BR")).apply{isLenient=false}.parse(s)?.time}.getOrNull()
fun dateText(ms:Long)=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR")).format(Date(ms))
fun exportCsv(context:android.content.Context,items:List<News>) {
    fun clean(v:String)=v.replace(";",",").replace("\n"," ")
    val csv=buildString{
        append("Publicação;Captura;Veículo;Título;Termo;Demanda;Link\n")
        items.forEach{append(dateText(it.date)).append(';').append(dateText(it.capturedAt)).append(';').append(clean(it.source)).append(';').append(clean(it.title)).append(';').append(clean(it.matchedTerm)).append(';').append(clean(it.matchedDemand)).append(';').append(it.link).append('\n')}
    }
    val send=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_TEXT,csv)}
    context.startActivity(Intent.createChooser(send,"Exportar CSV"))
}

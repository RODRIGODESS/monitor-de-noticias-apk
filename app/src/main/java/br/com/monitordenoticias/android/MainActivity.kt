package br.com.monitordenoticias.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        setContent { MaterialTheme { MonitorApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorApp(vm: MonitorViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val tabs = listOf("Notícias","Período","Demandas","Histórico","Config.")
    Scaffold(
        topBar={ TopAppBar(title={Text("Monitor de Notícias")}, actions={ Text("v2.0.0",modifier=Modifier.padding(end=16.dp)) })},
        bottomBar={ NavigationBar { tabs.forEachIndexed { i,t -> NavigationBarItem(selected=state.selectedTab==i,onClick={vm.setTab(i)},icon={Spacer(Modifier.size(1.dp))},label={Text(t)}) } } }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if(state.status.isNotBlank()) Text(state.status,Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium)
            when(state.selectedTab) {
                0->NewsScreen(state,vm); 1->PeriodScreen(state,vm); 2->DemandScreen(state,vm); 3->HistoryScreen(state,vm); 4->SettingsScreen(state,vm)
            }
        }
    }
}

@Composable
fun NewsScreen(s:AppState,vm:MonitorViewModel) {
    val shown = if (s.showOnlyDemands) s.news.filter { it.demand } else s.news
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Últimas 24 horas",fontWeight=FontWeight.Bold)
                Text(s.lastUpdatedAt?.let { "Atualizado em ${dateText(it)}" } ?: "Monitoramento ativo",style=MaterialTheme.typography.labelSmall)
            }
            Button(onClick=vm::search,enabled=!s.busy){Text(if(s.busy)"Buscando…" else "Buscar agora")}
        }
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(8.dp)); NewsList(shown)
    }
}

@Composable
fun SummaryCard(label:String,value:String,modifier:Modifier=Modifier) {
    Card(modifier) { Column(Modifier.padding(10.dp)) { Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text(label,style=MaterialTheme.typography.labelSmall) } }
}

@Composable
fun NewsList(items:List<News>) {
    val context=LocalContext.current
    if(items.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Nenhuma notícia encontrada.")}
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

from pathlib import Path

path = Path("app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt")
text = path.read_text(encoding="utf-8")
start_marker = "@Composable\nprivate fun V28Demands"
end_marker = "\n@Composable\nprivate fun V28Period"
start = text.index(start_marker)
end = text.index(end_marker, start)

replacement = r'''@Composable
private fun V28Demands(s: AppState, vm: MonitorViewModel) {
    var vehicle by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var resultsDemand by remember { mutableStateOf<Demand?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Surface(color = V28Surface, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Nova demanda", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(subject, { subject = it }, label = { Text("Assunto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(9.dp))
                    Button(onClick = { vm.addDemand(vehicle, subject); vehicle = ""; subject = "" }, enabled = vehicle.isNotBlank() && subject.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AddAlert, null); Spacer(Modifier.width(7.dp)); Text("Adicionar demanda")
                    }
                }
            }
        }
        item {
            Button(onClick = vm::searchAllDemandsNow, enabled = !s.demandSearchBusy && s.demands.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ManageSearch, null); Spacer(Modifier.width(7.dp)); Text(if (s.demandSearchBusy) "Pesquisando demandas..." else "Buscar demandas agora")
            }
        }
        item { Text("${s.demands.size} demanda(s)", color = V28Text2, fontSize = 11.5.sp) }
        if (s.demands.isEmpty()) item { V28Empty("Nenhuma demanda", "Adicione veículo e assunto para criar um alerta.") }
        else items(s.demands, key = { it.id }) { d ->
            val demandKey = "${d.vehicle} • ${d.subject}"
            val resultItems = s.history.filter { it.matchedDemand == demandKey }.sortedByDescending { it.date }
            Surface(color = V28Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, V28Divider), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = V28Amber, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.vehicle, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(d.subject, color = V28Text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { vm.searchDemandNow(d.id) }, enabled = s.demandBusyId == null && !s.demandSearchBusy) { Icon(Icons.Outlined.Search, "Buscar", tint = V28Accent) }
                        IconButton(onClick = { vm.removeDemand(d.id) }) { Icon(Icons.Outlined.DeleteOutline, "Excluir", tint = V28Red) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = V28Divider)
                    Text(if (d.lastCheckedAt > 0) "Última busca: ${v28DateTime(d.lastCheckedAt)}" else "Ainda não pesquisada", color = V28Text2, fontSize = 10.8.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${d.lastFoundCount} resultado(s) • ${d.lastNewCount} novo(s)", color = if (d.lastNewCount > 0) V28Mint else V28Text2, fontSize = 10.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (d.lastFoundCount > 0) {
                            TextButton(
                                onClick = { resultsDemand = d },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (d.lastFoundCount == 1) "Ver resultado" else "Ver resultados", fontSize = 11.sp)
                            }
                        }
                    }
                    if (d.lastFoundCount > 0 && resultItems.isEmpty()) {
                        Text("Toque na lupa para atualizar e vincular os resultados desta demanda.", color = V28Amber, fontSize = 9.8.sp)
                    }
                    if (d.lastError.isNotBlank()) Text(d.lastError, color = V28Red, fontSize = 10.5.sp)
                }
            }
        }
    }

    resultsDemand?.let { demand ->
        val key = "${demand.vehicle} • ${demand.subject}"
        val matched = s.history.filter { it.matchedDemand == key }.sortedByDescending { it.date }
        ModalBottomSheet(onDismissRequest = { resultsDemand = null }, containerColor = V28Surface2) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(V28Amber.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = V28Amber)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Resultados da demanda", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${demand.vehicle} • ${demand.subject}", color = V28Text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (matched.isEmpty()) {
                    V28Empty("Resultado ainda não vinculado", "Toque na lupa da demanda e abra novamente esta lista.")
                } else {
                    Text("${matched.size} matéria(s) armazenada(s) • toque para abrir", color = V28Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(matched, key = { it.link }) { news -> V28NewsCard(news) }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
            }
        }
    }
}
'''

text = text[:start] + replacement + text[end:]
text = text.replace('"2.8.0"', '"2.8.1"')
text = text.replace('Monitor de Notícias 2.8.0', 'Monitor de Notícias 2.8.1')
path.write_text(text, encoding="utf-8")
print("MainActivityV28.kt patched for v2.8.1")

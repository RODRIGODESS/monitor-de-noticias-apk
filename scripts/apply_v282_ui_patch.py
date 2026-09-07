from pathlib import Path

path = Path("app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt")
text = path.read_text(encoding="utf-8")

start_marker = "@Composable\nprivate fun V28VideoCard"
end_marker = "\n@Composable\nprivate fun V28Sources"
start = text.index(start_marker)
end = text.index(end_marker, start)

replacement = r'''@Composable
private fun V28VideoCard(item: VideoItem) {
    val context = LocalContext.current
    val openVideo = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
        Unit
    }
    Surface(
        color = V28Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, V28Divider.copy(alpha = .7f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = openVideo)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(V28Purple.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = V28Purple, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.sourceName, color = V28Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(v28DateTime(item.publishedAt), color = V28Text2, fontSize = 10.5.sp)
                }
                Surface(color = V28Mint.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Verified, null, tint = V28Mint, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Link direto", color = V28Mint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(item.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.summary.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(item.summary, color = V28Text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (item.relevant) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (item.demand) V28Badge("DEMANDA", V28Amber)
                    if (item.matchedTerm.isNotBlank()) V28Badge(item.matchedTerm, V28Accent)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = openVideo, modifier = Modifier.fillMaxWidth().height(42.dp)) {
                Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Abrir vídeo")
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(15.dp))
            }
        }
    }
}
'''

text = text[:start] + replacement + text[end:]
text = text.replace('Text("2.8.1",', 'Text("2.8.2",')
text = text.replace('Monitor de Notícias 2.8.1', 'Monitor de Notícias 2.8.2')
path.write_text(text, encoding="utf-8")
print("MainActivityV28.kt patched for v2.8.2")

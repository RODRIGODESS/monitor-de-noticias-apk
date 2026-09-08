from pathlib import Path

ui = Path('app/src/main/java/br/com/monitordenoticias/android/V30Screens.kt')
s = ui.read_text()

old_base = '''    val base = if (mode == 0) SourceCatalog.national else SourceCatalog.byState
    val visible = base.filter { src ->
        val regionOk = mode == 0 || region == SourceCatalog.ALL_REGION || src.region == region
        val stateOk = mode == 0 || stateCode.isBlank() || src.state == stateCode
'''
new_base = '''    val base = when (mode) {
        0 -> SourceCatalog.national
        1 -> SourceCatalog.byState
        else -> SourceCatalog.specialized
    }
    val visible = base.filter { src ->
        val regionOk = mode != 1 || region == SourceCatalog.ALL_REGION || src.region == region
        val stateOk = mode != 1 || stateCode.isBlank() || src.state == stateCode
'''
if old_base not in s:
    raise SystemExit('active news source base block not found')
s = s.replace(old_base, new_base, 1)

old_tabs = '''        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                V30Segment("Nacionais", Icons.Outlined.Public, mode == 0, Modifier.weight(1f)) { mode = 0; stateCode = ""; region = SourceCatalog.ALL_REGION }
                V30Segment("Estados", Icons.Outlined.Map, mode == 1, Modifier.weight(1f)) { mode = 1 }
            }
        }
'''
new_tabs = '''        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                V30Segment("Nacionais", Icons.Outlined.Public, mode == 0, Modifier.width(130.dp)) {
                    mode = 0; stateCode = ""; region = SourceCatalog.ALL_REGION
                }
                V30Segment("Mídia especializada", Icons.Outlined.Article, mode == 2, Modifier.width(190.dp)) {
                    mode = 2; stateCode = ""; region = SourceCatalog.ALL_REGION
                }
                V30Segment("Estados", Icons.Outlined.Map, mode == 1, Modifier.width(120.dp)) {
                    mode = 1
                }
            }
        }
'''
if old_tabs not in s:
    raise SystemExit('active news source tabs block not found')
s = s.replace(old_tabs, new_tabs, 1)

old_source_card = '''        items(visible, key = { "news-${it.id}" }) { source ->
            val selected = source.id in s.selectedSourceIds
            V30SourceCard(source.name, if (source.state.isBlank()) source.group else "${source.state} • ${source.region}", selected) {
                vm.setSourceSelected(source.id, !selected)
            }
        }
'''
new_source_card = '''        items(visible, key = { "news-${it.id}" }) { source ->
            val selected = source.id in s.selectedSourceIds
            val subtitle = if (source.region == SourceCatalog.NATIONAL_REGION) source.group else "${source.state} • ${source.region}"
            V30SourceCard(source.name, subtitle, selected) {
                vm.setSourceSelected(source.id, !selected)
            }
        }
'''
if old_source_card not in s:
    raise SystemExit('active news source card block not found')
s = s.replace(old_source_card, new_source_card, 1)

old_video_actions = '''            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = open, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir vídeo")
                }
                OutlinedButton(
                    onClick = { v30ShareWhatsApp(context, item.title, item.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp", maxLines = 1, fontSize = 10.8.sp)
                }
            }
'''
new_video_actions = '''            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(
                    onClick = open,
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Abrir", maxLines = 1, fontSize = 10.5.sp)
                }
                OutlinedButton(
                    onClick = { v30CopyLink(context, "Link do vídeo", item.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Accent)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copiar link", maxLines = 1, fontSize = 10.2.sp)
                }
                OutlinedButton(
                    onClick = { v30ShareWhatsApp(context, item.title, item.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("WhatsApp", maxLines = 1, fontSize = 10.2.sp)
                }
            }
'''
if old_video_actions not in s:
    raise SystemExit('active video action row not found')
s = s.replace(old_video_actions, new_video_actions, 1)

old_news_actions = '''            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir notícia", maxLines = 1, fontSize = 10.8.sp)
                }
                OutlinedButton(
                    onClick = { v30ShareWhatsApp(context, n.title, n.link) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }
            }
'''
new_news_actions = '''            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Abrir", maxLines = 1, fontSize = 10.5.sp)
                }
                OutlinedButton(
                    onClick = { v30CopyLink(context, "Link da notícia", n.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Accent)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copiar link", maxLines = 1, fontSize = 10.2.sp)
                }
                OutlinedButton(
                    onClick = { v30ShareWhatsApp(context, n.title, n.link) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("WhatsApp", maxLines = 1, fontSize = 10.2.sp)
                }
            }
'''
if old_news_actions not in s:
    raise SystemExit('active news action row not found')
s = s.replace(old_news_actions, new_news_actions, 1)

helper_anchor = '''private fun v30ShareWhatsApp(context: Context, title: String, link: String) {
'''
helper = '''private fun v30CopyLink(context: Context, label: String, link: String) {
    val value = link.trim()
    if (value.isBlank()) {
        android.widget.Toast.makeText(context, "Link indisponível", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    context.getSystemService(android.content.ClipboardManager::class.java)
        ?.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
    android.widget.Toast.makeText(context, "Link copiado", android.widget.Toast.LENGTH_SHORT).show()
}

private fun v30ShareWhatsApp(context: Context, title: String, link: String) {
'''
if helper_anchor not in s:
    raise SystemExit('share helper anchor not found')
s = s.replace(helper_anchor, helper, 1)

ui.write_text(s)

build = Path('app/build.gradle.kts')
b = build.read_text()
if 'versionCode = 404' not in b or 'versionName = "4.0.4"' not in b:
    raise SystemExit('expected v4.0.4 version not found')
b = b.replace('versionCode = 404', 'versionCode = 405', 1)
b = b.replace('versionName = "4.0.4"', 'versionName = "4.0.5"', 1)
build.write_text(b)

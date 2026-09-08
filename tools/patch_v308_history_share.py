from pathlib import Path

path = Path('app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt')
text = path.read_text()

old = '''            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(8.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (n.demand) V28Badge("DEMANDA", V28Amber)
                    if (n.matchedTerm.isNotBlank()) V28Badge(n.matchedTerm, V28Accent)
                }
            }
        }
    }
}

@Composable
private fun V28Metric'''
new = '''            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(8.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (n.demand) V28Badge("DEMANDA", V28Amber)
                    if (n.matchedTerm.isNotBlank()) V28Badge(n.matchedTerm, V28Accent)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link))) } },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir notícia")
                }
                OutlinedButton(
                    onClick = { v28ShareWhatsApp(context, n.title, n.link) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V28Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }
            }
        }
    }
}

@Composable
private fun V28Metric'''
assert old in text, 'V28NewsCard anchor not found'
text = text.replace(old, new, 1)

anchor = '''private fun v28DateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
'''
helper = '''private fun v28ShareWhatsApp(context: android.content.Context, title: String, link: String) {
    val message = listOf(title.trim(), link.trim()).filter { it.isNotBlank() }.joinToString("\\n")
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

private fun v28DateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))
'''
assert anchor in text and 'private fun v28ShareWhatsApp' not in text, 'helper anchor failed'
text = text.replace(anchor, helper, 1)
path.write_text(text)
print('PATCH_V308_HISTORY_SHARE_OK')

from pathlib import Path

repo = Path('.')
vr = repo / 'app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt'
ui = repo / 'app/src/main/java/br/com/monitordenoticias/android/V30Screens.kt'
build = repo / 'app/build.gradle.kts'

# VideoRepository: count only genuinely unstable scan sources and expand deep metadata scan for core national newscasts.
text = vr.read_text()
old = '''                fun sourceError() {
                    sourceRequestFailures++
                    markUnstable(source)
                }

                val sourceScanCandidates = if (isSourceScanMode(source)) {
                    collectRecentBySource(source, capturedAt, effectiveFrom, effectiveTo) { sourceError() }
                } else {
                    emptyList()
                }
'''
new = '''                fun sourceError() {
                    sourceRequestFailures++
                }

                val sourceScanCandidates = if (isSourceScanMode(source)) {
                    collectRecentBySource(source, capturedAt, effectiveFrom, effectiveTo) { sourceError() }
                } else {
                    emptyList()
                }
                // Uma tentativa auxiliar pode falhar e outro caminho da mesma fonte funcionar.
                // Só classificamos uma fonte de varredura como instável quando ela realmente
                // não conseguiu entregar candidatos e houve falha de rede/HTTP.
                if (isSourceScanMode(source) && sourceScanCandidates.isEmpty() && sourceRequestFailures > 0) {
                    markUnstable(source)
                }
'''
assert old in text, 'sourceError block not found'
text = text.replace(old, new, 1)

old = '''                    if (!scanMode && sourceRequestFailures >= MAX_REQUEST_FAILURES_PER_SOURCE) {
                        completed++
'''
new = '''                    if (!scanMode && sourceRequestFailures >= MAX_REQUEST_FAILURES_PER_SOURCE) {
                        markUnstable(source)
                        completed++
'''
assert old in text, 'circuit breaker block not found'
text = text.replace(old, new, 1)

old = '''    private fun resolveLimitFor(source: VideoSource): Int = when {
        source.youtubeHandle.isNotBlank() -> MAX_YOUTUBE_ITEMS_PER_SCAN
        source.id == "video-globoplay-jornalismo" -> MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN
        source.id in VideoSourceCatalog.globoplayRegionalSweepIds -> MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN
        isGloboplaySource(source) -> MAX_GLOBOPLAY_ITEMS_PER_SCAN
        else -> MAX_RESOLVED_PER_QUERY
    }

    private fun deepFallbackLimitFor(source: VideoSource): Int = when {
        source.id == "video-globoplay-jornalismo" -> MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL
        source.id in VideoSourceCatalog.globoplayRegionalSweepIds -> MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL
        isGloboplaySource(source) -> MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE
        else -> 0
    }
'''
new = '''    private fun resolveLimitFor(source: VideoSource): Int = when {
        source.youtubeHandle.isNotBlank() -> MAX_YOUTUBE_ITEMS_PER_SCAN
        source.id in CORE_NATIONAL_GLOBOPLAY_IDS -> MAX_GLOBOPLAY_NATIONAL_ITEMS_PER_SCAN
        source.id == "video-globoplay-jornalismo" -> MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN
        source.id in VideoSourceCatalog.globoplayRegionalSweepIds -> MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN
        isGloboplaySource(source) -> MAX_GLOBOPLAY_ITEMS_PER_SCAN
        else -> MAX_RESOLVED_PER_QUERY
    }

    private fun deepFallbackLimitFor(source: VideoSource): Int = when {
        // Nos cinco telejornais nacionais, uma menção importante pode existir apenas
        // na descrição/tags da página individual. O orçamento cobre todos os trechos
        // usuais de uma edição recente sem transformar todas as regionais em varredura profunda.
        source.id in CORE_NATIONAL_GLOBOPLAY_IDS -> MAX_GLOBOPLAY_DEEP_FALLBACK_NATIONAL
        source.id == "video-globoplay-jornalismo" -> MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL
        source.id in VideoSourceCatalog.globoplayRegionalSweepIds -> MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL
        isGloboplaySource(source) -> MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE
        else -> 0
    }
'''
assert old in text, 'limit functions not found'
text = text.replace(old, new, 1)

old = '''        private const val MAX_RESOLVED_PER_QUERY = 8
        private const val MAX_GLOBOPLAY_ITEMS_PER_SCAN = 24
        private const val MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN = 32
        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE = 6
        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL = 10
'''
new = '''        private const val MAX_RESOLVED_PER_QUERY = 8
        private const val MAX_GLOBOPLAY_ITEMS_PER_SCAN = 40
        private const val MAX_GLOBOPLAY_GENERAL_ITEMS_PER_SCAN = 48
        private const val MAX_GLOBOPLAY_NATIONAL_ITEMS_PER_SCAN = 72
        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE = 12
        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_GENERAL = 14
        private const val MAX_GLOBOPLAY_DEEP_FALLBACK_NATIONAL = 72
'''
assert old in text, 'constants block not found'
text = text.replace(old, new, 1)

anchor = '''        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
'''
insert = '''        private val CORE_NATIONAL_GLOBOPLAY_IDS = setOf(
            "globoplay-bom-dia-brasil",
            "globoplay-hora-1",
            "globoplay-jornal-hoje",
            "globoplay-jornal-nacional",
            "globoplay-jornal-da-globo"
        )
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
'''
assert anchor in text, 'STOP_WORDS anchor not found'
text = text.replace(anchor, insert, 1)
vr.write_text(text)

# V30Screens: explicit WhatsApp sharing for both news and video cards, with generic chooser fallback.
text = ui.read_text()
old = 'import android.content.Intent\n'
new = 'import android.content.Context\nimport android.content.Intent\n'
assert old in text and 'import android.content.Context' not in text, 'Context import guard failed'
text = text.replace(old, new, 1)

old = '''            Spacer(Modifier.height(9.dp))
            OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Abrir vídeo")
            }
'''
new = '''            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = open, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.SmartDisplay, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir vídeo")
                }
                OutlinedButton(
                    onClick = { v30ShareWhatsApp(context, item.title, item.link) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }
            }
'''
assert old in text, 'video button block not found'
text = text.replace(old, new, 1)

old = '''            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (n.demand) V30Badge("DEMANDA", V30Amber)
                    if (n.matchedTerm.isNotBlank()) V30Badge(n.matchedTerm, V30Accent)
                }
            }
        }
    }
}

@Composable
private fun V30Metric'''
new = '''            if (n.demand || n.matchedTerm.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (n.demand) V30Badge("DEMANDA", V30Amber)
                    if (n.matchedTerm.isNotBlank()) V30Badge(n.matchedTerm, V30Accent)
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
                    onClick = { v30ShareWhatsApp(context, n.title, n.link) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V30Mint)
                ) {
                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }
            }
        }
    }
}

private fun v30ShareWhatsApp(context: Context, title: String, link: String) {
    val message = listOf(title.trim(), link.trim()).filter { it.isNotBlank() }.joinToString("\\n")
    if (message.isBlank()) return

    fun shareIntent(packageName: String? = null) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        packageName?.let(::setPackage)
    }

    // Prioriza WhatsApp comum, depois WhatsApp Business. Se nenhum estiver instalado,
    // abre o seletor padrão do Android sem perder o título + link.
    if (runCatching { context.startActivity(shareIntent("com.whatsapp")) }.isSuccess) return
    if (runCatching { context.startActivity(shareIntent("com.whatsapp.w4b")) }.isSuccess) return
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent(), "Compartilhar link"))
    }
}

@Composable
private fun V30Metric'''
assert old in text, 'news card insertion anchor not found'
text = text.replace(old, new, 1)
ui.write_text(text)

# Version bump.
text = build.read_text()
assert 'versionCode = 307' in text and 'versionName = "3.0.7"' in text, 'version guard failed'
text = text.replace('versionCode = 307', 'versionCode = 308', 1)
text = text.replace('versionName = "3.0.7"', 'versionName = "3.0.8"', 1)
build.write_text(text)

print('PATCH_V308_OK')

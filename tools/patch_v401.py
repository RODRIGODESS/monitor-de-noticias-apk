from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


root = Path(__file__).resolve().parents[1]

# Preserve captured_at as the first-seen timestamp. Duplicate discoveries must not
# become "new" again on every automatic/manual scan.
news_db = root / "app/src/main/java/br/com/monitordenoticias/android/NewsDb.kt"
replace_once(
    news_db,
    '                        if (n.matchedDemand.isNotBlank()) put("matched_demand", n.matchedDemand)\n                        put("captured_at", n.capturedAt)\n',
    '                        if (n.matchedDemand.isNotBlank()) put("matched_demand", n.matchedDemand)\n',
)

video_db = root / "app/src/main/java/br/com/monitordenoticias/android/VideoDb.kt"
replace_once(
    video_db,
    '                        if (item.matchedDemand.isNotBlank()) put("matched_demand", item.matchedDemand)\n                        put("captured_at", item.capturedAt)\n',
    '                        if (item.matchedDemand.isNotBlank()) put("matched_demand", item.matchedDemand)\n',
)

# v4.0.1 version.
gradle = root / "app/build.gradle.kts"
replace_once(gradle, '        versionCode = 400\n        versionName = "4.0"\n', '        versionCode = 401\n        versionName = "4.0.1"\n')

# Current V30 UI: latest auto/manual run becomes the authoritative NEW window.
v30 = root / "app/src/main/java/br/com/monitordenoticias/android/V30Screens.kt"
replace_once(
    v30,
    '    val demandHits = news.demands.count { it.lastFoundCount > 0 }\n\n    LazyColumn(',
    '''    val demandHits = news.demands.count { it.lastFoundCount > 0 }\n    val context = LocalContext.current\n    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)\n    val autoNewsStart = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)\n    val autoNewsEnd = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)\n    val manualNewsStart = news.searchProgress.startedAt\n    val manualNewsEnd = news.searchProgress.finishedAt.takeIf { it > 0L } ?: if (news.searchProgress.active) now else 0L\n    val useManualNewsWindow = manualNewsStart > autoNewsStart\n    val newNewsStart = if (useManualNewsWindow) manualNewsStart else autoNewsStart\n    val newNewsEnd = if (useManualNewsWindow) manualNewsEnd else autoNewsEnd\n    val visibleNewNews = news.news.count { v401InRun(it.capturedAt, newNewsStart, newNewsEnd) }\n    val orderedNews = news.news.sortedWith(\n        compareByDescending<News> { v401InRun(it.capturedAt, newNewsStart, newNewsEnd) }\n            .thenByDescending { it.date }\n    )\n\n    LazyColumn(''',
)
replace_once(
    v30,
    '''                Text("Últimas notícias", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))\n                if (news.busy) Text("atualizando...", color = V30Mint, fontSize = 10.5.sp)\n''',
    '''                Text("Últimas notícias", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))\n                if (visibleNewNews > 0) {\n                    V30Badge(if (visibleNewNews == 1) "1 NOVA" else "$visibleNewNews NOVAS", V30Mint)\n                    Spacer(Modifier.width(6.dp))\n                }\n                if (news.busy) Text("atualizando...", color = V30Mint, fontSize = 10.5.sp)\n''',
)
replace_once(
    v30,
    '            items(news.news.take(40), key = { it.link }) { V30NewsCard(it) }\n',
    '            items(orderedNews.take(40), key = { it.link }) { V30NewsCard(it, newNewsStart, newNewsEnd) }\n',
)
replace_once(
    v30,
    '''    val validPeriod = from != null && to != null && from < to\n    val shown = when (s.filter) {\n        VideoFilter.ALL -> s.items\n        VideoFilter.RELEVANT -> s.items.filter { it.relevant }\n        VideoFilter.DEMANDS -> s.items.filter { it.demand }\n    }\n\n    LazyColumn(''',
    '''    val validPeriod = from != null && to != null && from < to\n    val context = LocalContext.current\n    val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)\n    val autoVideoStart = prefs.getLong(VideoAutoRunLog.KEY_ATTEMPT_AT, 0L)\n    val autoVideoEnd = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)\n    val manualVideoStart = s.searchProgress.startedAt\n    val manualVideoEnd = s.searchProgress.finishedAt.takeIf { it > 0L } ?: if (s.searchProgress.active) System.currentTimeMillis() else 0L\n    val useManualVideoWindow = manualVideoStart > autoVideoStart\n    val newVideoStart = if (useManualVideoWindow) manualVideoStart else autoVideoStart\n    val newVideoEnd = if (useManualVideoWindow) manualVideoEnd else autoVideoEnd\n    val shown = when (s.filter) {\n        VideoFilter.ALL -> s.items\n        VideoFilter.RELEVANT -> s.items.filter { it.relevant }\n        VideoFilter.DEMANDS -> s.items.filter { it.demand }\n    }\n    val visibleNewVideos = shown.count { v401InRun(it.capturedAt, newVideoStart, newVideoEnd) }\n    val orderedShown = shown.sortedWith(\n        compareByDescending<VideoItem> { v401InRun(it.capturedAt, newVideoStart, newVideoEnd) }\n            .thenByDescending { it.publishedAt }\n    )\n\n    LazyColumn(''',
)
replace_once(
    v30,
    '''                Text("Vídeos encontrados", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))\n                Text("${shown.size}", color = if (s.busy) V30Mint else V30Text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)\n''',
    '''                Text("Vídeos encontrados", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))\n                if (visibleNewVideos > 0) {\n                    V30Badge(if (visibleNewVideos == 1) "1 NOVO" else "$visibleNewVideos NOVOS", V30Mint)\n                    Spacer(Modifier.width(6.dp))\n                }\n                Text("${shown.size}", color = if (s.busy) V30Mint else V30Text2, fontSize = 12.sp, fontWeight = FontWeight.Bold)\n''',
)
replace_once(
    v30,
    '            items(shown, key = { it.link }) { V30VideoCard(it) }\n',
    '            items(orderedShown, key = { it.link }) { V30VideoCard(it, newVideoStart, newVideoEnd) }\n',
)
replace_once(
    v30,
    'private fun V30VideoCard(item: VideoItem) {\n',
    'private fun V30VideoCard(item: VideoItem, newStart: Long, newEnd: Long) {\n',
)
replace_once(
    v30,
    '''    val context = LocalContext.current\n    val open = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }; Unit }\n    Surface(color = V30Surface,''',
    '''    val context = LocalContext.current\n    val isNew = v401InRun(item.capturedAt, newStart, newEnd)\n    val open = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }; Unit }\n    Surface(color = V30Surface,''',
)
replace_once(
    v30,
    '''                Column(Modifier.weight(1f)) {\n                    Text(item.sourceName, color = V30Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)\n                    Text(v30DateTime(item.publishedAt), color = V30Text2, fontSize = 10.5.sp)\n                }\n                Text("Link direto", color = V30Mint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)\n''',
    '''                Column(Modifier.weight(1f)) {\n                    Text(item.sourceName, color = V30Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)\n                    Text(v30DateTime(item.publishedAt), color = V30Text2, fontSize = 10.5.sp)\n                }\n                if (isNew) {\n                    V30Badge("NOVO", V30Mint)\n                    Spacer(Modifier.width(6.dp))\n                }\n                Text("Link direto", color = V30Mint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)\n''',
)
replace_once(
    v30,
    'private fun V30NewsCard(n: News) {\n',
    'private fun V30NewsCard(n: News, newStart: Long, newEnd: Long) {\n',
)
replace_once(
    v30,
    '''private fun V30NewsCard(n: News, newStart: Long, newEnd: Long) {\n    val context = LocalContext.current\n    Surface(color = V30Surface,''',
    '''private fun V30NewsCard(n: News, newStart: Long, newEnd: Long) {\n    val context = LocalContext.current\n    val isNew = v401InRun(n.capturedAt, newStart, newEnd)\n    Surface(color = V30Surface,''',
)
replace_once(
    v30,
    '''            Row(verticalAlignment = Alignment.CenterVertically) {\n                Text(n.source, color = V30Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)\n                Text(v30DateTime(n.date), color = V30Text2, fontSize = 10.5.sp)\n            }\n''',
    '''            Row(verticalAlignment = Alignment.CenterVertically) {\n                Text(n.source, color = V30Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)\n                if (isNew) {\n                    V30Badge("NOVO", V30Mint)\n                    Spacer(Modifier.width(6.dp))\n                }\n                Text(v30DateTime(n.date), color = V30Text2, fontSize = 10.5.sp)\n            }\n''',
)
# Allow the same card to remain reusable if future call sites are added without a window.
# Current call sites are the Home and Videos lists above.
replace_once(
    v30,
    '''private fun v30DateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))\n''',
    '''private fun v401InRun(capturedAt: Long, startedAt: Long, completedAt: Long): Boolean {\n    if (capturedAt <= 0L || startedAt <= 0L) return false\n    val safeEnd = completedAt.takeIf { it >= startedAt } ?: return false\n    return capturedAt in startedAt..(safeEnd + 5_000L)\n}\n\nprivate fun v30DateTime(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(ms))\n''',
)

# Settings must report the same automatic-run NEW count that the user can actually
# see in the current news/video scope, not a raw insertion total from a different list.
main = root / "app/src/main/java/br/com/monitordenoticias/android/MainActivityV28.kt"
replace_once(
    main,
    '''    val newsAttempt = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)\n    val newsCompleted = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)\n    val demandAttempt = prefs.getLong(AutoRunLog.KEY_DEMAND_ATTEMPT_AT, 0L)\n''',
    '''    val newsAttempt = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)\n    val newsCompleted = prefs.getLong(AutoRunLog.KEY_NEWS_COMPLETED_AT, 0L)\n    val newsNewVisible = s.news.count { v401InRun(it.capturedAt, newsAttempt, newsCompleted) }\n    val demandAttempt = prefs.getLong(AutoRunLog.KEY_DEMAND_ATTEMPT_AT, 0L)\n''',
)
replace_once(
    main,
    '''    val videoAttempt = prefs.getLong(VideoAutoRunLog.KEY_ATTEMPT_AT, 0L)\n    val videoCompleted = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)\n    val videoFound = prefs.getInt(VideoAutoRunLog.KEY_FOUND, 0)\n    val videoNew = prefs.getInt(VideoAutoRunLog.KEY_NEW, 0)\n''',
    '''    val videoAttempt = prefs.getLong(VideoAutoRunLog.KEY_ATTEMPT_AT, 0L)\n    val videoCompleted = prefs.getLong(VideoAutoRunLog.KEY_COMPLETED_AT, 0L)\n    val videoFound = prefs.getInt(VideoAutoRunLog.KEY_FOUND, 0)\n    val videoNew = videos.items.count { v401InRun(it.capturedAt, videoAttempt, videoCompleted) }\n''',
)
replace_once(
    main,
    '        item { V28ReportCard("Notícias automáticas", Icons.Outlined.Article, V28Accent, newsAttempt, newsCompleted, "${prefs.getInt(AutoRunLog.KEY_NEWS_FOUND, 0)} resultado(s) • ${prefs.getInt(AutoRunLog.KEY_NEWS_NEW, 0)} nova(s)", prefs.getString(AutoRunLog.KEY_NEWS_ERROR_TEXT, "").orEmpty()) }\n',
    '        item { V28ReportCard("Notícias automáticas", Icons.Outlined.Article, V28Accent, newsAttempt, newsCompleted, "${prefs.getInt(AutoRunLog.KEY_NEWS_FOUND, 0)} resultado(s) • $newsNewVisible nova(s)", prefs.getString(AutoRunLog.KEY_NEWS_ERROR_TEXT, "").orEmpty()) }\n',
)

print("v4.0.1 patch applied")

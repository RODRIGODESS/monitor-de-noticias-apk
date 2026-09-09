package br.com.monitordenoticias.desktop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.monitordenoticias.android.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

class DesktopController(
    val context: Context = Context(),
    private val notify: (String, String) -> Unit = { _, _ -> }
) : AutoCloseable {
    private val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val newsDb = NewsDb(context)
    val videoDb = VideoDb(context)
    private val newsRepository = NewsRepository(newsDb)
    private val videoRepository = VideoRepository(context, videoDb)

    // Estado observável pelo Compose Desktop. Antes estes campos eram apenas @Volatile:
    // a busca mudava os valores em background, mas a tela podia continuar congelada até
    // outra interação (como trocar de aba) provocar recomposição.
    var news: List<News> by mutableStateOf(emptyList())
        private set
    var videos: List<VideoItem> by mutableStateOf(emptyList())
        private set
    var terms: List<String> by mutableStateOf(emptyList())
        private set
    var videoTerms: List<String> by mutableStateOf(emptyList())
        private set
    var demands: List<Demand> by mutableStateOf(emptyList())
        private set
    var newsProgress by mutableStateOf(LiveSearchProgress())
        private set
    var demandProgress by mutableStateOf(LiveSearchProgress())
        private set
    var videoProgress by mutableStateOf(LiveSearchProgress())
        private set
    var newsBusy by mutableStateOf(false)
        private set
    var demandBusy by mutableStateOf(false)
        private set
    var videoBusy by mutableStateOf(false)
        private set
    var status by mutableStateOf("Pronto")
        private set
    var videoStatus by mutableStateOf("Pronto")
        private set
    var unstableVideoSources: List<VideoSourceIssue> by mutableStateOf(emptyList())
        private set
    var totalStoredVideos by mutableStateOf(0)
        private set
    var capturedTodayVideos by mutableStateOf(0)
        private set

    // Coleções usadas diretamente pelas telas precisam ser estado Compose.
    // Assim limpar histórico, buscas e mutações aparecem sem trocar de aba.
    var newsHistory: List<News> by mutableStateOf(emptyList())
        private set
    var videoHistory: List<VideoItem> by mutableStateOf(emptyList())
        private set

    // NOVO agora significa inserido na execução corrente, não apenas capturado há <24h.
    var newsNewLinks: Set<String> by mutableStateOf(emptySet())
        private set
    var videoNewLinks: Set<String> by mutableStateOf(emptySet())
        private set

    var uiRevision by mutableIntStateOf(0)
        private set

    private fun touchUi() { uiRevision++ }

    private val newsAllSourcesState = mutableStateOf(prefs.getBoolean("desktop_news_all_sources", true))
    var newsAllSources: Boolean
        get() = newsAllSourcesState.value
        set(v) {
            newsAllSourcesState.value = v
            prefs.edit().putBoolean("desktop_news_all_sources", v).apply()
        }

    private val selectedNewsSourceIdsState = mutableStateOf(prefs.getStringSet("desktop_news_source_ids", emptySet()).orEmpty())
    var selectedNewsSourceIds: Set<String>
        get() = selectedNewsSourceIdsState.value
        set(v) {
            selectedNewsSourceIdsState.value = v
            prefs.edit().putStringSet("desktop_news_source_ids", v).apply()
        }

    private val selectedVideoSourceIdsState = mutableStateOf(prefs.getStringSet("desktop_video_source_ids", VideoSourceCatalog.defaultIds).orEmpty())
    var selectedVideoSourceIds: Set<String>
        get() = selectedVideoSourceIdsState.value
        set(v) {
            selectedVideoSourceIdsState.value = v
            prefs.edit().putStringSet("desktop_video_source_ids", v).apply()
        }

    private val newsIntervalMinutesState = mutableStateOf(prefs.getInt("desktop_news_interval", 30).coerceAtLeast(15))
    var newsIntervalMinutes: Int
        get() = newsIntervalMinutesState.value
        set(v) {
            val value = v.coerceAtLeast(15)
            newsIntervalMinutesState.value = value
            prefs.edit().putInt("desktop_news_interval", value).apply()
        }

    private val demandIntervalMinutesState = mutableStateOf(prefs.getInt(KEY_DEMAND_INTERVAL, 60).coerceAtLeast(15))
    var demandIntervalMinutes: Int
        get() = demandIntervalMinutesState.value
        set(v) {
            val value = v.coerceAtLeast(15)
            demandIntervalMinutesState.value = value
            prefs.edit().putInt(KEY_DEMAND_INTERVAL, value).apply()
        }

    private val newsAutomaticEnabledState = mutableStateOf(prefs.getBoolean(KEY_NEWS_AUTO_ENABLED, true))
    var newsAutomaticEnabled: Boolean
        get() = newsAutomaticEnabledState.value
        set(v) {
            newsAutomaticEnabledState.value = v
            prefs.edit().putBoolean(KEY_NEWS_AUTO_ENABLED, v).apply()
        }

    private val demandAutomaticEnabledState = mutableStateOf(prefs.getBoolean(KEY_DEMAND_AUTO_ENABLED, true))
    var demandAutomaticEnabled: Boolean
        get() = demandAutomaticEnabledState.value
        set(v) {
            demandAutomaticEnabledState.value = v
            prefs.edit().putBoolean(KEY_DEMAND_AUTO_ENABLED, v).apply()
        }

    private val videoAutomaticEnabledState = mutableStateOf(prefs.getBoolean(KEY_VIDEO_AUTO_ENABLED, true))
    var videoAutomaticEnabled: Boolean
        get() = videoAutomaticEnabledState.value
        set(v) {
            videoAutomaticEnabledState.value = v
            prefs.edit().putBoolean(KEY_VIDEO_AUTO_ENABLED, v).apply()
        }

    private val videoAutoTimesState = mutableStateOf(loadVideoAutoTimes())
    var videoAutoTimes: Set<String>
        get() = videoAutoTimesState.value
        private set(v) {
            val clean = v.mapNotNull(::normalizeVideoAutoTime).toSortedSet()
            videoAutoTimesState.value = clean
            prefs.edit().putStringSet(KEY_VIDEO_AUTO_TIMES, clean).apply()
        }

    fun addVideoAutoTime(value: String): Boolean {
        val clean = normalizeVideoAutoTime(value) ?: return false
        videoAutoTimes = videoAutoTimes + clean
        return true
    }

    fun removeVideoAutoTime(value: String) {
        videoAutoTimes = videoAutoTimes - value
    }

    fun resetVideoAutoTimes() {
        videoAutoTimes = DEFAULT_VIDEO_AUTO_TIMES
    }

    private fun loadVideoAutoTimes(): Set<String> {
        val saved = prefs.getStringSet(KEY_VIDEO_AUTO_TIMES, DEFAULT_VIDEO_AUTO_TIMES).orEmpty()
        return saved.mapNotNull(::normalizeVideoAutoTime).toSortedSet()
    }

    private fun normalizeVideoAutoTime(value: String): String? = runCatching {
        LocalTime.parse(value.trim(), TIME).format(TIME)
    }.getOrNull()

    fun nextNewsAutoAt(now: Long = System.currentTimeMillis()): Long {
        if (!automaticMonitoring || !newsAutomaticEnabled) return 0L
        val last = prefs.getLong(KEY_NEWS_SCHEDULE_AT, 0L)
        return if (last <= 0L) now else (last + newsIntervalMinutes * 60_000L).coerceAtLeast(now)
    }

    fun nextDemandAutoAt(now: Long = System.currentTimeMillis()): Long {
        if (!automaticMonitoring || !demandAutomaticEnabled) return 0L
        val last = prefs.getLong(KEY_DEMAND_SCHEDULE_AT, 0L)
        return if (last <= 0L) now else (last + demandIntervalMinutes * 60_000L).coerceAtLeast(now)
    }

    fun nextVideoAutoAt(now: Long = System.currentTimeMillis()): Long {
        if (!automaticMonitoring || !videoAutomaticEnabled || videoAutoTimes.isEmpty()) return 0L
        val zone = ZoneId.systemDefault()
        val current = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        val times = videoAutoTimes.mapNotNull { runCatching { LocalTime.parse(it, TIME) }.getOrNull() }.sorted()
        if (times.isEmpty()) return 0L
        times.forEach { time ->
            val candidate = LocalDateTime.of(current.toLocalDate(), time)
            if (!candidate.isBefore(current)) return candidate.atZone(zone).toInstant().toEpochMilli()
        }
        return LocalDateTime.of(current.toLocalDate().plusDays(1), times.first()).atZone(zone).toInstant().toEpochMilli()
    }

    private val automaticMonitoringState = mutableStateOf(prefs.getBoolean("desktop_automatic_monitoring", true))
    var automaticMonitoring: Boolean
        get() = automaticMonitoringState.value
        set(v) {
            automaticMonitoringState.value = v
            prefs.edit().putBoolean("desktop_automatic_monitoring", v).apply()
        }

    private val startWithWindowsState = mutableStateOf(prefs.getBoolean("desktop_start_with_windows", true))
    var startWithWindows: Boolean
        get() = startWithWindowsState.value
        set(v) {
            startWithWindowsState.value = v
            prefs.edit().putBoolean("desktop_start_with_windows", v).apply()
            updateWindowsStartup(v)
        }

    init {
        DesktopProxyManager.apply(context)
        migrateEstablishedTerms()
        refresh()
        if (startWithWindows) updateWindowsStartup(true)
        scope.launch { automationLoop() }
    }

    private fun migrateEstablishedTerms() {
        val migrationKey = "desktop_established_terms_20260909"
        if (prefs.getBoolean(migrationKey, false)) return

        DesktopEstablishedTerms.all.forEach(newsDb::addTerm)
        val newsSeed = newsDb.listTerms()
        DesktopEstablishedTerms.all.forEach { term ->
            VideoTermStore.add(context, term, newsSeed)
        }
        prefs.edit().putBoolean(migrationKey, true).apply()
    }

    fun refresh() {
        news = newsDb.listRecent(24, 1000)
        videos = videoDb.listRecent(7, 1500)
        newsHistory = newsDb.listNews(5000)
        videoHistory = videoDb.listAll(5000)
        terms = newsDb.listTerms()
        demands = newsDb.listDemands()
        videoTerms = VideoTermStore.load(context, terms)
        val stored = videoHistory.filter { it.relevant }
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        totalStoredVideos = stored.size
        capturedTodayVideos = stored.count { it.capturedAt >= startOfDay }
        touchUi()
    }

    private fun selectedNewsSources(): List<MediaSource> =
        if (newsAllSources) emptyList() else DesktopSourceCatalog.selected(selectedNewsSourceIds)

    private fun selectedVideoSources(): List<VideoSource> = VideoSourceCatalog.selected(selectedVideoSourceIds)

    fun searchNews(from: Long? = null, to: Long? = null, automatic: Boolean = false) {
        if (newsBusy) return
        if (!ensureProxyReady(false)) return
        if (!newsAllSources && selectedNewsSourceIds.isEmpty()) {
            status = "⚠ Selecione pelo menos uma fonte ou ative ‘Buscar em todos os veículos’."
            if (automatic) markAutoFailed("news", status)
            return
        }
        val knownBeforeRun = newsDb.listKnownLinks()
        newsNewLinks = emptySet()
        newsBusy = true
        if (automatic) markAutoAttempt("news")
        status = if (from == null) "Buscando notícias..." else "Buscando notícias no período..."
        scope.launch {
            try {
                val result = if (from == null || to == null) {
                    newsRepository.searchProgressive(selectedNewsSources(), newsAllSources) { update ->
                        newsProgress = update.progress
                        if (update.items.isNotEmpty()) {
                            news = mergeNewsForUi(news, update.items)
                            val currentNew = update.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                            if (currentNew.isNotEmpty()) newsNewLinks = newsNewLinks + currentNew
                        }
                    }
                } else {
                    newsRepository.searchPeriodProgressive(from, to, selectedNewsSources(), newsAllSources) { update ->
                        newsProgress = update.progress
                        if (update.items.isNotEmpty()) {
                            news = mergeNewsForUi(news, update.items)
                            val currentNew = update.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                            if (currentNew.isNotEmpty()) newsNewLinks = newsNewLinks + currentNew
                        }
                    }
                }
                if (automatic) markNewsAutoCompleted(result)
                newsNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                refresh()
                status = "✓ ${result.newCount} nova(s) notícia(s) • ${result.newDemandCount} demanda(s) • ${result.errors} falha(s)"
                if (result.newCount + result.newDemandCount > 0) notify("Monitor de Notícias", status)
            } catch (t: Throwable) {
                status = "Falha na busca de notícias: ${t.message ?: t.javaClass.simpleName}"
                if (automatic) markAutoFailed("news", t.message ?: t.javaClass.simpleName)
            } finally {
                newsBusy = false
            }
        }
    }

    fun searchDemand(demand: Demand) {
        if (demandBusy) return
        if (!ensureProxyReady(false)) return
        val knownBeforeRun = newsDb.listKnownLinks()
        newsNewLinks = emptySet()
        demandBusy = true
        status = "Buscando demanda: ${demand.vehicle} • ${demand.subject}"
        demandProgress = LiveSearchProgress(
            active = true,
            kind = "Demanda",
            startedAt = System.currentTimeMillis(),
            total = 1,
            currentSource = demand.vehicle,
            currentQuery = demand.subject
        )
        scope.launch {
            try {
                val result = newsRepository.searchDemand(demand)
                newsNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                refresh()
                status = "✓ Demanda: ${result.foundCount} resultado(s), ${result.newCount} novo(s)"
                demandProgress = demandProgress.copy(
                    active = false,
                    finishedAt = System.currentTimeMillis(),
                    completed = 1,
                    found = result.foundCount,
                    newCount = result.newCount,
                    errors = if (result.error != null) 1 else 0
                )
                if (result.newCount > 0) notify(
                    "Nova demanda encontrada",
                    "${demand.vehicle} • ${demand.subject}: ${result.newCount}"
                )
            } catch (t: Throwable) {
                status = "Falha na demanda: ${t.message ?: t.javaClass.simpleName}"
                demandProgress = demandProgress.copy(
                    active = false,
                    finishedAt = System.currentTimeMillis(),
                    errors = 1
                )
            } finally {
                demandBusy = false
            }
        }
    }

    fun searchAllDemands(automatic: Boolean = false) {
        if (demandBusy) return
        if (!ensureProxyReady(false)) return
        val active = demands.filter { it.active }
        if (active.isEmpty()) {
            status = "Nenhuma demanda ativa para pesquisar"
            if (automatic) markAutoFailed("demand", status)
            return
        }
        val knownBeforeRun = newsDb.listKnownLinks()
        newsNewLinks = emptySet()
        demandBusy = true
        if (automatic) markAutoAttempt("demand")
        status = "Buscando todas as demandas..."
        demandProgress = LiveSearchProgress(
            active = true,
            kind = "Demandas",
            startedAt = System.currentTimeMillis(),
            total = active.size
        )
        scope.launch {
            try {
                val result = newsRepository.searchAllDemands()
                if (automatic) markDemandAutoCompleted(result)
                newsNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                refresh()
                status = "✓ ${result.checkedCount} demanda(s) • ${result.foundCount} resultado(s) • ${result.newCount} novo(s)"
                demandProgress = demandProgress.copy(
                    active = false,
                    finishedAt = System.currentTimeMillis(),
                    completed = result.checkedCount,
                    found = result.foundCount,
                    newCount = result.newCount,
                    errors = result.errors
                )
                if (result.newCount > 0) notify("Demandas", "${result.newCount} novo(s) resultado(s)")
            } catch (t: Throwable) {
                status = "Falha nas demandas: ${t.message ?: t.javaClass.simpleName}"
                demandProgress = demandProgress.copy(
                    active = false,
                    finishedAt = System.currentTimeMillis(),
                    errors = 1
                )
                if (automatic) markAutoFailed("demand", t.message ?: t.javaClass.simpleName)
            } finally {
                demandBusy = false
            }
        }
    }

    fun searchVideos(from: Long? = null, to: Long? = null, automatic: Boolean = false) {
        if (videoBusy) return
        if (!ensureProxyReady(true)) return
        val sources = selectedVideoSources()
        if (sources.isEmpty()) {
            videoStatus = "⚠ Selecione pelo menos uma fonte de vídeo"
            if (automatic) markAutoFailed("video", videoStatus)
            return
        }
        val knownBeforeRun = videoDb.listKnownLinks()
        videoNewLinks = emptySet()
        videoBusy = true
        if (automatic) markAutoAttempt("video")
        videoStatus = if (from == null) "Buscando vídeos..." else "Buscando vídeos no período..."
        scope.launch {
            try {
                videoDb.removeInvalidListingEntries()
                videoDb.repairStoredMatches()
                val result = if (from == null || to == null) {
                    videoRepository.searchProgressive(sources) { update ->
                        videoProgress = update.progress
                        if (update.items.isNotEmpty()) {
                            videos = mergeVideosForUi(videos, update.items)
                            val currentNew = update.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                            if (currentNew.isNotEmpty()) videoNewLinks = videoNewLinks + currentNew
                        }
                    }
                } else {
                    videoRepository.searchPeriodProgressive(sources, from, to) { update ->
                        videoProgress = update.progress
                        if (update.items.isNotEmpty()) {
                            videos = mergeVideosForUi(videos, update.items)
                            val currentNew = update.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                            if (currentNew.isNotEmpty()) videoNewLinks = videoNewLinks + currentNew
                        }
                    }
                }
                unstableVideoSources = result.unstableSources
                if (automatic) markVideoAutoCompleted(result)
                videoNewLinks = result.items.asSequence().map { it.link }.filterNot { it in knownBeforeRun }.toSet()
                refresh()
                videoStatus = "✓ ${result.relevantCount} relevante(s) • ${result.newRelevantCount} novo(s) • ${result.errors} fonte(s) instável(is)"
                if (result.newRelevantCount > 0) notify("Novos vídeos", "${result.newRelevantCount} vídeo(s) relevante(s)")
            } catch (t: Throwable) {
                videoStatus = "Falha na busca de vídeos: ${t.message ?: t.javaClass.simpleName}"
                if (automatic) markAutoFailed("video", t.message ?: t.javaClass.simpleName)
            } finally {
                videoBusy = false
            }
        }
    }

    fun addTerm(v: String) { newsDb.addTerm(v); refresh() }
    fun removeTerm(v: String) { newsDb.removeTerm(v); refresh() }
    fun addVideoTerm(v: String) { VideoTermStore.add(context, v, terms); refresh() }
    fun removeVideoTerm(v: String) { VideoTermStore.remove(context, v, terms); refresh() }
    fun addDemand(vehicle: String, subject: String) { newsDb.addDemand(vehicle, subject); refresh() }
    fun removeDemand(id: Long) { newsDb.removeDemand(id); refresh() }
    fun clearNewsHistory() {
        newsDb.clearHistory()
        news = emptyList()
        newsHistory = emptyList()
        newsNewLinks = emptySet()
        status = "✓ Histórico de notícias limpo"
        refresh()
    }
    fun clearVideoHistory() {
        videoDb.clear()
        videos = emptyList()
        videoHistory = emptyList()
        videoNewLinks = emptySet()
        status = "✓ Histórico de vídeos limpo"
        videoStatus = status
        refresh()
    }

    fun exportNewsHistoryCsv(): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(dir, "historico-noticias-$stamp.csv")
        val rows = newsDb.listNews(10_000)
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("\uFEFFData;Fonte;Título;Link;Trecho;Termo;Demanda\n")
            rows.forEach { n ->
                w.write(
                    listOf(
                        formatCsvDate(n.date),
                        n.source,
                        n.title,
                        n.link,
                        n.snippet,
                        n.matchedTerm,
                        n.matchedDemand
                    ).joinToString(";") { csv(it) }
                )
                w.newLine()
            }
        }
        status = "✓ Histórico exportado: ${file.absolutePath}"
        return file
    }

    private fun csv(v: String) = "\"${v.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""
    private fun formatCsvDate(ms: Long) = java.text.SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        java.util.Locale("pt", "BR")
    ).format(java.util.Date(ms))

    fun setNewsSource(id: String, selected: Boolean) {
        selectedNewsSourceIds = selectedNewsSourceIds.toMutableSet().apply {
            if (selected) add(id) else remove(id)
        }
        newsAllSources = false
    }

    fun setNewsSources(ids: Set<String>, selected: Boolean) {
        val valid = ids.filter { DesktopSourceCatalog.byId.containsKey(it) }.toSet()
        selectedNewsSourceIds = selectedNewsSourceIds.toMutableSet().apply {
            if (selected) addAll(valid) else removeAll(valid)
        }
        newsAllSources = false
    }

    fun setVideoSource(id: String, selected: Boolean) {
        selectedVideoSourceIds = selectedVideoSourceIds.toMutableSet().apply {
            if (selected) add(id) else remove(id)
        }
    }

    fun setVideoSources(ids: Set<String>, selected: Boolean) {
        val valid = ids.filter { VideoSourceCatalog.byId.containsKey(it) }.toSet()
        selectedVideoSourceIds = selectedVideoSourceIds.toMutableSet().apply {
            if (selected) addAll(valid) else removeAll(valid)
        }
    }

    fun selectAllNewsSources() {
        newsAllSources = true
        selectedNewsSourceIds = DesktopSourceCatalog.all.map { it.id }.toSet()
    }

    fun clearNewsSources() {
        newsAllSources = false
        selectedNewsSourceIds = emptySet()
    }

    fun selectAllVideoSources() {
        selectedVideoSourceIds = VideoSourceCatalog.all.map { it.id }.toSet()
    }

    fun clearVideoSources() {
        selectedVideoSourceIds = emptySet()
    }

    fun parsePeriod(sd: String, st: String, ed: String, et: String): Pair<Long, Long>? = runCatching {
        val zone = ZoneId.systemDefault()
        val start = LocalDateTime.of(
            parseDate(sd),
            LocalTime.parse(st.ifBlank { "00:00" })
        ).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDateTime.of(
            parseDate(ed),
            LocalTime.parse(et.ifBlank { "23:59" })
        ).atZone(zone).toInstant().toEpochMilli()
        require(end > start)
        start to end
    }.getOrNull()

    fun periodPreset(days: Int): PeriodPreset {
        val end = LocalDateTime.now()
        val start = when (days) {
            0 -> end.toLocalDate().atStartOfDay()
            1 -> end.minusHours(24)
            else -> end.minusDays(days.toLong())
        }
        return PeriodPreset(
            start.toLocalDate().format(BR_DATE),
            start.toLocalTime().format(TIME),
            end.toLocalDate().format(BR_DATE),
            end.toLocalTime().format(TIME)
        )
    }

    private fun parseDate(v: String): LocalDate = runCatching {
        LocalDate.parse(v, BR_DATE)
    }.getOrElse {
        LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun newsAutoReport() = AutoReport(
        prefs.getLong(KEY_NEWS_ATTEMPT, 0L),
        prefs.getLong(KEY_NEWS_COMPLETED, 0L),
        prefs.getInt(KEY_NEWS_FOUND, 0),
        prefs.getInt(KEY_NEWS_NEW, 0),
        prefs.getInt(KEY_NEWS_ERRORS, 0),
        prefs.getString(KEY_NEWS_ERROR, "").orEmpty()
    )

    fun demandAutoReport() = AutoReport(
        prefs.getLong(KEY_DEMAND_ATTEMPT, 0L),
        prefs.getLong(KEY_DEMAND_COMPLETED, 0L),
        prefs.getInt(KEY_DEMAND_FOUND, 0),
        prefs.getInt(KEY_DEMAND_NEW, 0),
        prefs.getInt(KEY_DEMAND_ERRORS, 0),
        prefs.getString(KEY_DEMAND_ERROR, "").orEmpty(),
        prefs.getInt(KEY_DEMAND_CHECKED, 0)
    )

    fun videoAutoReport() = AutoReport(
        prefs.getLong(KEY_VIDEO_ATTEMPT, 0L),
        prefs.getLong(KEY_VIDEO_COMPLETED, 0L),
        prefs.getInt(KEY_VIDEO_FOUND, 0),
        prefs.getInt(KEY_VIDEO_NEW, 0),
        prefs.getInt(KEY_VIDEO_ERRORS, 0),
        prefs.getString(KEY_VIDEO_ERROR, "").orEmpty(),
        relevant = prefs.getInt(KEY_VIDEO_RELEVANT, 0)
    )

    private fun markAutoAttempt(kind: String) {
        val keyAttempt = when (kind) {
            "news" -> KEY_NEWS_ATTEMPT
            "demand" -> KEY_DEMAND_ATTEMPT
            else -> KEY_VIDEO_ATTEMPT
        }
        val keyError = when (kind) {
            "news" -> KEY_NEWS_ERROR
            "demand" -> KEY_DEMAND_ERROR
            else -> KEY_VIDEO_ERROR
        }
        prefs.edit()
            .putLong(keyAttempt, System.currentTimeMillis())
            .putString(keyError, "")
            .apply()
        touchUi()
    }

    private fun markAutoFailed(kind: String, error: String) {
        val key = when (kind) {
            "news" -> KEY_NEWS_ERROR
            "demand" -> KEY_DEMAND_ERROR
            else -> KEY_VIDEO_ERROR
        }
        prefs.edit().putString(key, error.take(180)).apply()
        touchUi()
    }

    private fun markNewsAutoCompleted(result: SearchResult) {
        prefs.edit()
            .putLong(KEY_NEWS_COMPLETED, System.currentTimeMillis())
            .putInt(KEY_NEWS_FOUND, result.foundCount)
            .putInt(KEY_NEWS_NEW, result.newCount)
            .putInt(KEY_NEWS_ERRORS, result.errors)
            .putString(KEY_NEWS_ERROR, if (result.errors > 0) "${result.errors} consulta(s) com falha" else "")
            .apply()
        touchUi()
    }

    private fun markDemandAutoCompleted(result: DemandSweepResult) {
        prefs.edit()
            .putLong(KEY_DEMAND_COMPLETED, System.currentTimeMillis())
            .putInt(KEY_DEMAND_CHECKED, result.checkedCount)
            .putInt(KEY_DEMAND_FOUND, result.foundCount)
            .putInt(KEY_DEMAND_NEW, result.newCount)
            .putInt(KEY_DEMAND_ERRORS, result.errors)
            .putString(KEY_DEMAND_ERROR, if (result.errors > 0) "${result.errors} demanda(s) com falha" else "")
            .apply()
        touchUi()
    }

    private fun markVideoAutoCompleted(result: VideoSearchResult) {
        prefs.edit()
            .putLong(KEY_VIDEO_COMPLETED, System.currentTimeMillis())
            .putInt(KEY_VIDEO_FOUND, result.foundCount)
            .putInt(KEY_VIDEO_NEW, result.newCount)
            .putInt(KEY_VIDEO_RELEVANT, result.newRelevantCount)
            .putInt(KEY_VIDEO_ERRORS, result.errors)
            .putString(KEY_VIDEO_ERROR, if (result.errors > 0) "${result.errors} fonte(s) instável(is)" else "")
            .apply()
        touchUi()
    }

    private fun updateWindowsStartup(enabled: Boolean) {
        if (!System.getProperty("os.name", "").startsWith("Windows", true)) return
        val appPath = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            val key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
            val command = if (enabled) {
                listOf(
                    "reg", "add", key,
                    "/v", "Monitor de Noticias",
                    "/t", "REG_SZ",
                    "/d", "\"$appPath\"",
                    "/f"
                )
            } else {
                listOf("reg", "delete", key, "/v", "Monitor de Noticias", "/f")
            }
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
                .apply {
                    inputStream.bufferedReader().use { it.readText() }
                    waitFor()
                }
        }
    }

    private suspend fun automationLoop() {
        while (currentCoroutineContext().isActive) {
            if (automaticMonitoring && DesktopProxyManager.isReady(context)) {
                val now = System.currentTimeMillis()

                if (newsAutomaticEnabled) {
                    val lastNews = prefs.getLong(KEY_NEWS_SCHEDULE_AT, 0L)
                    if (!newsBusy && now - lastNews >= newsIntervalMinutes * 60_000L) {
                        prefs.edit().putLong(KEY_NEWS_SCHEDULE_AT, now).apply()
                        searchNews(automatic = true)
                    }
                }

                if (demandAutomaticEnabled) {
                    val lastDemand = prefs.getLong(KEY_DEMAND_SCHEDULE_AT, 0L)
                    if (!demandBusy && now - lastDemand >= demandIntervalMinutes * 60_000L) {
                        prefs.edit().putLong(KEY_DEMAND_SCHEDULE_AT, now).apply()
                        searchAllDemands(automatic = true)
                    }
                }

                if (videoAutomaticEnabled && videoAutoTimes.isNotEmpty() && !videoBusy) {
                    val dt = LocalDateTime.now()
                    val dueTime = videoAutoTimes.firstOrNull { raw ->
                        runCatching {
                            val scheduled = LocalDateTime.of(dt.toLocalDate(), LocalTime.parse(raw, TIME))
                            java.time.Duration.between(scheduled, dt).toMinutes() in 0L..2L
                        }.getOrDefault(false)
                    }
                    if (dueTime != null) {
                        val slot = "${dt.toLocalDate()}-$dueTime"
                        if (prefs.getString(KEY_VIDEO_SCHEDULE_SLOT, "") != slot) {
                            prefs.edit().putString(KEY_VIDEO_SCHEDULE_SLOT, slot).apply()
                            searchVideos(automatic = true)
                        }
                    }
                }
            }
            delay(60_000L)
        }
    }

    private fun mergeNewsForUi(old: List<News>, fresh: List<News>) =
        (fresh + old)
            .distinctBy { it.link }
            .sortedWith(compareByDescending<News> { it.capturedAt }.thenByDescending { it.date })
            .take(1500)

    private fun mergeVideosForUi(old: List<VideoItem>, fresh: List<VideoItem>) =
        (fresh + old)
            .distinctBy { it.link }
            .sortedWith(compareByDescending<VideoItem> { it.capturedAt }.thenByDescending { it.publishedAt })
            .take(2000)

    private fun ensureProxyReady(video: Boolean): Boolean {
        if (DesktopProxyManager.isReady(context)) return true
        val warning = "⚠ Proxy autenticado: configure usuário e senha em Configurações."
        if (video) videoStatus = warning else status = warning
        return false
    }

    override fun close() {
        scope.cancel()
        newsDb.close()
        videoDb.close()
    }

    data class PeriodPreset(
        val startDate: String,
        val startTime: String,
        val endDate: String,
        val endTime: String
    )

    data class AutoReport(
        val attemptAt: Long,
        val completedAt: Long,
        val found: Int,
        val newCount: Int,
        val errors: Int,
        val error: String,
        val checked: Int = 0,
        val relevant: Int = 0
    )

    companion object {
        private val BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val DEFAULT_VIDEO_AUTO_TIMES = setOf("08:00", "12:00", "15:00", "19:00", "21:00")
        private const val KEY_NEWS_AUTO_ENABLED = "desktop_auto_news_enabled"
        private const val KEY_DEMAND_AUTO_ENABLED = "desktop_auto_demand_enabled"
        private const val KEY_VIDEO_AUTO_ENABLED = "desktop_auto_video_enabled"
        private const val KEY_DEMAND_INTERVAL = "desktop_demand_interval"
        private const val KEY_VIDEO_AUTO_TIMES = "desktop_video_auto_times"
        private const val KEY_NEWS_SCHEDULE_AT = "desktop_auto_news_at"
        private const val KEY_DEMAND_SCHEDULE_AT = "desktop_auto_demands_at"
        private const val KEY_VIDEO_SCHEDULE_SLOT = "desktop_auto_video_slot"
        private const val KEY_NEWS_ATTEMPT = "auto_news_attempt_at"
        private const val KEY_NEWS_COMPLETED = "auto_news_completed_at"
        private const val KEY_NEWS_FOUND = "auto_news_found"
        private const val KEY_NEWS_NEW = "auto_news_new"
        private const val KEY_NEWS_ERRORS = "auto_news_errors"
        private const val KEY_NEWS_ERROR = "auto_news_error_text"
        private const val KEY_DEMAND_ATTEMPT = "auto_demand_attempt_at"
        private const val KEY_DEMAND_COMPLETED = "auto_demand_completed_at"
        private const val KEY_DEMAND_CHECKED = "auto_demand_checked"
        private const val KEY_DEMAND_FOUND = "auto_demand_found"
        private const val KEY_DEMAND_NEW = "auto_demand_new"
        private const val KEY_DEMAND_ERRORS = "auto_demand_errors"
        private const val KEY_DEMAND_ERROR = "auto_demand_error_text"
        private const val KEY_VIDEO_ATTEMPT = "auto_video_attempt_at"
        private const val KEY_VIDEO_COMPLETED = "auto_video_completed_at"
        private const val KEY_VIDEO_FOUND = "auto_video_found"
        private const val KEY_VIDEO_NEW = "auto_video_new"
        private const val KEY_VIDEO_RELEVANT = "auto_video_new_relevant"
        private const val KEY_VIDEO_ERRORS = "auto_video_errors"
        private const val KEY_VIDEO_ERROR = "auto_video_error_text"
    }
}

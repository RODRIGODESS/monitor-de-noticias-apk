package br.com.monitordenoticias.desktop

import android.content.Context
import androidx.compose.runtime.getValue
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
        refresh()
        if (startWithWindows) updateWindowsStartup(true)
        scope.launch { automationLoop() }
    }

    fun refresh() {
        news = newsDb.listRecent(24, 1000)
        videos = videoDb.listRecent(7, 1500)
        terms = newsDb.listTerms()
        demands = newsDb.listDemands()
        videoTerms = VideoTermStore.load(context, terms)
        val stored = videoDb.listAll(1000).filter { it.relevant }
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        totalStoredVideos = stored.size
        capturedTodayVideos = stored.count { it.capturedAt >= startOfDay }
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
        newsBusy = true
        if (automatic) markAutoAttempt("news")
        status = if (from == null) "Buscando notícias..." else "Buscando notícias no período..."
        scope.launch {
            try {
                val result = if (from == null || to == null) {
                    newsRepository.searchProgressive(selectedNewsSources(), newsAllSources) { update ->
                        newsProgress = update.progress
                        if (update.items.isNotEmpty()) news = mergeNewsForUi(news, update.items)
                    }
                } else {
                    newsRepository.searchPeriodProgressive(from, to, selectedNewsSources(), newsAllSources) { update ->
                        newsProgress = update.progress
                        if (update.items.isNotEmpty()) news = mergeNewsForUi(news, update.items)
                    }
                }
                if (automatic) markNewsAutoCompleted(result)
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
                        if (update.items.isNotEmpty()) videos = mergeVideosForUi(videos, update.items)
                    }
                } else {
                    videoRepository.searchPeriodProgressive(sources, from, to) { update ->
                        videoProgress = update.progress
                        if (update.items.isNotEmpty()) videos = mergeVideosForUi(videos, update.items)
                    }
                }
                unstableVideoSources = result.unstableSources
                if (automatic) markVideoAutoCompleted(result)
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
    fun clearNewsHistory() { newsDb.clearHistory(); refresh() }
    fun clearVideoHistory() { videoDb.clear(); refresh() }

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
    }

    private fun markAutoFailed(kind: String, error: String) {
        val key = when (kind) {
            "news" -> KEY_NEWS_ERROR
            "demand" -> KEY_DEMAND_ERROR
            else -> KEY_VIDEO_ERROR
        }
        prefs.edit().putString(key, error.take(180)).apply()
    }

    private fun markNewsAutoCompleted(result: SearchResult) {
        prefs.edit()
            .putLong(KEY_NEWS_COMPLETED, System.currentTimeMillis())
            .putInt(KEY_NEWS_FOUND, result.foundCount)
            .putInt(KEY_NEWS_NEW, result.newCount)
            .putInt(KEY_NEWS_ERRORS, result.errors)
            .putString(KEY_NEWS_ERROR, if (result.errors > 0) "${result.errors} consulta(s) com falha" else "")
            .apply()
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
                val lastNews = prefs.getLong("desktop_auto_news_at", 0L)
                if (!newsBusy && now - lastNews >= newsIntervalMinutes * 60_000L) {
                    prefs.edit().putLong("desktop_auto_news_at", now).apply()
                    searchNews(automatic = true)
                }

                val lastDemand = prefs.getLong("desktop_auto_demands_at", 0L)
                if (!demandBusy && now - lastDemand >= 60L * 60L * 1000L) {
                    prefs.edit().putLong("desktop_auto_demands_at", now).apply()
                    searchAllDemands(automatic = true)
                }

                val dt = LocalDateTime.now()
                if (dt.minute < 2 && dt.hour in setOf(8, 12, 15, 19, 21) && !videoBusy) {
                    val slot = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
                    if (prefs.getString("desktop_auto_video_slot", "") != slot) {
                        prefs.edit().putString("desktop_auto_video_slot", slot).apply()
                        searchVideos(automatic = true)
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

package br.com.monitordenoticias.desktop

import android.content.Context
import br.com.monitordenoticias.android.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    @Volatile var news: List<News> = emptyList()
    @Volatile var videos: List<VideoItem> = emptyList()
    @Volatile var terms: List<String> = emptyList()
    @Volatile var videoTerms: List<String> = emptyList()
    @Volatile var demands: List<Demand> = emptyList()
    @Volatile var newsProgress: LiveSearchProgress = LiveSearchProgress()
    @Volatile var demandProgress: LiveSearchProgress = LiveSearchProgress()
    @Volatile var videoProgress: LiveSearchProgress = LiveSearchProgress()
    @Volatile var newsBusy = false
    @Volatile var demandBusy = false
    @Volatile var videoBusy = false
    @Volatile var status = "Pronto"
    @Volatile var videoStatus = "Pronto"
    @Volatile var unstableVideoSources: List<VideoSourceIssue> = emptyList()

    var newsAllSources: Boolean
        get() = prefs.getBoolean("desktop_news_all_sources", true)
        set(v) { prefs.edit().putBoolean("desktop_news_all_sources", v).apply() }

    var selectedNewsSourceIds: Set<String>
        get() = prefs.getStringSet("desktop_news_source_ids", emptySet()).orEmpty()
        set(v) { prefs.edit().putStringSet("desktop_news_source_ids", v).apply() }

    var selectedVideoSourceIds: Set<String>
        get() = prefs.getStringSet("desktop_video_source_ids", VideoSourceCatalog.defaultIds).orEmpty()
        set(v) { prefs.edit().putStringSet("desktop_video_source_ids", v).apply() }

    var newsIntervalMinutes: Int
        get() = prefs.getInt("desktop_news_interval", 30).coerceAtLeast(15)
        set(v) { prefs.edit().putInt("desktop_news_interval", v.coerceAtLeast(15)).apply() }

    var automaticMonitoring: Boolean
        get() = prefs.getBoolean("desktop_automatic_monitoring", true)
        set(v) { prefs.edit().putBoolean("desktop_automatic_monitoring", v).apply() }

    init {
        refresh()
        scope.launch { automationLoop() }
    }

    fun refresh() {
        news = newsDb.listRecent(24, 1000)
        videos = videoDb.listRecent(7, 1500)
        terms = newsDb.listTerms()
        demands = newsDb.listDemands()
        videoTerms = VideoTermStore.load(context, terms)
    }

    private fun selectedNewsSources(): List<MediaSource> =
        if (newsAllSources) emptyList() else SourceCatalog.selected(selectedNewsSourceIds)

    private fun selectedVideoSources(): List<VideoSource> = VideoSourceCatalog.selected(selectedVideoSourceIds)

    fun searchNews(from: Long? = null, to: Long? = null) {
        if (newsBusy) return
        if (!newsAllSources && selectedNewsSourceIds.isEmpty()) {
            status = "⚠ Selecione pelo menos uma fonte ou ative ‘Buscar em todos os veículos’."
            return
        }
        newsBusy = true
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
                refresh()
                status = "✓ ${result.newCount} nova(s) notícia(s) • ${result.newDemandCount} demanda(s) • ${result.errors} falha(s)"
                if (result.newCount + result.newDemandCount > 0) notify("Monitor de Notícias", status)
            } catch (t: Throwable) {
                status = "Falha na busca de notícias: ${t.message ?: t.javaClass.simpleName}"
            } finally { newsBusy = false }
        }
    }

    fun searchDemand(demand: Demand) {
        if (demandBusy) return
        demandBusy = true
        status = "Buscando demanda: ${demand.vehicle} • ${demand.subject}"
        demandProgress = LiveSearchProgress(active=true,kind="Demanda",startedAt=System.currentTimeMillis(),total=1,currentSource=demand.vehicle,currentQuery=demand.subject)
        scope.launch {
            try {
                val result = newsRepository.searchDemand(demand)
                refresh()
                status = "✓ Demanda: ${result.foundCount} resultado(s), ${result.newCount} novo(s)"
                demandProgress = demandProgress.copy(active=false,finishedAt=System.currentTimeMillis(),completed=1,found=result.foundCount,newCount=result.newCount,errors=if(result.error!=null)1 else 0)
                if (result.newCount > 0) notify("Nova demanda encontrada", "${demand.vehicle} • ${demand.subject}: ${result.newCount}")
            } catch (t: Throwable) {
                status = "Falha na demanda: ${t.message ?: t.javaClass.simpleName}"
                demandProgress = demandProgress.copy(active=false,finishedAt=System.currentTimeMillis(),errors=1)
            } finally { demandBusy = false }
        }
    }

    fun searchAllDemands() {
        if (demandBusy) return
        val active = demands.filter { it.active }
        if (active.isEmpty()) { status = "Nenhuma demanda ativa para pesquisar"; return }
        demandBusy = true
        status = "Buscando todas as demandas..."
        demandProgress = LiveSearchProgress(active=true,kind="Demandas",startedAt=System.currentTimeMillis(),total=active.size)
        scope.launch {
            try {
                val result = newsRepository.searchAllDemands()
                refresh()
                status = "✓ ${result.checkedCount} demanda(s) • ${result.foundCount} resultado(s) • ${result.newCount} novo(s)"
                demandProgress = demandProgress.copy(active=false,finishedAt=System.currentTimeMillis(),completed=result.checkedCount,found=result.foundCount,newCount=result.newCount,errors=result.errors)
                if (result.newCount > 0) notify("Demandas", "${result.newCount} novo(s) resultado(s)")
            } catch (t: Throwable) {
                status = "Falha nas demandas: ${t.message ?: t.javaClass.simpleName}"
                demandProgress = demandProgress.copy(active=false,finishedAt=System.currentTimeMillis(),errors=1)
            } finally { demandBusy = false }
        }
    }

    fun searchVideos(from: Long? = null, to: Long? = null) {
        if (videoBusy) return
        val sources = selectedVideoSources()
        if (sources.isEmpty()) { videoStatus = "⚠ Selecione pelo menos uma fonte de vídeo"; return }
        videoBusy = true
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
                refresh()
                videoStatus = "✓ ${result.relevantCount} relevante(s) • ${result.newRelevantCount} novo(s) • ${result.errors} fonte(s) instável(is)"
                if (result.newRelevantCount > 0) notify("Novos vídeos", "${result.newRelevantCount} vídeo(s) relevante(s)")
            } catch (t: Throwable) {
                videoStatus = "Falha na busca de vídeos: ${t.message ?: t.javaClass.simpleName}"
            } finally { videoBusy = false }
        }
    }

    fun addTerm(value: String) { newsDb.addTerm(value); refresh() }
    fun removeTerm(value: String) { newsDb.removeTerm(value); refresh() }
    fun addVideoTerm(value: String) { VideoTermStore.add(context, value, terms); refresh() }
    fun removeVideoTerm(value: String) { VideoTermStore.remove(context, value, terms); refresh() }
    fun addDemand(vehicle: String, subject: String) { newsDb.addDemand(vehicle, subject); refresh() }
    fun removeDemand(id: Long) { newsDb.removeDemand(id); refresh() }
    fun clearNewsHistory() { newsDb.clearHistory(); refresh() }
    fun clearVideoHistory() { videoDb.clear(); refresh() }

    fun setNewsSource(id: String, selected: Boolean) {
        val next=selectedNewsSourceIds.toMutableSet().apply { if(selected)add(id) else remove(id) }
        selectedNewsSourceIds=next
        newsAllSources=false
    }
    fun setNewsSources(ids:Set<String>,selected:Boolean) {
        val valid=ids.filter { SourceCatalog.byId.containsKey(it) }.toSet()
        selectedNewsSourceIds=selectedNewsSourceIds.toMutableSet().apply { if(selected)addAll(valid) else removeAll(valid) }
        newsAllSources=false
    }
    fun setVideoSource(id: String, selected: Boolean) {
        selectedVideoSourceIds=selectedVideoSourceIds.toMutableSet().apply { if(selected)add(id) else remove(id) }
    }
    fun setVideoSources(ids:Set<String>,selected:Boolean) {
        val valid=ids.filter { VideoSourceCatalog.byId.containsKey(it) }.toSet()
        selectedVideoSourceIds=selectedVideoSourceIds.toMutableSet().apply { if(selected)addAll(valid) else removeAll(valid) }
    }

    fun selectAllNewsSources() { newsAllSources=true; selectedNewsSourceIds=SourceCatalog.all.map { it.id }.toSet() }
    fun clearNewsSources() { newsAllSources=false; selectedNewsSourceIds=emptySet() }
    fun selectAllVideoSources() { selectedVideoSourceIds=VideoSourceCatalog.all.map { it.id }.toSet() }
    fun clearVideoSources() { selectedVideoSourceIds=emptySet() }

    fun parsePeriod(startDate:String,startTime:String,endDate:String,endTime:String):Pair<Long,Long>? = runCatching {
        val zone=ZoneId.systemDefault()
        val start=LocalDateTime.of(parseDate(startDate),LocalTime.parse(startTime.ifBlank{"00:00"})).atZone(zone).toInstant().toEpochMilli()
        val end=LocalDateTime.of(parseDate(endDate),LocalTime.parse(endTime.ifBlank{"23:59"})).atZone(zone).toInstant().toEpochMilli()
        require(end>start); start to end
    }.getOrNull()

    fun periodPreset(days:Int):PeriodPreset {
        val end=LocalDateTime.now()
        val start=when(days){0->end.toLocalDate().atStartOfDay();1->end.minusHours(24);else->end.minusDays(days.toLong())}
        return PeriodPreset(start.toLocalDate().format(BR_DATE),start.toLocalTime().format(TIME),end.toLocalDate().format(BR_DATE),end.toLocalTime().format(TIME))
    }
    private fun parseDate(value:String):LocalDate=runCatching{LocalDate.parse(value,BR_DATE)}.getOrElse{LocalDate.parse(value,DateTimeFormatter.ISO_LOCAL_DATE)}

    private suspend fun automationLoop() {
        while(currentCoroutineContext().isActive) {
            if(automaticMonitoring) {
                val now=System.currentTimeMillis()
                val lastNews=prefs.getLong("desktop_auto_news_at",0L)
                if(!newsBusy && now-lastNews>=newsIntervalMinutes*60_000L){prefs.edit().putLong("desktop_auto_news_at",now).apply();searchNews()}
                val lastDemand=prefs.getLong("desktop_auto_demands_at",0L)
                if(!demandBusy && now-lastDemand>=60L*60L*1000L){prefs.edit().putLong("desktop_auto_demands_at",now).apply();searchAllDemands()}
                val dt=LocalDateTime.now()
                if(dt.minute<2 && dt.hour in setOf(8,12,15,19,21) && !videoBusy) {
                    val key=dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"))
                    if(prefs.getString("desktop_auto_video_slot","")!=key){prefs.edit().putString("desktop_auto_video_slot",key).apply();searchVideos()}
                }
            }
            delay(60_000L)
        }
    }

    private fun mergeNewsForUi(old:List<News>,fresh:List<News>):List<News>=(fresh+old).distinctBy{it.link}.sortedWith(compareByDescending<News>{it.capturedAt}.thenByDescending{it.date}).take(1500)
    private fun mergeVideosForUi(old:List<VideoItem>,fresh:List<VideoItem>):List<VideoItem>=(fresh+old).distinctBy{it.link}.sortedWith(compareByDescending<VideoItem>{it.capturedAt}.thenByDescending{it.publishedAt}).take(2000)

    override fun close(){scope.cancel();newsDb.close();videoDb.close()}

    data class PeriodPreset(val startDate:String,val startTime:String,val endDate:String,val endTime:String)
    companion object { private val BR_DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy"); private val TIME=DateTimeFormatter.ofPattern("HH:mm") }
}

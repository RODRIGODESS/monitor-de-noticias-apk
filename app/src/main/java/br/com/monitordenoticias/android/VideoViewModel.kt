package br.com.monitordenoticias.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VideoViewModel(app: Application) : AndroidViewModel(app) {
    private val db = VideoDb(app).apply { removeInvalidListingEntries() }
    private val repo = VideoRepository(app, db)
    private val prefs = app.getSharedPreferences(BackgroundMonitor.PREFS, 0)

    private val savedIds = loadSelectedSources()
    private val initialPeriod = loadOrCreatePeriod()

    private fun loadSelectedSources(): Set<String> {
        val existing = prefs.getStringSet(KEY_SELECTED_SOURCES, null)
            ?.filter { VideoSourceCatalog.byId.containsKey(it) }
            ?.toSet()

        // Nova instalação: nacionais por padrão; regionais ficam disponíveis para
        // seleção por Região/UF, evitando centenas de requisições automáticas.
        if (existing == null) {
            val defaults = VideoSourceCatalog.defaultIds
            prefs.edit()
                .putStringSet(KEY_SELECTED_SOURCES, defaults)
                .putBoolean(KEY_YOUTUBE_283_MIGRATED, true)
                .putBoolean(KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, true)
                .apply()
            return defaults
        }

        var selected = existing
        var changed = false
        val editor = prefs.edit()

        if (!prefs.getBoolean(KEY_YOUTUBE_283_MIGRATED, false)) {
            selected = selected + VideoSourceCatalog.youtubeOfficialIds
            editor.putBoolean(KEY_YOUTUBE_283_MIGRATED, true)
            changed = true
        }

        // Compatibilidade para quem vem diretamente da 2.8.3: habilita apenas o
        // conjunto inicial da 2.8.4. O catálogo nacional expandido por UF não é
        // ligado automaticamente para não sobrecarregar o monitor em segundo plano.
        if (!prefs.getBoolean(KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, false)) {
            selected = selected + V284_STARTER_IDS.filter { VideoSourceCatalog.byId.containsKey(it) }
            editor.putBoolean(KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, true)
            changed = true
        }

        if (changed) editor.putStringSet(KEY_SELECTED_SOURCES, selected).apply()
        return selected
    }

    private fun loadOrCreatePeriod(): Array<String> {
        val existingStartDate = prefs.getString(KEY_PERIOD_START_DATE, "").orEmpty()
        val existingStartTime = prefs.getString(KEY_PERIOD_START_TIME, "").orEmpty()
        val existingEndDate = prefs.getString(KEY_PERIOD_END_DATE, "").orEmpty()
        val existingEndTime = prefs.getString(KEY_PERIOD_END_TIME, "").orEmpty()
        if (existingStartDate.isNotBlank() && existingStartTime.isNotBlank() && existingEndDate.isNotBlank() && existingEndTime.isNotBlank()) {
            return arrayOf(existingStartDate, existingStartTime, existingEndDate, existingEndTime)
        }
        val now = LocalDateTime.now()
        val start = now.minusDays(7)
        val values = arrayOf(start.format(DATE_FMT), start.format(TIME_FMT), now.format(DATE_FMT), now.format(TIME_FMT))
        prefs.edit()
            .putString(KEY_PERIOD_START_DATE, values[0])
            .putString(KEY_PERIOD_START_TIME, values[1])
            .putString(KEY_PERIOD_END_DATE, values[2])
            .putString(KEY_PERIOD_END_TIME, values[3])
            .apply()
        return values
    }

    private fun scopedItems(): List<VideoItem> = db.listRecent().filter { it.relevant }

    private val _state = MutableStateFlow(
        VideoState(
            items = scopedItems(),
            selectedSourceIds = savedIds,
            lastManualAt = prefs.getLong(KEY_LAST_MANUAL, 0L),
            periodStartDate = initialPeriod[0],
            periodStartTime = initialPeriod[1],
            periodEndDate = initialPeriod[2],
            periodEndTime = initialPeriod[3]
        )
    )
    val state: StateFlow<VideoState> = _state

    init {
        VideoBackgroundMonitor.scheduleAll(app)
        viewModelScope.launch(Dispatchers.IO) {
            val removed = repo.revalidateStored()
            if (removed > 0) {
                _state.value = _state.value.copy(
                    items = scopedItems(),
                    status = "✓ $removed resultado(s) de vídeo com associação incorreta removido(s)"
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            db.removeInvalidListingEntries()
            repo.revalidateStored()
            val current = _state.value
            val items = if (current.periodActive) {
                val range = parsePeriod(current)
                if (range != null) db.listBetween(range.first, range.second).filter { it.relevant } else scopedItems()
            } else scopedItems()
            _state.value = current.copy(items = items)
        }
    }

    fun searchNow() {
        if (_state.value.busy) return
        val selected = VideoSourceCatalog.selected(_state.value.selectedSourceIds)
        if (selected.isEmpty()) {
            _state.value = _state.value.copy(status = "⚠ Selecione pelo menos uma fonte de vídeo")
            return
        }
        _state.value = _state.value.copy(busy = true, status = "Buscando vídeos nos portais, telejornais e canais oficiais do YouTube...")
        viewModelScope.launch {
            val result = repo.search(selected)
            db.removeInvalidListingEntries()
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_MANUAL, now).apply()
            val status = resultStatus(result)
            _state.value = _state.value.copy(
                items = scopedItems(),
                busy = false,
                status = status,
                lastManualAt = now,
                periodActive = false
            )
        }
    }

    fun searchPeriodNow() {
        if (_state.value.busy) return
        val current = _state.value
        val range = parsePeriod(current)
        if (range == null || range.first >= range.second) {
            _state.value = current.copy(status = "⚠ Revise as datas e horários do período")
            return
        }
        val selected = VideoSourceCatalog.selected(current.selectedSourceIds)
        if (selected.isEmpty()) {
            _state.value = current.copy(status = "⚠ Selecione pelo menos uma fonte de vídeo")
            return
        }
        _state.value = current.copy(busy = true, status = "Pesquisando vídeos no período selecionado...")
        viewModelScope.launch {
            val result = repo.search(selected, range.first, range.second)
            val selectedIds = current.selectedSourceIds
            val stored = db.listBetween(range.first, range.second)
                .filter { it.relevant && it.sourceId in selectedIds }
            val combined = (result.items + stored)
                .distinctBy { it.link }
                .sortedByDescending { it.publishedAt }
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_MANUAL, now).apply()
            _state.value = _state.value.copy(
                items = combined,
                busy = false,
                status = when {
                    combined.isNotEmpty() -> "✓ ${combined.size} vídeo(s) no período selecionado"
                    result.errors > 0 -> "⚠ Nenhum vídeo no período • ${result.errors} consulta(s) falharam"
                    else -> "✓ Nenhum vídeo encontrado no período selecionado"
                },
                lastManualAt = now,
                periodActive = true
            )
        }
    }

    fun clearPeriodFilter() {
        _state.value = _state.value.copy(items = scopedItems(), periodActive = false, status = "✓ Filtro de período removido")
    }

    fun applyPeriodPreset(days: Int) {
        val now = LocalDateTime.now()
        val start = when (days) {
            0 -> LocalDate.now().atStartOfDay()
            1 -> now.minusHours(24)
            else -> now.minusDays(days.toLong())
        }
        setPeriodValues(start, now)
    }

    fun setPeriodStartDate(value: String) = updatePeriod(KEY_PERIOD_START_DATE, value) { it.copy(periodStartDate = value) }
    fun setPeriodStartTime(value: String) = updatePeriod(KEY_PERIOD_START_TIME, value) { it.copy(periodStartTime = value) }
    fun setPeriodEndDate(value: String) = updatePeriod(KEY_PERIOD_END_DATE, value) { it.copy(periodEndDate = value) }
    fun setPeriodEndTime(value: String) = updatePeriod(KEY_PERIOD_END_TIME, value) { it.copy(periodEndTime = value) }

    private fun updatePeriod(key: String, value: String, transform: (VideoState) -> VideoState) {
        prefs.edit().putString(key, value).apply()
        _state.value = transform(_state.value)
    }

    private fun setPeriodValues(start: LocalDateTime, end: LocalDateTime) {
        val sd = start.format(DATE_FMT)
        val st = start.format(TIME_FMT)
        val ed = end.format(DATE_FMT)
        val et = end.format(TIME_FMT)
        prefs.edit()
            .putString(KEY_PERIOD_START_DATE, sd)
            .putString(KEY_PERIOD_START_TIME, st)
            .putString(KEY_PERIOD_END_DATE, ed)
            .putString(KEY_PERIOD_END_TIME, et)
            .apply()
        _state.value = _state.value.copy(periodStartDate = sd, periodStartTime = st, periodEndDate = ed, periodEndTime = et)
    }

    private fun parsePeriod(state: VideoState): Pair<Long, Long>? = runCatching {
        val start = LocalDateTime.of(LocalDate.parse(state.periodStartDate, DATE_FMT), LocalTime.parse(state.periodStartTime, TIME_FMT))
        val end = LocalDateTime.of(LocalDate.parse(state.periodEndDate, DATE_FMT), LocalTime.parse(state.periodEndTime, TIME_FMT))
        val zone = ZoneId.systemDefault()
        start.atZone(zone).toInstant().toEpochMilli() to end.atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private fun resultStatus(result: VideoSearchResult): String = when {
        result.errors > 0 && result.foundCount == 0 -> "⚠ Busca concluída sem vídeos diretos • ${result.errors} consulta(s) falharam"
        result.newCount > 0 -> "✓ ${result.newCount} novo(s) vídeo(s) com link direto"
        result.foundCount > 0 -> "✓ Busca concluída • ${result.foundCount} vídeo(s) com link direto"
        else -> "✓ Busca concluída • nenhum vídeo direto para os Termos/Demandas"
    }

    fun setSourceSelected(id: String, selected: Boolean) {
        if (!VideoSourceCatalog.byId.containsKey(id)) return
        val next = _state.value.selectedSourceIds.toMutableSet().apply {
            if (selected) add(id) else remove(id)
        }.toSet()
        prefs.edit().putStringSet(KEY_SELECTED_SOURCES, next).apply()
        _state.value = _state.value.copy(selectedSourceIds = next, status = "✓ ${next.size} fonte(s) de vídeo selecionada(s)")
    }

    fun setSources(ids: Set<String>, selected: Boolean) {
        val valid = ids.filter { VideoSourceCatalog.byId.containsKey(it) }.toSet()
        val next = _state.value.selectedSourceIds.toMutableSet().apply {
            if (selected) addAll(valid) else removeAll(valid)
        }.toSet()
        prefs.edit().putStringSet(KEY_SELECTED_SOURCES, next).apply()
        _state.value = _state.value.copy(selectedSourceIds = next, status = "✓ ${next.size} fonte(s) de vídeo selecionada(s)")
    }

    fun setFilter(filter: VideoFilter) {
        _state.value = _state.value.copy(filter = filter, status = "Pronto")
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clear()
            _state.value = _state.value.copy(items = emptyList(), status = "✓ Histórico de vídeos limpo", periodActive = false)
        }
    }

    override fun onCleared() {
        db.close()
        super.onCleared()
    }

    companion object {
        const val KEY_SELECTED_SOURCES = "video_selected_source_ids"
        const val KEY_LAST_MANUAL = "video_last_manual_at"
        const val KEY_YOUTUBE_283_MIGRATED = "video_v283_youtube_sources_added"
        const val KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED = "video_v284_globoplay_telejournals_added"
        const val KEY_PERIOD_START_DATE = "video_period_start_date"
        const val KEY_PERIOD_START_TIME = "video_period_start_time"
        const val KEY_PERIOD_END_DATE = "video_period_end_date"
        const val KEY_PERIOD_END_TIME = "video_period_end_time"

        private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

        private val V284_STARTER_IDS = setOf(
            "globoplay-bom-dia-brasil", "globoplay-hora-1", "globoplay-jornal-hoje", "globoplay-jornal-nacional", "globoplay-jornal-da-globo",
            "globoplay-bom-dia-sp", "globoplay-sp1", "globoplay-sp2", "globoplay-bom-dia-rio", "globoplay-rj1", "globoplay-rj2",
            "globoplay-bom-dia-es", "globoplay-gazeta-meio-dia-es", "globoplay-bom-dia-minas", "globoplay-mg1", "globoplay-df1",
            "globoplay-bom-dia-rio-grande", "globoplay-tj1-tapajos", "globoplay-tj2-tapajos"
        )
    }
}

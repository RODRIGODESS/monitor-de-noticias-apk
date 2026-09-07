package br.com.monitordenoticias.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val db = NewsDb(app)
    private val repo = NewsRepository(db)
    private val prefs = app.getSharedPreferences("monitor_prefs", 0)
    private val locale = Locale("pt", "BR")

    private val savedInterval = prefs.getInt("interval_minutes", 30).coerceAtLeast(15)
    private val savedSourceIds = prefs.getStringSet("selected_source_ids", emptySet())
        .orEmpty().filter { SourceCatalog.byId.containsKey(it) }.toSet()
    private val savedSearchAll = prefs.getBoolean("search_all_sources", true)

    private val now = System.currentTimeMillis()
    private val defaultFrom = now - 7L * 24L * 60L * 60L * 1000L

    private val _state = MutableStateFlow(
        AppState(
            intervalMinutes = savedInterval,
            selectedSourceIds = savedSourceIds,
            searchAllSources = savedSearchAll,
            periodStartDate = prefs.getString("period_start_date", formatDate(defaultFrom)) ?: formatDate(defaultFrom),
            periodStartTime = prefs.getString("period_start_time", formatTime(defaultFrom)) ?: formatTime(defaultFrom),
            periodEndDate = prefs.getString("period_end_date", formatDate(now)) ?: formatDate(now),
            periodEndTime = prefs.getString("period_end_time", formatTime(now)) ?: formatTime(now)
        )
    )
    val state: StateFlow<AppState> = _state

    init {
        refresh()
        schedule(savedInterval)
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            publish {
                it.copy(
                    news = db.listRecent(),
                    history = db.listNews(),
                    terms = db.listTerms(),
                    demands = db.listDemands()
                )
            }
        }
    }

    fun search() {
        if (_state.value.busy) return
        val current = _state.value
        if (!current.searchAllSources && current.selectedSourceIds.isEmpty()) {
            _state.value = current.copy(status = "⚠ Selecione pelo menos uma fonte ou ative ‘Buscar em todos os veículos’.")
            return
        }

        _state.value = current.copy(busy = true, status = sourceStatusPrefix("Buscando notícias"))
        viewModelScope.launch {
            val latest = _state.value
            val sources = SourceCatalog.selected(latest.selectedSourceIds)
            val result = repo.search(sources, latest.searchAllSources)
            val status = when {
                result.errors > 0 && result.foundCount == 0 -> "⚠ Não foi possível consultar as fontes. Verifique sua conexão."
                result.errors > 0 -> "Busca parcial: ${result.newCount} nova(s) • ${result.errors} consulta(s) falharam"
                result.newCount == 0 -> "Busca concluída: nenhuma notícia nova • ${result.foundCount} resultado(s)"
                result.newDemandCount > 0 -> "✓ ${result.newCount} nova(s) • ${result.newDemandCount} demanda(s) encontrada(s)"
                else -> "✓ ${result.newCount} nova(s) notícia(s) encontrada(s)"
            }
            _state.value = _state.value.copy(
                news = db.listRecent(),
                history = db.listNews(),
                busy = false,
                status = status,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }

    fun searchSavedPeriod() {
        val current = _state.value
        if (current.busy) return
        if (!current.searchAllSources && current.selectedSourceIds.isEmpty()) {
            _state.value = current.copy(status = "⚠ Selecione pelo menos uma fonte ou ative a busca em todos os veículos.")
            return
        }
        val from = parseDateTime(current.periodStartDate, current.periodStartTime)
        val to = parseDateTime(current.periodEndDate, current.periodEndTime)
        if (from == null || to == null) {
            _state.value = current.copy(status = "⚠ Data ou hora inválida. Use dd/MM/aaaa e HH:mm.")
            return
        }
        if (from >= to) {
            _state.value = current.copy(status = "⚠ Período inválido: o início precisa ser anterior ao fim.")
            return
        }
        searchPeriod(from, to)
    }

    private fun searchPeriod(from: Long, to: Long) {
        _state.value = _state.value.copy(busy = true, status = "Pesquisando período salvo...")
        viewModelScope.launch {
            val latest = _state.value
            val result = repo.searchPeriod(
                from,
                to,
                SourceCatalog.selected(latest.selectedSourceIds),
                latest.searchAllSources
            )
            val status = if (result.errors > 0 && result.foundCount == 0)
                "⚠ Não foi possível concluir a pesquisa externa."
            else "Período: ${result.foundCount} matéria(s) • ${result.newCount} nova(s) no histórico"
            _state.value = _state.value.copy(
                news = result.items,
                history = db.listNews(),
                busy = false,
                status = status,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }

    fun setPeriodStartDate(value: String) = updatePeriod { it.copy(periodStartDate = value) }
    fun setPeriodStartTime(value: String) = updatePeriod { it.copy(periodStartTime = value) }
    fun setPeriodEndDate(value: String) = updatePeriod { it.copy(periodEndDate = value) }
    fun setPeriodEndTime(value: String) = updatePeriod { it.copy(periodEndTime = value) }

    fun applyPeriodPreset(days: Int) {
        val end = System.currentTimeMillis()
        val start = when (days) {
            0 -> SimpleDateFormat("dd/MM/yyyy", locale).parse(formatDate(end))?.time ?: end
            1 -> end - 24L * 60L * 60L * 1000L
            else -> end - days.toLong() * 24L * 60L * 60L * 1000L
        }
        val newState = _state.value.copy(
            periodStartDate = formatDate(start),
            periodStartTime = if (days == 0) "00:00" else formatTime(start),
            periodEndDate = formatDate(end),
            periodEndTime = formatTime(end),
            status = "✓ Período atualizado"
        )
        _state.value = newState
        persistPeriod(newState)
    }

    fun setSourceSelected(id: String, selected: Boolean) {
        if (!SourceCatalog.byId.containsKey(id)) return
        val ids = _state.value.selectedSourceIds.toMutableSet().apply {
            if (selected) add(id) else remove(id)
        }.toSet()
        prefs.edit().putStringSet("selected_source_ids", ids).apply()
        _state.value = _state.value.copy(
            selectedSourceIds = ids,
            searchAllSources = if (selected) false else _state.value.searchAllSources,
            status = "✓ ${ids.size} fonte(s) selecionada(s)"
        )
        if (selected) prefs.edit().putBoolean("search_all_sources", false).apply()
    }

    fun setVisibleSources(ids: Set<String>, selected: Boolean) {
        val valid = ids.filter { SourceCatalog.byId.containsKey(it) }.toSet()
        val result = _state.value.selectedSourceIds.toMutableSet().apply {
            if (selected) addAll(valid) else removeAll(valid)
        }.toSet()
        val searchAll = if (selected && valid.isNotEmpty()) false else _state.value.searchAllSources
        prefs.edit()
            .putStringSet("selected_source_ids", result)
            .putBoolean("search_all_sources", searchAll)
            .apply()
        _state.value = _state.value.copy(
            selectedSourceIds = result,
            searchAllSources = searchAll,
            status = if (selected) "✓ Fontes visíveis selecionadas" else "✓ Fontes visíveis desmarcadas"
        )
    }

    fun setSearchAllSources(enabled: Boolean) {
        prefs.edit().putBoolean("search_all_sources", enabled).apply()
        _state.value = _state.value.copy(
            searchAllSources = enabled,
            status = if (enabled) "✓ Busca aberta em qualquer veículo ativada" else "Selecione as fontes desejadas"
        )
    }

    fun addTerm(value: String) { viewModelScope.launch(Dispatchers.IO) { db.addTerm(value); refresh() } }
    fun removeTerm(value: String) { viewModelScope.launch(Dispatchers.IO) { db.removeTerm(value); refresh() } }
    fun addDemand(vehicle:String,subject:String) { viewModelScope.launch(Dispatchers.IO) { db.addDemand(vehicle,subject); refresh() } }
    fun removeDemand(id:Long) { viewModelScope.launch(Dispatchers.IO) { db.removeDemand(id); refresh() } }
    fun clearHistory() { viewModelScope.launch(Dispatchers.IO) { db.clearHistory(); refresh(); publish { it.copy(status="✓ Histórico limpo com sucesso") } } }
    fun setTab(tab:Int) { _state.value = _state.value.copy(selectedTab = tab) }
    fun setDemandFilter(enabled: Boolean) { _state.value = _state.value.copy(showOnlyDemands = enabled) }

    fun setInterval(minutes:Int) {
        val safe = minutes.coerceAtLeast(15)
        prefs.edit().putInt("interval_minutes", safe).apply()
        _state.value = _state.value.copy(intervalMinutes = safe, status = "✓ Intervalo salvo: $safe min")
        schedule(safe)
    }

    private fun updatePeriod(block: (AppState) -> AppState) {
        val updated = block(_state.value)
        _state.value = updated
        persistPeriod(updated)
    }

    private fun persistPeriod(state: AppState) {
        prefs.edit()
            .putString("period_start_date", state.periodStartDate)
            .putString("period_start_time", state.periodStartTime)
            .putString("period_end_date", state.periodEndDate)
            .putString("period_end_time", state.periodEndTime)
            .apply()
    }

    private fun parseDateTime(date: String, time: String): Long? = runCatching {
        SimpleDateFormat("dd/MM/yyyy HH:mm", locale).apply { isLenient = false }.parse("$date $time")?.time
    }.getOrNull()

    private fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", locale).format(Date(ms))
    private fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(ms))

    private fun sourceStatusPrefix(prefix: String): String {
        val state = _state.value
        return if (state.searchAllSources) "$prefix • qualquer veículo" else "$prefix • ${state.selectedSourceIds.size} fonte(s)"
    }

    private fun schedule(minutes:Int) {
        val req = PeriodicWorkRequestBuilder<MonitorWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "monitor_noticias",
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    private fun publish(block:(AppState)->AppState) { _state.value = block(_state.value) }
    override fun onCleared(){ db.close(); super.onCleared() }
}

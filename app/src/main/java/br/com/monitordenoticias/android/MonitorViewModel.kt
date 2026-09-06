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
import java.util.concurrent.TimeUnit

class MonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val db = NewsDb(app)
    private val repo = NewsRepository(db)
    private val prefs = app.getSharedPreferences("monitor_prefs", 0)
    private val savedInterval = prefs.getInt("interval_minutes", 30).coerceAtLeast(15)
    private val _state = MutableStateFlow(AppState(intervalMinutes = savedInterval))
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
        _state.value = _state.value.copy(busy = true, status = "Buscando notícias...")
        viewModelScope.launch {
            val result = repo.search()
            val status = when {
                result.errors > 0 && result.foundCount == 0 -> "⚠ Não foi possível consultar as fontes. Verifique sua conexão."
                result.errors > 0 -> "Busca parcial: ${result.newCount} nova(s) • ${result.errors} consulta(s) falharam"
                result.newCount == 0 -> "Busca concluída: nenhuma notícia nova • ${result.foundCount} já conhecida(s)"
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

    fun searchPeriod(from: Long, to: Long) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, status = "Pesquisando período...")
        viewModelScope.launch {
            val result = repo.searchPeriod(from, to)
            val status = if (result.errors > 0 && result.foundCount == 0)
                "⚠ Não foi possível concluir a pesquisa externa."
            else "Período: ${result.foundCount} matéria(s) encontrada(s) • ${result.newCount} nova(s) no histórico"
            _state.value = _state.value.copy(news = result.items, history = db.listNews(), busy = false, status = status)
        }
    }

    fun addTerm(value: String) { viewModelScope.launch(Dispatchers.IO) { db.addTerm(value); refresh() } }
    fun removeTerm(value: String) { viewModelScope.launch(Dispatchers.IO) { db.removeTerm(value); refresh() } }
    fun addDemand(vehicle:String,subject:String) { viewModelScope.launch(Dispatchers.IO) { db.addDemand(vehicle,subject); refresh() } }
    fun removeDemand(id:Long) { viewModelScope.launch(Dispatchers.IO) { db.removeDemand(id); refresh() } }
    fun clearHistory() { viewModelScope.launch(Dispatchers.IO) { db.clearHistory(); refresh(); publish { it.copy(status="✓ Histórico limpo com sucesso") } } }
    fun setTab(tab:Int) { _state.value=_state.value.copy(selectedTab=tab) }
    fun setDemandFilter(enabled: Boolean) { _state.value = _state.value.copy(showOnlyDemands = enabled) }

    fun setInterval(minutes:Int) {
        val safe = minutes.coerceAtLeast(15)
        prefs.edit().putInt("interval_minutes", safe).apply()
        _state.value=_state.value.copy(intervalMinutes=safe, status="✓ Intervalo salvo: $safe min")
        schedule(safe)
    }

    private fun schedule(minutes:Int) {
        val req=PeriodicWorkRequestBuilder<MonitorWorker>(minutes.toLong(),TimeUnit.MINUTES).build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork("monitor_noticias",ExistingPeriodicWorkPolicy.UPDATE,req)
    }

    private fun publish(block:(AppState)->AppState) { _state.value=block(_state.value) }
    override fun onCleared(){ db.close(); super.onCleared() }
}

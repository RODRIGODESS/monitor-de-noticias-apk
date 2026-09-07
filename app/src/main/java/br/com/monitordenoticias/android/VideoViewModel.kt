package br.com.monitordenoticias.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VideoViewModel(app: Application) : AndroidViewModel(app) {
    private val db = VideoDb(app).apply { removeInvalidListingEntries() }
    private val repo = VideoRepository(app, db)
    private val prefs = app.getSharedPreferences(BackgroundMonitor.PREFS, 0)

    private val savedIds = prefs.getStringSet(KEY_SELECTED_SOURCES, null)
        ?.filter { VideoSourceCatalog.byId.containsKey(it) }
        ?.toSet()
        ?: VideoSourceCatalog.defaultIds

    private fun scopedItems(): List<VideoItem> = db.listRecent().filter { it.relevant }

    private val _state = MutableStateFlow(
        VideoState(
            items = scopedItems(),
            selectedSourceIds = savedIds,
            lastManualAt = prefs.getLong(KEY_LAST_MANUAL, 0L)
        )
    )
    val state: StateFlow<VideoState> = _state

    init {
        VideoBackgroundMonitor.scheduleAll(app)
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            db.removeInvalidListingEntries()
            _state.value = _state.value.copy(items = scopedItems())
        }
    }

    fun searchNow() {
        if (_state.value.busy) return
        val selected = VideoSourceCatalog.selected(_state.value.selectedSourceIds)
        if (selected.isEmpty()) {
            _state.value = _state.value.copy(status = "⚠ Selecione pelo menos uma fonte de vídeo")
            return
        }
        _state.value = _state.value.copy(busy = true, status = "Buscando links diretos de vídeos pelos Termos e Demandas...")
        viewModelScope.launch {
            val result = repo.search(selected)
            db.removeInvalidListingEntries()
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_MANUAL, now).apply()
            val status = when {
                result.errors > 0 && result.foundCount == 0 -> "⚠ Busca concluída sem vídeos diretos • ${result.errors} consulta(s) falharam"
                result.newCount > 0 -> "✓ ${result.newCount} novo(s) vídeo(s) com link direto"
                result.foundCount > 0 -> "✓ Busca concluída • ${result.foundCount} vídeo(s) com link direto"
                else -> "✓ Busca concluída • nenhum vídeo direto para os Termos/Demandas"
            }
            _state.value = _state.value.copy(
                items = scopedItems(),
                busy = false,
                status = status,
                lastManualAt = now
            )
        }
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
            _state.value = _state.value.copy(items = emptyList(), status = "✓ Histórico de vídeos limpo")
        }
    }

    override fun onCleared() {
        db.close()
        super.onCleared()
    }

    companion object {
        const val KEY_SELECTED_SOURCES = "video_selected_source_ids"
        const val KEY_LAST_MANUAL = "video_last_manual_at"
    }
}

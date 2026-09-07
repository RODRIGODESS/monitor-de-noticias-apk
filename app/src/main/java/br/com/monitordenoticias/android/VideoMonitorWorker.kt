package br.com.monitordenoticias.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class VideoMonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        VideoAutoRunLog.markAttempt(applicationContext)
        val db = VideoDb(applicationContext).apply { removeInvalidListingEntries() }
        return try {
            val prefs = applicationContext.getSharedPreferences(BackgroundMonitor.PREFS, 0)
            val existing = prefs.getStringSet(VideoViewModel.KEY_SELECTED_SOURCES, null)
                ?.filter { VideoSourceCatalog.byId.containsKey(it) }
                ?.toSet()

            var selectedIds = existing ?: VideoSourceCatalog.defaultIds
            var changed = false
            val editor = prefs.edit()

            if (existing == null) {
                editor.putBoolean(VideoViewModel.KEY_YOUTUBE_283_MIGRATED, true)
                editor.putBoolean(VideoViewModel.KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, true)
                changed = true
            } else {
                if (!prefs.getBoolean(VideoViewModel.KEY_YOUTUBE_283_MIGRATED, false)) {
                    selectedIds = selectedIds + VideoSourceCatalog.youtubeOfficialIds
                    editor.putBoolean(VideoViewModel.KEY_YOUTUBE_283_MIGRATED, true)
                    changed = true
                }
                if (!prefs.getBoolean(VideoViewModel.KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, false)) {
                    selectedIds = selectedIds + V284_STARTER_IDS.filter { VideoSourceCatalog.byId.containsKey(it) }
                    editor.putBoolean(VideoViewModel.KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, true)
                    changed = true
                }
            }

            if (changed || existing == null) {
                editor.putStringSet(VideoViewModel.KEY_SELECTED_SOURCES, selectedIds).apply()
            }

            val sources = VideoSourceCatalog.selected(selectedIds)
            if (sources.isEmpty()) {
                val empty = VideoSearchResult(emptyList(), 0, 0, 0, 0, 0)
                VideoAutoRunLog.markCompleted(applicationContext, empty)
                return Result.success()
            }

            val repository = VideoRepository(applicationContext, db)
            repository.revalidateStored()
            val result = repository.search(sources)
            db.removeInvalidListingEntries()
            VideoAutoRunLog.markCompleted(applicationContext, result)

            if (result.newCount > 0) {
                val text = "${result.newCount} novo(s) vídeo(s) com link direto relacionado(s) aos seus Termos/Demandas."
                NotificationHelper.notify(applicationContext, "Monitor de Vídeos", text)
            }

            if (result.errors >= sources.size && result.foundCount == 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            VideoAutoRunLog.markFailed(applicationContext, e)
            Result.retry()
        } finally {
            db.close()
        }
    }

    companion object {
        private val V284_STARTER_IDS = setOf(
            "globoplay-bom-dia-brasil", "globoplay-hora-1", "globoplay-jornal-hoje", "globoplay-jornal-nacional", "globoplay-jornal-da-globo",
            "globoplay-bom-dia-sp", "globoplay-sp1", "globoplay-sp2", "globoplay-bom-dia-rio", "globoplay-rj1", "globoplay-rj2",
            "globoplay-bom-dia-es", "globoplay-gazeta-meio-dia-es", "globoplay-bom-dia-minas", "globoplay-mg1", "globoplay-df1",
            "globoplay-bom-dia-rio-grande", "globoplay-tj1-tapajos", "globoplay-tj2-tapajos"
        )
    }
}

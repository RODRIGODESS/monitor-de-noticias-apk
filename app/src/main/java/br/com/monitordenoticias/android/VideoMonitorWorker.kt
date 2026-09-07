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

            if (!prefs.getBoolean(VideoViewModel.KEY_YOUTUBE_283_MIGRATED, false)) {
                selectedIds = selectedIds + VideoSourceCatalog.youtubeOfficialIds
                editor.putBoolean(VideoViewModel.KEY_YOUTUBE_283_MIGRATED, true)
                changed = true
            }

            if (!prefs.getBoolean(VideoViewModel.KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, false)) {
                selectedIds = selectedIds + VideoSourceCatalog.globoplayTelejournalIds
                editor.putBoolean(VideoViewModel.KEY_GLOBOPLAY_TELEJOURNALS_284_MIGRATED, true)
                changed = true
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

            val result = VideoRepository(applicationContext, db).search(sources)
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
}

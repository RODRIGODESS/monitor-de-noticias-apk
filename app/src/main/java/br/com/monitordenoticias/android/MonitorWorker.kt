package br.com.monitordenoticias.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = NewsDb(applicationContext)
        return try {
            val prefs = applicationContext.getSharedPreferences("monitor_prefs", 0)
            val searchAll = prefs.getBoolean("search_all_sources", true)
            val sourceIds = prefs.getStringSet("selected_source_ids", emptySet()).orEmpty()
            val sources = SourceCatalog.selected(sourceIds)
            val result = NewsRepository(db).searchBlockingCompatible(sources, searchAll)
            if (result.newCount > 0) {
                val text = if (result.newDemandCount > 0)
                    "${result.newCount} nova(s) notícia(s) • ${result.newDemandCount} demanda(s) encontrada(s)."
                else "${result.newCount} nova(s) notícia(s) encontrada(s) nas últimas 24h."
                NotificationHelper.notify(applicationContext, "Monitor de Notícias", text)
            }
            if (result.errors > 0 && result.foundCount == 0) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }
}

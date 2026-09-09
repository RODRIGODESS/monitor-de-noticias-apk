package br.com.monitordenoticias.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Centraliza o monitoramento em segundo plano de Notícias e Demandas.
 *
 * Desde a v4.2.0, Notícias e Demandas possuem liga/desliga e intervalos
 * independentes. O WorkManager permanece como agendador principal. Um alarme
 * inexato permitido durante idle funciona como heartbeat de recuperação para os
 * monitores que estiverem habilitados.
 */
object BackgroundMonitor {
    const val PREFS = "monitor_prefs"
    private const val HEARTBEAT_REQUEST_CODE = 2601
    private const val HEARTBEAT_INTERVAL_MS = 60L * 60L * 1000L
    private const val MIN_STALE_AFTER_MS = 50L * 60L * 1000L

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Mantém compatibilidade com chamadas antigas que ainda informam o intervalo. */
    fun scheduleAll(context: Context, newsIntervalMinutes: Int) {
        val app = context.applicationContext
        AutoSearchSettings.migrate(app)
        val cfg = AutoSearchSettings.read(app)

        if (cfg.newsEnabled) scheduleNews(app, cfg.newsIntervalMinutes)
        else cancelNews(app)

        if (cfg.demandsEnabled) scheduleDemands(app, cfg.demandsIntervalMinutes)
        else cancelDemands(app)

        if (cfg.newsEnabled || cfg.demandsEnabled) scheduleHeartbeat(app)
        else cancelHeartbeat(app)
    }

    fun scheduleAll(context: Context) {
        val legacy = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("interval_minutes", 30)
        scheduleAll(context, legacy)
    }

    fun scheduleNews(context: Context, minutes: Int) {
        val safe = minutes.coerceAtLeast(15)
        val request = PeriodicWorkRequestBuilder<MonitorWorker>(safe.toLong(), TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "monitor_noticias",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleDemands(context: Context, minutes: Int = 60) {
        val safe = minutes.coerceAtLeast(15)
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("monitor_demandas_1h")
        val request = PeriodicWorkRequestBuilder<DemandMonitorWorker>(safe.toLong(), TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .build()
        wm.enqueueUniquePeriodicWork(
            "monitor_demandas",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelNews(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("monitor_noticias")
            cancelUniqueWork("monitor_noticias_heartbeat")
        }
    }

    private fun cancelDemands(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("monitor_demandas")
            cancelUniqueWork("monitor_demandas_1h")
            cancelUniqueWork("monitor_demandas_heartbeat")
        }
    }

    fun scheduleHeartbeat(context: Context, delayMs: Long = HEARTBEAT_INTERVAL_MS) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, BackgroundHeartbeatReceiver::class.java)
            .setAction("br.com.monitordenoticias.android.BACKGROUND_HEARTBEAT")
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            HEARTBEAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val safeDelay = delayMs.coerceAtLeast(60_000L)
        val triggerElapsed = SystemClock.elapsedRealtime() + safeDelay
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerElapsed,
            pendingIntent
        )
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(AutoRunLog.KEY_NEXT_HEARTBEAT_AT, System.currentTimeMillis() + safeDelay)
            .apply()
    }

    private fun cancelHeartbeat(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, BackgroundHeartbeatReceiver::class.java)
            .setAction("br.com.monitordenoticias.android.BACKGROUND_HEARTBEAT")
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            HEARTBEAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(AutoRunLog.KEY_NEXT_HEARTBEAT_AT, 0L).apply()
    }

    /** Solicita recuperação somente para monitores habilitados e realmente atrasados. */
    fun enqueueRecoveryIfStale(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cfg = AutoSearchSettings.read(app)
        val now = System.currentTimeMillis()
        val wm = WorkManager.getInstance(app)

        if (cfg.newsEnabled) {
            val lastNews = prefs.getLong(AutoRunLog.KEY_NEWS_ATTEMPT_AT, 0L)
            val staleAfter = staleAfter(cfg.newsIntervalMinutes)
            if (force || lastNews == 0L || now - lastNews >= staleAfter) {
                val news = OneTimeWorkRequestBuilder<MonitorWorker>()
                    .setConstraints(connectedConstraints())
                    .build()
                wm.enqueueUniqueWork("monitor_noticias_heartbeat", ExistingWorkPolicy.REPLACE, news)
            }
        }

        if (cfg.demandsEnabled) {
            val lastDemand = prefs.getLong(AutoRunLog.KEY_DEMAND_ATTEMPT_AT, 0L)
            val staleAfter = staleAfter(cfg.demandsIntervalMinutes)
            if (force || lastDemand == 0L || now - lastDemand >= staleAfter) {
                val demand = OneTimeWorkRequestBuilder<DemandMonitorWorker>()
                    .setConstraints(connectedConstraints())
                    .build()
                wm.enqueueUniqueWork("monitor_demandas_heartbeat", ExistingWorkPolicy.REPLACE, demand)
            }
        }
    }

    private fun staleAfter(intervalMinutes: Int): Long = maxOf(
        MIN_STALE_AFTER_MS,
        (intervalMinutes + 20L) * 60L * 1000L
    )
}

object AutoRunLog {
    const val KEY_NEXT_HEARTBEAT_AT = "background_next_heartbeat_at"

    const val KEY_NEWS_ATTEMPT_AT = "auto_news_attempt_at"
    const val KEY_NEWS_COMPLETED_AT = "auto_news_completed_at"
    const val KEY_NEWS_FOUND = "auto_news_found"
    const val KEY_NEWS_NEW = "auto_news_new"
    const val KEY_NEWS_ERRORS = "auto_news_errors"
    const val KEY_NEWS_ERROR_TEXT = "auto_news_error_text"

    const val KEY_DEMAND_ATTEMPT_AT = "auto_demand_attempt_at"
    const val KEY_DEMAND_COMPLETED_AT = "auto_demand_completed_at"
    const val KEY_DEMAND_CHECKED = "auto_demand_checked"
    const val KEY_DEMAND_FOUND = "auto_demand_found"
    const val KEY_DEMAND_NEW = "auto_demand_new"
    const val KEY_DEMAND_ERRORS = "auto_demand_errors"
    const val KEY_DEMAND_ERROR_TEXT = "auto_demand_error_text"

    fun markNewsAttempt(context: Context) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEWS_ATTEMPT_AT, System.currentTimeMillis())
            .putString(KEY_NEWS_ERROR_TEXT, "")
            .apply()
    }

    fun markNewsCompleted(context: Context, result: SearchResult) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEWS_COMPLETED_AT, System.currentTimeMillis())
            .putInt(KEY_NEWS_FOUND, result.foundCount)
            .putInt(KEY_NEWS_NEW, result.newCount)
            .putInt(KEY_NEWS_ERRORS, result.errors)
            .putString(
                KEY_NEWS_ERROR_TEXT,
                if (result.errors > 0) "${result.errors} consulta(s) com falha" else ""
            )
            .apply()
    }

    fun markNewsFailed(context: Context, throwable: Throwable) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NEWS_ERROR_TEXT, throwable.message?.take(180) ?: throwable.javaClass.simpleName)
            .apply()
    }

    fun markDemandAttempt(context: Context) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DEMAND_ATTEMPT_AT, System.currentTimeMillis())
            .putString(KEY_DEMAND_ERROR_TEXT, "")
            .apply()
    }

    fun markDemandCompleted(context: Context, result: DemandSweepResult) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DEMAND_COMPLETED_AT, System.currentTimeMillis())
            .putInt(KEY_DEMAND_CHECKED, result.checkedCount)
            .putInt(KEY_DEMAND_FOUND, result.foundCount)
            .putInt(KEY_DEMAND_NEW, result.newCount)
            .putInt(KEY_DEMAND_ERRORS, result.errors)
            .putString(
                KEY_DEMAND_ERROR_TEXT,
                if (result.errors > 0) "${result.errors} demanda(s) com falha" else ""
            )
            .apply()
    }

    fun markDemandFailed(context: Context, throwable: Throwable) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEMAND_ERROR_TEXT, throwable.message?.take(180) ?: throwable.javaClass.simpleName)
            .apply()
    }
}

class BackgroundHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val cfg = AutoSearchSettings.read(context)
        if (!cfg.newsEnabled && !cfg.demandsEnabled) return
        BackgroundMonitor.enqueueRecoveryIfStale(context)
        BackgroundMonitor.scheduleHeartbeat(context)
    }
}

class MonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        BackgroundMonitor.scheduleAll(context)
        BackgroundMonitor.enqueueRecoveryIfStale(context)
    }
}

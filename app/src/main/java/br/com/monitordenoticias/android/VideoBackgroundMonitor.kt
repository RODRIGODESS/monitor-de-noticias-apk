package br.com.monitordenoticias.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Monitor automático de vídeos.
 *
 * Na v4.2.0 a agenda fixa de 08h/12h/15h/19h/21h é migrada para uma cadência
 * configurável pelo usuário. A busca manual continua independente da automação.
 */
object VideoBackgroundMonitor {
    private const val SCHEDULE_REQUEST_CODE = 2801
    const val ACTION_SCHEDULED_SCAN = "br.com.monitordenoticias.android.VIDEO_SCHEDULED_SCAN"
    private const val LEGACY_HOURLY_ACTION = "br.com.monitordenoticias.android.VIDEO_HEARTBEAT"
    private const val UNIQUE_PERIODIC_WORK = "monitor_videos_interval_v420"

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        AutoSearchSettings.migrate(app)
        val cfg = AutoSearchSettings.read(app)
        val wm = WorkManager.getInstance(app)

        // Remove agendas legadas para evitar varreduras duplicadas após a atualização.
        wm.cancelUniqueWork("monitor_videos_1h")
        wm.cancelUniqueWork("monitor_videos_heartbeat")
        wm.cancelUniqueWork("monitor_videos_scheduled")
        cancelLegacyAlarm(app)

        if (!cfg.videosEnabled) {
            wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            app.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
                .edit().putLong(VideoAutoRunLog.KEY_NEXT_HEARTBEAT_AT, 0L).apply()
            return
        }

        val safe = cfg.videosIntervalMinutes.coerceAtLeast(15)
        val request = PeriodicWorkRequestBuilder<VideoMonitorWorker>(safe.toLong(), TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        // WorkManager é inexato por projeto; este horário é apenas uma estimativa visual.
        app.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(VideoAutoRunLog.KEY_NEXT_HEARTBEAT_AT, System.currentTimeMillis() + safe * 60_000L)
            .apply()
    }

    private fun cancelLegacyAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        listOf(ACTION_SCHEDULED_SCAN, LEGACY_HOURLY_ACTION).forEach { action ->
            val intent = Intent(context, VideoHeartbeatReceiver::class.java).setAction(action)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                SCHEDULE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    /** Compatibilidade para um alarme legado que eventualmente dispare durante a migração. */
    fun enqueueScheduled(context: Context) {
        val app = context.applicationContext
        if (!AutoSearchSettings.read(app).videosEnabled) return
        val request = OneTimeWorkRequestBuilder<VideoMonitorWorker>()
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            "monitor_videos_scheduled",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

object VideoAutoRunLog {
    const val KEY_ATTEMPT_AT = "auto_video_attempt_at"
    const val KEY_COMPLETED_AT = "auto_video_completed_at"
    const val KEY_FOUND = "auto_video_found"
    const val KEY_NEW = "auto_video_new"
    const val KEY_RELEVANT = "auto_video_relevant"
    const val KEY_NEW_RELEVANT = "auto_video_new_relevant"
    const val KEY_ERRORS = "auto_video_errors"
    const val KEY_ERROR_TEXT = "auto_video_error_text"
    const val KEY_NEXT_HEARTBEAT_AT = "auto_video_next_heartbeat_at"

    fun markAttempt(context: Context) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ATTEMPT_AT, System.currentTimeMillis())
            .putString(KEY_ERROR_TEXT, "")
            .apply()
    }

    fun markCompleted(context: Context, result: VideoSearchResult) {
        val cfg = AutoSearchSettings.read(context)
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .putLong(
                KEY_NEXT_HEARTBEAT_AT,
                if (cfg.videosEnabled) System.currentTimeMillis() + cfg.videosIntervalMinutes * 60_000L else 0L
            )
            .putInt(KEY_FOUND, result.foundCount)
            .putInt(KEY_NEW, result.newCount)
            .putInt(KEY_RELEVANT, result.relevantCount)
            .putInt(KEY_NEW_RELEVANT, result.newRelevantCount)
            .putInt(KEY_ERRORS, result.errors)
            .putString(KEY_ERROR_TEXT, if (result.errors > 0) "${result.errors} fonte(s) instável(is) nesta varredura" else "")
            .apply()
    }

    fun markFailed(context: Context, throwable: Throwable) {
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ERROR_TEXT, throwable.message?.take(180) ?: throwable.javaClass.simpleName)
            .apply()
    }
}

class VideoHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(VideoBackgroundMonitor.ACTION_SCHEDULED_SCAN, "br.com.monitordenoticias.android.VIDEO_HEARTBEAT")) return
        VideoBackgroundMonitor.enqueueScheduled(context)
        VideoBackgroundMonitor.scheduleAll(context)
    }
}

class VideoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED
            )
        ) return
        VideoBackgroundMonitor.scheduleAll(context)
    }
}

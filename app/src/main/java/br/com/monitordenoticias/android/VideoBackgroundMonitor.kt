package br.com.monitordenoticias.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar

object VideoBackgroundMonitor {
    private const val SCHEDULE_REQUEST_CODE = 2801
    private val SCHEDULE_HOURS = intArrayOf(8, 12, 15, 19, 21)

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * A partir da v3.0.1, vídeos não usam mais WorkManager periódico de 1h.
     * O próximo horário é calculado no fuso local do aparelho: 08h, 12h,
     * 15h, 19h e 21h. AlarmManager/Doze ainda pode atrasar a execução alguns
     * minutos, mas não dispara varreduras horárias fora dessas janelas.
     */
    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        val workManager = WorkManager.getInstance(app)
        // Cancela agendamentos herdados da v3.0 e anteriores.
        workManager.cancelUniqueWork("monitor_videos_1h")
        workManager.cancelUniqueWork("monitor_videos_heartbeat")
        scheduleNext(app)
    }

    fun scheduleNext(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val nextAt = nextScheduledAt(nowMillis)
        val intent = Intent(app, VideoHeartbeatReceiver::class.java)
            .setAction("br.com.monitordenoticias.android.VIDEO_SCHEDULED_SCAN")
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            SCHEDULE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextAt,
            pendingIntent
        )
        app.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(VideoAutoRunLog.KEY_NEXT_HEARTBEAT_AT, nextAt)
            .apply()
    }

    fun enqueueScheduled(context: Context) {
        val app = context.applicationContext
        val request = OneTimeWorkRequestBuilder<VideoMonitorWorker>()
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            "monitor_videos_scheduled",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Mantido por compatibilidade interna. A recuperação só executa quando forçada. */
    fun enqueueRecoveryIfStale(context: Context, force: Boolean = false) {
        if (force) enqueueScheduled(context)
    }

    fun scheduleLabel(): String = "08h • 12h • 15h • 19h • 21h"

    private fun nextScheduledAt(nowMillis: Long): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        for (hour in SCHEDULE_HOURS) {
            val candidate = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis > nowMillis + 30_000L) return candidate.timeInMillis
        }
        return (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, SCHEDULE_HOURS.first())
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
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
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .putInt(KEY_FOUND, result.foundCount)
            .putInt(KEY_NEW, result.newCount)
            .putInt(KEY_RELEVANT, result.relevantCount)
            .putInt(KEY_NEW_RELEVANT, result.newRelevantCount)
            .putInt(KEY_ERRORS, result.errors)
            .putString(KEY_ERROR_TEXT, if (result.errors > 0) "${result.errors} grupo(s) de fonte com falha" else "")
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
        if (intent?.action != "br.com.monitordenoticias.android.VIDEO_SCHEDULED_SCAN") return
        VideoBackgroundMonitor.enqueueScheduled(context)
        VideoBackgroundMonitor.scheduleNext(context)
    }
}

class VideoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        VideoBackgroundMonitor.scheduleAll(context)
    }
}

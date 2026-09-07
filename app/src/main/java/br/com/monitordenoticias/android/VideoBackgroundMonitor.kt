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

object VideoBackgroundMonitor {
    private const val HEARTBEAT_REQUEST_CODE = 2801
    private const val HEARTBEAT_INTERVAL_MS = 60L * 60L * 1000L
    private const val STALE_AFTER_MS = 55L * 60L * 1000L

    private fun connectedConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        val periodic = PeriodicWorkRequestBuilder<VideoMonitorWorker>(1, TimeUnit.HOURS)
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            "monitor_videos_1h",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
        scheduleHeartbeat(app)
    }

    fun scheduleHeartbeat(context: Context, delayMs: Long = HEARTBEAT_INTERVAL_MS) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, VideoHeartbeatReceiver::class.java)
            .setAction("br.com.monitordenoticias.android.VIDEO_HEARTBEAT")
        val pendingIntent = PendingIntent.getBroadcast(
            app,
            HEARTBEAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val safeDelay = delayMs.coerceAtLeast(60_000L)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + safeDelay,
            pendingIntent
        )
        app.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(VideoAutoRunLog.KEY_NEXT_HEARTBEAT_AT, System.currentTimeMillis() + safeDelay)
            .apply()
    }

    fun enqueueRecoveryIfStale(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
        val lastAttempt = prefs.getLong(VideoAutoRunLog.KEY_ATTEMPT_AT, 0L)
        val now = System.currentTimeMillis()
        if (!force && lastAttempt != 0L && now - lastAttempt < STALE_AFTER_MS) return
        val request = OneTimeWorkRequestBuilder<VideoMonitorWorker>()
            .setConstraints(connectedConstraints())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            "monitor_videos_heartbeat",
            ExistingWorkPolicy.REPLACE,
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
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .putInt(KEY_FOUND, result.foundCount)
            .putInt(KEY_NEW, result.newCount)
            .putInt(KEY_RELEVANT, result.relevantCount)
            .putInt(KEY_NEW_RELEVANT, result.newRelevantCount)
            .putInt(KEY_ERRORS, result.errors)
            .putString(KEY_ERROR_TEXT, if (result.errors > 0) "${result.errors} fonte(s) sem resposta" else "")
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
        VideoBackgroundMonitor.enqueueRecoveryIfStale(context)
        VideoBackgroundMonitor.scheduleHeartbeat(context)
    }
}

class VideoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        VideoBackgroundMonitor.scheduleAll(context)
        VideoBackgroundMonitor.enqueueRecoveryIfStale(context)
    }
}

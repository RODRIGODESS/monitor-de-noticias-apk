package br.com.monitordenoticias.android

import android.content.Context

data class AutoSearchConfig(
    val newsEnabled: Boolean,
    val newsIntervalMinutes: Int,
    val demandsEnabled: Boolean,
    val demandsIntervalMinutes: Int,
    val videosEnabled: Boolean,
    val videosIntervalMinutes: Int
)

/**
 * Preferências da automação v4.2.0.
 *
 * Cada monitor possui liga/desliga e cadência independentes. A migração é feita
 * uma única vez e preserva o intervalo de Notícias que já existia nas versões
 * anteriores. Demandas iniciam em 60 min e Vídeos em 180 min para manter uma
 * cadência conservadora de rede/bateria; o usuário pode alterar tudo em
 * Configurações.
 */
object AutoSearchSettings {
    const val KEY_INITIALIZED = "auto_search_v420_initialized"
    const val KEY_NEWS_ENABLED = "auto_news_enabled_v420"
    const val KEY_NEWS_INTERVAL = "auto_news_interval_v420"
    const val KEY_DEMANDS_ENABLED = "auto_demands_enabled_v420"
    const val KEY_DEMANDS_INTERVAL = "auto_demands_interval_v420"
    const val KEY_VIDEOS_ENABLED = "auto_videos_enabled_v420"
    const val KEY_VIDEOS_INTERVAL = "auto_videos_interval_v420"

    val intervalOptions = listOf(15, 30, 60, 120, 180, 240, 360)

    fun migrate(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return

        val legacyNews = sanitize(prefs.getInt("interval_minutes", 30), 30)
        prefs.edit()
            .putBoolean(KEY_NEWS_ENABLED, true)
            .putInt(KEY_NEWS_INTERVAL, legacyNews)
            .putBoolean(KEY_DEMANDS_ENABLED, true)
            .putInt(KEY_DEMANDS_INTERVAL, 60)
            .putBoolean(KEY_VIDEOS_ENABLED, true)
            .putInt(KEY_VIDEOS_INTERVAL, 180)
            .putInt("interval_minutes", legacyNews)
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    fun read(context: Context): AutoSearchConfig {
        migrate(context)
        val prefs = context.applicationContext.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
        return AutoSearchConfig(
            newsEnabled = prefs.getBoolean(KEY_NEWS_ENABLED, true),
            newsIntervalMinutes = sanitize(prefs.getInt(KEY_NEWS_INTERVAL, 30), 30),
            demandsEnabled = prefs.getBoolean(KEY_DEMANDS_ENABLED, true),
            demandsIntervalMinutes = sanitize(prefs.getInt(KEY_DEMANDS_INTERVAL, 60), 60),
            videosEnabled = prefs.getBoolean(KEY_VIDEOS_ENABLED, true),
            videosIntervalMinutes = sanitize(prefs.getInt(KEY_VIDEOS_INTERVAL, 180), 180)
        )
    }

    fun setNewsEnabled(context: Context, enabled: Boolean) {
        migrate(context)
        prefs(context).edit().putBoolean(KEY_NEWS_ENABLED, enabled).apply()
    }

    fun setNewsInterval(context: Context, minutes: Int) {
        migrate(context)
        val safe = sanitize(minutes, 30)
        prefs(context).edit()
            .putInt(KEY_NEWS_INTERVAL, safe)
            .putInt("interval_minutes", safe)
            .apply()
    }

    fun setDemandsEnabled(context: Context, enabled: Boolean) {
        migrate(context)
        prefs(context).edit().putBoolean(KEY_DEMANDS_ENABLED, enabled).apply()
    }

    fun setDemandsInterval(context: Context, minutes: Int) {
        migrate(context)
        prefs(context).edit().putInt(KEY_DEMANDS_INTERVAL, sanitize(minutes, 60)).apply()
    }

    fun setVideosEnabled(context: Context, enabled: Boolean) {
        migrate(context)
        prefs(context).edit().putBoolean(KEY_VIDEOS_ENABLED, enabled).apply()
    }

    fun setVideosInterval(context: Context, minutes: Int) {
        migrate(context)
        prefs(context).edit().putInt(KEY_VIDEOS_INTERVAL, sanitize(minutes, 180)).apply()
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)

    private fun sanitize(value: Int, fallback: Int): Int {
        if (value < 15) return fallback.coerceAtLeast(15)
        return value.coerceAtMost(24 * 60)
    }
}

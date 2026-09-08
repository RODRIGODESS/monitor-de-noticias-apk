package br.com.monitordenoticias.desktop

import android.content.Context
import br.com.monitordenoticias.android.BackgroundMonitor
import br.com.monitordenoticias.android.SourceCatalog
import br.com.monitordenoticias.android.VideoSourceCatalog
import java.io.File

/**
 * Entry point used only by CI to validate the exact jpackage launcher/runtime.
 * The workflow temporarily points MonitorDeNoticias.cfg to this class, runs the
 * real MonitorDeNoticias.exe, checks for exit code 0, then restores the cfg
 * before creating the distributable ZIP.
 */
fun main() {
    val tempRoot = File(
        System.getProperty("java.io.tmpdir"),
        "monitor-de-noticias-self-test-${System.nanoTime()}"
    ).apply { mkdirs() }

    try {
        val context = Context(tempRoot)
        context.getSharedPreferences(BackgroundMonitor.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("desktop_start_with_windows", false)
            .putBoolean("desktop_automatic_monitoring", false)
            .apply()

        DesktopController(context).use { controller ->
            controller.refresh()
            check(SourceCatalog.all.isNotEmpty()) { "News source catalog is empty" }
            check(VideoSourceCatalog.all.isNotEmpty()) { "Video source catalog is empty" }
            controller.newsDb.listTerms()
            controller.newsDb.listDemands()
            controller.videoDb.listRecent(1, 10)
        }
    } finally {
        tempRoot.deleteRecursively()
    }
}

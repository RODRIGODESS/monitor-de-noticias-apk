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

        // Regressão v4.0.3: o Google Notícias pode publicar "Folha PE" enquanto
        // nosso catálogo usa "Folha de Pernambuco". Essa equivalência precisa
        // continuar válida para buscas com fontes selecionadas e deduplicação.
        val folhaPe = DesktopSourceCatalog.byId["pe-folha-de-pernambuco"]
            ?: error("Folha de Pernambuco não encontrada no catálogo Windows")
        check(DesktopSourceCatalog.publisherMatches("Folha PE", folhaPe)) {
            "Alias Folha PE não corresponde à Folha de Pernambuco"
        }
        check(DesktopSourceCatalog.canonicalName("Folha PE") == "Folha de Pernambuco") {
            "Nome canônico de Folha PE incorreto"
        }

        DesktopController(context).use { controller ->
            controller.refresh()
            check(SourceCatalog.all.isNotEmpty()) { "News source catalog is empty" }
            check(DesktopSourceCatalog.all.isNotEmpty()) { "Desktop news source catalog is empty" }
            check(VideoSourceCatalog.all.isNotEmpty()) { "Video source catalog is empty" }
            controller.newsDb.listTerms()
            controller.newsDb.listDemands()
            controller.videoDb.listRecent(1, 10)
        }
    } finally {
        tempRoot.deleteRecursively()
    }
}

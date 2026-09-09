package br.com.monitordenoticias.desktop

import android.content.Context
import br.com.monitordenoticias.android.BackgroundMonitor
import br.com.monitordenoticias.android.DesktopEstablishedTerms
import br.com.monitordenoticias.android.News
import br.com.monitordenoticias.android.SourceCatalog
import br.com.monitordenoticias.android.VideoItem
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
        check(DesktopSourceCatalog.publisherMatches("folhape.com.br", folhaPe)) {
            "Domínio folhape.com.br não corresponde à Folha de Pernambuco"
        }

        val expectedSpecializedIds = setOf(
            "especializada-defesa-em-foco",
            "especializada-defesa-aerea-naval",
            "especializada-defesanet",
            "especializada-tecnodefesa",
            "especializada-zona-militar",
            "especializada-click-petroleo-gas",
            "especializada-poder-naval",
            "especializada-gbn-news"
        )
        check(DesktopSourceCatalog.specialized.size == 8) {
            "Catálogo especializado Windows deveria conter exatamente 8 fontes"
        }
        check(expectedSpecializedIds.all(DesktopSourceCatalog.byId::containsKey)) {
            "Uma ou mais mídias especializadas esperadas não estão no catálogo Windows"
        }

        DesktopController(context).use { controller ->
            controller.refresh()
            check(SourceCatalog.all.isNotEmpty()) { "News source catalog is empty" }
            check(DesktopSourceCatalog.all.isNotEmpty()) { "Desktop news source catalog is empty" }
            check(VideoSourceCatalog.all.isNotEmpty()) { "Video source catalog is empty" }

            val expectedTerms = DesktopEstablishedTerms.all.toSet()
            check(expectedTerms.isNotEmpty()) { "Established terms list is empty" }
            check(controller.terms.toSet().containsAll(expectedTerms)) {
                "Os termos estabelecidos não foram migrados integralmente para Notícias"
            }
            check(controller.videoTerms.toSet().containsAll(expectedTerms)) {
                "Os termos estabelecidos não foram migrados integralmente para Vídeos"
            }

            // Regressão de histórico/"Nova": a existência é consultada em todo o banco,
            // e limpar histórico precisa atualizar tanto o SQLite quanto o estado Compose
            // imediatamente, sem depender de troca de aba.
            val now = System.currentTimeMillis()
            val newsLink = "https://selftest.invalid/noticia-ja-conhecida"
            val secondNewsLink = "https://selftest.invalid/noticia-historica-2"
            controller.newsDb.insertNews(
                listOf(
                    News(
                        title = "Marinha realiza atividade de teste",
                        source = "Folha de Pernambuco",
                        date = now,
                        link = newsLink,
                        matchedTerm = "MARINHA",
                        capturedAt = now - 60_000L
                    ),
                    News(
                        title = "Fragata participa de atividade de teste",
                        source = "Folha de Pernambuco",
                        date = now - 120_000L,
                        link = secondNewsLink,
                        matchedTerm = "FRAGATA",
                        capturedAt = now - 120_000L
                    )
                )
            )
            check(newsLink in controller.newsDb.listKnownLinks()) {
                "Índice completo de links de notícias não reconheceu item existente"
            }
            check(controller.newsDb.listNews(1).size == 1) {
                "Consulta visual limitada de notícias não respeitou o limite"
            }
            check(controller.newsDb.listAllNews().count { it.link == newsLink || it.link == secondNewsLink } == 2) {
                "Histórico completo da deduplicação não enxergou itens além da janela visual"
            }
            controller.refresh()
            check(controller.newsHistory.any { it.link == newsLink }) {
                "Histórico observável de notícias não recebeu o item de teste"
            }
            controller.clearNewsHistory()
            check(controller.newsHistory.isEmpty()) {
                "Histórico observável de notícias não limpou em tempo real"
            }
            check(newsLink !in controller.newsDb.listKnownLinks() && secondNewsLink !in controller.newsDb.listKnownLinks()) {
                "SQLite de notícias ainda contém item após limpar histórico"
            }

            val videoLink = "https://selftest.invalid/video-ja-conhecido"
            controller.videoDb.insert(
                listOf(
                    VideoItem(
                        title = "Marinha - vídeo de teste",
                        sourceId = "selftest",
                        sourceName = "Fonte de teste",
                        publishedAt = now,
                        link = videoLink,
                        matchedTerm = "MARINHA",
                        capturedAt = now - 60_000L
                    )
                )
            )
            check(videoLink in controller.videoDb.listKnownLinks()) {
                "Índice completo de links de vídeos não reconheceu item existente"
            }
            controller.refresh()
            check(controller.videoHistory.any { it.link == videoLink }) {
                "Histórico observável de vídeos não recebeu o item de teste"
            }
            controller.clearVideoHistory()
            check(controller.videoHistory.isEmpty()) {
                "Histórico observável de vídeos não limpou em tempo real"
            }
            check(videoLink !in controller.videoDb.listKnownLinks()) {
                "SQLite de vídeos ainda contém item após limpar histórico"
            }

            controller.newsDb.listTerms()
            controller.newsDb.listDemands()
            controller.videoDb.listRecent(1, 10)
        }
    } finally {
        tempRoot.deleteRecursively()
    }
}

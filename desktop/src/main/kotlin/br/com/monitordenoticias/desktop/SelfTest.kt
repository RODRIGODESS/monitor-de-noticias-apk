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
            .putBoolean("desktop_proxy_enabled", false)
            .apply()

        // Regressão v4.0.3: o Google Notícias pode publicar "Folha PE" enquanto
        // nosso catálogo usa "Folha de Pernambuco". Essa equivalência precisa
        // continuar válida para buscas com fontes selecionadas e deduplicação.
        val folhaPe = DesktopSourceCatalog.byId["pe-folha-de-pernambuco"]
            ?: error("Folha de Pernambuco não encontrada no catálogo Windows")
        check(DesktopSourceCatalog.publisherMatches("Folha PE", folhaPe)) {
            "Alias Folha PE não corresponde à Folha de Pernambuco"
        }
        check(DesktopSourceCatalog.publisherMatches("FolhaPE", folhaPe)) {
            "Alias FolhaPE não corresponde à Folha de Pernambuco"
        }
        check(DesktopSourceCatalog.canonicalName("Folha PE") == "Folha de Pernambuco") {
            "Nome canônico de Folha PE incorreto"
        }
        check(DesktopSourceCatalog.publisherMatches("folhape.com.br", folhaPe)) {
            "Domínio folhape.com.br não corresponde à Folha de Pernambuco"
        }
        check(DesktopSourceCatalog.publisherMatches("www.folhape.com.br", folhaPe)) {
            "Domínio www.folhape.com.br não corresponde à Folha de Pernambuco"
        }

        val pureTitleKey = DesktopSourceCatalog.canonicalTitleKey(
            "Marinha realiza atividade de teste",
            "Folha de Pernambuco"
        )
        val googleTitleKey = DesktopSourceCatalog.canonicalTitleKey(
            "Marinha realiza atividade de teste - Folha PE",
            "Folha PE"
        )
        check(pureTitleKey == googleTitleKey) {
            "Título do Google com sufixo editorial não foi normalizado para a mesma matéria"
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

            // Regressão de seleção: vazio significa realmente vazio. Não pode haver
            // fallback silencioso para fontes padrão, especialmente em Vídeos.
            controller.clearNewsSources()
            check(!controller.newsAllSources && controller.selectedNewsSourceIds.isEmpty()) {
                "Limpar fontes de notícias não persistiu seleção vazia"
            }
            controller.searchNews()
            check(controller.status.startsWith("⚠")) {
                "Busca de notícias deveria bloquear quando nenhuma fonte está selecionada"
            }
            controller.selectAllNewsSources()
            check(controller.newsAllSources && controller.selectedNewsSourceIds.containsAll(DesktopSourceCatalog.all.map { it.id })) {
                "Selecionar todas as fontes de notícias não restaurou o catálogo Windows"
            }

            controller.clearVideoSources()
            check(controller.selectedVideoSourceIds.isEmpty()) {
                "Limpar fontes de vídeo não persistiu seleção vazia"
            }
            controller.searchVideos()
            check(controller.videoStatus.startsWith("⚠")) {
                "Busca de vídeos deveria bloquear quando nenhuma fonte está selecionada"
            }
            controller.selectAllVideoSources()
            check(controller.selectedVideoSourceIds.containsAll(VideoSourceCatalog.all.map { it.id })) {
                "Selecionar todas as fontes de vídeo não restaurou o catálogo"
            }

            // Regressão dos campos de período usados nas telas Notícias/Vídeos.
            val preset = controller.periodPreset(1)
            val parsedPeriod = controller.parsePeriod(
                preset.startDate,
                preset.startTime,
                preset.endDate,
                preset.endTime
            ) ?: error("Período de 24h gerado pela interface não pôde ser interpretado")
            check(parsedPeriod.second > parsedPeriod.first) {
                "Período interpretado não possui fim posterior ao início"
            }
            check(controller.parsePeriod("31/12/2025", "00:00", "01/01/2026", "23:59") != null) {
                "Parser de período não aceitou datas brasileiras válidas"
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

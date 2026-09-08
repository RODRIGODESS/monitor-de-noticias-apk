from pathlib import Path
import re


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


catalog = "app/src/main/java/br/com/monitordenoticias/android/VideoSourceCatalog.kt"
repo = "app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt"
vm = "app/src/main/java/br/com/monitordenoticias/android/VideoViewModel.kt"
worker = "app/src/main/java/br/com/monitordenoticias/android/VideoMonitorWorker.kt"

# Novos programas nacionais Band/Record com páginas próprias, para varredura uma vez por fonte.
replace_once(
    catalog,
    '''        VideoSource(
            id = "video-band",
            name = "Band Jornalismo",
            group = "Band",
            landingUrl = "https://www.band.com.br/videos",
            linkHints = listOf("/videos/"),
            aliases = listOf("Band", "Band Jornalismo", "BandNews", "Band News"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        )
    )

    /** Canais oficiais no YouTube monitorados como fontes independentes. */''',
    '''        VideoSource(
            id = "video-band",
            name = "Band Jornalismo",
            group = "Band",
            landingUrl = "https://www.band.com.br/videos",
            linkHints = listOf("/videos/"),
            aliases = listOf("Band", "Band Jornalismo", "BandNews", "Band News"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        )
    )

    private val bandProgramsNational = listOf(
        bandProgram("video-band-jornal-da-band", "Jornal da Band", "https://www.band.com.br/programas/jornal-da-band"),
        bandProgram("video-band-brasil-urgente", "Brasil Urgente", "https://www.band.com.br/programas/brasil-urgente"),
        bandProgram("video-band-bora-brasil", "Bora Brasil", "https://www.band.com.br/programas/bora-brasil")
    )

    private val recordProgramsNational = listOf(
        recordProgram("video-record-jornal-da-record", "Jornal da Record", "https://record.r7.com/jornal-da-record/videos/", listOf("JR")),
        recordProgram("video-record-fala-brasil", "Fala Brasil", "https://record.r7.com/fala-brasil/videos/"),
        recordProgram("video-record-domingo-espetacular", "Domingo Espetacular", "https://record.r7.com/domingo-espetacular/exclusivo/videos/"),
        recordProgram("video-record-balanco-geral", "Balanço Geral", "https://record.r7.com/balanco-geral/", listOf("Balanço Geral SP")),
        recordProgram("video-record-cidade-alerta", "Cidade Alerta", "https://record.r7.com/cidade-alerta/videos/")
    )

    /** Canais oficiais no YouTube monitorados como fontes independentes. */'''
)

replace_once(
    catalog,
    '''        nationalGlobo("globoplay-jornal-nacional", "Jornal Nacional"),
        nationalGlobo("globoplay-jornal-da-globo", "Jornal da Globo")
    )''',
    '''        nationalGlobo("globoplay-jornal-nacional", "Jornal Nacional"),
        nationalGlobo("globoplay-jornal-da-globo", "Jornal da Globo"),
        nationalGlobo("globoplay-fantastico", "Fantástico"),
        nationalGlobo("globoplay-globo-reporter", "Globo Repórter"),
        nationalGlobo("globoplay-profissao-reporter", "Profissão Repórter")
    )'''
)

replace_once(
    catalog,
    '''    val globoplayTelejournalsRegional: List<VideoSource> = globoplayRegionalSpecific + globoplayRegionalSweeps
    val national: List<VideoSource> = portalNational + youtubeOfficial + globoplayTelejournalsNational

    private val bandRegional = listOf(''',
    '''    val globoplayTelejournalsRegional: List<VideoSource> = globoplayRegionalSpecific + globoplayRegionalSweeps
    val national: List<VideoSource> = portalNational + bandProgramsNational + recordProgramsNational + youtubeOfficial + globoplayTelejournalsNational

    private val bandRegional = listOf('''
)

replace_once(
    catalog,
    '''        VideoSource(
            id = "video-band-rio", name = "Band Rio", group = "Band Regional", region = "Sudeste", state = "RJ",
            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos", linkHints = listOf("/rio-de-janeiro/videos/"), aliases = listOf("Band Rio", "Band Rio de Janeiro"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        ),''',
    '''        VideoSource(
            id = "video-band-rio", name = "Band Rio • Jornal do Rio", group = "Band Regional • Jornal do Rio", region = "Sudeste", state = "RJ",
            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos", linkHints = listOf("/rio-de-janeiro/videos/"), aliases = listOf("Band Rio", "Band Rio de Janeiro", "Jornal do Rio"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        ),'''
)

replace_once(
    catalog,
    '''    val regional: List<VideoSource> = bandRegional + globoplayTelejournalsRegional
    val all: List<VideoSource> = national + regional''',
    '''    private val recordRegional = listOf(
        recordRegionalProgram(
            "video-record-balanco-geral-rj", "Balanço Geral RJ", "RJ", "Sudeste",
            "https://record.r7.com/balanco-geral-rj/"
        ),
        recordRegionalProgram(
            "video-record-cidade-alerta-rj", "Cidade Alerta RJ", "RJ", "Sudeste",
            "https://record.r7.com/cidade-alerta-rj/videos/"
        )
    )

    val regional: List<VideoSource> = bandRegional + recordRegional + globoplayTelejournalsRegional
    val all: List<VideoSource> = national + regional'''
)

replace_once(
    catalog,
    '''    val globoplayRegionalSweepIds: Set<String> = globoplayRegionalSweeps.map { it.id }.toSet()
    val defaultIds: Set<String> = national.map { it.id }.toSet()

    fun selected(ids: Set<String>): List<VideoSource> = ids.mapNotNull(byId::get)

    private fun youtube''',
    '''    val globoplayRegionalSweepIds: Set<String> = globoplayRegionalSweeps.map { it.id }.toSet()
    val defaultIds: Set<String> = national.map { it.id }.toSet()
    val v3011ImportantSourceIds: Set<String> = setOf(
        "video-band-jornal-da-band", "video-band-brasil-urgente", "video-band-bora-brasil", "video-band-rio",
        "video-record-jornal-da-record", "video-record-fala-brasil", "video-record-domingo-espetacular",
        "video-record-balanco-geral", "video-record-cidade-alerta", "video-record-balanco-geral-rj", "video-record-cidade-alerta-rj",
        "globoplay-fantastico", "globoplay-globo-reporter", "globoplay-profissao-reporter"
    )

    fun selected(ids: Set<String>): List<VideoSource> = ids.mapNotNull(byId::get)

    private fun youtube'''
)

replace_once(
    catalog,
    '''    private fun nationalGlobo(id: String, program: String): VideoSource = VideoSource(''',
    '''    private fun bandProgram(id: String, program: String, landing: String): VideoSource = VideoSource(
        id = id,
        name = "Band • $program",
        group = "Band • Jornalismo nacional",
        landingUrl = landing,
        linkHints = listOf("/videos/"),
        aliases = listOf("Band", "Band Jornalismo", program)
    )

    private fun recordProgram(
        id: String,
        program: String,
        landing: String,
        extraAliases: List<String> = emptyList()
    ): VideoSource = VideoSource(
        id = id,
        name = "Record • $program",
        group = "Record • Jornalismo nacional",
        landingUrl = landing,
        linkHints = listOf("/videos/", "/video/"),
        aliases = (listOf("Record", "Record TV", "R7", program) + extraAliases).distinct()
    )

    private fun recordRegionalProgram(
        id: String,
        program: String,
        state: String,
        region: String,
        landing: String
    ): VideoSource = VideoSource(
        id = id,
        name = "Record • $program",
        group = "Record • Jornalismo regional",
        region = region,
        state = state,
        landingUrl = landing,
        linkHints = listOf("/videos/", "/video/"),
        aliases = listOf("Record", "Record TV", "R7", program)
    )

    private fun nationalGlobo(id: String, program: String): VideoSource = VideoSource('''
)

replace_once(catalog, '        group = "Globo / Globoplay • Telejornal nacional",', '        group = "Globo / Globoplay • Jornalismo nacional",')

# Fontes instáveis: Band/Record passam a varredura única; rotas auxiliares do Globoplay não geram falso alerta.
replace_once(
    repo,
    '''                fun sourceError(stage: String = "HTTP/rede") {
                    sourceRequestFailures++
                    sourceFailureStages[stage] = (sourceFailureStages[stage] ?: 0) + 1
                }''',
    '''                fun sourceError(stage: String = "HTTP/rede") {
                    val optionalGloboplayRoute = isGloboplaySource(source) &&
                        stage in setOf("Globoplay • Edições", "Globoplay • Trechos")
                    if (optionalGloboplayRoute) return
                    sourceRequestFailures++
                    sourceFailureStages[stage] = (sourceFailureStages[stage] ?: 0) + 1
                }'''
)

replace_once(
    repo,
    '''                    var globoplayDeepFallbacks = 0
                    prioritized.asSequence()
                        .take(resolveLimitFor(source))
                        .forEach { raw ->''',
    '''                    var deepFallbacks = 0
                    prioritized.asSequence()
                        .take(resolveLimitFor(source))
                        .forEach { raw ->'''
)

replace_once(
    repo,
    '''                            val globoplay = scanMode && isGloboplaySource(source)
                            val shallowBody = "${raw.title} ${raw.summary}"
                            val shallowTermMatch = globoplay && terms.any { phraseMatches(shallowBody, it) }
                            val shallowDemandMatch = globoplay && demands.any { demand ->
                                sourceMatchesDemand(source, demand.vehicle) &&
                                    phraseMatches(shallowBody, demand.subject)
                            }

                            if (globoplay && !shallowTermMatch && !shallowDemandMatch) {
                                if (globoplayDeepFallbacks >= deepFallbackLimitFor(source)) return@forEach
                                globoplayDeepFallbacks++
                            }''',
    '''                            val globoplay = scanMode && isGloboplaySource(source)
                            val optimizedScan = scanMode && (globoplay || isEditorialPortalScanSource(source))
                            val shallowBody = "${raw.title} ${raw.summary}"
                            val shallowTermMatch = optimizedScan && terms.any { phraseMatches(shallowBody, it) }
                            val shallowDemandMatch = optimizedScan && demands.any { demand ->
                                sourceMatchesDemand(source, demand.vehicle) &&
                                    phraseMatches(shallowBody, demand.subject)
                            }

                            if (optimizedScan && !shallowTermMatch && !shallowDemandMatch) {
                                if (deepFallbacks >= deepFallbackLimitFor(source)) return@forEach
                                deepFallbacks++
                            }'''
)

replace_once(
    repo,
    '''        isGloboplaySource(source) -> MAX_GLOBOPLAY_ITEMS_PER_SCAN
        else -> MAX_RESOLVED_PER_QUERY
    }''',
    '''        isGloboplaySource(source) -> MAX_GLOBOPLAY_ITEMS_PER_SCAN
        isEditorialPortalScanSource(source) -> MAX_EDITORIAL_PORTAL_ITEMS_PER_SCAN
        else -> MAX_RESOLVED_PER_QUERY
    }'''
)

replace_once(
    repo,
    '''        isGloboplaySource(source) -> MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE
        else -> 0
    }''',
    '''        isGloboplaySource(source) -> MAX_GLOBOPLAY_DEEP_FALLBACK_PER_SOURCE
        isEditorialPortalScanSource(source) -> MAX_EDITORIAL_PORTAL_DEEP_FALLBACK
        else -> 0
    }'''
)

replace_once(
    repo,
    '            source.id == "video-r7-record" -> hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")',
    '''            source.id == "video-r7-record" || source.id.startsWith("video-record-") ->
                hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")'''
)

replace_once(
    repo,
    '''    private fun isSourceScanMode(source: VideoSource): Boolean =
        isGloboplaySource(source) || source.youtubeHandle.isNotBlank()

    private fun hasSpecificSuffix''',
    '''    private fun isEditorialPortalScanSource(source: VideoSource): Boolean =
        source.id == "video-r7-record" ||
            source.id.startsWith("video-record-") ||
            source.id.startsWith("video-band")

    private fun isSourceScanMode(source: VideoSource): Boolean =
        isGloboplaySource(source) || source.youtubeHandle.isNotBlank() || isEditorialPortalScanSource(source)

    private fun hasSpecificSuffix'''
)

replace_once(
    repo,
    '''        private const val MAX_YOUTUBE_ITEMS_PER_SCAN = 40
        private const val MAX_ENRICHED_SUMMARY_LENGTH = 1800''',
    '''        private const val MAX_YOUTUBE_ITEMS_PER_SCAN = 40
        private const val MAX_EDITORIAL_PORTAL_ITEMS_PER_SCAN = 20
        private const val MAX_EDITORIAL_PORTAL_DEEP_FALLBACK = 4
        private const val MAX_ENRICHED_SUMMARY_LENGTH = 1800'''
)

replace_once(
    repo,
    '''            "globoplay-jornal-hoje",
            "globoplay-jornal-nacional",
            "globoplay-jornal-da-globo"
        )''',
    '''            "globoplay-jornal-hoje",
            "globoplay-jornal-nacional",
            "globoplay-jornal-da-globo",
            "globoplay-fantastico",
            "globoplay-globo-reporter",
            "globoplay-profissao-reporter"
        )'''
)

replace_once(
    repo,
    '''                    val progressQuery = when {
                        !scanMode -> spec.query
                        source.id in CORE_NATIONAL_GLOBOPLAY_IDS -> "Edições + Trechos + Jarvis • cruzamento local"
                        else -> "Vídeos recentes • cruzamento local"
                    }''',
    '''                    val progressQuery = when {
                        !scanMode -> spec.query
                        source.id in CORE_NATIONAL_GLOBOPLAY_IDS -> "Edições + Trechos + Jarvis • cruzamento local"
                        isEditorialPortalScanSource(source) -> "Programas recentes • cruzamento local"
                        else -> "Vídeos recentes • cruzamento local"
                    }'''
)

# Atualiza identificadores de agente do app usados em HTTP.
p = Path(repo)
p.write_text(p.read_text().replace("MonitorNoticias/3.0.7", "MonitorNoticias/3.0.11"))
jarvis = Path("app/src/main/java/br/com/monitordenoticias/android/GloboplayJarvisCollector.kt")
jarvis.write_text(jarvis.read_text().replace("MonitorNoticias/3.0.10", "MonitorNoticias/3.0.11"))

# Migração: adiciona automaticamente os programas importantes à seleção de quem já usa o app.
replace_once(
    vm,
    '''                .putBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
                .apply()''',
    '''                .putBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
                .putBoolean(KEY_IMPORTANT_PROGRAMS_3011_MIGRATED, true)
                .apply()'''
)

replace_once(
    vm,
    '''        if (!prefs.getBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, false)) {
            selected = selected + VideoSourceCatalog.globoplayRegionalSweepIds
            editor.putBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
            changed = true
        }

        if (changed)''',
    '''        if (!prefs.getBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, false)) {
            selected = selected + VideoSourceCatalog.globoplayRegionalSweepIds
            editor.putBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
            changed = true
        }
        if (!prefs.getBoolean(KEY_IMPORTANT_PROGRAMS_3011_MIGRATED, false)) {
            selected = selected + VideoSourceCatalog.v3011ImportantSourceIds
            editor.putBoolean(KEY_IMPORTANT_PROGRAMS_3011_MIGRATED, true)
            changed = true
        }

        if (changed)'''
)

replace_once(
    vm,
    '        const val KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED = "video_v285_globoplay_regional_sweeps_added"\n        const val KEY_PERIOD_START_DATE',
    '        const val KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED = "video_v285_globoplay_regional_sweeps_added"\n        const val KEY_IMPORTANT_PROGRAMS_3011_MIGRATED = "video_v3011_important_programs_added"\n        const val KEY_PERIOD_START_DATE'
)

replace_once(
    worker,
    '''                        .putBoolean(VideoViewModel.KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
                        .apply()''',
    '''                        .putBoolean(VideoViewModel.KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
                        .putBoolean(VideoViewModel.KEY_IMPORTANT_PROGRAMS_3011_MIGRATED, true)
                        .apply()'''
)

replace_once(
    worker,
    '''                if (!prefs.getBoolean(VideoViewModel.KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, false)) {
                    selected = selected + VideoSourceCatalog.globoplayRegionalSweepIds
                    editor.putBoolean(VideoViewModel.KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
                    changed = true
                }

                if (changed)''',
    '''                if (!prefs.getBoolean(VideoViewModel.KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, false)) {
                    selected = selected + VideoSourceCatalog.globoplayRegionalSweepIds
                    editor.putBoolean(VideoViewModel.KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)
                    changed = true
                }
                if (!prefs.getBoolean(VideoViewModel.KEY_IMPORTANT_PROGRAMS_3011_MIGRATED, false)) {
                    selected = selected + VideoSourceCatalog.v3011ImportantSourceIds
                    editor.putBoolean(VideoViewModel.KEY_IMPORTANT_PROGRAMS_3011_MIGRATED, true)
                    changed = true
                }

                if (changed)'''
)

# Versionamento e release.
build = Path("app/build.gradle.kts")
text = build.read_text()
if 'versionCode = 310' not in text or 'versionName = "3.0.10"' not in text:
    raise SystemExit("unexpected current Android version")
text = text.replace('versionCode = 310', 'versionCode = 311', 1)
text = text.replace('versionName = "3.0.10"', 'versionName = "3.0.11"', 1)
build.write_text(text)

release = Path(".github/workflows/release.yml")
text = release.read_text().replace("v3.0.10", "v3.0.11").replace("3.0.10", "3.0.11")
body = '''          body: |
            Monitor de Notícias Android v3.0.11 — expansão de programas jornalísticos e redução de falsos alertas de fontes instáveis.

            Band:
            - Jornal da Band, Brasil Urgente e Bora Brasil entram como fontes próprias;
            - Band Rio passa a identificar explicitamente o Jornal do Rio;
            - fontes Band passam a varrer a página recente uma vez e cruzar Termos/Demandas localmente, sem repetir a busca por cada termo.

            Record / R7:
            - Jornal da Record, Fala Brasil, Domingo Espetacular, Balanço Geral e Cidade Alerta entram como fontes próprias;
            - Balanço Geral RJ e Cidade Alerta RJ entram entre as fontes regionais verificadas;
            - Record também usa varredura única por programa com cruzamento local.

            Globo / Globoplay:
            - Fantástico, Globo Repórter e Profissão Repórter entram no núcleo nacional de Edições, Trechos e Jarvis;
            - Jornal Hoje e Jornal Nacional mantêm o fluxo validado na v3.0.10;
            - falhas de rotas auxiliares Edições/Trechos deixam de classificar sozinhas uma fonte como instável; o alerta fica reservado à falha real da rota final/fallback.

            Desempenho e diagnóstico:
            - Band/Record deixam o modelo fonte x termo nas fontes com listagem recente confiável;
            - candidatos superficiais sem correspondência recebem apenas um pequeno orçamento de enriquecimento profundo;
            - mantém links diretos, resultados progressivos, cronômetro, histórico e diagnóstico de fontes instáveis.

            Atualização:
            - versionCode 311 / versionName 3.0.11;
            - mesmo applicationId br.com.monitordenoticias.android;
            - mesma assinatura permanente;
            - instalação direta sobre v3.0.10 preservando Termos, Demandas, histórico e configurações.
'''
text, count = re.subn(r'          body: \|\n.*?(?=          files: monitor-de-noticias-v3\.0\.11\.apk)', body, text, flags=re.S)
if count != 1:
    raise SystemExit(f"release body replacement count={count}")
release.write_text(text)

print("v3.0.11 patch applied")

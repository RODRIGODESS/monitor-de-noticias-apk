from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


catalog = Path("app/src/main/java/br/com/monitordenoticias/android/VideoSourceCatalog.kt")
repo = Path("app/src/main/java/br/com/monitordenoticias/android/VideoRepository.kt")
vm = Path("app/src/main/java/br/com/monitordenoticias/android/VideoViewModel.kt")

# 1) Program-specific portal sources. They use one recent-program sweep and local matching.
marker = '''    /** Canais oficiais no YouTube monitorados como fontes independentes. */\n'''
insert = '''    /** Programas nacionais com página própria de vídeos e varredura única por fonte. */
    val portalProgramsNational = listOf(
        VideoSource(
            id = "video-band-jornal-da-band",
            name = "Jornal da Band",
            group = "Band • Jornal da Band",
            landingUrl = "https://www.band.com.br/noticias/jornal-da-band/videos",
            linkHints = listOf("/noticias/jornal-da-band/videos/"),
            aliases = listOf("Band", "Jornal da Band", "JDB"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}",
            searchPrefix = "Jornal da Band"
        ),
        VideoSource(
            id = "video-band-brasil-urgente",
            name = "Brasil Urgente",
            group = "Band • Brasil Urgente",
            landingUrl = "https://www.band.com.br/noticias/brasil-urgente/videos",
            linkHints = listOf("/noticias/brasil-urgente/videos/"),
            aliases = listOf("Band", "Brasil Urgente", "BU"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}",
            searchPrefix = "Brasil Urgente"
        ),
        VideoSource(
            id = "video-r7-jornal-da-record",
            name = "Jornal da Record",
            group = "Record • Jornal da Record",
            landingUrl = "https://noticias.r7.com/jr-na-tv/videos/",
            linkHints = listOf("/jr-na-tv/videos/"),
            aliases = listOf("Record", "Record TV", "Jornal da Record", "JR", "JR na TV"),
            searchUrlTemplate = "https://noticias.r7.com/busca?q={query}",
            searchPrefix = "Jornal da Record"
        ),
        VideoSource(
            id = "video-r7-domingo-espetacular",
            name = "Domingo Espetacular",
            group = "Record • Domingo Espetacular",
            landingUrl = "https://record.r7.com/domingo-espetacular/videos/",
            linkHints = listOf("/domingo-espetacular/videos/"),
            aliases = listOf("Record", "Record TV", "Domingo Espetacular"),
            searchUrlTemplate = "https://www.r7.com/busca?q={query}",
            searchPrefix = "Domingo Espetacular"
        ),
        VideoSource(
            id = "video-r7-balanco-geral-sp",
            name = "Balanço Geral SP",
            group = "Record • Balanço Geral",
            region = "Sudeste",
            state = "SP",
            landingUrl = "https://record.r7.com/balanco-geral-sp/videos/",
            linkHints = listOf("/balanco-geral-sp/videos/"),
            aliases = listOf("Record", "Record TV", "Balanço Geral", "Balanço Geral SP", "BG SP"),
            searchUrlTemplate = "https://www.r7.com/busca?q={query}",
            searchPrefix = "Balanço Geral SP"
        )
    )

    /** Programas regionais relevantes com página própria de vídeos. */
    val portalProgramsRegional = listOf(
        VideoSource(
            id = "video-band-jornal-do-rio",
            name = "Jornal do Rio",
            group = "Band Regional • Jornal do Rio",
            region = "Sudeste",
            state = "RJ",
            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos",
            linkHints = listOf("/rio-de-janeiro/videos/"),
            aliases = listOf("Band", "Band Rio", "Jornal do Rio"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}",
            searchPrefix = "Jornal do Rio"
        ),
        VideoSource(
            id = "video-r7-balanco-geral-rj",
            name = "Balanço Geral RJ",
            group = "Record • Balanço Geral",
            region = "Sudeste",
            state = "RJ",
            landingUrl = "https://record.r7.com/balanco-geral-rj/videos/",
            linkHints = listOf("/balanco-geral-rj/videos/"),
            aliases = listOf("Record", "Record TV", "Balanço Geral", "Balanço Geral RJ", "BG RJ"),
            searchUrlTemplate = "https://www.r7.com/busca?q={query}",
            searchPrefix = "Balanço Geral RJ"
        )
    )

''' + marker
replace_once(catalog, marker, insert)

replace_once(
    catalog,
    '''        nationalGlobo("globoplay-jornal-da-globo", "Jornal da Globo")\n''',
    '''        nationalGlobo("globoplay-jornal-da-globo", "Jornal da Globo"),\n        nationalGlobo("globoplay-fantastico", "Fantástico")\n'''
)

replace_once(
    catalog,
    '''    val national: List<VideoSource> = portalNational + youtubeOfficial + globoplayTelejournalsNational\n''',
    '''    val national: List<VideoSource> = portalNational + portalProgramsNational + youtubeOfficial + globoplayTelejournalsNational\n'''
)
replace_once(
    catalog,
    '''    val regional: List<VideoSource> = bandRegional + globoplayTelejournalsRegional\n''',
    '''    val regional: List<VideoSource> = bandRegional + portalProgramsRegional + globoplayTelejournalsRegional\n'''
)
replace_once(
    catalog,
    '''    val globoplayRegionalSweepIds: Set<String> = globoplayRegionalSweeps.map { it.id }.toSet()\n    val defaultIds: Set<String> = national.map { it.id }.toSet()\n''',
    '''    val globoplayRegionalSweepIds: Set<String> = globoplayRegionalSweeps.map { it.id }.toSet()\n    val portalProgramScanIds: Set<String> = (portalProgramsNational + portalProgramsRegional + bandRegional).map { it.id }.toSet() +\n        setOf("video-r7-record", "video-sbt-news", "video-band")\n    val v3011StarterIds: Set<String> = setOf(\n        "video-band-jornal-da-band",\n        "video-band-brasil-urgente",\n        "video-r7-jornal-da-record",\n        "video-r7-domingo-espetacular",\n        "video-r7-balanco-geral-sp",\n        "globoplay-fantastico"\n    )\n    val defaultIds: Set<String> = national.map { it.id }.toSet()\n'''
)

# 2) Portal program sources join source-scan mode, with one landing request and one fallback search.
replace_once(
    repo,
    '''            // Globoplay e YouTube fazem UMA coleta por fonte/canal. Termos e Demandas\n            // são cruzados localmente depois. As demais fontes mantêm busca por termo\n            // porque alguns portais ainda não oferecem uma listagem recente confiável.\n''',
    '''            // Globoplay, YouTube e páginas estáveis de programas fazem UMA coleta por\n            // fonte/canal. Termos e Demandas são cruzados localmente depois. Portais sem\n            // uma listagem recente confiável preservam a busca tradicional por termo.\n'''
)

replace_once(
    repo,
    '''        return runCatching { fetchWebsite(source, capturedAt) }\n            .onFailure { onError("Portal • página") }\n            .getOrDefault(emptyList())\n            .distinctBy { canonicalKey(it.link) }\n''',
    '''        if (source.id in VideoSourceCatalog.portalProgramScanIds) {\n            val landing = runCatching { fetchWebsite(source, capturedAt) }\n                .onFailure { onError("Portal • página do programa") }\n                .getOrDefault(emptyList())\n                .distinctBy { canonicalKey(it.link) }\n            if (landing.isNotEmpty()) return landing\n\n            if (source.searchUrlTemplate.isNotBlank()) {\n                return runCatching { fetchSearchWebsite(source, "", capturedAt) }\n                    .onFailure { onError("Portal • busca fallback") }\n                    .getOrDefault(emptyList())\n                    .distinctBy { canonicalKey(it.link) }\n            }\n            return emptyList()\n        }\n\n        return runCatching { fetchWebsite(source, capturedAt) }\n            .onFailure { onError("Portal • página") }\n            .getOrDefault(emptyList())\n            .distinctBy { canonicalKey(it.link) }\n'''
)

replace_once(
    repo,
    '''        isGloboplaySource(source) -> MAX_GLOBOPLAY_ITEMS_PER_SCAN\n        else -> MAX_RESOLVED_PER_QUERY\n''',
    '''        isGloboplaySource(source) -> MAX_GLOBOPLAY_ITEMS_PER_SCAN\n        source.id in VideoSourceCatalog.portalProgramScanIds -> MAX_PORTAL_PROGRAM_ITEMS_PER_SCAN\n        else -> MAX_RESOLVED_PER_QUERY\n'''
)

replace_once(
    repo,
    '''    private fun isSourceScanMode(source: VideoSource): Boolean =\n        isGloboplaySource(source) || source.youtubeHandle.isNotBlank()\n''',
    '''    private fun isSourceScanMode(source: VideoSource): Boolean =\n        isGloboplaySource(source) || source.youtubeHandle.isNotBlank() ||\n            source.id in VideoSourceCatalog.portalProgramScanIds\n'''
)

replace_once(
    repo,
    '''        private const val MAX_YOUTUBE_ITEMS_PER_SCAN = 40\n        private const val MAX_ENRICHED_SUMMARY_LENGTH = 1800\n''',
    '''        private const val MAX_YOUTUBE_ITEMS_PER_SCAN = 40\n        private const val MAX_PORTAL_PROGRAM_ITEMS_PER_SCAN = 40\n        private const val MAX_ENRICHED_SUMMARY_LENGTH = 1800\n'''
)

replace_once(
    repo,
    '''            "globoplay-jornal-nacional",\n            "globoplay-jornal-da-globo"\n''',
    '''            "globoplay-jornal-nacional",\n            "globoplay-jornal-da-globo",\n            "globoplay-fantastico"\n'''
)

# R7 program pages use record.r7.com and noticias.r7.com but the direct-video path is source-specific.
replace_once(
    repo,
    '''            source.id == "video-r7-record" -> hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")\n''',
    '''            source.id == "video-r7-record" -> hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")\n            source.id.startsWith("video-r7-") -> source.linkHints.any { hint -> hasSpecificSuffix(path, hint) }\n'''
)

# 3) Existing installs append only the new national starter sources once; no existing choice is removed.
replace_once(
    vm,
    '''        if (!prefs.getBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, false)) {\n            selected = selected + VideoSourceCatalog.globoplayRegionalSweepIds\n            editor.putBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)\n            changed = true\n        }\n\n        if (changed) editor.putStringSet(KEY_SELECTED_SOURCES, selected).apply()\n''',
    '''        if (!prefs.getBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, false)) {\n            selected = selected + VideoSourceCatalog.globoplayRegionalSweepIds\n            editor.putBoolean(KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED, true)\n            changed = true\n        }\n        if (!prefs.getBoolean(KEY_PROGRAM_SOURCES_3011_MIGRATED, false)) {\n            selected = selected + VideoSourceCatalog.v3011StarterIds.filter { VideoSourceCatalog.byId.containsKey(it) }\n            editor.putBoolean(KEY_PROGRAM_SOURCES_3011_MIGRATED, true)\n            changed = true\n        }\n\n        if (changed) editor.putStringSet(KEY_SELECTED_SOURCES, selected).apply()\n'''
)
replace_once(
    vm,
    '''        const val KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED = "video_v285_globoplay_regional_sweeps_added"\n''',
    '''        const val KEY_GLOBOPLAY_REGIONAL_SWEEPS_285_MIGRATED = "video_v285_globoplay_regional_sweeps_added"\n        const val KEY_PROGRAM_SOURCES_3011_MIGRATED = "video_v3011_program_sources_added"\n'''
)

print("v3.0.11 source patch applied")

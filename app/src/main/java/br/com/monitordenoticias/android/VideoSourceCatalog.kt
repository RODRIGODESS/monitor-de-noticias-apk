package br.com.monitordenoticias.android

object VideoSourceCatalog {
    private val portalNational = listOf(
        VideoSource(
            id = "video-globoplay-jornalismo",
            name = "Globoplay Jornalismo",
            group = "Globo / Globoplay",
            landingUrl = "https://globoplay.globo.com/categorias/jornalismo/",
            linkHints = listOf("/v/"),
            aliases = listOf("Globo", "Globoplay", "GloboNews", "Globo News"),
            searchUrlTemplate = "https://globoplay.globo.com/busca/?q={query}"
        ),
        VideoSource(
            id = "video-r7-record",
            name = "R7 / Record",
            group = "Record",
            landingUrl = "https://noticias.r7.com/videos/",
            linkHints = listOf("/videos/"),
            aliases = listOf("R7", "Record", "Record TV", "Record News"),
            searchUrlTemplate = "https://noticias.r7.com/busca?q={query}"
        ),
        VideoSource(
            id = "video-cnn-brasil",
            name = "CNN Brasil",
            group = "CNN",
            landingUrl = "https://www.cnnbrasil.com.br/ao-vivo/",
            linkHints = listOf("/ao-vivo/", "/videos/"),
            aliases = listOf("CNN", "CNN Brasil"),
            searchUrlTemplate = "https://www.cnnbrasil.com.br/?s={query}"
        ),
        VideoSource(
            id = "video-sbt-news",
            name = "SBT News",
            group = "SBT",
            landingUrl = "https://sbtnews.sbt.com.br/videos",
            linkHints = listOf("/videos/"),
            aliases = listOf("SBT", "SBT News"),
            searchUrlTemplate = "https://sbtnews.sbt.com.br/busca?q={query}"
        ),
        VideoSource(
            id = "video-band",
            name = "Band Jornalismo",
            group = "Band",
            landingUrl = "https://www.band.com.br/videos",
            linkHints = listOf("/videos/"),
            aliases = listOf("Band", "Band Jornalismo", "BandNews", "Band News"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        )
    )

    /**
     * Canais oficiais no YouTube monitorados como fontes independentes.
     *
     * Eles ficam separados dos portais para que o card indique claramente que o
     * resultado veio do YouTube e para que o link salvo seja sempre o watch?v=...
     * do vídeo específico. A coleta usa o feed oficial do próprio canal e cruza
     * os vídeos recentes com os Termos e Demandas cadastrados no app.
     */
    val youtubeOfficial = listOf(
        VideoSource(
            id = "youtube-cnn-brasil",
            name = "YouTube • CNN Brasil",
            group = "YouTube oficial • CNN Brasil",
            landingUrl = "https://www.youtube.com/@CNNBrasil/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("CNN", "CNN Brasil"),
            youtubeHandle = "@CNNBrasil"
        ),
        VideoSource(
            id = "youtube-jovem-pan-news",
            name = "YouTube • Jovem Pan News",
            group = "YouTube oficial • Jovem Pan News",
            landingUrl = "https://www.youtube.com/@jovempannews/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("Jovem Pan", "Jovem Pan News", "JP News"),
            youtubeHandle = "@jovempannews"
        ),
        VideoSource(
            id = "youtube-globonews",
            name = "YouTube • GloboNews",
            group = "YouTube oficial • GloboNews",
            landingUrl = "https://www.youtube.com/@globonews/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("GloboNews", "Globo News", "Globo"),
            youtubeHandle = "@globonews"
        ),
        VideoSource(
            id = "youtube-record-news",
            name = "YouTube • Record News",
            group = "YouTube oficial • Record News",
            landingUrl = "https://www.youtube.com/@recordnews/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("Record News", "RecordNews"),
            youtubeHandle = "@recordnews"
        ),
        VideoSource(
            id = "youtube-jornal-da-record",
            name = "YouTube • Jornal da Record",
            group = "YouTube oficial • Jornal da Record",
            landingUrl = "https://www.youtube.com/@JornaldaRecord/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("Jornal da Record", "JR", "Record TV"),
            youtubeHandle = "@JornaldaRecord"
        ),
        VideoSource(
            id = "youtube-band-jornalismo",
            name = "YouTube • Band Jornalismo",
            group = "YouTube oficial • Band / BandNews",
            landingUrl = "https://www.youtube.com/@bandjornalismo/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("Band", "Band Jornalismo", "BandNews", "Band News", "BandNews TV"),
            youtubeHandle = "@bandjornalismo"
        ),
        VideoSource(
            id = "youtube-sbt-news",
            name = "YouTube • SBT News",
            group = "YouTube oficial • SBT News",
            landingUrl = "https://www.youtube.com/@sbtnews/videos",
            linkHints = listOf("/watch"),
            aliases = listOf("SBT", "SBT News", "SBT Jornalismo"),
            youtubeHandle = "@sbtnews"
        )
    )

    val national: List<VideoSource> = portalNational + youtubeOfficial

    val regional = listOf(
        VideoSource(
            id = "video-band-brasilia",
            name = "Band Brasília",
            group = "Band Regional",
            region = "Centro-Oeste",
            state = "DF",
            landingUrl = "https://www.band.com.br/band-brasilia/videos",
            linkHints = listOf("/band-brasilia/videos/"),
            aliases = listOf("Band Brasília", "Band DF"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        ),
        VideoSource(
            id = "video-band-minas",
            name = "Band Minas",
            group = "Band Regional",
            region = "Sudeste",
            state = "MG",
            landingUrl = "https://www.band.com.br/band-minas",
            linkHints = listOf("/band-minas/videos/", "/videos/"),
            aliases = listOf("Band Minas", "Band Minas Gerais"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        ),
        VideoSource(
            id = "video-band-rio",
            name = "Band Rio",
            group = "Band Regional",
            region = "Sudeste",
            state = "RJ",
            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos",
            linkHints = listOf("/rio-de-janeiro/videos/"),
            aliases = listOf("Band Rio", "Band Rio de Janeiro"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        ),
        VideoSource(
            id = "video-band-parana",
            name = "Band Paraná",
            group = "Band Regional",
            region = "Sul",
            state = "PR",
            landingUrl = "https://www.band.com.br/band-parana/videos",
            linkHints = listOf("/band-parana/videos/"),
            aliases = listOf("Band Paraná", "Band PR"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        ),
        VideoSource(
            id = "video-band-bahia",
            name = "Band Bahia",
            group = "Band Regional",
            region = "Nordeste",
            state = "BA",
            landingUrl = "https://www.band.com.br/band-bahia",
            linkHints = listOf("/band-bahia/videos/", "/videos/"),
            aliases = listOf("Band Bahia", "Band BA"),
            searchUrlTemplate = "https://www.band.com.br/busca?q={query}"
        )
    )

    val all: List<VideoSource> = national + regional
    val byId: Map<String, VideoSource> = all.associateBy { it.id }
    val youtubeOfficialIds: Set<String> = youtubeOfficial.map { it.id }.toSet()
    val defaultIds: Set<String> = national.map { it.id }.toSet()

    fun selected(ids: Set<String>): List<VideoSource> = ids.mapNotNull(byId::get)
}

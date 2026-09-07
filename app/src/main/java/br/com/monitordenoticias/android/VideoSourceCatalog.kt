package br.com.monitordenoticias.android

object VideoSourceCatalog {
    val national = listOf(
        VideoSource(
            id = "video-globoplay-jornalismo",
            name = "Globoplay Jornalismo",
            group = "Globo / Globoplay",
            landingUrl = "https://globoplay.globo.com/categorias/jornalismo/",
            linkHints = listOf("/v/"),
            aliases = listOf("Globo", "Globoplay", "GloboNews", "Globo News")
        ),
        VideoSource(
            id = "video-r7-record",
            name = "R7 / Record",
            group = "Record",
            landingUrl = "https://noticias.r7.com/videos/",
            linkHints = listOf("/videos/"),
            aliases = listOf("R7", "Record", "Record TV", "Record News"),
            youtubeHandle = "@recordnews"
        ),
        VideoSource(
            id = "video-cnn-brasil",
            name = "CNN Brasil",
            group = "CNN",
            landingUrl = "https://www.cnnbrasil.com.br/ao-vivo/",
            linkHints = listOf("/ao-vivo/", "/videos/"),
            aliases = listOf("CNN", "CNN Brasil"),
            youtubeHandle = "@CNNBrasil"
        ),
        VideoSource(
            id = "video-sbt-news",
            name = "SBT News",
            group = "SBT",
            landingUrl = "https://sbtnews.sbt.com.br/videos",
            linkHints = listOf("/videos/"),
            aliases = listOf("SBT", "SBT News"),
            youtubeHandle = "@sbtnews"
        ),
        VideoSource(
            id = "video-band",
            name = "Band Jornalismo",
            group = "Band",
            landingUrl = "https://www.band.com.br/videos",
            linkHints = listOf("/videos/"),
            aliases = listOf("Band", "Band Jornalismo", "BandNews", "Band News"),
            youtubeHandle = "@bandjornalismo"
        )
    )

    val regional = listOf(
        VideoSource(
            id = "video-band-brasilia",
            name = "Band Brasília",
            group = "Band Regional",
            region = "Centro-Oeste",
            state = "DF",
            landingUrl = "https://www.band.com.br/band-brasilia/videos",
            linkHints = listOf("/band-brasilia/videos/"),
            aliases = listOf("Band Brasília", "Band DF")
        ),
        VideoSource(
            id = "video-band-minas",
            name = "Band Minas",
            group = "Band Regional",
            region = "Sudeste",
            state = "MG",
            landingUrl = "https://www.band.com.br/band-minas",
            linkHints = listOf("/band-minas/videos/", "/videos/"),
            aliases = listOf("Band Minas", "Band Minas Gerais")
        ),
        VideoSource(
            id = "video-band-rio",
            name = "Band Rio",
            group = "Band Regional",
            region = "Sudeste",
            state = "RJ",
            landingUrl = "https://www.band.com.br/rio-de-janeiro/videos",
            linkHints = listOf("/rio-de-janeiro/videos/"),
            aliases = listOf("Band Rio", "Band Rio de Janeiro")
        ),
        VideoSource(
            id = "video-band-parana",
            name = "Band Paraná",
            group = "Band Regional",
            region = "Sul",
            state = "PR",
            landingUrl = "https://www.band.com.br/band-parana/videos",
            linkHints = listOf("/band-parana/videos/"),
            aliases = listOf("Band Paraná", "Band PR")
        ),
        VideoSource(
            id = "video-band-bahia",
            name = "Band Bahia",
            group = "Band Regional",
            region = "Nordeste",
            state = "BA",
            landingUrl = "https://www.band.com.br/band-bahia",
            linkHints = listOf("/band-bahia/videos/", "/videos/"),
            aliases = listOf("Band Bahia", "Band BA")
        )
    )

    val all: List<VideoSource> = national + regional
    val byId: Map<String, VideoSource> = all.associateBy { it.id }
    val defaultIds: Set<String> = national.map { it.id }.toSet()

    fun selected(ids: Set<String>): List<VideoSource> = ids.mapNotNull(byId::get)
}

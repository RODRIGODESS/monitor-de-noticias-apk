package br.com.monitordenoticias.android

object VideoSourceCatalog {
    private val portalNational = listOf(
        VideoSource(
            id = "video-globoplay-jornalismo",
            name = "Globoplay Jornalismo (geral)",
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

    /**
     * Telejornais nacionais da Globo. Cada item é tratado como uma fonte própria.
     * A landingUrl aponta para a busca do próprio programa no Globoplay, permitindo
     * varrer os vídeos/edições daquele telejornal e cruzar localmente com os Termos.
     */
    val globoplayTelejournalsNational = listOf(
        globoplayTelejournal(
            id = "globoplay-bom-dia-brasil",
            name = "Globoplay • Bom Dia Brasil",
            program = "Bom Dia Brasil",
            landingUrl = "https://globoplay.globo.com/busca/?q=Bom%20Dia%20Brasil"
        ),
        globoplayTelejournal(
            id = "globoplay-hora-1",
            name = "Globoplay • Hora 1",
            program = "Hora 1",
            landingUrl = "https://globoplay.globo.com/busca/?q=Hora%201"
        ),
        globoplayTelejournal(
            id = "globoplay-jornal-hoje",
            name = "Globoplay • Jornal Hoje",
            program = "Jornal Hoje",
            landingUrl = "https://globoplay.globo.com/busca/?q=Jornal%20Hoje"
        ),
        globoplayTelejournal(
            id = "globoplay-jornal-nacional",
            name = "Globoplay • Jornal Nacional",
            program = "Jornal Nacional",
            landingUrl = "https://globoplay.globo.com/busca/?q=Jornal%20Nacional"
        ),
        globoplayTelejournal(
            id = "globoplay-jornal-da-globo",
            name = "Globoplay • Jornal da Globo",
            program = "Jornal da Globo",
            landingUrl = "https://globoplay.globo.com/busca/?q=Jornal%20da%20Globo"
        )
    )

    /**
     * Telejornais locais/afiliadas com páginas de programa ou trechos no Globoplay.
     * Muitas páginas de um trecho antigo continuam expondo a seção "Trechos" atual
     * do programa, por isso funcionam como ponto de entrada para a varredura recente.
     */
    val globoplayTelejournalsRegional = listOf(
        globoplayTelejournal(
            id = "globoplay-bom-dia-sp",
            name = "Globoplay • Bom Dia SP",
            program = "Bom Dia SP",
            landingUrl = "https://globoplay.globo.com/v/5701776/",
            region = "Sudeste",
            state = "SP",
            extraAliases = listOf("Bom Dia São Paulo", "BDSP")
        ),
        globoplayTelejournal(
            id = "globoplay-sp1",
            name = "Globoplay • SP1",
            program = "SP1",
            landingUrl = "https://globoplay.globo.com/v/12096579/",
            region = "Sudeste",
            state = "SP",
            extraAliases = listOf("SPTV 1ª Edição", "SP Primeira Edição")
        ),
        globoplayTelejournal(
            id = "globoplay-sp2",
            name = "Globoplay • SP2",
            program = "SP2",
            landingUrl = "https://globoplay.globo.com/v/5854721/",
            region = "Sudeste",
            state = "SP",
            extraAliases = listOf("SPTV 2ª Edição", "SP Segunda Edição")
        ),
        globoplayTelejournal(
            id = "globoplay-bom-dia-rio",
            name = "Globoplay • Bom Dia Rio",
            program = "Bom Dia Rio",
            landingUrl = "https://globoplay.globo.com/v/8383434/",
            region = "Sudeste",
            state = "RJ"
        ),
        globoplayTelejournal(
            id = "globoplay-rj1",
            name = "Globoplay • RJ1",
            program = "RJ1",
            landingUrl = "https://globoplay.globo.com/v/5976232/",
            region = "Sudeste",
            state = "RJ",
            extraAliases = listOf("RJTV 1ª Edição", "RJ Primeira Edição")
        ),
        globoplayTelejournal(
            id = "globoplay-rj2",
            name = "Globoplay • RJ2",
            program = "RJ2",
            landingUrl = "https://globoplay.globo.com/v/12954402/",
            region = "Sudeste",
            state = "RJ",
            extraAliases = listOf("RJTV 2ª Edição", "RJ Segunda Edição")
        ),
        globoplayTelejournal(
            id = "globoplay-bom-dia-es",
            name = "Globoplay • Bom Dia ES",
            program = "Bom Dia ES",
            landingUrl = "https://globoplay.globo.com/v/11645540/",
            region = "Sudeste",
            state = "ES"
        ),
        globoplayTelejournal(
            id = "globoplay-gazeta-meio-dia-es",
            name = "Globoplay • Gazeta Meio Dia / ESTV1",
            program = "Gazeta Meio Dia",
            landingUrl = "https://globoplay.globo.com/v/5423638/",
            region = "Sudeste",
            state = "ES",
            extraAliases = listOf("ESTV 1ª Edição", "ESTV1", "ES1")
        ),
        globoplayTelejournal(
            id = "globoplay-bom-dia-minas",
            name = "Globoplay • Bom Dia Minas",
            program = "Bom Dia Minas",
            landingUrl = "https://globoplay.globo.com/v/5535965/",
            region = "Sudeste",
            state = "MG"
        ),
        globoplayTelejournal(
            id = "globoplay-mg1",
            name = "Globoplay • MG1",
            program = "MG1",
            landingUrl = "https://globoplay.globo.com/v/12552834/",
            region = "Sudeste",
            state = "MG",
            extraAliases = listOf("MGTV 1ª Edição", "MG Primeira Edição")
        ),
        globoplayTelejournal(
            id = "globoplay-df1",
            name = "Globoplay • DF1",
            program = "DF1",
            landingUrl = "https://globoplay.globo.com/v/14711882/",
            region = "Centro-Oeste",
            state = "DF",
            extraAliases = listOf("DF 1", "DFTV 1ª Edição")
        ),
        globoplayTelejournal(
            id = "globoplay-bom-dia-rio-grande",
            name = "Globoplay • Bom Dia Rio Grande",
            program = "Bom Dia Rio Grande",
            landingUrl = "https://globoplay.globo.com/v/12316554/",
            region = "Sul",
            state = "RS"
        ),
        globoplayTelejournal(
            id = "globoplay-tj1-tapajos",
            name = "Globoplay • TJ1 / Jornal Tapajós 1ª",
            program = "Jornal Tapajós 1ª Edição",
            landingUrl = "https://globoplay.globo.com/v/6897326/",
            region = "Norte",
            state = "PA",
            extraAliases = listOf("TJ1", "Jornal Tapajós 1", "TV Tapajós")
        ),
        globoplayTelejournal(
            id = "globoplay-tj2-tapajos",
            name = "Globoplay • TJ2 / Jornal Tapajós 2ª",
            program = "Jornal Tapajós 2ª Edição",
            landingUrl = "https://globoplay.globo.com/v/13630892/",
            region = "Norte",
            state = "PA",
            extraAliases = listOf("TJ2", "Jornal Tapajós 2", "TV Tapajós")
        )
    )

    val national: List<VideoSource> = portalNational + youtubeOfficial + globoplayTelejournalsNational

    private val bandRegional = listOf(
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

    val regional: List<VideoSource> = bandRegional + globoplayTelejournalsRegional

    val all: List<VideoSource> = national + regional
    val byId: Map<String, VideoSource> = all.associateBy { it.id }
    val youtubeOfficialIds: Set<String> = youtubeOfficial.map { it.id }.toSet()
    val globoplayTelejournalIds: Set<String> =
        (globoplayTelejournalsNational + globoplayTelejournalsRegional).map { it.id }.toSet()
    val defaultIds: Set<String> = national.map { it.id }.toSet()

    fun selected(ids: Set<String>): List<VideoSource> = ids.mapNotNull(byId::get)

    private fun globoplayTelejournal(
        id: String,
        name: String,
        program: String,
        landingUrl: String,
        region: String = "Nacional",
        state: String = "",
        extraAliases: List<String> = emptyList()
    ): VideoSource = VideoSource(
        id = id,
        name = name,
        group = if (state.isBlank()) "Globo / Globoplay • Telejornal nacional" else "Globo / Globoplay • Telejornal regional",
        region = region,
        state = state,
        landingUrl = landingUrl,
        linkHints = listOf("/v/"),
        aliases = (listOf("Globo", "Globoplay", program) + extraAliases).distinct()
    )
}

package br.com.monitordenoticias.android

import java.net.URLEncoder

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

    val youtubeOfficial = listOf(
        youtube("youtube-cnn-brasil", "CNN Brasil", "@CNNBrasil", listOf("CNN", "CNN Brasil")),
        youtube("youtube-jovem-pan-news", "Jovem Pan News", "@jovempannews", listOf("Jovem Pan", "Jovem Pan News", "JP News")),
        youtube("youtube-globonews", "GloboNews", "@globonews", listOf("GloboNews", "Globo News", "Globo")),
        youtube("youtube-record-news", "Record News", "@recordnews", listOf("Record News", "RecordNews")),
        youtube("youtube-jornal-da-record", "Jornal da Record", "@JornaldaRecord", listOf("Jornal da Record", "JR", "Record TV")),
        youtube("youtube-band-jornalismo", "Band Jornalismo", "@bandjornalismo", listOf("Band", "Band Jornalismo", "BandNews", "Band News", "BandNews TV")),
        youtube("youtube-sbt-news", "SBT News", "@sbtnews", listOf("SBT", "SBT News", "SBT Jornalismo"))
    )

    val globoplayTelejournalsNational = listOf(
        nationalGlobo("globoplay-bom-dia-brasil", "Bom Dia Brasil"),
        nationalGlobo("globoplay-hora-1", "Hora 1"),
        nationalGlobo("globoplay-jornal-hoje", "Jornal Hoje"),
        nationalGlobo("globoplay-jornal-nacional", "Jornal Nacional"),
        nationalGlobo("globoplay-jornal-da-globo", "Jornal da Globo")
    )

    /**
     * Catálogo amplo das principais edições locais das afiliadas Globo em todas as UFs.
     * Quando não há página fixa estável, a fonte usa a busca do nome do programa no
     * Globoplay como ponto de entrada; o repositório expande as edições encontradas e
     * varre os links /v/ dos trechos individuais.
     */
    val globoplayTelejournalsRegional = listOf(
        // NORTE — Acre
        regionalGlobo("globoplay-bom-dia-acre", "Bom Dia Acre", "AC", "Norte", aliases = listOf("BDAC")),
        regionalGlobo("globoplay-jac1", "Jornal do Acre 1ª Edição", "AC", "Norte", aliases = listOf("JAC1", "Jornal do Acre 1")),
        regionalGlobo("globoplay-jac2", "Jornal do Acre 2ª Edição", "AC", "Norte", aliases = listOf("JAC2", "Jornal do Acre 2")),

        // Amapá
        regionalGlobo("globoplay-bom-dia-amapa", "Bom Dia Amapá", "AP", "Norte"),
        regionalGlobo("globoplay-jap1", "Jornal do Amapá 1ª Edição", "AP", "Norte", aliases = listOf("JAP1", "Jornal do Amapá 1")),
        regionalGlobo("globoplay-jap2", "Jornal do Amapá 2ª Edição", "AP", "Norte", aliases = listOf("JAP2", "Jornal do Amapá 2")),

        // Amazonas
        regionalGlobo("globoplay-bom-dia-amazonia-am", "Bom Dia Amazônia", "AM", "Norte", aliases = listOf("Bom Dia Amazonas")),
        regionalGlobo("globoplay-jam1", "Jornal do Amazonas 1ª Edição", "AM", "Norte", aliases = listOf("JAM1", "JAM 1ª edição")),
        regionalGlobo("globoplay-jam2", "Jornal do Amazonas 2ª Edição", "AM", "Norte", aliases = listOf("JAM2", "JAM 2ª edição")),

        // Pará — Belém / TV Liberal
        regionalGlobo("globoplay-bom-dia-para", "Bom Dia Pará", "PA", "Norte"),
        regionalGlobo("globoplay-jl1", "Jornal Liberal 1ª Edição", "PA", "Norte", aliases = listOf("JL1", "Jornal Liberal 1")),
        regionalGlobo("globoplay-jl2", "Jornal Liberal 2ª Edição", "PA", "Norte", aliases = listOf("JL2", "Jornal Liberal 2")),
        // Pará — Santarém / TV Tapajós (IDs preservados da 2.8.4)
        regionalGlobo("globoplay-bom-dia-santarem", "Bom Dia Santarém", "PA", "Norte", aliases = listOf("TV Tapajós")),
        regionalGlobo("globoplay-tj1-tapajos", "Jornal Tapajós 1ª Edição", "PA", "Norte", aliases = listOf("TJ1", "TV Tapajós"), landingOverride = "https://globoplay.globo.com/v/6897326/"),
        regionalGlobo("globoplay-tj2-tapajos", "Jornal Tapajós 2ª Edição", "PA", "Norte", aliases = listOf("TJ2", "TV Tapajós"), landingOverride = "https://globoplay.globo.com/v/13630892/"),

        // Rondônia
        regionalGlobo("globoplay-bom-dia-amazonia-ro", "Bom Dia Amazônia", "RO", "Norte", aliases = listOf("Bom Dia Rondônia")),
        regionalGlobo("globoplay-jro1", "Jornal de Rondônia 1ª Edição", "RO", "Norte", aliases = listOf("JRO1", "Jornal de Rondônia 1")),
        regionalGlobo("globoplay-jro2", "Jornal de Rondônia 2ª Edição", "RO", "Norte", aliases = listOf("JRO2", "Jornal de Rondônia 2")),

        // Roraima
        regionalGlobo("globoplay-bom-dia-amazonia-rr", "Bom Dia Amazônia", "RR", "Norte", aliases = listOf("Bom Dia Roraima")),
        regionalGlobo("globoplay-jrr1", "Jornal de Roraima 1ª Edição", "RR", "Norte", aliases = listOf("JRR1", "Jornal de Roraima 1")),
        regionalGlobo("globoplay-jrr2", "Jornal de Roraima 2ª Edição", "RR", "Norte", aliases = listOf("JRR2", "Jornal de Roraima 2")),

        // Tocantins
        regionalGlobo("globoplay-bom-dia-tocantins", "Bom Dia Tocantins", "TO", "Norte"),
        regionalGlobo("globoplay-ja1-to", "Jornal Anhanguera 1ª Edição Tocantins", "TO", "Norte", aliases = listOf("JA1", "JA 1ª Edição")),
        regionalGlobo("globoplay-ja2-to", "Jornal Anhanguera 2ª Edição Tocantins", "TO", "Norte", aliases = listOf("JA2", "JA 2ª Edição")),

        // NORDESTE — Alagoas
        regionalGlobo("globoplay-bom-dia-alagoas", "Bom Dia Alagoas", "AL", "Nordeste"),
        regionalGlobo("globoplay-al1", "AL1", "AL", "Nordeste", aliases = listOf("AL 1ª Edição", "ALTV 1ª Edição")),
        regionalGlobo("globoplay-al2", "AL2", "AL", "Nordeste", aliases = listOf("AL 2ª Edição", "ALTV 2ª Edição")),

        // Bahia
        regionalGlobo("globoplay-jornal-da-manha-ba", "Jornal da Manhã", "BA", "Nordeste", aliases = listOf("TV Bahia", "Jornal da Manhã Bahia")),
        regionalGlobo("globoplay-bahia-meio-dia", "Bahia Meio Dia", "BA", "Nordeste", aliases = listOf("BMD")),
        regionalGlobo("globoplay-batv", "BATV", "BA", "Nordeste", aliases = listOf("Bahia TV", "TV Bahia")),

        // Ceará
        regionalGlobo("globoplay-bom-dia-ceara", "Bom Dia Ceará", "CE", "Nordeste"),
        regionalGlobo("globoplay-cetv1", "CETV 1ª Edição", "CE", "Nordeste", aliases = listOf("CETV1", "CE1")),
        regionalGlobo("globoplay-cetv2", "CETV 2ª Edição", "CE", "Nordeste", aliases = listOf("CETV2", "CE2")),

        // Maranhão
        regionalGlobo("globoplay-bom-dia-mirante", "Bom Dia Mirante", "MA", "Nordeste"),
        regionalGlobo("globoplay-jmtv1", "JMTV 1ª Edição", "MA", "Nordeste", aliases = listOf("JMTV1", "Jornal Mirante 1ª Edição")),
        regionalGlobo("globoplay-jmtv2", "JMTV 2ª Edição", "MA", "Nordeste", aliases = listOf("JMTV2", "Jornal Mirante 2ª Edição")),

        // Paraíba
        regionalGlobo("globoplay-bom-dia-paraiba", "Bom Dia Paraíba", "PB", "Nordeste"),
        regionalGlobo("globoplay-jpb1", "JPB1", "PB", "Nordeste", aliases = listOf("JPB 1ª Edição", "Jornal da Paraíba 1ª Edição")),
        regionalGlobo("globoplay-jpb2", "JPB2", "PB", "Nordeste", aliases = listOf("JPB 2ª Edição", "Jornal da Paraíba 2ª Edição")),

        // Pernambuco
        regionalGlobo("globoplay-bom-dia-pe", "Bom Dia Pernambuco", "PE", "Nordeste", aliases = listOf("Bom Dia PE")),
        regionalGlobo("globoplay-ne1", "NE1", "PE", "Nordeste", aliases = listOf("NETV 1ª Edição", "NE 1ª Edição")),
        regionalGlobo("globoplay-ne2", "NE2", "PE", "Nordeste", aliases = listOf("NETV 2ª Edição", "NE 2ª Edição")),

        // Piauí
        regionalGlobo("globoplay-bom-dia-piaui", "Bom Dia Piauí", "PI", "Nordeste"),
        regionalGlobo("globoplay-pi1", "PI1", "PI", "Nordeste", aliases = listOf("PITV 1ª Edição", "PI 1ª Edição")),
        regionalGlobo("globoplay-pi2", "PI2", "PI", "Nordeste", aliases = listOf("PITV 2ª Edição", "PI 2ª Edição")),

        // Rio Grande do Norte
        regionalGlobo("globoplay-bom-dia-rn", "Bom Dia RN", "RN", "Nordeste"),
        regionalGlobo("globoplay-rn1", "RN1", "RN", "Nordeste", aliases = listOf("RNTV 1ª Edição", "RN 1ª Edição")),
        regionalGlobo("globoplay-rn2", "RN2", "RN", "Nordeste", aliases = listOf("RNTV 2ª Edição", "RN 2ª Edição")),

        // Sergipe
        regionalGlobo("globoplay-bom-dia-sergipe", "Bom Dia Sergipe", "SE", "Nordeste"),
        regionalGlobo("globoplay-se1", "SE1", "SE", "Nordeste", aliases = listOf("SETV 1ª Edição", "SE 1ª Edição")),
        regionalGlobo("globoplay-se2", "SE2", "SE", "Nordeste", aliases = listOf("SETV 2ª Edição", "SE 2ª Edição")),

        // CENTRO-OESTE — Distrito Federal (IDs preservados quando existiam)
        regionalGlobo("globoplay-bom-dia-df", "Bom Dia DF", "DF", "Centro-Oeste"),
        regionalGlobo("globoplay-df1", "DF1", "DF", "Centro-Oeste", aliases = listOf("DFTV 1ª Edição", "DF 1"), landingOverride = "https://globoplay.globo.com/v/14711882/"),
        regionalGlobo("globoplay-df2", "DF2", "DF", "Centro-Oeste", aliases = listOf("DFTV 2ª Edição", "DF 2")),

        // Goiás
        regionalGlobo("globoplay-bom-dia-goias", "Bom Dia Goiás", "GO", "Centro-Oeste"),
        regionalGlobo("globoplay-ja1-go", "Jornal Anhanguera 1ª Edição", "GO", "Centro-Oeste", aliases = listOf("JA1", "JA 1ª Edição")),
        regionalGlobo("globoplay-ja2-go", "Jornal Anhanguera 2ª Edição", "GO", "Centro-Oeste", aliases = listOf("JA2", "JA 2ª Edição")),

        // Mato Grosso
        regionalGlobo("globoplay-bom-dia-mt", "Bom Dia Mato Grosso", "MT", "Centro-Oeste"),
        regionalGlobo("globoplay-mt1", "MT1", "MT", "Centro-Oeste", aliases = listOf("MTTV 1ª Edição", "MT 1ª Edição")),
        regionalGlobo("globoplay-mt2", "MT2", "MT", "Centro-Oeste", aliases = listOf("MTTV 2ª Edição", "MT 2ª Edição")),

        // Mato Grosso do Sul
        regionalGlobo("globoplay-bom-dia-ms", "Bom Dia MS", "MS", "Centro-Oeste"),
        regionalGlobo("globoplay-mstv1", "MSTV 1ª Edição", "MS", "Centro-Oeste", aliases = listOf("MS1", "MSTV1")),
        regionalGlobo("globoplay-mstv2", "MSTV 2ª Edição", "MS", "Centro-Oeste", aliases = listOf("MS2", "MSTV2")),

        // SUDESTE — Espírito Santo (IDs preservados)
        regionalGlobo("globoplay-bom-dia-es", "Bom Dia ES", "ES", "Sudeste", landingOverride = "https://globoplay.globo.com/v/11645540/"),
        regionalGlobo("globoplay-gazeta-meio-dia-es", "Gazeta Meio Dia", "ES", "Sudeste", aliases = listOf("ESTV 1ª Edição", "ESTV1", "ES1"), landingOverride = "https://globoplay.globo.com/v/5423638/"),
        regionalGlobo("globoplay-estv2", "ESTV 2ª Edição", "ES", "Sudeste", aliases = listOf("ESTV2", "ES2")),

        // Minas Gerais
        regionalGlobo("globoplay-bom-dia-minas", "Bom Dia Minas", "MG", "Sudeste", landingOverride = "https://globoplay.globo.com/v/5535965/"),
        regionalGlobo("globoplay-mg1", "MG1", "MG", "Sudeste", aliases = listOf("MGTV 1ª Edição", "MG Primeira Edição"), landingOverride = "https://globoplay.globo.com/v/12552834/"),
        regionalGlobo("globoplay-mg2", "MG2", "MG", "Sudeste", aliases = listOf("MGTV 2ª Edição", "MG Segunda Edição")),

        // Rio de Janeiro
        regionalGlobo("globoplay-bom-dia-rio", "Bom Dia Rio", "RJ", "Sudeste", landingOverride = "https://globoplay.globo.com/v/8383434/"),
        regionalGlobo("globoplay-rj1", "RJ1", "RJ", "Sudeste", aliases = listOf("RJTV 1ª Edição", "RJ Primeira Edição"), landingOverride = "https://globoplay.globo.com/v/5976232/"),
        regionalGlobo("globoplay-rj2", "RJ2", "RJ", "Sudeste", aliases = listOf("RJTV 2ª Edição", "RJ Segunda Edição"), landingOverride = "https://globoplay.globo.com/v/12954402/"),

        // São Paulo — capital
        regionalGlobo("globoplay-bom-dia-sp", "Bom Dia SP", "SP", "Sudeste", aliases = listOf("Bom Dia São Paulo", "BDSP"), landingOverride = "https://globoplay.globo.com/v/5701776/"),
        regionalGlobo("globoplay-sp1", "SP1", "SP", "Sudeste", aliases = listOf("SPTV 1ª Edição", "SP Primeira Edição"), landingOverride = "https://globoplay.globo.com/v/12096579/"),
        regionalGlobo("globoplay-sp2", "SP2", "SP", "Sudeste", aliases = listOf("SPTV 2ª Edição", "SP Segunda Edição"), landingOverride = "https://globoplay.globo.com/v/5854721/"),
        // São Paulo — principais afiliadas do interior/litoral
        regionalGlobo("globoplay-bom-dia-cidade-eptv", "Bom Dia Cidade • EPTV", "SP", "Sudeste", aliases = listOf("EPTV Campinas", "EPTV Ribeirão", "EPTV Central")),
        regionalGlobo("globoplay-eptv1", "EPTV1", "SP", "Sudeste", aliases = listOf("EPTV 1ª Edição")),
        regionalGlobo("globoplay-eptv2", "EPTV2", "SP", "Sudeste", aliases = listOf("EPTV 2ª Edição")),
        regionalGlobo("globoplay-bom-dia-cidade-tem", "Bom Dia Cidade • TV TEM", "SP", "Sudeste", aliases = listOf("TV TEM")),
        regionalGlobo("globoplay-tem-noticias-1", "TEM Notícias 1ª Edição", "SP", "Sudeste", aliases = listOf("TV TEM 1ª Edição")),
        regionalGlobo("globoplay-tem-noticias-2", "TEM Notícias 2ª Edição", "SP", "Sudeste", aliases = listOf("TV TEM 2ª Edição")),
        regionalGlobo("globoplay-bom-dia-regiao-tribuna", "Bom Dia Região • TV Tribuna", "SP", "Sudeste", aliases = listOf("TV Tribuna Santos")),
        regionalGlobo("globoplay-jornal-tribuna-1", "Jornal Tribuna 1ª Edição", "SP", "Sudeste", aliases = listOf("JT1", "TV Tribuna")),
        regionalGlobo("globoplay-jornal-tribuna-2", "Jornal Tribuna 2ª Edição", "SP", "Sudeste", aliases = listOf("JT2", "TV Tribuna")),
        regionalGlobo("globoplay-bom-dia-fronteira", "Bom Dia Fronteira", "SP", "Sudeste", aliases = listOf("TV Fronteira")),
        regionalGlobo("globoplay-fronteira-noticias-1", "Fronteira Notícias 1ª Edição", "SP", "Sudeste", aliases = listOf("TV Fronteira 1ª Edição")),
        regionalGlobo("globoplay-fronteira-noticias-2", "Fronteira Notícias 2ª Edição", "SP", "Sudeste", aliases = listOf("TV Fronteira 2ª Edição")),
        regionalGlobo("globoplay-bom-dia-vanguarda", "Bom Dia Vanguarda", "SP", "Sudeste", aliases = listOf("TV Vanguarda")),
        regionalGlobo("globoplay-link-vanguarda", "Link Vanguarda", "SP", "Sudeste", aliases = listOf("TV Vanguarda 1ª Edição")),
        regionalGlobo("globoplay-jornal-vanguarda", "Jornal Vanguarda", "SP", "Sudeste", aliases = listOf("TV Vanguarda 2ª Edição")),

        // SUL — Paraná
        regionalGlobo("globoplay-bom-dia-parana", "Bom Dia Paraná", "PR", "Sul", aliases = listOf("RPC")),
        regionalGlobo("globoplay-meio-dia-parana", "Meio Dia Paraná", "PR", "Sul", aliases = listOf("RPC 1ª Edição")),
        regionalGlobo("globoplay-boa-noite-parana", "Boa Noite Paraná", "PR", "Sul", aliases = listOf("RPC 2ª Edição")),

        // Santa Catarina
        regionalGlobo("globoplay-bom-dia-sc", "Bom Dia Santa Catarina", "SC", "Sul", aliases = listOf("Bom Dia SC", "NSC TV")),
        regionalGlobo("globoplay-jornal-do-almoco-sc", "Jornal do Almoço • SC", "SC", "Sul", aliases = listOf("NSC TV 1ª Edição", "Jornal do Almoço")),
        regionalGlobo("globoplay-nsc-noticias", "NSC Notícias", "SC", "Sul", aliases = listOf("NSC TV 2ª Edição")),

        // Rio Grande do Sul (ID de Bom Dia preservado)
        regionalGlobo("globoplay-bom-dia-rio-grande", "Bom Dia Rio Grande", "RS", "Sul", aliases = listOf("RBS TV"), landingOverride = "https://globoplay.globo.com/v/12316554/"),
        regionalGlobo("globoplay-jornal-do-almoco-rs", "Jornal do Almoço • RS", "RS", "Sul", aliases = listOf("RBS TV 1ª Edição", "Jornal do Almoço")),
        regionalGlobo("globoplay-rbs-noticias", "RBS Notícias", "RS", "Sul", aliases = listOf("RBS TV 2ª Edição"))
    )

    val national: List<VideoSource> = portalNational + youtubeOfficial + globoplayTelejournalsNational

    private val bandRegional = listOf(
        VideoSource("video-band-brasilia", "Band Brasília", "Band Regional", "Centro-Oeste", "DF", "https://www.band.com.br/band-brasilia/videos", listOf("/band-brasilia/videos/"), listOf("Band Brasília", "Band DF"), searchUrlTemplate = "https://www.band.com.br/busca?q={query}"),
        VideoSource("video-band-minas", "Band Minas", "Band Regional", "Sudeste", "MG", "https://www.band.com.br/band-minas", listOf("/band-minas/videos/", "/videos/"), listOf("Band Minas", "Band Minas Gerais"), searchUrlTemplate = "https://www.band.com.br/busca?q={query}"),
        VideoSource("video-band-rio", "Band Rio", "Band Regional", "Sudeste", "RJ", "https://www.band.com.br/rio-de-janeiro/videos", listOf("/rio-de-janeiro/videos/"), listOf("Band Rio", "Band Rio de Janeiro"), searchUrlTemplate = "https://www.band.com.br/busca?q={query}"),
        VideoSource("video-band-parana", "Band Paraná", "Band Regional", "Sul", "PR", "https://www.band.com.br/band-parana/videos", listOf("/band-parana/videos/"), listOf("Band Paraná", "Band PR"), searchUrlTemplate = "https://www.band.com.br/busca?q={query}"),
        VideoSource("video-band-bahia", "Band Bahia", "Band Regional", "Nordeste", "BA", "https://www.band.com.br/band-bahia", listOf("/band-bahia/videos/", "/videos/"), listOf("Band Bahia", "Band BA"), searchUrlTemplate = "https://www.band.com.br/busca?q={query}")
    )

    val regional: List<VideoSource> = bandRegional + globoplayTelejournalsRegional
    val all: List<VideoSource> = national + regional
    val byId: Map<String, VideoSource> = all.associateBy { it.id }
    val youtubeOfficialIds: Set<String> = youtubeOfficial.map { it.id }.toSet()
    val globoplayTelejournalIds: Set<String> = (globoplayTelejournalsNational + globoplayTelejournalsRegional).map { it.id }.toSet()
    val defaultIds: Set<String> = national.map { it.id }.toSet()

    fun selected(ids: Set<String>): List<VideoSource> = ids.mapNotNull(byId::get)

    private fun youtube(id: String, label: String, handle: String, aliases: List<String>) = VideoSource(
        id = id,
        name = "YouTube • $label",
        group = "YouTube oficial • $label",
        landingUrl = "https://www.youtube.com/$handle/videos",
        linkHints = listOf("/watch"),
        aliases = aliases,
        youtubeHandle = handle
    )

    private fun nationalGlobo(id: String, program: String) = VideoSource(
        id = id,
        name = "Globoplay • $program",
        group = "Globo / Globoplay • Telejornal nacional",
        landingUrl = globoplaySearch(program),
        linkHints = listOf("/v/"),
        aliases = listOf("Globo", "Globoplay", program)
    )

    private fun regionalGlobo(
        id: String,
        program: String,
        state: String,
        region: String,
        aliases: List<String> = emptyList(),
        landingOverride: String = ""
    ) = VideoSource(
        id = id,
        name = "Globoplay • $program",
        group = "Globo / Globoplay • Telejornal regional",
        region = region,
        state = state,
        landingUrl = landingOverride.ifBlank { globoplaySearch(program) },
        linkHints = listOf("/v/"),
        aliases = (listOf("Globo", "Globoplay", program) + aliases).distinct()
    )

    private fun globoplaySearch(program: String): String =
        "https://globoplay.globo.com/busca/?q=${URLEncoder.encode(program, "UTF-8")}" 
}

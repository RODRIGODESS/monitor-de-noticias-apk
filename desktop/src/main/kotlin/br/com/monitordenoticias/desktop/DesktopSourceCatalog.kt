package br.com.monitordenoticias.desktop

import br.com.monitordenoticias.android.MediaSource
import br.com.monitordenoticias.android.SourceCatalog

/**
 * Fontes e aliases adicionais exclusivos da edição Windows.
 *
 * O SourceCatalog compartilhado com o Android permanece intacto. Aqui também
 * normalizamos nomes editoriais que o Google Notícias costuma abreviar. Isso
 * evita que uma fonte selecionada seja descartada apenas porque o publisher do
 * RSS usa um nome diferente do nome apresentado no catálogo do aplicativo.
 */
object DesktopSourceCatalog {
    const val SPECIALIZED_GROUP = "Mídias especializadas"

    private val windowsSharedSources: List<MediaSource> = SourceCatalog.all.map(::withWindowsAliases)

    val specialized: List<MediaSource> = listOf(
        specialized("especializada-defesa-em-foco", "Defesa em Foco", "DefesaEmFoco", "defesaemfoco.com.br"),
        specialized("especializada-defesa-aerea-naval", "Defesa Aérea & Naval", "Defesa Aerea & Naval", "Defesa Aérea Naval", "DAN", "defesaaereanaval.com.br"),
        specialized("especializada-defesanet", "DefesaNet", "Defesa Net", "defesanet.com.br"),
        specialized("especializada-tecnodefesa", "Tecnologia & Defesa", "Tecnodefesa", "Tecnologia e Defesa", "T&D", "tecnodefesa.com.br"),
        specialized("especializada-zona-militar", "Zona Militar", "Zona-Militar", "zona-militar.com"),
        specialized("especializada-click-petroleo-gas", "Click Petróleo e Gás", "Click Petroleo e Gas", "CPG", "clickpetroleoegas.com.br"),
        specialized("especializada-poder-naval", "Poder Naval", "Naval.com.br", "naval.com.br"),
        specialized("especializada-gbn-news", "GBN Defense", "GBN News", "GBN Defense - A informação começa aqui", "gbnnews.com.br")
    )

    val all: List<MediaSource> = (windowsSharedSources + specialized).distinctBy { it.id }
    val byId: Map<String, MediaSource> = all.associateBy { it.id }

    fun selected(ids: Set<String>): List<MediaSource> = ids.mapNotNull(byId::get)

    private fun withWindowsAliases(source: MediaSource): MediaSource {
        val extra = WINDOWS_ALIAS_OVERRIDES[source.id].orEmpty()
        if (extra.isEmpty()) return source
        return source.copy(aliases = (source.aliases + extra).distinctBy { it.lowercase() })
    }

    private fun specialized(id: String, name: String, vararg aliases: String) = MediaSource(
        id = id,
        name = name,
        region = SourceCatalog.NATIONAL_REGION,
        state = "BR",
        stateName = "Brasil",
        group = SPECIALIZED_GROUP,
        aliases = aliases.toList()
    )

    /**
     * Aliases observados em agregadores/RSS. Mantidos no Windows para não
     * alterar o catálogo da aplicação Android.
     */
    private val WINDOWS_ALIAS_OVERRIDES: Map<String, List<String>> = mapOf(
        "pe-folha-de-pernambuco" to listOf(
            "Folha PE",
            "FolhaPE",
            "Folha de Pernambuco",
            "folhape.com.br",
            "www.folhape.com.br"
        )
    )
}

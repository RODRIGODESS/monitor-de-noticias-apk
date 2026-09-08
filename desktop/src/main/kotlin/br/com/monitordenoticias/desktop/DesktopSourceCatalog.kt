package br.com.monitordenoticias.desktop

import br.com.monitordenoticias.android.MediaSource
import br.com.monitordenoticias.android.SourceCatalog

/**
 * Fontes adicionais exclusivas da edição Windows.
 *
 * Este catálogo não altera o SourceCatalog compartilhado com o Android. Assim, as
 * mídias especializadas solicitadas ficam isoladas na branch/edição Windows.
 */
object DesktopSourceCatalog {
    const val SPECIALIZED_GROUP = "Mídias especializadas"

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

    val all: List<MediaSource> = (SourceCatalog.all + specialized).distinctBy { it.id }
    val byId: Map<String, MediaSource> = all.associateBy { it.id }

    fun selected(ids: Set<String>): List<MediaSource> = ids.mapNotNull(byId::get)

    private fun specialized(id: String, name: String, vararg aliases: String) = MediaSource(
        id = id,
        name = name,
        region = SourceCatalog.NATIONAL_REGION,
        state = "BR",
        stateName = "Brasil",
        group = SPECIALIZED_GROUP,
        aliases = aliases.toList()
    )
}

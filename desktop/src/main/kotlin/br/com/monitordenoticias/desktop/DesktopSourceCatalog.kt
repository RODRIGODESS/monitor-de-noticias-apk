package br.com.monitordenoticias.desktop

import br.com.monitordenoticias.android.MediaSource
import br.com.monitordenoticias.android.SourceCatalog
import java.text.Normalizer

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
        val generated = generatedRegionalAliases(source)
        val overrides = WINDOWS_ALIAS_OVERRIDES[source.id].orEmpty()
        val allAliases = (source.aliases + generated + overrides)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { normalize(it) }
        return if (allAliases == source.aliases) source else source.copy(aliases = allAliases)
    }

    /**
     * O Google Notícias frequentemente encurta nomes regionais usando a UF.
     * Ex.: "Folha de Pernambuco" -> "Folha PE".
     *
     * Geramos variantes conservadoras, usadas apenas para comparação exata no
     * filtro do repositório. Isso melhora cobertura sem transformar "Folha" em
     * correspondência genérica para qualquer veículo do país.
     */
    private fun generatedRegionalAliases(source: MediaSource): List<String> {
        if (source.state.isBlank() || source.state.equals("BR", true)) return emptyList()

        val uf = source.state.uppercase()
        val generated = linkedSetOf<String>()
        val bases = (listOf(source.name) + source.aliases).filter { it.isNotBlank() }

        bases.forEach { base ->
            generated += "$base $uf"
            generated += "${compact(base)}$uf"

            val meaningful = normalizedWords(base).filterNot { it in STOP_WORDS }
            if (meaningful.isNotEmpty()) {
                generated += "${displayWord(meaningful.first())} $uf"
                generated += "${displayWord(meaningful.first())}$uf"
            }

            if (meaningful.size >= 2) {
                val initials = meaningful.joinToString("") { it.take(1).uppercase() }
                if (initials.length in 2..5) {
                    generated += "$initials $uf"
                    generated += "$initials$uf"
                }
            }
        }

        return generated.toList()
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

    /** Aliases editoriais/domínios observados em agregadores e RSS. */
    private val WINDOWS_ALIAS_OVERRIDES: Map<String, List<String>> = mapOf(
        "pe-folha-de-pernambuco" to listOf(
            "Folha PE",
            "FolhaPE",
            "Folha de Pernambuco",
            "folhape.com.br",
            "www.folhape.com.br"
        )
    )

    private fun normalizedWords(value: String): List<String> = normalize(value)
        .split(' ')
        .filter(String::isNotBlank)

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun compact(value: String): String = normalize(value).replace(" ", "")

    private fun displayWord(normalized: String): String = normalized.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }

    private val STOP_WORDS = setOf(
        "de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os"
    )
}

package br.com.monitordenoticias.android

import java.text.Normalizer

/**
 * Correspondência textual compartilhada por notícias e vídeos.
 *
 * Termos curtos e siglas (FAB, MB, GSI, etc.) precisam casar como palavra
 * inteira. Isso impede falsos positivos como FAB -> "fábrica".
 */
object MediaTextMatcher {
    private val STOP_WORDS = setOf(
        "de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas",
        "a", "o", "as", "os", "para", "por", "com"
    )

    fun matches(text: String, phrase: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(phrase)
        if (wanted.isBlank()) return true
        if (haystack.isBlank()) return false

        val haystackTokens = haystack.split(' ').filter { it.isNotBlank() }
        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }

        // Sigla/termo curto: somente token inteiro. FAB não pode casar com fábrica.
        if (wantedTokens.size == 1 && wantedTokens[0].length <= 4) {
            return wantedTokens[0] in haystackTokens
        }

        // Frase completa preserva a ordem quando possível.
        if (containsWholePhrase(haystack, wanted)) return true

        // Fallback para consultas compostas: todos os termos significativos
        // precisam existir como tokens inteiros, nunca como substring de outra palavra.
        val meaningful = wantedTokens.filter { it.length >= 2 && it !in STOP_WORDS }
        if (meaningful.isEmpty()) return false
        val tokenSet = haystackTokens.toHashSet()
        return meaningful.all { it in tokenSet }
    }

    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun containsWholePhrase(haystack: String, wanted: String): Boolean {
        if (haystack == wanted) return true
        return (" $haystack ").contains(" $wanted ")
    }
}

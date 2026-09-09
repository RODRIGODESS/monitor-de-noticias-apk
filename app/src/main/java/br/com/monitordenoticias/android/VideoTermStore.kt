package br.com.monitordenoticias.android

import android.content.Context

/**
 * Termos de vídeo independentes dos termos de notícias.
 *
 * Na primeira execução da v4.0, copiamos os termos atuais de notícias apenas para
 * preservar o comportamento anterior. A partir daí as duas listas evoluem de forma
 * totalmente independente.
 *
 * Na v4.1.0 acrescentamos, uma única vez, o novo pacote-base solicitado. Depois dessa
 * migração os termos continuam independentes e o usuário pode remover qualquer item.
 */
object VideoTermStore {
    private const val KEY_TERMS = "video_terms_v400"
    private const val KEY_INITIALIZED = "video_terms_v400_initialized"
    private const val KEY_V410_BASELINE_ADDED = "video_terms_v410_baseline_added"

    fun load(context: Context, seedTerms: List<String>): List<String> {
        val prefs = context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            val seed = clean(seedTerms + V410_BASELINE_TERMS)
            prefs.edit()
                .putStringSet(KEY_TERMS, seed.toSet())
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_V410_BASELINE_ADDED, true)
                .apply()
            return seed
        }

        val current = clean(prefs.getStringSet(KEY_TERMS, emptySet()).orEmpty().toList())
        if (!prefs.getBoolean(KEY_V410_BASELINE_ADDED, false)) {
            val migrated = clean(current + V410_BASELINE_TERMS)
            prefs.edit()
                .putStringSet(KEY_TERMS, migrated.toSet())
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_V410_BASELINE_ADDED, true)
                .apply()
            return migrated
        }
        return current
    }

    fun add(context: Context, value: String, seedTerms: List<String>): List<String> {
        val current = load(context, seedTerms).toMutableList()
        val cleanValue = value.trim()
        if (cleanValue.isNotBlank() && current.none { it.equals(cleanValue, ignoreCase = true) }) {
            current += cleanValue
        }
        return save(context, current)
    }

    fun remove(context: Context, value: String, seedTerms: List<String>): List<String> {
        val next = load(context, seedTerms).filterNot { it.equals(value.trim(), ignoreCase = true) }
        return save(context, next)
    }

    private fun save(context: Context, values: List<String>): List<String> {
        val cleanValues = clean(values)
        context.getSharedPreferences(BackgroundMonitor.PREFS, 0)
            .edit()
            .putStringSet(KEY_TERMS, cleanValues.toSet())
            .putBoolean(KEY_INITIALIZED, true)
            .putBoolean(KEY_V410_BASELINE_ADDED, true)
            .apply()
        return cleanValues
    }

    private fun clean(values: List<String>): List<String> = values
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    val V410_BASELINE_TERMS = listOf(
        "Capitania Fluvial",
        "Fragata",
        "Submarino",
        "Comandante da Marinha",
        "Exército",
        "FAB",
        "Marinha",
        "Ministro da Defesa",
        "Ministério da Defesa",
        "Maior navio da América Latina",
        "PROSUB"
    )
}

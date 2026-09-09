package br.com.monitordenoticias.desktop

import androidx.compose.ui.Modifier

/**
 * Fallback usado apenas quando um helper de UI é executado fora de RowScope/ColumnScope.
 * Dentro de Row/Column, os modifiers weight nativos do Compose têm precedência.
 *
 * Mantém o helper de ação rápida compilável sem alterar o comportamento dos pesos
 * definidos nos call-sites que pertencem aos escopos de layout do Compose.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.weight(weight: Float): Modifier = this

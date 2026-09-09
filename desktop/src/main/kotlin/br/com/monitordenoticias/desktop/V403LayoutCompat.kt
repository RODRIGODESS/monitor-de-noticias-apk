package br.com.monitordenoticias.desktop

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Fallback usado apenas quando um helper de UI é executado fora de RowScope/ColumnScope.
 * Dentro de Row/Column, os modifiers weight nativos do Compose têm precedência.
 *
 * No helper de ações rápidas, convertemos o peso solicitado em uma altura estável.
 * Isso evita que um botão tente ocupar toda a altura do painel por estar fora do
 * escopo que fornece o weight nativo do Compose.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.weight(weight: Float): Modifier = this.height(52.dp)

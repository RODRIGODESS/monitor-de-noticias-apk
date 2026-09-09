package br.com.monitordenoticias.desktop

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Fallback usado apenas quando um helper de UI é executado fora de RowScope/ColumnScope.
 * Dentro de Row/Column, os modifiers weight nativos do Compose têm precedência.
 *
 * No helper de ações rápidas, convertemos o peso solicitado em uma altura estável.
 * 48 dp mantém os três botões uniformes dentro do painel sem recorte/overflow.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.weight(weight: Float): Modifier = this.height(48.dp)

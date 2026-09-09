package br.com.monitordenoticias.desktop

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Compatibilidade exclusiva da UI v4.0.3.
 *
 * Em RowScope/ColumnScope o Compose continua preferindo o weight nativo.
 * Fora desses scopes (caso dos helpers reutilizáveis das ações rápidas),
 * esta extensão evita erro de compilação e mantém uma altura mínima uniforme.
 */
internal fun Modifier.weight(@Suppress("UNUSED_PARAMETER") fraction: Float): Modifier =
    this.heightIn(min = 52.dp)

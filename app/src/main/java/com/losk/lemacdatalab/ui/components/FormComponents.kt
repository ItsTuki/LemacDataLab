package com.losk.lemacdatalab.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Mensaje de error bajo un campo. No ocupa espacio cuando no hay error. */
@Composable
fun MensajeError(texto: String?, modifier: Modifier = Modifier) {
    if (texto != null) Text(texto, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = modifier.padding(start = 4.dp, top = 2.dp))
}

/**
 * Aviso al inicio del formulario cuando hay campos por corregir.
 * La animación (aparece y se pliega) comunica el cambio de estado: formulario con errores ↔ formulario válido.
 */
@Composable
fun ResumenErrores(cantidad: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = cantidad > 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (cantidad == 1) "Hay 1 campo por corregir." else "Hay $cantidad campos por corregir.",
                    color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall
                )
                Text("Revisa los mensajes en rojo bajo cada campo.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

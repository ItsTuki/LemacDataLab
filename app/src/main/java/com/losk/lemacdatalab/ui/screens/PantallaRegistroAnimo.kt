package com.losk.lemacdatalab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.losk.lemacdatalab.viewmodel.AnimoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRegistroAnimo(viewModel: AnimoViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Registro de Ánimo", style = MaterialTheme.typography.headlineMedium)
        Text("Intensidad del ánimo (1 al 5):")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (1..5).forEach { nivel ->
                FilterChip(
                    selected = state.intensidad == nivel,
                    onClick = { viewModel.actualizarIntensidad(nivel) },
                    label = { Text(nivel.toString()) }
                )
            }
        }

        OutlinedTextField(
            value = state.contexto,
            onValueChange = { viewModel.actualizarContexto(it) },
            label = { Text("Contexto (ej. Trabajo, Familia)") },
            modifier = Modifier.fillMaxWidth(),
            isError = state.errorMensaje?.contains("contexto") == true
        )

        OutlinedTextField(
            value = state.notaOpcional,
            onValueChange = { viewModel.actualizarNota(it) },
            label = { Text("Nota Opcional (Opcional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )


        state.errorMensaje?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }


        if (state.registroExitoso) {
            Text("¡Registro guardado exitosamente!", color = Color(0xFF4CAF50))
        }

        Spacer(modifier = Modifier.weight(1f))


        Button(
            onClick = { viewModel.guardarRegistroAnimo() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar Registro")
        }
    }
}
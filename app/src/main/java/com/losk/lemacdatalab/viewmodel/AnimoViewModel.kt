package com.losk.lemacdatalab.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


data class AnimoUiState(
    val intensidad: Int? = null,
    val contexto: String = "",
    val notaOpcional: String = "",
    val errorMensaje: String? = null,
    val registroExitoso: Boolean = false
)









class AnimoViewModel : ViewModel() {
    var uiState by mutableStateOf(AnimoUiState())
        private set

    fun actualizarIntensidad(valor: Int) {
        uiState = uiState.copy(intensidad = valor, errorMensaje = null)
    }

    fun actualizarContexto(valor: String) {
        uiState = uiState.copy(contexto = valor, errorMensaje = null)
    }

    fun actualizarNota(valor: String) {
        uiState = uiState.copy(notaOpcional = valor)
    }

    fun guardarRegistroAnimo() {
        if (uiState.intensidad == null || uiState.intensidad !in 1..5) {
            uiState = uiState.copy(errorMensaje = "Por favor, selecciona una intensidad del 1 al 5.")
            return
        }

        if (uiState.contexto.isBlank()) {
            uiState = uiState.copy(errorMensaje = "El contexto es obligatorio (ej. Trabajo, Casa).")
            return
        }


        val fechaHoraActual = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)


        val nuevoRegistro = RegistroAnimoEntity(
            id_usuario = "usuario_prueba_123",
            fecha_hora = fechaHoraActual,
            intensidad = uiState.intensidad!!,
            contexto = uiState.contexto,
            nota_opcional = uiState.notaOpcional
        )


        uiState = uiState.copy(errorMensaje = null, registroExitoso = true)
    }
}
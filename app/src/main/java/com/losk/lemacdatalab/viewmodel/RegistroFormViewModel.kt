package com.losk.lemacdatalab.viewmodel

import androidx.lifecycle.ViewModel
import com.losk.lemacdatalab.model.Ambito
import com.losk.lemacdatalab.model.Registro
import com.losk.lemacdatalab.model.Validaciones
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Todo lo que la pantalla del formulario necesita dibujar. La pantalla NO guarda estado propio. */
data class RegistroFormState(
    val id: String = UUID.randomUUID().toString(),
    val fecha: Long = System.currentTimeMillis(),
    val intensidad: Int = 0,
    val categoria: String = "",
    val contexto: String = "",
    val estrategia: String = "",
    val resultado: Int = 0,
    val nota: String = "",
    val objetivoId: String? = null,
    /** Mensaje de error por campo (clave = nombre del campo). Vacío = sin errores. */
    val errores: Map<String, String> = emptyMap(),
    /** Se activa al primer intento de guardar; desde ahí los errores se actualizan en tiempo real. */
    val intentoGuardar: Boolean = false
)

/**
 * ViewModel del formulario de registro (RF-04, RF-ND/DBT/AN/AD).
 * Interfaz → evento (onXxx) → ViewModel valida → nuevo estado → Interfaz se redibuja.
 */
class RegistroFormViewModel(
    private val ambito: Ambito,
    private val owner: String,
    existente: Registro?
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        if (existente != null) RegistroFormState(
            id = existente.id, fecha = existente.fecha, intensidad = existente.intensidad,
            categoria = existente.categoria, contexto = existente.contexto, estrategia = existente.estrategia,
            resultado = existente.resultado, nota = existente.nota, objetivoId = existente.objetivoId
        ) else RegistroFormState(categoria = if (ambito == Ambito.ADICCIONES) "Impulso" else "")
    )
    val state: StateFlow<RegistroFormState> = mutableState.asStateFlow()

    // ---- Eventos que envía la pantalla ----
    fun onIntensidad(v: Int) = cambiar { it.copy(intensidad = v) }
    fun onCategoria(v: String) = cambiar { it.copy(categoria = v) }
    /** En DBT la categoría es la habilidad practicada y se guarda también como estrategia. */
    fun onHabilidad(v: String) = cambiar { it.copy(categoria = v, estrategia = v) }
    fun onContexto(v: String) = cambiar { it.copy(contexto = v) }
    fun onEstrategia(v: String) = cambiar { it.copy(estrategia = v) }
    fun onResultado(v: Int) = cambiar { it.copy(resultado = v) }
    fun onNota(v: String) = cambiar { it.copy(nota = v) }
    fun onObjetivo(id: String?) = cambiar { it.copy(objetivoId = id) }

    /**
     * Valida el formulario. Si hay errores los publica en el estado y devuelve null;
     * si es válido devuelve el Registro listo para guardar.
     */
    fun intentarGuardar(): Registro? {
        val registro = construir(mutableState.value)
        val errores = Validaciones.registroCampos(registro)
        mutableState.update { it.copy(errores = errores, intentoGuardar = true) }
        return if (errores.isEmpty()) registro else null
    }

    private fun cambiar(transformar: (RegistroFormState) -> RegistroFormState) {
        mutableState.update { actual ->
            val nuevo = transformar(actual)
            // Después del primer intento, corregir un campo limpia su error al instante.
            if (nuevo.intentoGuardar) nuevo.copy(errores = Validaciones.registroCampos(construir(nuevo))) else nuevo
        }
    }

    private fun construir(s: RegistroFormState) = Registro(
        id = s.id, usuarioId = owner, ambito = ambito, fecha = s.fecha, intensidad = s.intensidad,
        categoria = s.categoria.trim(), contexto = s.contexto.trim(), estrategia = s.estrategia,
        resultado = s.resultado, nota = s.nota.trim(), objetivoId = s.objetivoId
    )
}

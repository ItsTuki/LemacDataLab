@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.losk.lemacdatalab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.losk.lemacdatalab.data.AppState
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.ui.*
import com.losk.lemacdatalab.ui.components.MensajeError
import com.losk.lemacdatalab.ui.components.ResumenErrores
import com.losk.lemacdatalab.viewmodel.RegistroFormViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

fun fechaTexto(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

@Composable
fun ScaleField(label: String, value: Int, error: String? = null, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n -> FilterChip(value == n, { onChange(n) }, label = { Text("$n") }, modifier = Modifier.weight(1f)) }
        }
        Text("1 = menor · 5 = mayor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MensajeError(error)
    }
}

/** Lista desplegable con mensaje de error debajo (SelectField no muestra errores por sí mismo). */
@Composable
private fun CampoSelect(label: String, value: String, options: List<String>, error: String?, onChange: (String) -> Unit) {
    Column {
        SelectField(label, value, options, onChange)
        MensajeError(error)
    }
}

/**
 * Pantalla del formulario de registro. No guarda estado propio: lo administra RegistroFormViewModel
 * (valores, validaciones y errores por campo). La pantalla solo dibuja y envía eventos.
 */
@Composable
fun RegistroScreen(a: Ambito, owner: String, existente: Registro?, state: AppState, busy: Boolean, error: String?, onSave: (Registro) -> Unit) {
    // Una "sesión" por apertura del formulario (sobrevive a girar la pantalla, pero no reutiliza datos de un formulario anterior).
    val sesion = rememberSaveable(a, existente?.id) { UUID.randomUUID().toString() }
    val factory = remember(sesion) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RegistroFormViewModel(a, owner, existente) as T
        }
    }
    val form: RegistroFormViewModel = viewModel(key = "registro-$sesion", factory = factory)
    val f by form.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val catalogo = state.recursos.filter { !it.eliminado && it.ambito == a }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ResumenErrores(f.errores.size)
            Surface(color = colorAmbito(a).copy(alpha = .10f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (existente == null) "Nuevo registro" else "Editar registro", style = MaterialTheme.typography.headlineSmall)
                    Text(a.descripcion)
                    Text(fechaTexto(f.fecha), style = MaterialTheme.typography.bodySmall)
                }
            }
            when (a) {
                Ambito.NEURODESARROLLO -> OutlinedTextField(f.categoria, form::onCategoria, label = { Text("Rutina o tarea") },
                    isError = f.errores["categoria"] != null, supportingText = f.errores["categoria"]?.let { msg -> { Text(msg) } },
                    modifier = Modifier.fillMaxWidth().testTag("categoria"), singleLine = true)
                Ambito.DBT -> CampoSelect("Habilidad practicada", f.categoria, catalogo.filter { it.tipo == TipoRecurso.HABILIDAD }.map { it.titulo }, f.errores["categoria"], form::onHabilidad)
                Ambito.ANIMO -> CampoSelect("Estado de ánimo", f.categoria,
                    catalogo.find { it.id == "animo-categorias" && it.tipo == TipoRecurso.PARAMETRO }?.contenido?.lines()?.map { it.trim() }
                        ?: listOf("Muy bajo", "Bajo", "Neutral", "Bueno", "Muy bueno"), f.errores["categoria"], form::onCategoria)
                Ambito.ADICCIONES -> CampoSelect("Tipo de registro", f.categoria, listOf("Impulso", "Evento de prueba"), f.errores["categoria"], form::onCategoria)
            }
            ScaleField(if (a == Ambito.ADICCIONES) "Intensidad del impulso" else "Intensidad percibida", f.intensidad, f.errores["intensidad"], form::onIntensidad)
            OutlinedTextField(f.contexto, form::onContexto, label = { Text(when (a) {
                Ambito.NEURODESARROLLO -> "Contexto o situación de sobrecarga"
                Ambito.DBT -> "Emoción o situación asociada"
                Ambito.ANIMO -> "Contexto (casa, trabajo, estudio…)"
                Ambito.ADICCIONES -> "Desencadenante del impulso"
            }) }, isError = f.errores["contexto"] != null, supportingText = f.errores["contexto"]?.let { msg -> { Text(msg) } },
                modifier = Modifier.fillMaxWidth().testTag("contexto"), minLines = 2)
            if (a == Ambito.NEURODESARROLLO || a == Ambito.ADICCIONES) {
                val tipo = if (a == Ambito.NEURODESARROLLO) TipoRecurso.ESTRATEGIA else TipoRecurso.ACCION
                CampoSelect(if (a == Ambito.NEURODESARROLLO) "Estrategia utilizada" else "Acción protectora", f.estrategia,
                    catalogo.filter { it.tipo == tipo }.map { it.titulo }, f.errores["estrategia"], form::onEstrategia)
            }
            if (a in listOf(Ambito.NEURODESARROLLO, Ambito.DBT)) {
                ScaleField(if (a == Ambito.DBT) "Utilidad percibida" else "Resultado percibido", f.resultado, f.errores["resultado"], form::onResultado)
            }
            if (a == Ambito.ADICCIONES) {
                Column {
                    Text("Resultado", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Resistí", "Cedí", "En proceso").forEachIndexed { i, text -> FilterChip(f.resultado == i + 1, { form.onResultado(i + 1) }, label = { Text(text) }) }
                    }
                    MensajeError(f.errores["resultado"])
                }
                val goals = state.objetivos.filterNot { it.eliminado }
                val choices = goals.mapIndexed { index, o -> "${index + 1}. ${o.descripcion}" }
                val selected = goals.indexOfFirst { it.id == f.objetivoId }
                SelectField("Objetivo de prueba (opcional)", choices.getOrNull(selected) ?: "Sin objetivo",
                    listOf("Sin objetivo") + choices) { seleccion -> form.onObjetivo(goals.getOrNull(choices.indexOf(seleccion))?.id) }
            }
            val skill = catalogo.find { it.titulo == if (a == Ambito.DBT) f.categoria else f.estrategia }
            skill?.let { Text(it.contenido, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedTextField(f.nota, form::onNota, label = { Text("Nota opcional") },
                isError = f.errores["nota"] != null,
                supportingText = { Text(f.errores["nota"] ?: "${f.nota.length}/1000 · Usa datos ficticios") },
                modifier = Modifier.fillMaxWidth().testTag("nota"), minLines = 3)
            error?.let { ErrorPanel(it) }
        }
        Button(onClick = {
            val registro = form.intentarGuardar()
            if (registro != null) onSave(registro) else scope.launch { scroll.animateScrollTo(0) }
        }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp).testTag("guardarRegistro")) {
            Text(if (busy) "Guardando…" else "Guardar registro")
        }
    }
}

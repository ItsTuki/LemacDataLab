package com.losk.lemacdatalab.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.losk.lemacdatalab.data.AppState
import com.losk.lemacdatalab.model.Objetivo
import com.losk.lemacdatalab.ui.ErrorPanel
import com.losk.lemacdatalab.ui.SelectField
import com.losk.lemacdatalab.viewmodel.AppViewModel

@Composable
fun ObjetivosScreen(state: AppState, busy: Boolean, error: String?, vm: AppViewModel) {
    var descripcion by rememberSaveable { mutableStateOf("") }
    var meta by rememberSaveable { mutableStateOf("3") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var estado by rememberSaveable { mutableStateOf("En curso") }
    var borrar by remember { mutableStateOf<Objetivo?>(null) }
    val previous = state.objetivos.find { it.id == editId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Metas para tus registros de prueba", style = MaterialTheme.typography.headlineSmall)
            Text("El progreso cuenta registros de Adicciones asociados a este objetivo con resultado «Resistí».")
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (previous == null) "Crear objetivo" else "Editar objetivo", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción de prueba") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(meta, { meta = it }, label = { Text("Meta de registros (1–100)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    SelectField("Estado", estado, listOf("En curso", "Cumplido", "No cumplido")) { estado = it }
                    error?.let { ErrorPanel(it) }
                    Button(onClick = { vm.guardar(Objetivo(id = previous?.id ?: java.util.UUID.randomUUID().toString(), usuarioId = state.cuenta!!.id,
                        descripcion = descripcion.trim(), meta = meta.toIntOrNull() ?: 0, estado = estado, fecha = previous?.fecha ?: System.currentTimeMillis())) {
                        descripcion = ""; meta = "3"; estado = "En curso"; editId = null
                    } }, enabled = !busy) { Text("Guardar objetivo") }
                    if (editId != null) TextButton(onClick = { editId = null; descripcion = ""; meta = "3"; estado = "En curso"; vm.limpiarError() }) { Text("Cancelar edición") }
                }
            }
        }
        val objetivos = state.objetivos.filterNot { it.eliminado }.sortedByDescending { it.fecha }
        if (objetivos.isEmpty()) item { Text("Todavía no tienes objetivos de prueba.") }
        items(objetivos, key = { it.id }) { o ->
            val progreso = o.progreso(state.registros)
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(o.descripcion, style = MaterialTheme.typography.titleMedium)
                    Text(if (o.estado == "En curso" && progreso >= o.meta) "Cumplido · meta alcanzada" else o.estado)
                    // Animación con propósito: al registrar un «Resistí» la barra avanza suavemente y el usuario ve que su registro sumó.
                    val avance by animateFloatAsState((progreso.toFloat() / o.meta).coerceIn(0f, 1f), tween(600), label = "avanceObjetivo")
                    LinearProgressIndicator(progress = { avance }, modifier = Modifier.fillMaxWidth())
                    Text("$progreso / ${o.meta} registros · ${if(o.pendiente) "Pendiente" else "Sincronizado"}")
                    Row {
                        TextButton(onClick = { editId = o.id; descripcion = o.descripcion; meta = o.meta.toString(); estado = o.estado; vm.limpiarError() }, enabled = !busy) { Text("Editar") }
                        TextButton(onClick = { borrar = o }, enabled = !busy) { Text("Eliminar") }
                    }
                }
            }
        }
    }
    borrar?.let { o -> AlertDialog(onDismissRequest = { borrar = null }, title = { Text("Eliminar objetivo") }, text = { Text("Los registros asociados se conservarán en tu historial.") },
        confirmButton = { TextButton(onClick = { vm.guardar(o.copy(eliminado = true)); borrar = null }, enabled = !busy) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } }) }
}

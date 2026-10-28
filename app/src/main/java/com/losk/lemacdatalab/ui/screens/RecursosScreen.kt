package com.losk.lemacdatalab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.losk.lemacdatalab.data.AppState
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.ui.*
import com.losk.lemacdatalab.viewmodel.AppViewModel

@Composable
fun RecursosScreen(state: AppState, busy: Boolean, error: String?, vm: AppViewModel) {
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    val admin = state.cuenta!!.administrador
    val filtered = state.recursos.filter { !it.eliminado && (filtro == null || it.ambito.name == filtro) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Recursos y catálogos", style = MaterialTheme.typography.headlineMedium)
            Text("Contenido ilustrativo para el MVP académico", style = MaterialTheme.typography.bodySmall)
            ScopeSelector(filtro?.let(Ambito::valueOf), { filtro = it?.name })
            error?.let { ErrorPanel(it) }
            if (admin) Button(onClick = { editing = true; editId = null; vm.limpiarError() }, enabled = !busy) { Text("Crear recurso o habilidad") }
        }
        if (filtered.isEmpty()) item { Text("No hay recursos para este ámbito.") }
        items(filtered, key = { it.id }) { r ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${r.ambito.titulo} · ${r.tipo.titulo}", style = MaterialTheme.typography.labelMedium)
                    Text(r.titulo, style = MaterialTheme.typography.titleMedium)
                    Text(r.contenido)
                    if (admin) {
                        Text(if (r.pendiente) "Cambios locales pendientes de publicar en la API" else "Disponible en el catálogo local", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { editId = r.id; editing = true; vm.limpiarError() }, enabled = !busy) { Text("Editar contenido") }
                    }
                }
            }
        }
    }
    if (editing) key(editId ?: "nuevo") {
        val prev = state.recursos.find { it.id == editId }
        var ambito by rememberSaveable { mutableStateOf(prev?.ambito?.name ?: filtro ?: Ambito.ANIMO.name) }
        var tipo by rememberSaveable { mutableStateOf(prev?.tipo?.name ?: TipoRecurso.TEXTO.name) }
        var titulo by rememberSaveable { mutableStateOf(prev?.titulo ?: "") }
        var contenido by rememberSaveable { mutableStateOf(prev?.contenido ?: "") }
        AlertDialog(onDismissRequest = { if (!busy) { editing = false; vm.limpiarError() } }, title = { Text(if (prev == null) "Nuevo recurso" else "Editar recurso") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectField("Ámbito", Ambito.valueOf(ambito).titulo, Ambito.entries.map { it.titulo }) { label -> ambito = Ambito.entries.first { it.titulo == label }.name }
                    val types = if (prev?.tipo == TipoRecurso.PARAMETRO) listOf(TipoRecurso.PARAMETRO) else TipoRecurso.entries.filter { it != TipoRecurso.PARAMETRO }
                    SelectField("Tipo de contenido", TipoRecurso.valueOf(tipo).titulo, types.map { it.titulo }) { label -> tipo = TipoRecurso.entries.first { it.titulo == label }.name }
                    OutlinedTextField(titulo, { titulo = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(contenido, { contenido = it }, label = { Text("Contenido de prueba") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    if (prev?.tipo == TipoRecurso.PARAMETRO) Text("Escribe una categoría por línea. Estas opciones aparecerán en el formulario de Ánimo.")
                    error?.let { ErrorPanel(it) }
                }
            }, confirmButton = { TextButton(onClick = { vm.guardar(Recurso(id = prev?.id ?: java.util.UUID.randomUUID().toString(), ambito = Ambito.valueOf(ambito),
                tipo = TipoRecurso.valueOf(tipo), titulo = titulo.trim(), contenido = contenido.trim())) { editing = false } }, enabled = !busy) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editing = false; vm.limpiarError() }, enabled = !busy) { Text("Cancelar") } })
    }
}

package com.losk.lemacdatalab.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.losk.lemacdatalab.data.AppState
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.ui.*
import java.time.LocalDate
import java.util.Locale

@Composable
fun HistorialScreen(state: AppState, ambito: Ambito?, onScope: (Ambito?) -> Unit, busy: Boolean, error: String?, onEdit: (Registro) -> Unit, onDelete: (Registro) -> Unit) {
    var desde by rememberSaveable { mutableStateOf("") }
    var hasta by rememberSaveable { mutableStateOf("") }
    var borrar by remember { mutableStateOf<Registro?>(null) }
    fun parse(s: String) = if (s.isBlank()) null else runCatching { LocalDate.parse(s) }.getOrNull()
    val inicio = parse(desde); val fin = parse(hasta)
    val dateError = when {
        (desde.isNotBlank() && inicio == null) || (hasta.isNotBlank() && fin == null) -> "Usa fechas válidas con formato AAAA-MM-DD."
        inicio != null && fin != null && inicio > fin -> "La fecha inicial debe ser anterior a la final."
        else -> null
    }
    val registros = if (dateError == null) filtrarRegistros(state.registros, ambito, inicio, fin) else emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Mi historial", style = MaterialTheme.typography.headlineMedium); ScopeSelector(ambito, onScope) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(desde, { desde = it }, label = { Text("Desde") }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(hasta, { hasta = it }, label = { Text("Hasta") }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { desde = ""; hasta = ""; onScope(null) }) { Text("Limpiar filtros") }
            dateError?.let { ErrorPanel(it) }
            error?.let { ErrorPanel(it) }
        }
        if (ambito == Ambito.ANIMO && registros.isNotEmpty()) item { EvolucionAnimo(registros) }
        item { Text("${registros.size} registros", style = MaterialTheme.typography.titleSmall) }
        if (registros.isEmpty() && dateError == null) item {
            Text("No hay registros en este periodo. Puedes crear uno desde Inicio o cambiar los filtros.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(registros, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.ambito.titulo, style = MaterialTheme.typography.labelLarge)
                    Text(r.categoria, style = MaterialTheme.typography.titleMedium)
                    Text(fechaTexto(r.fecha), style = MaterialTheme.typography.bodySmall)
                    Text("Intensidad: ${r.intensidad}/5 · ${if (r.pendiente) "Pendiente" else "Sincronizado"}", style = MaterialTheme.typography.bodySmall)
                    Text(r.contexto)
                    if (r.estrategia.isNotBlank()) Text("${if (r.ambito == Ambito.DBT) "Habilidad" else "Estrategia / acción"}: ${r.estrategia}")
                    when(r.ambito) {
                        Ambito.DBT, Ambito.NEURODESARROLLO -> Text("Utilidad / resultado percibido: ${r.resultado}/5")
                        Ambito.ADICCIONES -> {
                            Text("Resultado: ${listOf("Resistí", "Cedí", "En proceso").getOrNull(r.resultado - 1).orEmpty()}")
                            state.objetivos.find { it.id == r.objetivoId }?.let { Text("Objetivo: ${it.descripcion}") }
                        }
                        else -> Unit
                    }
                    if (r.nota.isNotBlank()) Text(r.nota)
                    Row {
                        TextButton(onClick = { onEdit(r) }, enabled = !busy) { Text("Editar") }
                        TextButton(onClick = { borrar = r }, enabled = !busy) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    borrar?.let { r -> AlertDialog(onDismissRequest = { borrar = null }, title = { Text("Eliminar registro") },
        text = { Text("Se quitará de tu historial. La eliminación se enviará a la API cuando sincronices.") },
        confirmButton = { TextButton(onClick = { onDelete(r); borrar = null }, enabled = !busy, modifier = Modifier.testTag("confirmarEliminar")) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } }) }
}

@Composable
fun EvolucionAnimo(registros: List<Registro>) {
    val puntos = registros.sortedBy { it.fecha }.takeLast(30)
    val color = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val locale = LocalConfiguration.current.locales[0]
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Evolución de la intensidad", style = MaterialTheme.typography.titleMedium)
            Text("Promedio del periodo: ${String.format(locale, "%.1f", registros.map { it.intensidad }.average())}/5", style = MaterialTheme.typography.bodySmall)
            Text("Escala de autorreporte · últimos ${puntos.size} check-ins", style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(140.dp).semantics {
                contentDescription = puntos.joinToString("; ") { "${fechaTexto(it.fecha)}, ${it.categoria}, intensidad ${it.intensidad} de 5" }
            }) {
                val pad = 8.dp.toPx(); val w = size.width - pad * 2; val h = size.height - pad * 2
                for (n in 0..4) drawLine(line, Offset(pad, pad + h * n / 4), Offset(pad + w, pad + h * n / 4), 1.dp.toPx())
                val minDate = puntos.first().fecha; val maxDate = puntos.last().fecha
                val coords = puntos.map { r ->
                    val x = if (maxDate == minDate) .5f else ((r.fecha - minDate).toDouble() / (maxDate - minDate)).toFloat()
                    Offset(pad + x * w, pad + (5 - r.intensidad) / 4f * h)
                }
                coords.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 2.dp.toPx()) }
                coords.forEach { drawCircle(color, 4.dp.toPx(), it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fechaTexto(puntos.first().fecha).take(10), style = MaterialTheme.typography.labelSmall)
                Text(fechaTexto(puntos.last().fecha).take(10), style = MaterialTheme.typography.labelSmall)
            }
            Text("Las categorías de ánimo aparecen en cada registro del historial.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

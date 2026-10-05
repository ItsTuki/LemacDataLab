package com.losk.lemacdatalab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.losk.lemacdatalab.data.AppState
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.ui.colorAmbito

@Composable
fun InicioScreen(state: AppState, onModule: (Ambito) -> Unit, onHistory: () -> Unit, onGoals: () -> Unit) {
    val visibles = state.registros.filterNot { it.eliminado }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Hola, ${state.cuenta!!.alias}", style = MaterialTheme.typography.headlineMedium)
            Text("Elige un ámbito para comenzar", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${visibles.size} registros · ${state.pendientes} cambios pendientes", style = MaterialTheme.typography.titleMedium)
                    Text(if (state.conectado) state.mensajeSync else "Tus registros se guardan en este dispositivo. Vincula la API desde Perfil para sincronizar.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Ambito.entries.chunked(2).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEach { a ->
                        val c = colorAmbito(a)
                        Card(onClick = { onModule(a) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = c.copy(alpha = .10f))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(when(a) { Ambito.NEURODESARROLLO -> "ND"; Ambito.DBT -> "DBT"; Ambito.ANIMO -> "1–5"; Ambito.ADICCIONES -> "∞" }, color = c, style = MaterialTheme.typography.headlineMedium)
                                Text(a.titulo, style = MaterialTheme.typography.titleMedium)
                                Text(a.descripcion, style = MaterialTheme.typography.bodySmall)
                                Text("${visibles.count { it.ambito == a }} registros", style = MaterialTheme.typography.labelMedium)
                                Text("Registrar →", color = c, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        item { OutlinedButton(onHistory, Modifier.fillMaxWidth()) { Text("Revisar mi historial") } }
        item { OutlinedButton(onGoals, Modifier.fillMaxWidth()) { Text("Mis objetivos de prueba") } }
        item { Text("MVP académico · Usa únicamente datos ficticios. Las escalas representan autorreportes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

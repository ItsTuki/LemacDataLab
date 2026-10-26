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
import com.losk.lemacdatalab.data.AppState
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

fun fechaTexto(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

@Composable
fun ScaleField(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n -> FilterChip(value == n, { onChange(n) }, label = { Text("$n") }, modifier = Modifier.weight(1f)) }
        }
        Text("1 = menor · 5 = mayor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RegistroScreen(a: Ambito, owner: String, existente: Registro?, state: AppState, busy: Boolean, error: String?, onSave: (Registro) -> Unit) {
    val formKey = existente?.id ?: a.name
    var id by rememberSaveable(formKey) { mutableStateOf(existente?.id ?: UUID.randomUUID().toString()) }
    var fecha by rememberSaveable(formKey) { mutableLongStateOf(existente?.fecha ?: System.currentTimeMillis()) }
    var intensidad by rememberSaveable(formKey) { mutableIntStateOf(existente?.intensidad ?: 0) }
    var categoria by rememberSaveable(formKey) { mutableStateOf(existente?.categoria ?: if (a == Ambito.ADICCIONES) "Impulso" else "") }
    var contexto by rememberSaveable(formKey) { mutableStateOf(existente?.contexto ?: "") }
    var estrategia by rememberSaveable(formKey) { mutableStateOf(existente?.estrategia ?: "") }
    var resultado by rememberSaveable(formKey) { mutableIntStateOf(existente?.resultado ?: 0) }
    var nota by rememberSaveable(formKey) { mutableStateOf(existente?.nota ?: "") }
    var objetivoId by rememberSaveable(formKey) { mutableStateOf(existente?.objetivoId) }
    val catalogo = state.recursos.filter { !it.eliminado && it.ambito == a }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = colorAmbito(a).copy(alpha = .10f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (existente == null) "Nuevo registro" else "Editar registro", style = MaterialTheme.typography.headlineSmall)
                    Text(a.descripcion)
                    Text(fechaTexto(fecha), style = MaterialTheme.typography.bodySmall)
                }
            }
            when (a) {
                Ambito.NEURODESARROLLO -> OutlinedTextField(categoria, { categoria = it }, label = { Text("Rutina o tarea") }, modifier = Modifier.fillMaxWidth().testTag("categoria"), singleLine = true)
                Ambito.DBT -> SelectField("Habilidad practicada", categoria, catalogo.filter { it.tipo == TipoRecurso.HABILIDAD }.map { it.titulo }) { categoria = it; estrategia = it }
                Ambito.ANIMO -> SelectField("Estado de ánimo", categoria,
                    catalogo.find { it.id == "animo-categorias" && it.tipo == TipoRecurso.PARAMETRO }?.contenido?.lines()?.map { it.trim() }
                        ?: listOf("Muy bajo", "Bajo", "Neutral", "Bueno", "Muy bueno")) { categoria = it }
                Ambito.ADICCIONES -> SelectField("Tipo de registro", categoria, listOf("Impulso", "Evento de prueba")) { categoria = it }
            }
            ScaleField(if (a == Ambito.ADICCIONES) "Intensidad del impulso" else "Intensidad percibida", intensidad) { intensidad = it }
            OutlinedTextField(contexto, { contexto = it }, label = { Text(when(a) {
                Ambito.NEURODESARROLLO -> "Contexto o situación de sobrecarga"
                Ambito.DBT -> "Emoción o situación asociada"
                Ambito.ANIMO -> "Contexto (casa, trabajo, estudio…)"
                Ambito.ADICCIONES -> "Desencadenante del impulso"
            }) }, modifier = Modifier.fillMaxWidth().testTag("contexto"), minLines = 2)
            if (a == Ambito.NEURODESARROLLO || a == Ambito.ADICCIONES) {
                val tipo = if (a == Ambito.NEURODESARROLLO) TipoRecurso.ESTRATEGIA else TipoRecurso.ACCION
                SelectField(if (a == Ambito.NEURODESARROLLO) "Estrategia utilizada" else "Acción protectora", estrategia,
                    catalogo.filter { it.tipo == tipo }.map { it.titulo }) { estrategia = it }
            }
            if (a in listOf(Ambito.NEURODESARROLLO, Ambito.DBT)) {
                ScaleField(if (a == Ambito.DBT) "Utilidad percibida" else "Resultado percibido", resultado) { resultado = it }
            }
            if (a == Ambito.ADICCIONES) {
                Text("Resultado", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Resistí", "Cedí", "En proceso").forEachIndexed { i, text -> FilterChip(resultado == i + 1, { resultado = i + 1 }, label = { Text(text) }) }
                }
                val goals = state.objetivos.filterNot { it.eliminado }
                val choices = goals.mapIndexed { index, o -> "${index + 1}. ${o.descripcion}" }
                val selected = goals.indexOfFirst { it.id == objetivoId }
                SelectField("Objetivo de prueba (opcional)", choices.getOrNull(selected) ?: "Sin objetivo",
                    listOf("Sin objetivo") + choices) { seleccion -> objetivoId = goals.getOrNull(choices.indexOf(seleccion))?.id }
            }
            val skill = catalogo.find { it.titulo == if (a == Ambito.DBT) categoria else estrategia }
            skill?.let { Text(it.contenido, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedTextField(nota, { nota = it }, label = { Text("Nota opcional") }, supportingText = { Text("${nota.length}/1000 · Usa datos ficticios") },
                modifier = Modifier.fillMaxWidth().testTag("nota"), minLines = 3)
            error?.let { ErrorPanel(it) }
        }
        Button(onClick = { onSave(Registro(id = id, usuarioId = owner, ambito = a, fecha = fecha, intensidad = intensidad,
            categoria = categoria.trim(), contexto = contexto.trim(), estrategia = estrategia, resultado = resultado,
            nota = nota.trim(), objetivoId = objetivoId)) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp).testTag("guardarRegistro")) {
            Text(if (busy) "Guardando…" else "Guardar registro")
        }
    }
}

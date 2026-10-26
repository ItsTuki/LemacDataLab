@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.losk.lemacdatalab.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.losk.lemacdatalab.data.AppState
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.notifications.ReminderScheduler
import com.losk.lemacdatalab.ui.*
import com.losk.lemacdatalab.viewmodel.AppViewModel
import java.util.Locale

@Composable
fun PerfilScreen(state: AppState, busy: Boolean, error: String?, vm: AppViewModel, onGoals: () -> Unit) {
    val context = LocalContext.current
    var hora by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%02d:%02d", state.recordatorio.hora, state.recordatorio.minuto)) }
    var frecuencia by rememberSaveable { mutableStateOf(state.recordatorio.frecuencia) }
    var scope by rememberSaveable { mutableStateOf(state.recordatorio.ambito?.name) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var url by rememberSaveable(state.apiUrl) { mutableStateOf(state.apiUrl) }
    var password by rememberSaveable { mutableStateOf("") }
    var logout by remember { mutableStateOf(false) }
    var pendingReminder by remember { mutableStateOf<Recordatorio?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingReminder?.let(vm::recordatorio)
        else localError = "No se activó el recordatorio. Puedes permitir las notificaciones en Ajustes de Android."
        pendingReminder = null
    }
    fun reminder(active: Boolean) {
        val parts = hora.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull(); val m = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size != 2 || h == null || m == null || h !in 0..23 || m !in 0..59) {
            localError = "Usa un horario válido de 24 horas, por ejemplo 20:30."; return
        }
        localError = null
        val value = Recordatorio(active, h, m, frecuencia, scope?.let(Ambito::valueOf))
        if (active && !ReminderScheduler.permitido(context)) {
            pendingReminder = value
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.recordatorio(value)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(state.cuenta!!.alias, style = MaterialTheme.typography.headlineMedium)
            Text(if (state.cuenta.administrador) "Responsable de contenidos de prueba" else "Cuenta de usuario de prueba")
            error?.let { ErrorPanel(it) }; localError?.let { ErrorPanel(it) }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mi actividad", style = MaterialTheme.typography.titleLarge)
                    Ambito.entries.forEach { a -> Text("${a.titulo}: ${state.registros.count { !it.eliminado && it.ambito == a }} registros") }
                    Text("${state.objetivos.count { !it.eliminado }} objetivos de prueba")
                    OutlinedButton(onGoals) { Text("Gestionar objetivos") }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Recordatorios discretos", style = MaterialTheme.typography.titleLarge)
                    Text("El aviso muestra únicamente «Tienes una actividad pendiente». Android puede retrasar la entrega para ahorrar batería.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(hora, { hora = it }, label = { Text("Horario (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SelectField("Frecuencia", frecuencia, listOf("Diario", "Lunes a viernes", "Semanal")) { frecuencia = it }
                    SelectField("Ámbito del recordatorio", scope?.let { Ambito.valueOf(it).titulo } ?: "General",
                        listOf("General") + Ambito.entries.map { it.titulo }) { label -> scope = Ambito.entries.find { it.titulo == label }?.name }
                    Text(if (state.recordatorio.activo && ReminderScheduler.permitido(context)) "Recordatorio activo" else "Recordatorio inactivo o sin permiso", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            reminder(true)
                        }, enabled = !busy) { Text("Activar / guardar") }
                        TextButton(onClick = { vm.recordatorio(state.recordatorio.copy(activo = false)) }, enabled = !busy) { Text("Desactivar") }
                    }
                    if (!ReminderScheduler.permitido(context)) TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text("Abrir ajustes de notificaciones") }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("API de prueba", style = MaterialTheme.typography.titleLarge)
                    Text(state.mensajeSync)
                    Text("${state.pendientes} cambios pendientes", style = MaterialTheme.typography.labelLarge)
                    if (state.ultimaSync > 0) Text("Última sincronización: ${fechaTexto(state.ultimaSync)}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(url, { url = it }, label = { Text("URL de la API") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Emulador: http://10.0.2.2:8080 · Dispositivo por USB: http://127.0.0.1:8080 con adb reverse.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(password, { password = it }, label = { Text("Contraseña de la cuenta de prueba") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.conectar(url, password); password = "" }, enabled = !busy && !state.sincronizando) { Text("Vincular API") }
                    OutlinedButton(onClick = vm::sincronizar, enabled = !busy && state.conectado && !state.sincronizando) { Text(if(state.sincronizando) "Sincronizando…" else "Sincronizar ahora") }
                    Text("Después de vincular, los cambios se envían al recuperar conexión y en segundo plano. Sin API activa, permanecen en el dispositivo.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Privacidad del MVP", style = MaterialTheme.typography.titleMedium)
            Text("Usa solo alias y registros sintéticos. Las contraseñas se verifican con hashes; los registros se guardan en el espacio privado de la app. Este MVP no ofrece diagnóstico, tratamiento ni atención de urgencia.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { logout = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
        }
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Cerrar sesión") }, text = { Text("Tus registros locales se conservarán. Los recordatorios y la sincronización se detendrán hasta el próximo acceso y la vinculación de la API.") },
        confirmButton = { TextButton(onClick = { logout = false; vm.salir() }) { Text("Cerrar sesión") } }, dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancelar") } })
}

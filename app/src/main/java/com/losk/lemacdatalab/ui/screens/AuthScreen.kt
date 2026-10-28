package com.losk.lemacdatalab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.losk.lemacdatalab.ui.ErrorPanel

@Composable
fun LoginScreen(busy: Boolean, error: String?, onClearError: () -> Unit, onLogin: (String, String, Boolean) -> Unit) {
    var alias by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var registrar by rememberSaveable { mutableStateOf(false) }
    var mostrar by rememberSaveable { mutableStateOf(false) }
    var aceptado by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Text("L", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Lemac DataLab", style = MaterialTheme.typography.headlineLarge)
        Text("Tu registro, en un solo lugar", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Text(if (registrar) "Crear cuenta de prueba" else "Iniciar sesión", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(alias, { alias = it; onClearError() }, label = { Text("Alias de prueba") }, singleLine = true,
            supportingText = { Text("3 a 24 letras, números o guion bajo") }, modifier = Modifier.fillMaxWidth().testTag("alias"), enabled = !busy,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next))
        OutlinedTextField(password, { password = it; onClearError() }, label = { Text("Contraseña") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("password"), enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            visualTransformation = if (mostrar) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { mostrar = !mostrar }) { Text(if (mostrar) "Ocultar" else "Ver") } })
        if (registrar) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(aceptado, { aceptado = it })
                Text("Usaré únicamente alias y datos ficticios en este MVP académico.", style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { ErrorPanel(it) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onLogin(alias.trim(), password, registrar) }, enabled = !busy && (!registrar || aceptado),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("login")) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (registrar) "Crear cuenta" else "Ingresar")
        }
        TextButton(onClick = { registrar = !registrar; onClearError() }, enabled = !busy) {
            Text(if (registrar) "Ya tengo una cuenta" else "Crear una cuenta de prueba")
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Acceso de demostración: demo / Demo12345!", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text("MVP académico con datos sintéticos. No ofrece diagnóstico, tratamiento ni atención de urgencia.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

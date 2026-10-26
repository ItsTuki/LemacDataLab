@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.losk.lemacdatalab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.ui.screens.*
import com.losk.lemacdatalab.viewmodel.AppViewModel

@Composable fun colorAmbito(a: Ambito): Color {
    if (androidx.compose.foundation.isSystemInDarkTheme()) return when (a) {
        Ambito.NEURODESARROLLO -> Color(0xFF94D5AC)
        Ambito.DBT -> Color(0xFF9CBDED)
        Ambito.ANIMO -> Color(0xFFEBC374)
        Ambito.ADICCIONES -> Color(0xFFC5ADF2)
    }
    return when (a) {
    Ambito.NEURODESARROLLO -> Color(0xFF287447)
    Ambito.DBT -> Color(0xFF245EA9)
    Ambito.ANIMO -> Color(0xFF8D5E12)
    Ambito.ADICCIONES -> Color(0xFF6743B5)
    }
}

@Composable fun ErrorPanel(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
    }
}

@Composable fun ScopeSelector(selected: Ambito?, onSelect: (Ambito?) -> Unit, todos: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (todos) FilterChip(selected == null, { onSelect(null) }, label = { Text("Todos") })
        Ambito.entries.forEach { a -> FilterChip(selected == a, { onSelect(a) }, label = { Text(a.titulo) }) }
    }
}

@Composable fun SelectField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(value, {}, label = { Text(label) }, readOnly = true, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("select-$label"))
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@Composable fun NavGlyph(index: Int) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val h = size.height; val s = 2.dp.toPx()
        when (index) {
            0 -> { drawLine(color, Offset(w * .1f, h * .45f), Offset(w * .5f, h * .1f), s); drawLine(color, Offset(w * .5f, h * .1f), Offset(w * .9f, h * .45f), s)
                drawRect(color, Offset(w * .22f, h * .42f), androidx.compose.ui.geometry.Size(w * .56f, h * .48f), style = Stroke(s)) }
            1 -> { drawCircle(color, w * .4f, style = Stroke(s)); drawLine(color, center, Offset(w * .5f, h * .25f), s); drawLine(color, center, Offset(w * .7f, h * .65f), s) }
            2 -> { drawRect(color, Offset(w * .2f, h * .1f), androidx.compose.ui.geometry.Size(w * .6f, h * .8f), style = Stroke(s))
                for (y in listOf(.35f, .5f, .65f)) drawLine(color, Offset(w * .35f, h * y), Offset(w * .65f, h * y), s) }
            3 -> { drawCircle(color, w * .16f, Offset(w * .5f, h * .3f), style = Stroke(s)); drawArc(color, 180f, 180f, false, Offset(w * .15f, h * .6f), androidx.compose.ui.geometry.Size(w * .7f, h * .5f), style = Stroke(s)) }
        }
    }
}

@Composable
fun LemacApp(vm: AppViewModel = viewModel(), reminderScope: String? = null, onReminderHandled: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    if (state.cargando) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }; return }
    if (state.cuenta == null) { LoginScreen(busy, error, vm::limpiarError, vm::ingresar); return }
    val account = state.cuenta!!
    key(account.id) {
        var route by rememberSaveable { mutableStateOf("Inicio") }
        var formScope by rememberSaveable { mutableStateOf<String?>(null) }
        var editId by rememberSaveable { mutableStateOf<String?>(null) }
        var objetivos by rememberSaveable { mutableStateOf(false) }
        var filtroHistorial by rememberSaveable { mutableStateOf<String?>(null) }
        LaunchedEffect(reminderScope) {
            if (reminderScope != null && Ambito.entries.any { it.name == reminderScope }) {
                formScope = reminderScope; editId = null; onReminderHandled()
            }
        }
        val snack = remember { SnackbarHostState() }
        var savedMessage by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(savedMessage) { savedMessage?.let { snack.showSnackbar(it); savedMessage = null } }
        val closeForm = { formScope = null; editId = null; vm.limpiarError() }
        BackHandler(formScope != null || objetivos || route != "Inicio") {
            when { formScope != null -> closeForm(); objetivos -> objetivos = false; else -> route = "Inicio" }
        }
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = { TopAppBar(title = { Text(if (formScope != null) Ambito.valueOf(formScope!!).titulo else if (objetivos) "Objetivos de prueba" else "Lemac DataLab") },
                navigationIcon = { if (formScope != null || objetivos) TextButton(onClick = { closeForm(); objetivos = false }) { Text("Volver") } }) },
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = { if (formScope == null && !objetivos) NavigationBar {
                listOf("Inicio", "Historial", "Recursos", "Perfil").forEachIndexed { index, item ->
                    NavigationBarItem(route == item, { route = item; vm.limpiarError() }, icon = { NavGlyph(index) }, label = { Text(item) })
                }
            } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    formScope != null -> {
                        val existing = state.registros.firstOrNull { it.id == editId && !it.eliminado }
                        RegistroScreen(Ambito.valueOf(formScope!!), account.id, existing, state, busy, error) { registro ->
                            vm.guardar(registro) { closeForm(); route = "Historial"; filtroHistorial = registro.ambito.name; savedMessage = "Registro guardado en el dispositivo" }
                        }
                    }
                    objetivos -> ObjetivosScreen(state, busy, error, vm)
                    route == "Inicio" -> InicioScreen(state, onModule = { formScope = it.name; editId = null; vm.limpiarError() },
                        onHistory = { filtroHistorial = null; route = "Historial" }, onGoals = { objetivos = true })
                    route == "Historial" -> HistorialScreen(state, filtroHistorial?.let(Ambito::valueOf), { filtroHistorial = it?.name }, busy, error,
                        onEdit = { editId = it.id; formScope = it.ambito.name; vm.limpiarError() }, onDelete = { vm.guardar(it.copy(eliminado = true)) { savedMessage = "Registro eliminado" } })
                    route == "Recursos" -> RecursosScreen(state, busy, error, vm)
                    else -> PerfilScreen(state, busy, error, vm, onGoals = { objetivos = true })
                }
            }
        }
    }
}

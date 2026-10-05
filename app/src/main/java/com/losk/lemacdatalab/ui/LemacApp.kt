@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.losk.lemacdatalab.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.ui.navigation.Rutas
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

/** Cambia de pestaña de la barra inferior conservando el estado de cada una. */
private fun NavHostController.irAPestana(ruta: String) = navigate(ruta) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
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
        val nav = rememberNavController()
        val entrada by nav.currentBackStackEntryAsState()
        val base = Rutas.base(entrada?.destination?.route)
        val enPestana = base in Rutas.pestanas.map { it.first }
        val snack = remember { SnackbarHostState() }
        var savedMessage by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(savedMessage) { savedMessage?.let { snack.showSnackbar(it); savedMessage = null } }
        // Un recordatorio abre directamente el formulario del ámbito configurado.
        // (se espera a que exista una entrada en la pila: así el NavHost ya tiene su grafo cargado)
        LaunchedEffect(reminderScope, entrada != null) {
            val ambito = Ambito.entries.firstOrNull { it.name == reminderScope }
            if (ambito != null && entrada != null) { nav.navigate(Rutas.registro(ambito)); onReminderHandled() }
        }
        val titulo = when (base) {
            Rutas.REGISTRO -> entrada?.arguments?.getString("ambito")?.let { n -> Ambito.entries.firstOrNull { it.name == n } }?.titulo ?: "Registro"
            Rutas.OBJETIVOS -> "Objetivos de prueba"
            else -> "Lemac DataLab"
        }
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = { TopAppBar(title = { Text(titulo) },
                navigationIcon = { if (!enPestana) TextButton(onClick = { vm.limpiarError(); nav.popBackStack() }) { Text("Volver") } }) },
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = { if (enPestana) NavigationBar {
                Rutas.pestanas.forEachIndexed { index, (ruta, texto) ->
                    NavigationBarItem(base == ruta, { vm.limpiarError(); nav.irAPestana(ruta) }, icon = { NavGlyph(index) }, label = { Text(texto) })
                }
            } }
        ) { padding ->
            NavHost(nav, startDestination = Rutas.INICIO, modifier = Modifier.fillMaxSize().padding(padding)) {
                composable(Rutas.INICIO) {
                    InicioScreen(state,
                        onModule = { vm.limpiarError(); nav.navigate(Rutas.registro(it)) },
                        onHistory = { nav.navigate(Rutas.historial()) { launchSingleTop = true } },
                        onGoals = { nav.navigate(Rutas.OBJETIVOS) })
                }
                composable(Rutas.HISTORIAL_PATRON,
                    arguments = listOf(navArgument("ambito") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                    val inicial = e.arguments?.getString("ambito")
                    var filtro by rememberSaveable(inicial) { mutableStateOf(inicial) }
                    HistorialScreen(state, filtro?.let(Ambito::valueOf), { filtro = it?.name }, busy, error,
                        onEdit = { vm.limpiarError(); nav.navigate(Rutas.registro(it.ambito, it.id)) },
                        onDelete = { vm.guardar(it.copy(eliminado = true)) { savedMessage = "Registro eliminado" } })
                }
                composable(Rutas.REGISTRO_PATRON,
                    arguments = listOf(
                        navArgument("ambito") { type = NavType.StringType },
                        navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    // El formulario entra deslizándose desde abajo: comunica que es una acción nueva sobre la pantalla actual.
                    enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(300)) },
                    popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(300)) }) { e ->
                    val ambito = e.arguments?.getString("ambito")?.let { n -> Ambito.entries.firstOrNull { it.name == n } }
                    if (ambito == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
                    val existente = state.registros.firstOrNull { it.id == e.arguments?.getString("id") && !it.eliminado }
                    RegistroScreen(ambito, account.id, existente, state, busy, error) { registro ->
                        vm.guardar(registro) {
                            nav.navigate(Rutas.historial(registro.ambito)) { popUpTo(Rutas.INICIO) }
                            savedMessage = "Registro guardado en el dispositivo"
                        }
                    }
                }
                composable(Rutas.RECURSOS) { RecursosScreen(state, busy, error, vm) }
                composable(Rutas.PERFIL) { PerfilScreen(state, busy, error, vm, onGoals = { nav.navigate(Rutas.OBJETIVOS) }) }
                composable(Rutas.OBJETIVOS) { ObjetivosScreen(state, busy, error, vm) }
            }
        }
    }
}

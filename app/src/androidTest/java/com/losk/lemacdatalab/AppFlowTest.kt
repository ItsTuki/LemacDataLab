package com.losk.lemacdatalab

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.losk.lemacdatalab.data.LemacRepository
import kotlinx.coroutines.runBlocking
import org.junit.*

class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var repo: LemacRepository
    @Before fun setup() = runBlocking {
        repo = LemacRepository.get(rule.activity)
        repo.cargar()
        repo.ingresar("ui_${java.util.UUID.randomUUID().toString().take(8)}", "Password123", true)
    }
    @After fun restoreDemo() = runBlocking { repo.ingresar("demo", "Demo12345!", false) }

    @Test fun crearEditarYEliminarAnimoDesdeLaInterfaz() {
        rule.onNodeWithText("Estado de ánimo").performClick()
        rule.onNodeWithTag("select-Estado de ánimo").performClick()
        rule.onNodeWithText("Neutral").performClick()
        rule.onNodeWithText("3", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("contexto").performTextInput("Contexto ficticio")
        rule.onNodeWithTag("nota").performScrollTo().performTextInput("Nota de prueba UI")
        rule.onNodeWithTag("guardarRegistro").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Nota de prueba UI").fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Nota de prueba UI").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Editar").performScrollTo().performClick()
        rule.onNodeWithTag("nota").performScrollTo().performTextReplacement("Nota corregida UI")
        rule.onNodeWithTag("guardarRegistro").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Nota corregida UI").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Eliminar").performScrollTo().performClick()
        rule.onNodeWithTag("confirmarEliminar").performClick()
        rule.waitUntil(15_000) { repo.state.value.registros.all { it.eliminado } }
        rule.onNodeWithText("0 registros").assertExists()
    }
}

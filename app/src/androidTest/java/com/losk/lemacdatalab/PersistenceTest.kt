package com.losk.lemacdatalab

import androidx.test.platform.app.InstrumentationRegistry
import com.losk.lemacdatalab.data.*
import com.losk.lemacdatalab.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class PersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-${java.util.UUID.randomUUID()}.db"
    private lateinit var db: LemacDatabase
    private lateinit var account: Cuenta
    @Before fun setup() { db = LemacDatabase(context, name); account = db.autenticar("demo", "Demo12345!") }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private fun record() = Registro(usuarioId = account.id, ambito = Ambito.ANIMO, intensidad = 3, categoria = "Neutral", contexto = "Prueba sintética")

    @Test fun persistenciaEdicionYBorrado() {
        val record = record()
        db.guardarRegistro(account.id, record)
        db.close(); db = LemacDatabase(context, name)
        assertEquals(record.id, db.registros(account.id).single().id)
        db.guardarRegistro(account.id, record.copy(nota = "Editado"))
        assertEquals("Editado", db.registros(account.id).single().nota)
        db.guardarRegistro(account.id, record.copy(eliminado = true))
        assertTrue(db.registros(account.id).single().eliminado)
        assertTrue(db.registros(account.id).single().pendiente)
    }

    @Test fun cuentasNoCompartenRegistrosNiPermisos() {
        val record = record(); db.guardarRegistro(account.id, record)
        val other = db.registrar("otro_usuario", "Password123")
        assertTrue(db.registros(other.id).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { db.guardarRegistro(other.id, record.copy(usuarioId = other.id)) }
        assertThrows(IllegalArgumentException::class.java) { db.autenticar("demo", "WrongPass") }
        assertThrows(IllegalArgumentException::class.java) { db.guardarRecurso(account.copy(administrador = true), CatalogoInicial.recursos.first()) }
        assertFalse(db.registrar("nuevo_demo", "Password123").administrador)
    }

    @Test fun ackNoBorraUnaEdicionHechaDuranteElEnvio() {
        val record = record(); db.guardarRegistro(account.id, record)
        val enviados = mapOf("registros" to mapOf(record.id to 1))
        db.guardarRegistro(account.id, record.copy(nota = "Cambio durante envío"))
        val response = JSONObject().put("registros", JSONArray().put(record.json())).put("objetivos", JSONArray()).put("recursos", JSONArray())
        db.aplicarSincronizacion(account.id, response, enviados)
        val nuevo = db.registros(account.id).single()
        assertTrue(nuevo.pendiente); assertEquals("Cambio durante envío", nuevo.nota)
        db.aplicarSincronizacion(account.id, JSONObject().put("registros", JSONArray().put(nuevo.json())).put("objetivos", JSONArray()).put("recursos", JSONArray()),
            mapOf("registros" to mapOf(record.id to 2)))
        assertFalse(db.registros(account.id).single().pendiente)
    }

    @Test fun objetivoEliminadoConservaHistorialEditable() {
        val goal = Objetivo(usuarioId = account.id, descripcion = "Objetivo de prueba")
        db.guardarObjetivo(account.id, goal)
        val r = record().copy(ambito = Ambito.ADICCIONES, estrategia = "Pausa", resultado = 1, objetivoId = goal.id)
        db.guardarRegistro(account.id, r)
        db.guardarObjetivo(account.id, goal.copy(eliminado = true))
        db.guardarRegistro(account.id, r.copy(nota = "Conservado"))
        db.guardarRegistro(account.id, r.copy(eliminado = true))
        assertTrue(db.registros(account.id).single().eliminado)
    }
}

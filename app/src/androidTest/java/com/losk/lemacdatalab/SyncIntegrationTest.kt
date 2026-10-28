package com.losk.lemacdatalab

import androidx.test.platform.app.InstrumentationRegistry
import com.losk.lemacdatalab.data.LemacRepository
import com.losk.lemacdatalab.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Ejecutar con la API de prueba activa en el puerto 8080 del equipo. */
class SyncIntegrationTest {
    @Test fun sincronizaCreacionEdicionYBorradoConLaApiReal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = LemacRepository.get(context)
        repo.cargar()
        try {
            repo.ingresar("sync_${java.util.UUID.randomUUID().toString().take(8)}", "Password123", true)
            val record = Registro(usuarioId = repo.state.value.cuenta!!.id, ambito = Ambito.ANIMO, intensidad = 3, categoria = "Neutral", contexto = "Contexto de integración")
            repo.guardar(record)
            assertTrue(repo.state.value.registros.single().pendiente)
            repo.conectar("http://10.0.2.2:8080", "Password123")
            assertTrue(repo.state.value.conectado)
            assertFalse(repo.state.value.registros.single().pendiente)
            repo.guardar(record.copy(nota = "Edición de integración"))
            assertTrue(repo.sincronizar())
            assertEquals("Edición de integración", repo.state.value.registros.single().nota)
            assertFalse(repo.state.value.registros.single().pendiente)
            repo.guardar(record.copy(nota = "Edición de integración", eliminado = true))
            assertTrue(repo.sincronizar())
            assertTrue(repo.state.value.registros.single().eliminado)
            assertFalse(repo.state.value.registros.single().pendiente)
        } finally {
            if (repo.state.value.cuenta != null) repo.salir()
            repo.ingresar("demo", "Demo12345!", false)
        }
    }
}

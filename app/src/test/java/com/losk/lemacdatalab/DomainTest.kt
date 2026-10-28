package com.losk.lemacdatalab

import com.losk.lemacdatalab.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DomainTest {
    private fun registro(ambito: Ambito = Ambito.ANIMO) = Registro(usuarioId = "a", ambito = ambito,
        intensidad = 3, categoria = "Prueba", contexto = "Contexto sintético", estrategia = if (ambito == Ambito.ANIMO) "" else "Habilidad", resultado = if (ambito == Ambito.ANIMO) 0 else 3)

    @Test fun todosLosModulosValidanDatos() {
        Ambito.entries.forEach { assertNull(Validaciones.registro(registro(it))) }
        assertNotNull(Validaciones.registro(registro().copy(intensidad = 0)))
        assertNotNull(Validaciones.registro(registro().copy(contexto = " ")))
        assertNotNull(Validaciones.registro(registro(Ambito.DBT).copy(estrategia = "")))
        assertNotNull(Validaciones.registro(registro(Ambito.NEURODESARROLLO).copy(resultado = 0)))
        assertNotNull(Validaciones.registro(registro().copy(nota = "x".repeat(1001))))
    }

    @Test fun aliasNoSolicitaCorreoReal() {
        assertNull(Validaciones.credenciales("usuario_demo", "Password123"))
        assertNotNull(Validaciones.credenciales("real@email.com", "Password123"))
        assertNotNull(Validaciones.credenciales("demo", "short"))
    }

    @Test fun filtroIncluyeTodoElDiaFinalYExcluyeEliminados() {
        val date = LocalDate.of(2026, 10, 11)
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val a = registro().copy(id = "inicio", fecha = start)
        val b = registro().copy(id = "fin", fecha = end)
        val fuera = registro().copy(fecha = end + 1)
        val borrado = registro().copy(fecha = start, eliminado = true)
        val otro = registro(Ambito.DBT).copy(fecha = start)
        assertEquals(listOf(b, a), filtrarRegistros(listOf(a, b, fuera, borrado, otro), Ambito.ANIMO, date, date))
    }

    @Test fun progresoCuentaSoloResultadosPropiosAsociados() {
        val objetivo = Objetivo(id = "g", usuarioId = "a", descripcion = "Prueba")
        val a = registro(Ambito.ADICCIONES).copy(objetivoId = "g", resultado = 1)
        assertEquals(1, objetivo.progreso(listOf(a, a.copy(eliminado = true), a.copy(objetivoId = "otro"), a.copy(resultado = 2), a.copy(ambito = Ambito.DBT))))
    }

    @Test fun catalogosRespetanElAmbito() {
        val recurso = Recurso(ambito = Ambito.DBT, tipo = TipoRecurso.HABILIDAD, titulo = "Prueba", contenido = "Contenido ficticio")
        assertNull(Validaciones.recurso(recurso))
        assertNotNull(Validaciones.recurso(recurso.copy(ambito = Ambito.ANIMO)))
        val parametro = CatalogoInicial.recursos.first { it.tipo == TipoRecurso.PARAMETRO }
        assertNull(Validaciones.recurso(parametro))
        assertNotNull(Validaciones.recurso(parametro.copy(contenido = "Bien\nBien")))
    }
}

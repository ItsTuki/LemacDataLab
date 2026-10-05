package com.losk.lemacdatalab.ui.navigation

import com.losk.lemacdatalab.model.Ambito

/**
 * Rutas de navegación de la app (ver docs: Mapa de navegación).
 *
 *  Login (si no hay sesión) → inicio ──┬── registro/{ambito}?id={id}   (nuevo o edición)
 *                                      ├── historial?ambito={ambito}   ──→ registro (editar)
 *                                      ├── recursos
 *                                      ├── perfil ──→ objetivos
 *                                      └── objetivos
 *
 * Se pasa el ID del registro (no su posición en la lista) para no editar/eliminar el elemento equivocado.
 */
object Rutas {
    const val INICIO = "inicio"
    const val HISTORIAL = "historial"
    const val RECURSOS = "recursos"
    const val PERFIL = "perfil"
    const val OBJETIVOS = "objetivos"
    const val REGISTRO = "registro"

    const val HISTORIAL_PATRON = "historial?ambito={ambito}"
    const val REGISTRO_PATRON = "registro/{ambito}?id={id}"

    /** Destinos de la barra inferior, en orden (ruta, texto). */
    val pestanas = listOf(INICIO to "Inicio", HISTORIAL to "Historial", RECURSOS to "Recursos", PERFIL to "Perfil")

    fun historial(ambito: Ambito? = null) = if (ambito == null) HISTORIAL else "$HISTORIAL?ambito=${ambito.name}"
    fun registro(ambito: Ambito, id: String? = null) = if (id == null) "$REGISTRO/${ambito.name}" else "$REGISTRO/${ambito.name}?id=$id"

    /** "historial?ambito={ambito}" → "historial"; "registro/{ambito}?id={id}" → "registro". */
    fun base(ruta: String?): String? = ruta?.substringBefore('?')?.substringBefore('/')
}

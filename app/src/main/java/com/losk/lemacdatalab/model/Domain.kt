package com.losk.lemacdatalab.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class Ambito(val titulo: String, val descripcion: String) {
    NEURODESARROLLO("Neurodesarrollo", "Rutinas, tareas y autorregulación"),
    DBT("Habilidades DBT", "Práctica y utilidad percibida"),
    ANIMO("Estado de ánimo", "Check-in, intensidad y contexto"),
    ADICCIONES("Adicciones", "Impulsos y objetivos de prueba")
}

enum class TipoRecurso(val titulo: String) {
    TEXTO("Texto educativo"), EJERCICIO("Ejercicio"), HABILIDAD("Habilidad DBT"),
    ESTRATEGIA("Estrategia de autorregulación"), ACCION("Acción protectora"), PARAMETRO("Parámetro de catálogo")
}

data class Cuenta(val id: String, val alias: String, val administrador: Boolean = false)

data class Registro(
    val id: String = UUID.randomUUID().toString(),
    val usuarioId: String,
    val ambito: Ambito,
    val fecha: Long = System.currentTimeMillis(),
    val intensidad: Int = 0,
    val categoria: String = "",
    val contexto: String = "",
    val estrategia: String = "",
    val resultado: Int = 0,
    val nota: String = "",
    val objetivoId: String? = null,
    val revision: Int = 1,
    val pendiente: Boolean = true,
    val eliminado: Boolean = false
)

data class Objetivo(
    val id: String = UUID.randomUUID().toString(), val usuarioId: String,
    val descripcion: String, val meta: Int = 3, val estado: String = "En curso",
    val fecha: Long = System.currentTimeMillis(), val revision: Int = 1,
    val pendiente: Boolean = true, val eliminado: Boolean = false
) {
    fun progreso(registros: List<Registro>) = registros.count {
        !it.eliminado && it.usuarioId == usuarioId && it.ambito == Ambito.ADICCIONES && it.objetivoId == id && it.resultado == 1
    }
}

data class Recurso(
    val id: String = UUID.randomUUID().toString(), val ambito: Ambito,
    val tipo: TipoRecurso, val titulo: String, val contenido: String,
    val revision: Int = 1, val pendiente: Boolean = true, val eliminado: Boolean = false
)

data class Recordatorio(
    val activo: Boolean = false, val hora: Int = 20, val minuto: Int = 0,
    val frecuencia: String = "Diario", val ambito: Ambito? = null
)

object Validaciones {
    fun credenciales(alias: String, password: String): String? = when {
        !Regex("[a-zA-Z0-9_]{3,24}").matches(alias.trim()) -> "Usa un alias de prueba de 3 a 24 letras, números o guion bajo."
        password.length !in 8..128 -> "La contraseña debe tener entre 8 y 128 caracteres."
        else -> null
    }

    fun registro(r: Registro): String? = when {
        r.intensidad !in 1..5 -> "Selecciona una intensidad del 1 al 5."
        r.categoria.isBlank() -> "Completa el tipo de actividad o categoría."
        r.contexto.isBlank() -> "Completa el contexto, emoción o desencadenante."
        r.ambito != Ambito.ANIMO && r.estrategia.isBlank() -> "Selecciona una habilidad, estrategia o acción protectora."
        r.ambito in listOf(Ambito.NEURODESARROLLO, Ambito.DBT) && r.resultado !in 1..5 -> "Selecciona el resultado o utilidad del 1 al 5."
        r.ambito == Ambito.ADICCIONES && r.resultado !in 1..3 -> "Selecciona el resultado del impulso."
        listOf(r.categoria, r.contexto, r.estrategia).any { it.length > 200 } -> "Los campos breves admiten hasta 200 caracteres."
        r.nota.length > 1000 -> "La nota admite hasta 1000 caracteres."
        r.fecha > System.currentTimeMillis() + 60_000 -> "La fecha del registro no puede estar en el futuro."
        else -> null
    }

    fun recurso(r: Recurso): String? = when {
        r.titulo.trim().length !in 3..100 -> "El título debe tener entre 3 y 100 caracteres."
        r.contenido.trim().length !in (if (r.tipo == TipoRecurso.PARAMETRO) 1..1000 else 10..5000) -> "Revisa la longitud del contenido. Los textos admiten de 10 a 5000 caracteres."
        r.tipo == TipoRecurso.PARAMETRO && (r.id != "animo-categorias" || r.ambito != Ambito.ANIMO) -> "Edita el parámetro existente «Categorías de ánimo» en el ámbito Ánimo."
        r.tipo == TipoRecurso.PARAMETRO && (r.contenido.lines().size !in 1..20 || r.contenido.lines().any { it.trim().length !in 1..40 } || r.contenido.lines().map { it.trim().lowercase() }.distinct().size != r.contenido.lines().size) -> "Usa de 1 a 20 categorías únicas, una por línea, de hasta 40 caracteres."
        r.tipo == TipoRecurso.HABILIDAD && r.ambito != Ambito.DBT -> "Las habilidades pertenecen al ámbito DBT."
        r.tipo == TipoRecurso.ESTRATEGIA && r.ambito != Ambito.NEURODESARROLLO -> "Las estrategias pertenecen a Neurodesarrollo."
        r.tipo == TipoRecurso.ACCION && r.ambito != Ambito.ADICCIONES -> "Las acciones protectoras pertenecen a Adicciones."
        else -> null
    }
}

fun filtrarRegistros(registros: List<Registro>, ambito: Ambito?, desde: LocalDate?, hasta: LocalDate?): List<Registro> =
    registros.filter {
        val dia = Instant.ofEpochMilli(it.fecha).atZone(ZoneId.systemDefault()).toLocalDate()
        !it.eliminado && (ambito == null || it.ambito == ambito) &&
            (desde == null || !dia.isBefore(desde)) && (hasta == null || !dia.isAfter(hasta))
    }.sortedByDescending { it.fecha }

object CatalogoInicial {
    val recursos = listOf(
        Recurso("nd-pausa", Ambito.NEURODESARROLLO, TipoRecurso.ESTRATEGIA, "Pausa planificada", "Ejemplo de prueba: anota una pausa en una tarea ficticia y registra cómo percibiste el resultado.", pendiente = false),
        Recurso("nd-organizar", Ambito.NEURODESARROLLO, TipoRecurso.ESTRATEGIA, "Organizar pasos", "Ejemplo de prueba: divide una rutina ficticia en pasos y registra qué parte realizaste.", pendiente = false),
        Recurso("nd-texto", Ambito.NEURODESARROLLO, TipoRecurso.TEXTO, "Rutinas y contexto", "Este módulo organiza autorreportes de rutinas, tareas y situaciones de sobrecarga. La intensidad y el resultado expresan la percepción del usuario de prueba.", pendiente = false),
        Recurso("dbt-atencion", Ambito.DBT, TipoRecurso.HABILIDAD, "Atención plena", "Entrada ilustrativa del catálogo académico: registra una práctica de atención y una emoción o situación ficticia. Puntúa su utilidad percibida. El contenido requiere revisión del responsable de contenidos.", pendiente = false),
        Recurso("dbt-regulacion", Ambito.DBT, TipoRecurso.HABILIDAD, "Regulación emocional", "Entrada ilustrativa: registra la habilidad practicada, la emoción o situación y la utilidad que percibió el usuario de prueba.", pendiente = false),
        Recurso("dbt-tolerancia", Ambito.DBT, TipoRecurso.HABILIDAD, "Tolerancia al malestar", "Entrada ilustrativa del catálogo DBT para probar el diario de habilidades. Usa una situación sintética y registra intensidad y utilidad percibida.", pendiente = false),
        Recurso("animo-texto", Ambito.ANIMO, TipoRecurso.TEXTO, "Cómo leer tus check-ins", "Cada check-in contiene una categoría, intensidad, contexto y nota opcional. El historial permite observar las anotaciones del periodo. Las escalas son de autorreporte y no son resultados clínicos.", pendiente = false),
        Recurso("animo-ejercicio", Ambito.ANIMO, TipoRecurso.EJERCICIO, "Un check-in de prueba", "Usa un escenario ficticio, elige una categoría de ánimo y una intensidad del 1 al 5. Añade un contexto y guarda. Luego comprueba el registro en el historial.", pendiente = false),
        Recurso("animo-categorias", Ambito.ANIMO, TipoRecurso.PARAMETRO, "Categorías de ánimo", "Muy bajo\nBajo\nNeutral\nBueno\nMuy bueno", pendiente = false),
        Recurso("ad-pausa", Ambito.ADICCIONES, TipoRecurso.ACCION, "Hacer una pausa", "Acción de ejemplo del catálogo sintético. Permite probar el registro de un impulso, su desencadenante y el resultado percibido.", pendiente = false),
        Recurso("ad-contacto", Ambito.ADICCIONES, TipoRecurso.ACCION, "Contactar apoyo de prueba", "Acción ficticia para demostrar el registro. No se realizan llamadas ni se almacenan datos de contacto.", pendiente = false),
        Recurso("ad-objetivo", Ambito.ADICCIONES, TipoRecurso.TEXTO, "Objetivos de prueba", "Define una meta de registros con resultado «Resistí». El progreso cuenta únicamente registros propios asociados a ese objetivo. También puedes indicar su estado de forma manual.", pendiente = false)
    )
}

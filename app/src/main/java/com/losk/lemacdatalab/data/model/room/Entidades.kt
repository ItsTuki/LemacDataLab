package com.losk.lemacdatalab.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.losk.lemacdatalab.model.Ambito
import com.losk.lemacdatalab.model.Objetivo
import com.losk.lemacdatalab.model.Recurso
import com.losk.lemacdatalab.model.Registro
import com.losk.lemacdatalab.model.TipoRecurso

/** Propietario de los recursos de catálogo (compartidos por todas las cuentas). */
const val OWNER_CATALOGO = "catalogo"

/** Tabla `cuentas`: usuarios de prueba. La contraseña nunca se guarda: solo salt + hash PBKDF2. */
@Entity(tableName = "cuentas", indices = [Index(value = ["alias"], unique = true)])
data class CuentaEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val alias: String,
    val salt: String,
    val hash: String,
    val admin: Boolean
)

/** Tabla `registros`: un registro de autorreporte (los 4 ámbitos comparten la tabla; ver docs/Modelo Relacional). */
@Entity(tableName = "registros", indices = [Index("owner")])
data class RegistroEntity(
    @PrimaryKey val id: String,
    val owner: String,
    val revision: Int,
    val pending: Boolean,
    val ambito: String,
    val fecha: Long,
    val intensidad: Int,
    val categoria: String,
    val contexto: String,
    val estrategia: String,
    val resultado: Int,
    val nota: String,
    val objetivoId: String?,
    val eliminado: Boolean
)

/** Tabla `objetivos`: objetivos de prueba del módulo Adicciones. */
@Entity(tableName = "objetivos", indices = [Index("owner")])
data class ObjetivoEntity(
    @PrimaryKey val id: String,
    val owner: String,
    val revision: Int,
    val pending: Boolean,
    val descripcion: String,
    val meta: Int,
    val estado: String,
    val fecha: Long,
    val eliminado: Boolean
)

/** Tabla `recursos`: catálogo de contenidos (owner = "catalogo"). */
@Entity(tableName = "recursos", indices = [Index("owner")])
data class RecursoEntity(
    @PrimaryKey val id: String,
    val owner: String,
    val revision: Int,
    val pending: Boolean,
    val ambito: String,
    val tipo: String,
    val titulo: String,
    val contenido: String,
    val eliminado: Boolean
)

// ---- Conversión Entity <-> modelo de dominio (los datos de la BD son los mismos que usa la app) ----

fun RegistroEntity.aDominio() = Registro(
    id = id, usuarioId = owner, ambito = Ambito.valueOf(ambito), fecha = fecha, intensidad = intensidad,
    categoria = categoria, contexto = contexto, estrategia = estrategia, resultado = resultado, nota = nota,
    objetivoId = objetivoId, revision = revision, pendiente = pending, eliminado = eliminado
)

fun Registro.aEntidad(owner: String) = RegistroEntity(
    id = id, owner = owner, revision = revision, pending = pendiente, ambito = ambito.name, fecha = fecha,
    intensidad = intensidad, categoria = categoria, contexto = contexto, estrategia = estrategia,
    resultado = resultado, nota = nota, objetivoId = objetivoId, eliminado = eliminado
)

fun ObjetivoEntity.aDominio() = Objetivo(
    id = id, usuarioId = owner, descripcion = descripcion, meta = meta, estado = estado, fecha = fecha,
    revision = revision, pendiente = pending, eliminado = eliminado
)

fun Objetivo.aEntidad(owner: String) = ObjetivoEntity(
    id = id, owner = owner, revision = revision, pending = pendiente, descripcion = descripcion,
    meta = meta, estado = estado, fecha = fecha, eliminado = eliminado
)

fun RecursoEntity.aDominio() = Recurso(
    id = id, ambito = Ambito.valueOf(ambito), tipo = TipoRecurso.valueOf(tipo), titulo = titulo,
    contenido = contenido, revision = revision, pendiente = pending, eliminado = eliminado
)

fun Recurso.aEntidad() = RecursoEntity(
    id = id, owner = OWNER_CATALOGO, revision = revision, pending = pendiente, ambito = ambito.name,
    tipo = tipo.name, titulo = titulo, contenido = contenido, eliminado = eliminado
)

package com.losk.lemacdatalab.data

import android.content.Context
import androidx.room.Room
import com.losk.lemacdatalab.data.room.*
import com.losk.lemacdatalab.model.*
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Fuente de datos local sobre Room (SQLite).
 *
 * Flujo de datos: ViewModel → LemacRepository → LemacDatabase → DAO (CuentaDao, RegistroDao, ...) → Room → SQLite.
 * Todas las consultas privadas se limitan al propietario. Ejecutar en Dispatchers.IO (Room no permite
 * consultas en el hilo principal). La base se abre y se siembra la primera vez que se usa (lazy).
 */
class LemacDatabase(context: Context, name: String = "lemac_room.db") {
    private val appContext = context.applicationContext
    private val lazyRoom = lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, name).build().also { sembrar(it) }
    }
    private val room: AppDatabase get() = lazyRoom.value

    fun close() { if (lazyRoom.isInitialized()) lazyRoom.value.close() }

    /** Primera apertura: cuentas de demostración y catálogo sintético inicial. */
    private fun sembrar(db: AppDatabase) {
        db.runInTransaction(Runnable {
            if (db.cuentaDao().contar() == 0) {
                crearCuenta(db.cuentaDao(), "demo", "Demo12345!", false)
                crearCuenta(db.cuentaDao(), "contenidos_demo", "Demo12345!", true)
                CatalogoInicial.recursos.forEach { db.recursoDao().guardar(it.aEntidad()) }
            }
        })
    }

    private fun hash(password: String, salt: ByteArray): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded

    private fun crearCuenta(dao: CuentaDao, alias: String, password: String, admin: Boolean): Cuenta {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cuenta = Cuenta(UUID.randomUUID().toString(), alias, admin)
        dao.insertar(CuentaEntity(
            id = cuenta.id, alias = alias, admin = admin,
            salt = Base64.getEncoder().encodeToString(salt),
            hash = Base64.getEncoder().encodeToString(hash(password, salt))
        ))
        return cuenta
    }

    fun registrar(alias: String, password: String): Cuenta {
        require(Validaciones.credenciales(alias, password) == null) { Validaciones.credenciales(alias, password)!! }
        require(room.cuentaDao().buscarPorAlias(alias.trim()) == null) { "Ese alias ya existe en este dispositivo." }
        return crearCuenta(room.cuentaDao(), alias.trim(), password, false)
    }

    fun autenticar(alias: String, password: String): Cuenta {
        val c = room.cuentaDao().buscarPorAlias(alias.trim())
        require(c != null) { "Alias o contraseña incorrectos." }
        val salt = Base64.getDecoder().decode(c.salt)
        val esperado = Base64.getDecoder().decode(c.hash)
        require(MessageDigest.isEqual(esperado, hash(password, salt))) { "Alias o contraseña incorrectos." }
        return Cuenta(c.id, c.alias, c.admin)
    }

    fun cuenta(id: String): Cuenta? = room.cuentaDao().buscarPorId(id)?.let { Cuenta(it.id, it.alias, it.admin) }

    // ---- Read ----
    fun registros(owner: String): List<Registro> = room.registroDao().listar(owner).map { it.aDominio() }
    fun objetivos(owner: String): List<Objetivo> = room.objetivoDao().listar(owner).map { it.aDominio() }
    fun recursos(): List<Recurso> = room.recursoDao().listar(OWNER_CATALOGO).map { it.aDominio() }

    // ---- Create / Update / Delete lógico ----
    @Synchronized fun guardarRegistro(owner: String, r: Registro) {
        require(r.usuarioId == owner) { "Solo puedes modificar tus propios registros." }
        val dao = room.registroDao()
        val previo = dao.buscar(r.id, owner)?.aDominio()
        require(!dao.existeDeOtro(r.id, owner)) { "El registro no pertenece a esta cuenta." }
        if (!r.eliminado) require(Validaciones.registro(r) == null) { Validaciones.registro(r)!! }
        if (!r.eliminado && r.objetivoId != null) require(r.objetivoId == previo?.objetivoId || objetivos(owner).any { it.id == r.objetivoId && !it.eliminado }) { "El objetivo no está disponible para esta cuenta." }
        val nuevo = r.copy(revision = (previo?.revision ?: 0) + 1, pendiente = true)
        dao.guardar(nuevo.aEntidad(owner))
    }

    @Synchronized fun guardarObjetivo(owner: String, o: Objetivo) {
        val dao = room.objetivoDao()
        require(o.usuarioId == owner && !dao.existeDeOtro(o.id, owner)) { "El objetivo no pertenece a esta cuenta." }
        require(o.descripcion.trim().length in 3..200 && o.meta in 1..100 && o.estado in listOf("En curso", "Cumplido", "No cumplido")) { "Usa una descripción de 3 a 200 caracteres y una meta de 1 a 100 registros." }
        val revision = (dao.buscar(o.id, owner)?.revision ?: 0) + 1
        dao.guardar(o.copy(revision = revision, pendiente = true).aEntidad(owner))
    }

    @Synchronized fun guardarRecurso(actor: Cuenta, r: Recurso) {
        require(cuenta(actor.id)?.administrador == true) { "Se requiere el rol responsable de contenidos." }
        require(Validaciones.recurso(r) == null) { Validaciones.recurso(r)!! }
        val dao = room.recursoDao()
        val revision = (dao.buscar(r.id, OWNER_CATALOGO)?.revision ?: 0) + 1
        dao.guardar(r.copy(revision = revision, pendiente = true).aEntidad())
    }

    /**
     * ACK condicional: una edición posterior al envío conserva su estado pendiente.
     * Todo ocurre en una transacción: si algo falla, no se aplica ningún cambio.
     */
    @Synchronized fun aplicarSincronizacion(owner: String, response: JSONObject, enviados: Map<String, Map<String, Int>>) {
        val db = room
        db.runInTransaction(Runnable {
            for (tabla in listOf("objetivos", "registros", "recursos")) {
                val scopeOwner = if (tabla == "recursos") OWNER_CATALOGO else owner
                val arr = response.getJSONArray(tabla)
                val enviadosTabla = enviados[tabla].orEmpty()
                for (i in 0 until arr.length()) {
                    val payload = arr.getJSONObject(i)
                    val id = payload.getString("id")
                    when (tabla) {
                        "objetivos" -> {
                            val dao = db.objetivoDao()
                            require(!dao.existeDeOtro(id, scopeOwner)) { MSG_OTRA_CUENTA }
                            val actual = dao.buscar(id, scopeOwner)
                            val revision = actual?.revision ?: 1
                            if (debeAplicar(actual?.pending ?: false, enviadosTabla[id], revision))
                                dao.guardar(objetivoDeJson(payload, scopeOwner, revision, false).aEntidad(scopeOwner))
                        }
                        "registros" -> {
                            val dao = db.registroDao()
                            require(!dao.existeDeOtro(id, scopeOwner)) { MSG_OTRA_CUENTA }
                            val actual = dao.buscar(id, scopeOwner)
                            val revision = actual?.revision ?: 1
                            if (debeAplicar(actual?.pending ?: false, enviadosTabla[id], revision))
                                dao.guardar(registroDeJson(payload, scopeOwner, revision, false).aEntidad(scopeOwner))
                        }
                        else -> {
                            val dao = db.recursoDao()
                            require(!dao.existeDeOtro(id, scopeOwner)) { MSG_OTRA_CUENTA }
                            val actual = dao.buscar(id, scopeOwner)
                            val revision = actual?.revision ?: 1
                            if (debeAplicar(actual?.pending ?: false, enviadosTabla[id], revision))
                                dao.guardar(recursoDeJson(payload, revision, false).aEntidad())
                        }
                    }
                }
            }
        })
    }

    /** Se aplica lo que responde el servidor salvo que haya una edición local posterior al envío. */
    private fun debeAplicar(pendienteLocal: Boolean, revisionEnviada: Int?, revisionLocal: Int) =
        !pendienteLocal || revisionEnviada == revisionLocal

    private companion object {
        const val MSG_OTRA_CUENTA = "La API devolvió un identificador de otra cuenta."
    }
}

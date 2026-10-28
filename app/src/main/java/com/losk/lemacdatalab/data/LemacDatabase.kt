package com.losk.lemacdatalab.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.losk.lemacdatalab.model.*
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Todas las consultas privadas se limitan al propietario. Ejecutar en Dispatchers.IO. */
class LemacDatabase(context: Context, name: String = "lemac.db") : SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cuentas (id TEXT PRIMARY KEY, alias TEXT UNIQUE COLLATE NOCASE, salt TEXT NOT NULL, hash TEXT NOT NULL, admin INTEGER NOT NULL DEFAULT 0)")
        for (table in listOf("registros", "objetivos", "recursos")) {
            db.execSQL("CREATE TABLE $table (id TEXT PRIMARY KEY, owner TEXT NOT NULL, revision INTEGER NOT NULL, pending INTEGER NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX ${table}_owner ON $table(owner)")
        }
        crearCuenta(db, "demo", "Demo12345!", false)
        crearCuenta(db, "contenidos_demo", "Demo12345!", true)
        CatalogoInicial.recursos.forEach { guardar(db, "recursos", it.id, "catalogo", it.revision, false, it.json()) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun hash(password: String, salt: ByteArray): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded

    private fun crearCuenta(db: SQLiteDatabase, alias: String, password: String, admin: Boolean): Cuenta {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cuenta = Cuenta(UUID.randomUUID().toString(), alias, admin)
        db.insertOrThrow("cuentas", null, ContentValues().apply {
            put("id", cuenta.id); put("alias", alias); put("admin", if (admin) 1 else 0)
            put("salt", Base64.getEncoder().encodeToString(salt)); put("hash", Base64.getEncoder().encodeToString(hash(password, salt)))
        })
        return cuenta
    }

    fun registrar(alias: String, password: String): Cuenta {
        require(Validaciones.credenciales(alias, password) == null) { Validaciones.credenciales(alias, password)!! }
        require(cuentaPorAlias(alias) == null) { "Ese alias ya existe en este dispositivo." }
        return crearCuenta(writableDatabase, alias.trim(), password, false)
    }

    fun autenticar(alias: String, password: String): Cuenta {
        readableDatabase.query("cuentas", null, "alias = ?", arrayOf(alias.trim()), null, null, null).use { c ->
            require(c.moveToFirst()) { "Alias o contraseña incorrectos." }
            val salt = Base64.getDecoder().decode(c.getString(c.getColumnIndexOrThrow("salt")))
            val expected = Base64.getDecoder().decode(c.getString(c.getColumnIndexOrThrow("hash")))
            require(MessageDigest.isEqual(expected, hash(password, salt))) { "Alias o contraseña incorrectos." }
            return Cuenta(c.getString(0), c.getString(1), c.getInt(c.getColumnIndexOrThrow("admin")) == 1)
        }
    }

    fun cuenta(id: String): Cuenta? = buscarCuenta("id", id)
    private fun cuentaPorAlias(alias: String) = buscarCuenta("alias", alias.trim())
    private fun buscarCuenta(column: String, value: String): Cuenta? = readableDatabase.query("cuentas", null, "$column = ?", arrayOf(value), null, null, null).use { c ->
        if (!c.moveToFirst()) null else Cuenta(c.getString(0), c.getString(1), c.getInt(c.getColumnIndexOrThrow("admin")) == 1)
    }

    private fun guardar(db: SQLiteDatabase, table: String, id: String, owner: String, revision: Int, pending: Boolean, json: JSONObject) {
        db.insertWithOnConflict(table, null, ContentValues().apply {
            put("id", id); put("owner", owner); put("revision", revision); put("pending", if (pending) 1 else 0); put("payload", json.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "No se pudo guardar el registro." } }
    }

    private fun <T> leer(table: String, owner: String, decode: (JSONObject, Int, Boolean) -> T): List<T> =
        readableDatabase.query(table, null, "owner = ?", arrayOf(owner), null, null, null).use { c ->
            buildList {
                while (c.moveToNext()) add(decode(JSONObject(c.getString(c.getColumnIndexOrThrow("payload"))), c.getInt(c.getColumnIndexOrThrow("revision")), c.getInt(c.getColumnIndexOrThrow("pending")) == 1))
            }
        }

    fun registros(owner: String) = leer("registros", owner) { j, r, p -> registroDeJson(j, owner, r, p) }
    fun objetivos(owner: String) = leer("objetivos", owner) { j, r, p -> objetivoDeJson(j, owner, r, p) }
    fun recursos() = leer("recursos", "catalogo", ::recursoDeJson)

    @Synchronized fun guardarRegistro(owner: String, r: Registro) {
        require(r.usuarioId == owner) { "Solo puedes modificar tus propios registros." }
        val previo = registros(owner).firstOrNull { it.id == r.id }
        require(!existeDeOtro("registros", r.id, owner)) { "El registro no pertenece a esta cuenta." }
        if (!r.eliminado) require(Validaciones.registro(r) == null) { Validaciones.registro(r)!! }
        if (!r.eliminado && r.objetivoId != null) require(r.objetivoId == previo?.objetivoId || objetivos(owner).any { it.id == r.objetivoId && !it.eliminado }) { "El objetivo no está disponible para esta cuenta." }
        val nuevo = r.copy(revision = (previo?.revision ?: 0) + 1, pendiente = true)
        guardar(writableDatabase, "registros", nuevo.id, owner, nuevo.revision, true, nuevo.json())
    }

    @Synchronized fun guardarObjetivo(owner: String, o: Objetivo) {
        require(o.usuarioId == owner && !existeDeOtro("objetivos", o.id, owner)) { "El objetivo no pertenece a esta cuenta." }
        require(o.descripcion.trim().length in 3..200 && o.meta in 1..100 && o.estado in listOf("En curso", "Cumplido", "No cumplido")) { "Usa una descripción de 3 a 200 caracteres y una meta de 1 a 100 registros." }
        val revision = (objetivos(owner).firstOrNull { it.id == o.id }?.revision ?: 0) + 1
        guardar(writableDatabase, "objetivos", o.id, owner, revision, true, o.copy(revision = revision, pendiente = true).json())
    }

    @Synchronized fun guardarRecurso(actor: Cuenta, r: Recurso) {
        require(cuenta(actor.id)?.administrador == true) { "Se requiere el rol responsable de contenidos." }
        require(Validaciones.recurso(r) == null) { Validaciones.recurso(r)!! }
        val revision = (recursos().firstOrNull { it.id == r.id }?.revision ?: 0) + 1
        guardar(writableDatabase, "recursos", r.id, "catalogo", revision, true, r.copy(revision = revision, pendiente = true).json())
    }

    private fun existeDeOtro(table: String, id: String, owner: String) = readableDatabase.rawQuery("SELECT 1 FROM $table WHERE id = ? AND owner != ?", arrayOf(id, owner)).use { it.moveToFirst() }

    /** ACK condicional: una edición posterior al envío conserva su estado pendiente. */
    @Synchronized fun aplicarSincronizacion(owner: String, response: JSONObject, enviados: Map<String, Map<String, Int>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in listOf("objetivos", "registros", "recursos")) {
                val scopeOwner = if (table == "recursos") "catalogo" else owner
                val arr = response.getJSONArray(table)
                val snapshot = enviados[table].orEmpty()
                for (i in 0 until arr.length()) {
                    val payload = arr.getJSONObject(i)
                    val id = payload.getString("id")
                    require(!existeDeOtro(table, id, scopeOwner)) { "La API devolvió un identificador de otra cuenta." }
                    db.rawQuery("SELECT revision, pending FROM $table WHERE id = ? AND owner = ?", arrayOf(id, scopeOwner)).use { c ->
                        val existe = c.moveToFirst()
                        val revision = if (existe) c.getInt(0) else 1
                        val pending = existe && c.getInt(1) == 1
                        if (!pending || snapshot[id] == revision) guardar(db, table, id, scopeOwner, revision, false, payload)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}

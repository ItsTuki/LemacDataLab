package com.losk.lemacdatalab.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO = Data Access Object: aquí están las operaciones CRUD sobre cada tabla.
 * Estas funciones son bloqueantes: el Repository las llama siempre dentro de withContext(Dispatchers.IO).
 */
@Dao
interface CuentaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertar(cuenta: CuentaEntity)

    @Query("SELECT * FROM cuentas WHERE alias = :alias LIMIT 1")
    fun buscarPorAlias(alias: String): CuentaEntity?

    @Query("SELECT * FROM cuentas WHERE id = :id LIMIT 1")
    fun buscarPorId(id: String): CuentaEntity?

    @Query("SELECT COUNT(*) FROM cuentas")
    fun contar(): Int
}

@Dao
interface RegistroDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)   // Create + Update
    fun guardar(registro: RegistroEntity)

    @Query("SELECT * FROM registros WHERE owner = :owner")   // Read
    fun listar(owner: String): List<RegistroEntity>

    @Query("SELECT * FROM registros WHERE id = :id AND owner = :owner LIMIT 1")
    fun buscar(id: String, owner: String): RegistroEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM registros WHERE id = :id AND owner != :owner)")
    fun existeDeOtro(id: String, owner: String): Boolean
    // Delete: los registros no se borran físicamente. Se marcan eliminado = 1 (borrado lógico)
    // para poder informar el borrado a la API cuando vuelva la conexión.
}

@Dao
interface ObjetivoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun guardar(objetivo: ObjetivoEntity)

    @Query("SELECT * FROM objetivos WHERE owner = :owner")
    fun listar(owner: String): List<ObjetivoEntity>

    @Query("SELECT * FROM objetivos WHERE id = :id AND owner = :owner LIMIT 1")
    fun buscar(id: String, owner: String): ObjetivoEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM objetivos WHERE id = :id AND owner != :owner)")
    fun existeDeOtro(id: String, owner: String): Boolean
}

@Dao
interface RecursoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun guardar(recurso: RecursoEntity)

    @Query("SELECT * FROM recursos WHERE owner = :owner")
    fun listar(owner: String): List<RecursoEntity>

    @Query("SELECT * FROM recursos WHERE id = :id AND owner = :owner LIMIT 1")
    fun buscar(id: String, owner: String): RecursoEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM recursos WHERE id = :id AND owner != :owner)")
    fun existeDeOtro(id: String, owner: String): Boolean
}

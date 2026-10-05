package com.losk.lemacdatalab.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

/** Base de datos Room (SQLite). Une las entidades con sus DAO. */
@Database(
    entities = [CuentaEntity::class, RegistroEntity::class, ObjetivoEntity::class, RecursoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cuentaDao(): CuentaDao
    abstract fun registroDao(): RegistroDao
    abstract fun objetivoDao(): ObjetivoDao
    abstract fun recursoDao(): RecursoDao
}

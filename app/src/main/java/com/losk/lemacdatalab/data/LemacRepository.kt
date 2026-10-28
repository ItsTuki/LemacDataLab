package com.losk.lemacdatalab.data

import android.content.Context
import com.losk.lemacdatalab.model.*
import com.losk.lemacdatalab.notifications.ReminderScheduler
import com.losk.lemacdatalab.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppState(
    val cargando: Boolean = true, val cuenta: Cuenta? = null,
    val registros: List<Registro> = emptyList(), val objetivos: List<Objetivo> = emptyList(),
    val recursos: List<Recurso> = emptyList(), val recordatorio: Recordatorio = Recordatorio(),
    val apiUrl: String = "http://10.0.2.2:8080", val conectado: Boolean = false,
    val sincronizando: Boolean = false, val mensajeSync: String = "API de prueba sin vincular",
    val ultimaSync: Long = 0
) {
    val pendientes: Int get() = registros.count { it.pendiente } + objetivos.count { it.pendiente } +
        if (cuenta?.administrador == true) recursos.count { it.pendiente } else 0
}

class LemacRepository private constructor(private val context: Context) {
    private val db = LemacDatabase(context)
    private val prefs = context.getSharedPreferences("lemac_preferences", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()
    private val syncMutex = Mutex()
    private val sessionMutex = Mutex()

    private fun owner() = mutableState.value.cuenta ?: error("Inicia sesión para continuar.")
    private fun key(suffix: String, id: String = owner().id) = "$id.$suffix"

    suspend fun cargar() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
        if (!mutableState.value.cargando) return@withLock
        val account = prefs.getString("currentUser", null)?.let(db::cuenta)
        mutableState.value = AppState(cargando = false, cuenta = account)
        refrescar()
        if (account != null) SyncScheduler.programar(context, account.id)
        }
    }

    @Synchronized private fun refrescar() {
        val s = mutableState.value
        val c = s.cuenta ?: return
        mutableState.value = s.copy(
            registros = db.registros(c.id), objetivos = db.objetivos(c.id), recursos = db.recursos(),
            recordatorio = ReminderScheduler.leer(context, c.id),
            apiUrl = prefs.getString(key("url", c.id), "http://10.0.2.2:8080")!!,
            conectado = !prefs.getString(key("token", c.id), null).isNullOrBlank(),
            ultimaSync = prefs.getLong(key("lastSync", c.id), 0)
        )
    }

    suspend fun ingresar(alias: String, password: String, registrar: Boolean) = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            require(Validaciones.credenciales(alias, password) == null) { Validaciones.credenciales(alias, password)!! }
            val c = if (registrar) db.registrar(alias, password) else db.autenticar(alias, password)
            prefs.edit().putString("currentUser", c.id).apply()
            mutableState.value = AppState(cargando = false, cuenta = c)
            refrescar()
            ReminderScheduler.restaurar(context)
            SyncScheduler.programar(context, c.id)
        }
    }

    suspend fun salir() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val c = owner()
            SyncScheduler.cancelar(context, c.id)
            ReminderScheduler.cancelar(context)
            prefs.edit().remove("currentUser").remove(key("token", c.id)).apply()
            mutableState.value = AppState(cargando = false)
        }
    }

    suspend fun guardar(r: Registro) = withContext(Dispatchers.IO) {
        sessionMutex.withLock { val c = owner(); db.guardarRegistro(c.id, r); refrescar(); SyncScheduler.encolar(context, c.id) }
    }
    suspend fun guardar(o: Objetivo) = withContext(Dispatchers.IO) {
        sessionMutex.withLock { val c = owner(); db.guardarObjetivo(c.id, o); refrescar(); SyncScheduler.encolar(context, c.id) }
    }
    suspend fun guardar(r: Recurso) = withContext(Dispatchers.IO) {
        sessionMutex.withLock { val c = owner(); db.guardarRecurso(c, r); refrescar(); SyncScheduler.encolar(context, c.id) }
    }

    suspend fun recordatorio(r: Recordatorio) = withContext(Dispatchers.IO) {
        require(r.hora in 0..23 && r.minuto in 0..59 && r.frecuencia in listOf("Diario", "Lunes a viernes", "Semanal")) { "Revisa el horario y la frecuencia." }
        sessionMutex.withLock { ReminderScheduler.guardar(context, owner().id, r); refrescar() }
    }

    suspend fun conectar(url: String, password: String) = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val c = owner()
            db.autenticar(c.alias, password)
            val api = ApiClient(url.trim())
            val body = JSONObject().put("alias", c.alias).put("password", password)
            val response = try { api.request("/auth/login", body) } catch (e: ApiException) {
                if (e.status != 404) throw e
                api.request("/auth/register", body)
            }
            require(response.getBoolean("admin") == c.administrador) { "El rol de esta cuenta no coincide con el servidor de prueba." }
            prefs.edit().putString(key("url", c.id), url.trim().trimEnd('/')).putString(key("token", c.id), response.getString("token")).apply()
            refrescar()
            SyncScheduler.programar(context, c.id)
        }
        sincronizar()
    }

    suspend fun sincronizar(usuarioEsperado: String? = null): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val c = mutableState.value.cuenta ?: return@withLock true
            if (usuarioEsperado != null && c.id != usuarioEsperado) return@withLock true
            val url = prefs.getString(key("url", c.id), null)
            val token = prefs.getString(key("token", c.id), null)
            if (url == null || token == null) return@withLock true
            val dispositivo = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }
            mutableState.value = mutableState.value.copy(sincronizando = true, mensajeSync = "Sincronizando…")
            try {
                val todosObjetivos = db.objetivos(c.id)
                val objetivos = todosObjetivos.filter { it.pendiente }.take(500)
                val idsEnviados = objetivos.map { it.id }.toSet()
                val objetivosDiferidos = todosObjetivos.filter { it.pendiente && it.id !in idsEnviados }.map { it.id }.toSet()
                val registros = db.registros(c.id).filter { it.pendiente && it.objetivoId !in objetivosDiferidos }.take(500)
                val recursos = if (c.administrador) db.recursos().filter { it.pendiente }.take(500) else emptyList()
                fun mutation(j: JSONObject, revision: Int) = j.put("mutationId", "$dispositivo:${j.getString("id")}:$revision")
                val body = JSONObject()
                    .put("registros", JSONArray(registros.map { mutation(it.json(), it.revision) }))
                    .put("objetivos", JSONArray(objetivos.map { mutation(it.json(), it.revision) }))
                    .put("recursos", JSONArray(recursos.map { mutation(it.json(), it.revision) }))
                val response = ApiClient(url).request("/sync", body, token)
                sessionMutex.withLock {
                    if (mutableState.value.cuenta?.id == c.id) {
                        db.aplicarSincronizacion(c.id, response, mapOf(
                            "registros" to registros.associate { it.id to it.revision },
                            "objetivos" to objetivos.associate { it.id to it.revision },
                            "recursos" to recursos.associate { it.id to it.revision }
                        ))
                        prefs.edit().putLong(key("lastSync", c.id), System.currentTimeMillis()).apply()
                        mutableState.value = mutableState.value.copy(sincronizando = false, mensajeSync = "Sincronización completada")
                        refrescar()
                        if (mutableState.value.pendientes > 0) SyncScheduler.encolar(context, c.id)
                    }
                }
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (mutableState.value.cuenta?.id == c.id) {
                    val authError = e is ApiException && e.status == 401
                    if (authError) prefs.edit().remove(key("token", c.id)).apply()
                    mutableState.value = mutableState.value.copy(sincronizando = false, mensajeSync =
                        if (authError) "Vuelve a vincular la API: la sesión del servidor venció."
                        else if (e is ApiException) "La API rechazó la sincronización: ${e.message}. Tus cambios siguen guardados."
                        else "Sin conexión con la API. Tus cambios siguen guardados en el dispositivo.")
                    refrescar()
                }
                false
            }
        }
    }

    companion object {
        @Volatile private var instance: LemacRepository? = null
        fun get(context: Context): LemacRepository = instance ?: synchronized(this) {
            instance ?: LemacRepository(context.applicationContext).also { instance = it }
        }
    }
}

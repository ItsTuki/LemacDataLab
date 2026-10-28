package com.losk.lemacdatalab.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.losk.lemacdatalab.data.LemacRepository
import com.losk.lemacdatalab.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LemacRepository.get(application)
    val state = repository.state
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    fun limpiarError() { mutableError.value = null }

    private fun ejecutar(block: suspend () -> Unit, completed: () -> Unit = {}) {
        if (mutableBusy.value) return
        mutableBusy.value = true; mutableError.value = null
        viewModelScope.launch {
            try { block(); completed() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableError.value = e.message ?: "No se pudo completar la operación. Inténtalo de nuevo." }
            finally { mutableBusy.value = false }
        }
    }

    fun ingresar(alias: String, password: String, registrar: Boolean) = ejecutar({ repository.ingresar(alias, password, registrar) })
    fun salir() = ejecutar({ repository.salir() })
    fun guardar(r: Registro, done: () -> Unit) = ejecutar({ repository.guardar(r) }, done)
    fun guardar(o: Objetivo, done: () -> Unit = {}) = ejecutar({ repository.guardar(o) }, done)
    fun guardar(r: Recurso, done: () -> Unit) = ejecutar({ repository.guardar(r) }, done)
    fun recordatorio(r: Recordatorio) = ejecutar({ repository.recordatorio(r) })
    fun conectar(url: String, password: String) = ejecutar({ repository.conectar(url, password) })
    fun sincronizar() = ejecutar({ repository.sincronizar() })
}

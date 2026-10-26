package com.losk.lemacdatalab

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import com.losk.lemacdatalab.data.LemacRepository
import com.losk.lemacdatalab.notifications.ReminderScheduler
import com.losk.lemacdatalab.sync.SyncScheduler
import kotlinx.coroutines.*

class LemacApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        val repo = LemacRepository.get(this)
        scope.launch { repo.cargar(); ReminderScheduler.restaurar(this@LemacApplication) }
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { repo.cargar(); repo.state.value.cuenta?.let { SyncScheduler.encolar(this@LemacApplication, it.id) } }
            }
        })
    }
}

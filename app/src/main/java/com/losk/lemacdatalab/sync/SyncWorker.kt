package com.losk.lemacdatalab.sync

import android.content.Context
import androidx.work.*
import com.losk.lemacdatalab.data.LemacRepository
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = LemacRepository.get(applicationContext)
        repo.cargar()
        return if (repo.sincronizar(inputData.getString("owner"))) Result.success() else Result.retry()
    }
}

object SyncScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private fun vinculada(context: Context, owner: String) = context.getSharedPreferences("lemac_preferences", Context.MODE_PRIVATE)
        .getString("$owner.token", null) != null

    fun encolar(context: Context, owner: String) {
        if (!vinculada(context, owner)) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints)
            .setInputData(workDataOf("owner" to owner)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync-$owner", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun programar(context: Context, owner: String) {
        if (!vinculada(context, owner)) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints)
            .setInputData(workDataOf("owner" to owner)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("periodic-$owner", ExistingPeriodicWorkPolicy.KEEP, request)
        encolar(context, owner)
    }

    fun cancelar(context: Context, owner: String) {
        WorkManager.getInstance(context).cancelUniqueWork("sync-$owner")
        WorkManager.getInstance(context).cancelUniqueWork("periodic-$owner")
    }
}

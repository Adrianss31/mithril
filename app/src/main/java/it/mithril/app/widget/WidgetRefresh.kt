package it.mithril.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Aggiornamento periodico dei widget (passi, giorno nuovo): Glance non serve in tempo reale. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Widgets.updateAll(applicationContext)
        return Result.success()
    }
}

object WidgetRefresh {
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("widgets", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

package com.acrylic.outbound.pda.evidence

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.sync.SyncRun
import com.acrylic.outbound.pda.update.UpdateGate
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class E2aRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as PdaApplication
        val id = inputData.getString("clientOrderId") ?: return@withContext Result.failure()
        val row = app.database.localE2aReceipts().queue(id) ?: return@withContext Result.success()
        if (!row.autoRetry) return@withContext Result.success()
        val manager = app.getSystemService(ConnectivityManager::class.java)
        if (manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true)
            return@withContext Result.retry()
        val job = currentCoroutineContext()
        try {
            if (app.e2aRecovery.resume(id, SyncRun(active = { job.ensureActive() })).autoRetry) schedule(app, id, true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (error is UpdateGate.Installing) return@withContext Result.retry()
            if (app.database.localE2aReceipts().queue(id)?.autoRetry == true) return@withContext Result.retry()
        }
        Result.success()
    }

    companion object {
        /** Repairs a process death between the durable queue write and WorkManager enqueue. */
        fun restorePending(app: PdaApplication) {
            val auth = app.session.saved() ?: return
            app.database.localE2aReceipts().pending(auth.getString("userId"), auth.getString("deviceId"),
                auth.getString("warehouseId"), auth.getString("serverRecoveryEpoch").toLong())
                .forEach { schedule(app, it) }
        }
        fun schedule(context: Context, id: String, append: Boolean = false) {
            val task = OneTimeWorkRequestBuilder<E2aRecoveryWorker>().setInputData(workDataOf("clientOrderId" to id))
                .setConstraints(com.acrylic.outbound.pda.sync.SyncScheduler.constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("e2a-recovery:$id",
                if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, task)
        }
    }
}

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

class LocalDraftRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as PdaApplication
        val id = inputData.getString("clientOrderId") ?: return@withContext Result.failure()
        LocalDraftQueueGuard(app.database).reconcile(app.session.saved())
        val queued = app.database.localDraftQueue().get(id) ?: return@withContext Result.success()
        if (!queued.autoRetry) return@withContext Result.success()
        val auth = app.session.saved() ?: return@withContext Result.success()
        val draft = app.database.temporaryDrafts().get(id) ?: return@withContext Result.failure()
        if (draft.operatorId != auth.getString("userId") || draft.creatorDeviceId != auth.getString("deviceId") ||
            draft.warehouseId != auth.getString("warehouseId")) return@withContext Result.success()
        val network = app.getSystemService(ConnectivityManager::class.java)
        if (network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true)
            return@withContext Result.retry()
        val job = currentCoroutineContext()
        try {
            app.localDraftRecovery.resume(id, SyncRun(active = { job.ensureActive() }))
            if (app.database.localDraftQueue().get(id)?.autoRetry == true) schedule(app, id, true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (error is UpdateGate.Installing || app.database.localDraftQueue().get(id)?.autoRetry == true) return@withContext Result.retry()
        }
        Result.success()
    }
    companion object {
        fun restorePending(app: PdaApplication) {
            LocalDraftQueueGuard(app.database).reconcile(app.session.saved())
            val auth = app.session.saved() ?: return
            app.database.localDraftQueue().pending(auth.getString("deviceId"), auth.getString("userId"), auth.getString("warehouseId"))
                .forEach { schedule(app, it) }
        }
        fun schedule(context: Context, id: String, append: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<LocalDraftRecoveryWorker>().setInputData(workDataOf("clientOrderId" to id))
                .setConstraints(com.acrylic.outbound.pda.sync.SyncScheduler.constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("local-draft-recovery:$id",
                if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, request)
        }
    }
}

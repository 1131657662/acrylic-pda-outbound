package com.acrylic.outbound.pda.temporary

import android.content.Context
import androidx.work.*
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.sync.SyncScheduler
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class TemporaryContinuationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT) return@withContext Result.success()
        val app = applicationContext as PdaApplication
        val ticket = inputData.getString("ticketId") ?: return@withContext Result.failure()
        val row = app.database.temporaryContinuationQueue().get(ticket) ?: return@withContext Result.success()
        if (!row.autoRetry) return@withContext Result.success()
        try { app.temporaryContinuationCoordinator.resume(ticket); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (app.database.temporaryContinuationQueue().get(ticket)?.autoRetry == true) Result.retry() else Result.success()
        }
    }

    companion object {
        fun restorePending(app: PdaApplication) {
            if (!BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT) return
            val session = app.session.saved() ?: return
            app.database.temporaryContinuationQueue().pending().forEach { row ->
                val request = app.database.temporary().request(row.ticketId)
                if (request != null && request.userId == session.optString("userId") && request.deviceId == session.optString("deviceId") &&
                    request.warehouseId == session.optString("warehouseId") && request.recoveryEpoch == session.optString("serverRecoveryEpoch")) schedule(app, row.ticketId)
            }
        }

        fun schedule(context: Context, ticket: String) {
            check(BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
            val request = OneTimeWorkRequestBuilder<TemporaryContinuationWorker>().setInputData(workDataOf("ticketId" to ticket))
                .setConstraints(SyncScheduler.constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("temporary-continuation:$ticket", ExistingWorkPolicy.KEEP, request)
        }
    }
}

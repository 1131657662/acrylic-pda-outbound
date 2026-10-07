package com.acrylic.outbound.pda.temporary

import android.content.Context
import androidx.work.*
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.sync.SyncScheduler
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class TemporaryHandoverConfirmationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG || !BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT) return@withContext Result.success()
        val app = applicationContext as PdaApplication
        val id = inputData.getString("caseId") ?: return@withContext Result.failure()
        if (app.database.temporaryHandoverConfirmations().get(id)?.autoRetry != true) return@withContext Result.success()
        try { app.temporaryHandoverConfirmations.resume(id); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (app.database.temporaryHandoverConfirmations().get(id)?.autoRetry == true) Result.retry() else Result.success()
        }
    }

    companion object {
        fun restorePending(app: PdaApplication) {
            if (!BuildConfig.DEBUG || !BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT) return
            val session = app.session.saved() ?: return
            app.database.temporaryHandoverConfirmations().pending().forEach { id ->
                val stage = app.database.temporaryHandoverStages().header(id)
                if (stage != null && stage.targetUserId == session.optString("userId") &&
                    stage.targetDeviceId == session.optString("deviceId") && stage.warehouseId == session.optString("warehouseId")) schedule(app, id)
            }
        }

        fun schedule(context: Context, id: String) {
            check(BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT)
            val request = OneTimeWorkRequestBuilder<TemporaryHandoverConfirmationWorker>().setInputData(workDataOf("caseId" to id))
                .setConstraints(SyncScheduler.constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("temporary-handover-confirmation:$id", ExistingWorkPolicy.KEEP, request)
        }
    }
}

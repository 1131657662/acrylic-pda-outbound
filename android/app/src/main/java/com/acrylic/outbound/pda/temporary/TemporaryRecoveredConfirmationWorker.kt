package com.acrylic.outbound.pda.temporary

import android.content.Context
import androidx.work.*
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.sync.SyncScheduler
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Restores only existing explicit intents belonging to the same admitted LOGIN. */
class TemporaryRecoveredConfirmationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG || !BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT) return@withContext Result.success()
        val app = applicationContext as PdaApplication
        val id = inputData.getString("caseId") ?: return@withContext Result.failure()
        if (app.database.temporaryRecoveredConfirmations().get(id)?.autoRetry != true) return@withContext Result.success()
        try { app.temporaryRecoveredConfirmations.resume(id); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (app.database.temporaryRecoveredConfirmations().get(id)?.autoRetry == true) Result.retry() else Result.success()
        }
    }

    companion object {
        fun restorePending(app: PdaApplication) {
            if (!BuildConfig.DEBUG || !BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT) return
            val session = app.session.saved() ?: return
            for (id in app.database.temporaryRecoveredConfirmations().pending()) {
                val stage = app.database.temporaryRecoveredHandoverStages().get(id) ?: continue
                if (sameLogin(stage, session)) schedule(app, id)
            }
        }
        internal fun sameLogin(stage: TemporaryRecoveredHandoverStage, session: JSONObject): Boolean =
            stage.userId == session.optString("userId") && stage.deviceId == session.optString("deviceId") &&
                stage.warehouseId == session.optString("warehouseId") && stage.sessionId == session.optString("sessionId") &&
                stage.localSessionId == session.optString("localSessionId") && stage.userAuthVersion == session.optString("userAuthVersion") &&
                stage.deviceVersion == session.optString("deviceVersion") && stage.targetRecoveryEpoch == session.optString("serverRecoveryEpoch")
        fun schedule(context: Context, id: String) {
            check(BuildConfig.DEBUG && BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT); TemporaryVerifier.uuid(id)
            val request = OneTimeWorkRequestBuilder<TemporaryRecoveredConfirmationWorker>().setInputData(workDataOf("caseId" to id))
                .setConstraints(SyncScheduler.constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("temporary-recovered-confirmation:$id", ExistingWorkPolicy.KEEP, request)
        }
    }
}

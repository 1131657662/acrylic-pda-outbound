package com.acrylic.outbound.pda.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.update.UpdateGate
import kotlinx.coroutines.*

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val network = applicationContext.getSystemService(ConnectivityManager::class.java)
        if (network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true)
            return@withContext Result.retry()
        val job = currentCoroutineContext()
        try {
            UpdateGate.enterBackground().use {
                val app = applicationContext as PdaApplication
                com.acrylic.outbound.pda.evidence.E2aRecoveryWorker.restorePending(app)
                com.acrylic.outbound.pda.evidence.LocalDraftRecoveryWorker.restorePending(app)
                com.acrylic.outbound.pda.temporary.TemporaryContinuationWorker.restorePending(app)
                com.acrylic.outbound.pda.temporary.TemporaryHandoverConfirmationWorker.restorePending(app)
                val state = SyncScheduleState(applicationContext)
                val next = SyncAttempt(state, app.session::saved, app.backgroundSync::run)
                    .execute { job.ensureActive() }
                job.ensureActive()
                next?.let { SyncScheduler.continueLater(applicationContext, it,
                    inputData.getBoolean(SyncScheduler.FOLLOW_UP, false)).result.get() }
            }
            val app = applicationContext as PdaApplication
            if (app.session.saved() != null) try { app.retention.clean() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: UpdateGate.Busy) { Result.retry() }
        catch (_: Exception) { Result.retry() }
    }
}

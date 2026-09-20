package com.acrylic.outbound.pda.temporary

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.update.UpdateGate
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class TemporaryUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as PdaApplication; val id = inputData.getString("submissionId") ?: return@withContext Result.failure()
        val local = app.database.temporarySubmissions().get(id) ?: return@withContext Result.success()
        if (!local.autoRetry || local.state == "ABORTED") return@withContext Result.success()
        val manager = app.getSystemService(ConnectivityManager::class.java)
        if (manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return@withContext Result.retry()
        try {
            if (app.temporarySubmissions.upload(id).autoRetry) schedule(app, id, 1800, true)
        } catch (error: Exception) {
            if (error is UpdateGate.Installing) return@withContext Result.retry()
            val saved = app.database.temporarySubmissions().get(id)
            if (saved?.autoRetry == true && runAttemptCount < 7) return@withContext Result.retry()
            if (saved != null) app.database.temporarySubmissions().progress(id, saved.state, saved.receipt,
                "自动重试暂停：${error.message.orEmpty()}，请打开原临时批次核对", false)
        }
        Result.success()
    }
    companion object {
        fun schedule(context: Context, id: String, delay: Long = 0, append: Boolean = false) {
            val task = OneTimeWorkRequestBuilder<TemporaryUploadWorker>().setInputData(workDataOf("submissionId" to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).setInitialDelay(delay, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("temporary-upload:$id",
                if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, task)
        }
    }
}

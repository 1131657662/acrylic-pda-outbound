package com.acrylic.outbound.pda.submission

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.network.ApiFailure
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val app = applicationContext as PdaApplication; val id = inputData.getString("submissionId") ?: return@withContext Result.failure()
        val local = app.database.submissions().get(id) ?: return@withContext Result.success()
        if (!local.autoRetry) return@withContext Result.success()
        val manager = app.getSystemService(ConnectivityManager::class.java)
        if (manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            schedule(app, id, 60, inputData.getInt("attempt", 0), true); return@withContext Result.success()
        }
        try {
            val result = com.acrylic.outbound.pda.update.UpdateGate.business { app.submissions.upload(id) }
            if (result.autoRetry) schedule(app, id, 1800, 0, true)
        } catch (error: Exception) {
            if (error is com.acrylic.outbound.pda.update.UpdateGate.Installing) return@withContext Result.retry()
            val attempt = inputData.getInt("attempt", 0) + 1; val saved = app.database.submissions().get(id)
            if (saved?.autoRetry == true && attempt < 8) {
                val seconds = minOf(1800L, 30L * (1L shl (attempt - 1)) + Random.nextLong(10))
                schedule(app, id, maxOf(seconds, (error as? ApiFailure)?.retryAfterSeconds ?: 0), attempt, true)
            } else if (saved != null) app.database.submissions().progress(id, saved.state, saved.receipt, "自动重试暂停：${error.message.orEmpty()}，请打开原批次核对", false)
        }
        Result.success()
    }
    companion object {
        fun schedule(context: Context, id: String, delay: Long = 0, attempt: Int = 0, append: Boolean = false) {
            val task = OneTimeWorkRequestBuilder<UploadWorker>().setInputData(workDataOf("submissionId" to id, "attempt" to attempt))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delay, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("upload:$id", if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, task)
        }
    }
}

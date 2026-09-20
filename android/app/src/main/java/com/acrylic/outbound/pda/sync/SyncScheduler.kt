package com.acrylic.outbound.pda.sync

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val PERIODIC = "pda-status-sync-periodic"
    const val CATCH_UP = "pda-status-sync-catch-up"
    internal const val FOLLOW_UP = "sync-follow-up"
    // A private warehouse API can be reachable while Android's public internet probe is unavailable.
    fun constraints(): Constraints = Constraints.Builder().setRequiredNetworkRequest(
        NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), NetworkType.CONNECTED).build()

    fun start(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(15, TimeUnit.MINUTES).setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    fun resume(context: Context) {
        SyncScheduleState(context).reset()
        enqueue(context, 30, ExistingWorkPolicy.REPLACE)
    }

    // Periodic wakes must not add another delayed node behind an existing catch-up chain.
    fun continueLater(context: Context, seconds: Long, fromFollowUp: Boolean = false) = enqueue(context, seconds,
        if (fromFollowUp) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP)

    private fun enqueue(context: Context, seconds: Long, policy: ExistingWorkPolicy): Operation {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(seconds.coerceAtMost(86400), TimeUnit.SECONDS)
            .setInputData(workDataOf(FOLLOW_UP to true))
            .setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        return WorkManager.getInstance(context).enqueueUniqueWork(CATCH_UP, policy, request)
    }
}

package com.acrylic.outbound.pda.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class SyncSchedulerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager get() = WorkManager.getInstance(context)

    @Before fun initialize() {
        val executor = SynchronousExecutor()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(executor).setTaskExecutor(executor).build())
    }
    @After fun close() { WorkManagerTestInitHelper.closeWorkDatabase() }

    @Test fun periodicWakesDoNotAccumulateDelayedRetries() {
        SyncScheduler.continueLater(context, 21600).result.get()
        val original = pending().single().id

        repeat(24) { SyncScheduler.continueLater(context, 21600 - 900L * it).result.get() }

        assertEquals(listOf(original), pending().map { it.id })
    }

    @Test fun followUpAppendsSuccessorButPeriodicCannotAddAnother() {
        SyncScheduler.continueLater(context, 30).result.get()
        SyncScheduler.continueLater(context, 60, fromFollowUp = true).result.get()

        SyncScheduler.continueLater(context, 30).result.get()

        assertEquals(2, pending().size)
        assertEquals(1, pending().count { it.state == WorkInfo.State.BLOCKED })
    }

    private fun pending() = manager.getWorkInfosForUniqueWork(SyncScheduler.CATCH_UP).get().filter { !it.state.isFinished }
}

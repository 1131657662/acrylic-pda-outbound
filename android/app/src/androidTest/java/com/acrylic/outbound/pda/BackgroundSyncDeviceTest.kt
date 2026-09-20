package com.acrylic.outbound.pda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.sync.*
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Run each method in a separate process on a dedicated package; retained business apps are forbidden. */
@RunWith(AndroidJUnit4::class)
class BackgroundSyncDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val manager get() = WorkManager.getInstance(app)
    private val state get() = SyncScheduleState(app)
    private val checkpoint get() = File(app.filesDir, "background-sync-test.json")
    private val facts get() = BackgroundSyncFacts(app)

    @Before fun requireIsolation() {
        check(app.packageName == "com.acrylic.outbound.pda.debug.r05background")
        check(InstrumentationRegistry.getArguments().getString("syntheticSync") == "true")
    }

    @Test fun seedDurableRetry() {
        check(!checkpoint.exists()) { "Preserve existing evidence; use resume phase" }
        check(app.session.saved()?.optString("localSessionId") in listOf(null, "synthetic-login"))
        manager.cancelUniqueWork(SyncScheduler.CATCH_UP).result.get()
        manager.cancelUniqueWork(SyncScheduler.PERIODIC).result.get()
        BackgroundSyncServer(throttled = true).use { server ->
            val auth = facts.seed()
            val work = immediate()
            await { status(work).state == WorkInfo.State.SUCCEEDED }
            assertEquals(1, state.attempt(state.scope(auth)))
            assertFalse(state.ready(state.scope(auth), System.currentTimeMillis()))
            assertTrue(server.paths.any { it.contains("/sync/orders") })
            assertTrue(server.paths.any { it.contains("/sync/barcodes") })
            assertEquals(server.audits.single(), app.database.audit().checkpoint("1", BackgroundSyncServer.DEVICE)!!.pendingJson)
            facts.assertRetained()
            val pending = manager.getWorkInfosForUniqueWork(SyncScheduler.CATCH_UP).get().filter { !it.state.isFinished }
            assertEquals("Only the retry from this pass should remain", 1, pending.size)
            val next = pending.single()
            assertEquals(WorkInfo.State.ENQUEUED, next.state)
            checkpoint.writeText(JSONObject().put("retryId", next.id.toString()).put("pid", android.os.Process.myPid())
                .put("login", auth.getString("localSessionId")).put("seedRequests", server.paths.size)
                .put("pendingAuditSha256", JwtVerifier.sha(server.audits.single().toByteArray()))
                .put("attempt", state.attempt(state.scope(auth))).put("remainingSeconds", state.remaining(state.scope(auth),
                    System.currentTimeMillis())).toString())
        }
    }

    @Test fun resumeDurableRetryInNewProcess() {
        val evidence = JSONObject(checkpoint.readText())
        assertNotEquals(evidence.getInt("pid"), android.os.Process.myPid())
        assertEquals(evidence.getString("login"), app.session.requireSession().getString("localSessionId"))
        val work = java.util.UUID.fromString(evidence.getString("retryId"))
        BackgroundSyncServer().use { server ->
            await { manager.getWorkInfoById(work).get()?.state == WorkInfo.State.SUCCEEDED }
            assertTrue(server.paths.any { it.contains("/sync/orders") })
            assertTrue(server.paths.any { it.contains("/sync/barcodes?limit=50&cursor=") })
            for (stream in listOf("ORDER", "BARCODE"))
                assertNotNull(app.database.sync().state("1", BackgroundSyncServer.WAREHOUSE, stream)?.cursor)
            assertEquals(0, state.attempt(state.scope(app.session.requireSession())))
            assertEquals(evidence.getString("pendingAuditSha256"), JwtVerifier.sha(server.audits.single().toByteArray()))
            assertEquals(1L, app.database.audit().checkpoint("1", BackgroundSyncServer.DEVICE)!!.acknowledgedThrough)
            assertNull(app.database.audit().checkpoint("1", BackgroundSyncServer.DEVICE)!!.pendingJson)
            assertNull(app.database.auditArchives().rows("1", BackgroundSyncServer.DEVICE).single().proofJson)
            awaitArchiveRetry()
            facts.assertRetained()
            evidence.put("resumedPid", android.os.Process.myPid()).put("resumedRequests", server.paths.size)
                .put("auditReplayedExactly", true).put("auditAcknowledgedThrough", 1).put("archiveUnsupportedRetained", true)
            checkpoint.writeText(evidence.toString())
        }
    }

    @Test fun busyWorkerDefersThenLogoutStopsRequests() {
        manager.cancelUniqueWork(SyncScheduler.CATCH_UP).result.get()
        manager.cancelUniqueWork(SyncScheduler.PERIODIC).result.get()
        BackgroundSyncServer().use { server ->
            UpdateGate.enterBusiness().use {
                val busy = immediate()
                await { status(busy).runAttemptCount >= 1 && status(busy).state == WorkInfo.State.ENQUEUED }
                assertTrue(server.paths.isEmpty())
                manager.cancelWorkById(busy.id).result.get()
            }
            app.session.logout()
            assertNull(app.session.saved())
            val requests = server.paths.size
            val loggedOut = immediate()
            await { status(loggedOut).state == WorkInfo.State.SUCCEEDED }
            assertEquals(requests, server.paths.size)
            facts.assertRetained()
            val evidence = JSONObject(checkpoint.readText()).put("busyAndLogoutVerified", true)
            checkpoint.writeText(evidence.toString())
            manager.cancelUniqueWork(SyncScheduler.CATCH_UP).result.get()
        }
    }

    private fun immediate(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(SyncScheduler.constraints())
        .build().also { manager.enqueueUniqueWork("background-native-probe", ExistingWorkPolicy.REPLACE, it).result.get() }

    private fun status(work: OneTimeWorkRequest): WorkInfo = checkNotNull(manager.getWorkInfoById(work.id).get())

    private fun awaitArchiveRetry() {
        val next = manager.getWorkInfosForUniqueWork(SyncScheduler.CATCH_UP).get().single { !it.state.isFinished }
        await { manager.getWorkInfoById(next.id).get()?.state == WorkInfo.State.SUCCEEDED }
        assertEquals(1, manager.getWorkInfosForUniqueWork(SyncScheduler.CATCH_UP).get().count { !it.state.isFinished })
        assertTrue(state.remaining(state.scope(app.session.requireSession()), System.currentTimeMillis()) in 21590..21601)
    }

    private fun await(condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(75)
        while (!condition()) {
            check(android.os.SystemClock.elapsedRealtime() < end) { "Worker did not reach expected state" }
            Thread.sleep(100)
        }
    }
}

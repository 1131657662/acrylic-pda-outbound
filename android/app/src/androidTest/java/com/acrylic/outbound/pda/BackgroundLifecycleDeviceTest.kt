package com.acrylic.outbound.pda

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.acrylic.outbound.pda.sync.SyncScheduleState
import com.acrylic.outbound.pda.sync.SyncScheduler
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Explicit retained-fixture phases; each is invoked separately by the device rehearsal. */
@RunWith(AndroidJUnit4::class)
class BackgroundLifecycleDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val manager get() = WorkManager.getInstance(app)
    private val facts get() = BackgroundLifecycleFacts(app)
    private val checkedAt get() = app.getSharedPreferences("background-sync", 0).getLong("checkedAt", 0)
    private val offlineFile get() = File(facts.directory, "offline.json")
    private val periodicFile get() = File(facts.directory, "periodic.json")

    @Before fun requireIsolation() {
        check(InstrumentationRegistry.getArguments().getString("retainedBackground") == "true")
        facts.requireFixture()
    }

    @Test fun restoreEndpointAndStartRealPeriodicInterval() {
        check(!periodicFile.exists()) { "Resume the retained periodic phase; do not reset its clock" }
        facts.restoreDirectEndpoint(); facts.preserveBaseline()
        val previousCheck = checkedAt
        SyncScheduler.resume(app)
        await { checkedAt > previousCheck && pending(SyncScheduler.CATCH_UP).isEmpty() }
        assertTrue(SyncScheduleState(app).message().contains("订单、条码和操作记录已同步"))
        manager.cancelUniqueWork(SyncScheduler.PERIODIC).result.get()
        val start = System.currentTimeMillis()
        SyncScheduler.start(app)
        await { pending(SyncScheduler.PERIODIC).size == 1 }
        val work = pending(SyncScheduler.PERIODIC).single()
        assertEquals(900_000L, work.initialDelayMillis)
        assertEquals(900_000L, work.periodicityInfo!!.repeatIntervalMillis)
        assertTrue(work.nextScheduleTimeMillis >= start + 900_000)
        periodicFile.writeText(JSONObject().put("id", work.id).put("startedAt", start)
            .put("firstDueAt", work.nextScheduleTimeMillis).put("pid", android.os.Process.myPid())
            .put("checkedAt", checkedAt).toString())
        facts.assertPreserved()
    }

    @Test fun enqueueWhileWifiDisconnected() {
        check(!offlineFile.exists()) { "Use retained retry phase" }
        assertFalse(wifi())
        facts.assertPreserved()
        SyncScheduler.resume(app)
        await { pending(SyncScheduler.CATCH_UP).size == 1 }
        val work = pending(SyncScheduler.CATCH_UP).single()
        val evidence = JSONObject().put("id", work.id).put("pid", android.os.Process.myPid())
            .put("dueAt", work.nextScheduleTimeMillis).put("checkedAt", checkedAt)
        offlineFile.writeText(evidence.toString())
    }

    @Test fun newProcessKeepsOfflineWorkPending() {
        val evidence = JSONObject(offlineFile.readText())
        assertNotEquals(evidence.getInt("pid"), android.os.Process.myPid())
        assertFalse(wifi())
        await { System.currentTimeMillis() > evidence.getLong("dueAt") + 5000 }
        val work = manager.getWorkInfoById(UUID.fromString(evidence.getString("id"))).get()!!
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(0, work.runAttemptCount)
        assertEquals(evidence.getLong("checkedAt"), checkedAt)
        facts.assertPreserved()
        offlineFile.writeText(evidence.put("offlinePid", android.os.Process.myPid()).put("offlinePastDue", true).toString())
    }

    @Test fun reconnectResumesOriginalWork() {
        val evidence = JSONObject(offlineFile.readText())
        await { wifi() }
        val id = UUID.fromString(evidence.getString("id"))
        await { manager.getWorkInfoById(id).get()?.state == WorkInfo.State.SUCCEEDED }
        assertTrue(checkedAt > evidence.getLong("checkedAt"))
        assertTrue(SyncScheduleState(app).message().contains("订单、条码和操作记录已同步"))
        facts.assertPreserved()
        offlineFile.writeText(evidence.put("resumedPid", android.os.Process.myPid()).put("resumedAt", checkedAt)
            .put("originalWorkSucceeded", true).toString())
    }

    @Test fun actualPeriodicExecutionPreservesWork() {
        val evidence = JSONObject(periodicFile.readText())
        val work = manager.getWorkInfoById(UUID.fromString(evidence.getString("id"))).get()!!
        assertTrue("Wait for the actual 15-minute deadline", System.currentTimeMillis() >= evidence.getLong("firstDueAt"))
        await { checkedAt >= evidence.getLong("firstDueAt") }
        val after = manager.getWorkInfoById(work.id).get()!!
        assertEquals(WorkInfo.State.ENQUEUED, after.state)
        assertTrue(after.nextScheduleTimeMillis >= evidence.getLong("firstDueAt") + 900_000)
        assertEquals(1, pending(SyncScheduler.PERIODIC).size)
        assertTrue(pending(SyncScheduler.CATCH_UP).isEmpty())
        facts.assertPreserved()
        periodicFile.writeText(evidence.put("verifiedAt", System.currentTimeMillis()).put("checkedAtAfter", checkedAt)
            .put("nextDueAt", after.nextScheduleTimeMillis).put("verifiedPid", android.os.Process.myPid()).toString())
    }

    private fun pending(name: String) = manager.getWorkInfosForUniqueWork(name).get().filter { !it.state.isFinished }
    private fun wifi(): Boolean {
        val network = app.getSystemService(ConnectivityManager::class.java)
        return network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    private fun await(condition: () -> Boolean) {
        val until = android.os.SystemClock.elapsedRealtime() + 90_000
        while (!condition()) {
            check(android.os.SystemClock.elapsedRealtime() < until) { "Lifecycle verification timed out: ${SyncScheduleState(app).message()}" }
            Thread.sleep(100)
        }
    }
}

package com.acrylic.outbound.pda

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.sync.SyncScheduleState
import com.acrylic.outbound.pda.sync.SyncScheduler
import com.acrylic.outbound.pda.ui.DeviceSettingsActivity
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** Real API and WorkManager evidence; opt-in on a separate package and fresh backend fixture. */
internal object BackgroundSyncApiScenario {
    fun verify(app: PdaApplication, assignment: String, completed: Boolean) {
        check(app.packageName == "com.acrylic.outbound.pda.debug.r05backgroundapi")
        val auth = app.session.requireSession()
        val epoch = auth.getString("serverRecoveryEpoch"); val warehouse = auth.getString("warehouseId")
        val device = auth.getString("deviceId"); val db = app.database
        val original = db.work().events(assignment)
        val captures = db.captures().page(assignment, 0, Int.MAX_VALUE)
        val scans = db.scans().page(assignment, 0, Int.MAX_VALUE)
        SyncScheduler.resume(app)
        awaitIdle(app)
        val order = checkNotNull(db.preparations().order(assignment))
        assertEquals(if (completed) "COMPLETED" else "ASSIGNED", db.sync().activeOrder(epoch, order.orderId)?.status)
        assertNotNull(db.sync().state(epoch, warehouse, "BARCODE")?.cursor)
        assertEquals(original, db.work().events(assignment))
        assertEquals(captures, db.captures().page(assignment, 0, Int.MAX_VALUE))
        assertEquals(scans, db.scans().page(assignment, 0, Int.MAX_VALUE))
        if (completed) {
            scans.forEach { assertEquals("DISPATCHED", db.barcodes().active(epoch, warehouse, it.barcode)?.status) }
            val prefix = AuditArchiveVerifier.prefix(db.auditArchives().rows(epoch, device), warehouse)
            assertEquals(original.last().localSeq, prefix.last().throughSeq)
            assertEquals(original.last().localSeq, db.audit().checkpoint(epoch, device)!!.acknowledgedThrough)
            verifyPage(app)
            File(app.filesDir, "background-api-evidence.json").writeText(JSONObject().put("orderId", order.orderId)
                .put("deviceId", device).put("events", original.size).put("scans", scans.size)
                .put("backgroundSignedArchiveVerified", true).put("pageVerified", true).put("originalFactsRetained", true).toString())
        }
    }

    private fun awaitIdle(app: PdaApplication) {
        val manager = WorkManager.getInstance(app)
        val deadline = android.os.SystemClock.elapsedRealtime() + 100_000
        while (true) {
            val work = manager.getWorkInfosForUniqueWork(SyncScheduler.CATCH_UP).get()
            if (work.isNotEmpty() && work.all { it.state.isFinished } && work.any { it.state == WorkInfo.State.SUCCEEDED }) break
            check(android.os.SystemClock.elapsedRealtime() < deadline) { SyncScheduleState(app).message() }
            Thread.sleep(100)
        }
        assertTrue(SyncScheduleState(app).message().contains("订单、条码和操作记录已同步"))
    }

    private fun verifyPage(app: PdaApplication) {
        ActivityScenario.launch(DeviceSettingsActivity::class.java).use { scenario ->
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            var shown = false
            while (!shown && android.os.SystemClock.elapsedRealtime() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { shown = it.findViewById<android.widget.TextView>(R.id.syncStatus).text
                    .contains("订单、条码和操作记录已同步") }
                if (!shown) Thread.sleep(50)
            }
            assertTrue("Settings should show the persisted automatic sync result", shown)
        }
        assertFalse(app.session.requireSession().has("blockedReason"))
    }
}

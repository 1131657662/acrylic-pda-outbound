package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.network.SessionChanged
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.update.UpdateGate
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackgroundSyncPersistenceTest {
    @get:Rule val folder = TemporaryFolder()
    private fun control(f: WorkFixture) = SyncRun(1, true, JSONObject(f.session.toString()))
    private fun page(f: WorkFixture, stream: String, complete: Boolean) = JSONObject().put("streamType", stream)
        .put("warehouseId", f.session.getString("warehouseId")).put("recoveryEpoch", "1").put("baselineId", "base")
        .put("baselineComplete", complete).put("baselineNextCursor", if (complete) JSONObject.NULL else "next/+")
        .put("highWatermark", "0").put("nextCursor", if (complete) "cursor" else JSONObject.NULL)
        .put("hasMore", !complete).put("baseline", JSONArray()).put("events", JSONArray())
    private fun answer(raw: String) = JSONObject(raw).let { body ->
        val rows = body.getJSONArray("events")
        JSONObject().put("recoveryEpoch", "1").put("originDeviceId", body.getString("originDeviceId"))
            .put("acknowledgedThrough", rows.getJSONObject(rows.length() - 1).getString("localSeq"))
            .put("batchSha256", JwtVerifier.sha(raw.toByteArray()))
    }

    @Test fun boundedBackgroundBaselinesResumeBothStreamsAfterRoomReopen() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val original = f.db.scans().page(f.assignment, 0, 10)
            assertTrue(SyncRepository(f.db, { f.session }) { _, _ -> page(f, "ORDER", false) }.syncOrders(run = control(f)).more)
            assertTrue(BarcodeRepository(f.db, { f.session }) { _, _ -> page(f, "BARCODE", false) }.syncBarcodes(run = control(f)).more)
            f.reload(); val paths = mutableListOf<String>()
            val orders = SyncRepository(f.db, { f.session }) { path, _ -> paths += path; page(f, "ORDER", true) }
            val barcodes = BarcodeRepository(f.db, { f.session }) { path, _ -> paths += path; page(f, "BARCODE", true) }
            assertFalse(orders.syncOrders(run = control(f)).more); assertFalse(barcodes.syncBarcodes(run = control(f)).more)
            assertTrue(paths.all { it.contains("baselineId=base&baselineCursor=next%2F%2B") })
            assertEquals(original, f.db.scans().page(f.assignment, 0, 10))
            assertNotNull(f.db.scans().guard(f.barcodes[0]))
        }
    }

    @Test fun scanBeginningDuringDownloadDiscardsUncommittedPage() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            var scan: AutoCloseable? = null
            val repo = SyncRepository(f.db, { f.session }) { _, _ -> scan = UpdateGate.enterBusiness(); page(f, "ORDER", true) }
            try { assertThrows(UpdateGate.Busy::class.java) { repo.syncOrders(run = control(f)) } }
            finally { scan?.close() }
            assertNull(f.db.sync().state("1", f.session.getString("warehouseId")))
            assertFalse(SyncRepository(f.db, { f.session }) { _, _ -> page(f, "ORDER", true) }.syncOrders(run = control(f)).more)
        }
    }

    @Test fun changedWarehouseOrLoginNeverActivatesDownloadedPage() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val original = JSONObject(f.session.toString())
            for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId")) {
                f.session = JSONObject(original.toString())
                val repo = BarcodeRepository(f.db, { f.session }) { _, _ ->
                    page(f, "BARCODE", true).also { f.session.put(field, "changed") }
                }
                assertThrows(SessionChanged::class.java) { repo.syncBarcodes(run = control(f)) }
            }
            assertNull(f.db.sync().state("1", original.getString("warehouseId"), "BARCODE"))
        }
    }

    @Test fun lostAuditResponseReplaysExactRequestAfterReopenThenCompletes() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); var sent = ""
            val lost = AuditRepository(f.db, { f.session }) { raw, _ -> sent = raw; throw IOException("lost") }
            assertThrows(IOException::class.java) { lost.syncAudit(control(f)) }; f.reload(); f.scan(f.barcodes[1])
            val requests = mutableListOf<String>()
            val resumed = AuditRepository(f.db, { f.session }) { raw, _ -> requests += raw; answer(raw) }
            assertTrue(resumed.syncAudit(control(f)).more); assertEquals(sent, requests.single())
            assertTrue(resumed.syncAudit(control(f)).more); assertFalse(resumed.syncAudit(control(f)).more)
            assertEquals(2, requests.size)
            assertEquals(f.db.work().deviceEvents(f.session.getString("deviceId")).last().localSeq,
                f.db.audit().checkpoint("1", f.session.getString("deviceId"))!!.acknowledgedThrough)
        }
    }

    @Test fun cancellationAfterResponseKeepsPendingAuditForExactReplay() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            var cancelled = false; var sent = ""
            val control = SyncRun(1, true) { if (cancelled) throw CancellationException("stopped") }
            val repo = AuditRepository(f.db, { f.session }) { raw, _ -> sent = raw; cancelled = true; answer(raw) }
            assertThrows(CancellationException::class.java) { repo.syncAudit(control) }
            val state = f.db.audit().checkpoint("1", f.session.getString("deviceId"))!!
            assertEquals(0L, state.acknowledgedThrough); assertEquals(sent, state.pendingJson)
        }
    }
}

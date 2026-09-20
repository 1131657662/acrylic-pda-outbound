package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.data.*
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
class BarcodeSyncTest {
    @get:Rule val folder = TemporaryFolder()
    private fun namespace(f: WorkFixture) = LocalSyncState("1", f.session.getString("warehouseId"), "BARCODE")
    private fun row(f: WorkFixture, status: String = "AVAILABLE", version: String = "1", cycle: String = "1"): JSONObject {
        val bale = checkNotNull(f.db.preparations().bale(f.assignment, f.barcodes[0]))
        return JSONObject().put("baleId", bale.baleId).put("barcode", bale.barcode).put("status", status)
            .put("dispatchCycle", cycle).put("rowVersion", version).put("updatedAt", "2026-09-18T00:00:00Z")
    }
    private fun response(f: WorkFixture, baseline: Boolean = true, high: String = "1", more: Boolean = false) = JSONObject()
        .put("streamType", "BARCODE").put("recoveryEpoch", "1").put("warehouseId", namespace(f).warehouseId)
        .put("baselineId", if (baseline) "b1" else JSONObject.NULL).put("baselineComplete", true)
        .put("baselineNextCursor", JSONObject.NULL).put("nextCursor", "c$high").put("highWatermark", high)
        .put("hasMore", more).put("baseline", JSONArray()).put("events", JSONArray())
    private fun state(f: WorkFixture) = f.db.sync().state("1", namespace(f).warehouseId, "BARCODE")
    private fun baseline(f: WorkFixture, status: String = "AVAILABLE", version: String = "1") {
        val data = response(f).also { it.getJSONArray("baseline").put(row(f, status, version)) }
        BarcodeApply(f.db).apply(null, namespace(f), data)
    }
    private fun delta(f: WorkFixture, status: String, version: String = "2", cycle: String = "1") = response(f, false, "2").also {
        it.getJSONArray("events").put(row(f, status, version, cycle).put("entityVersion", version).put("sequence", "2")
            .put("createdAt", "2026-09-18T00:00:00Z"))
    }
    @Test fun interruptedBaselineDoesNotActivateRestrictionsUntilAllPagesArrive() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val first = response(f).put("baselineComplete", false).put("baselineNextCursor", "p2").put("nextCursor", JSONObject.NULL)
            first.getJSONArray("baseline").put(row(f, "DISPATCHED")); var calls = 0
            val repo = BarcodeRepository(f.db, { f.session }) { _, _ -> if (calls++ == 0) first else throw IOException("断网") }
            assertThrows(IOException::class.java) { repo.refreshBarcodes() }
            val capture = f.receive(); assertNull(BarcodeWorkGuard(f.db).reason(capture.context)); f.reload()
            assertEquals("p2", state(f)!!.downloadCursor)
            BarcodeApply(f.db).apply(state(f), namespace(f), response(f))
            assertTrue(BarcodeWorkGuard(f.db).reason(capture.context)!!.contains("已在云端确认出库"))
        }
    }
    @Test fun receivedInputIsRecheckedBeforeSaveWhenCloudQuarantinesBale() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            baseline(f); val capture = f.receive()
            BarcodeApply(f.db).apply(state(f), namespace(f), delta(f, "QUARANTINED")); f.processor.drain(f.assignment)
            assertNull(f.db.scans().get(capture.id)); assertTrue(f.db.captures().get(capture.id)!!.error.contains("隔离"))
        }
    }
    @Test fun availableCloudStateCannotReleaseSavedGuardOrReplaceFixedStock() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val saved = f.scan(); val scan = f.db.scans().get(saved.id); val stock = f.db.preparations().bale(f.assignment, f.barcodes[0])
            baseline(f); BarcodeApply(f.db).apply(state(f), namespace(f), delta(f, "AVAILABLE"))
            assertEquals(scan, f.db.scans().get(saved.id)); assertEquals(stock, f.db.preparations().bale(f.assignment, f.barcodes[0]))
            assertNotNull(f.db.scans().guard(f.barcodes[0])); val duplicate = f.scan()
            assertTrue(f.db.captures().get(duplicate.id)!!.error.contains("重复装车"))
        }
    }
    @Test fun newCycleNeverChangesPreviouslyReceivedExpectedCycle() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            baseline(f); val capture = f.receive()
            BarcodeApply(f.db).apply(state(f), namespace(f), delta(f, "AVAILABLE", cycle = "2"))
            assertEquals(1L, f.db.captures().get(capture.id)!!.context.expectedDispatchCycle)
            assertTrue(BarcodeWorkGuard(f.db).reason(capture.context)!!.contains("周期已变化"))
        }
    }
    @Test fun gapRollsBackWholeDeltaAndOlderVersionCannotReopenBlockedBale() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            baseline(f, "DISPATCHED", "3"); val before = state(f)
            val invalid = delta(f, "AVAILABLE"); invalid.getJSONArray("events").getJSONObject(0).put("sequence", "3")
            assertThrows(IllegalStateException::class.java) { BarcodeApply(f.db).apply(before, namespace(f), invalid) }
            assertEquals(before, state(f)); BarcodeApply(f.db).apply(before, namespace(f), delta(f, "AVAILABLE"))
            assertEquals("DISPATCHED", f.db.barcodes().active("1", namespace(f).warehouseId, f.barcodes[0])!!.status)
        }
    }
    @Test fun otherWarehouseCannotAdvanceBarcodeCheckpoint() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            assertThrows(IllegalStateException::class.java) {
                BarcodeApply(f.db).apply(null, namespace(f), response(f).put("warehouseId", "another"))
            }
            assertNull(state(f))
        }
    }
}

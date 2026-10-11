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
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SyncStateTest {
    @get:Rule val folder = TemporaryFolder()
    private val order = UUID.randomUUID().toString()
    private val namespace = LocalSyncState("1", "warehouse")
    private fun response(id: String? = null, high: String = "2", cursor: String? = "c2", more: Boolean = false) = JSONObject()
        .put("streamType", "ORDER").put("recoveryEpoch", "1").put("warehouseId", "warehouse")
        .put("baselineId", id ?: JSONObject.NULL).put("baselineNextCursor", JSONObject.NULL).put("baselineComplete", true)
        .put("highWatermark", high).put("nextCursor", cursor ?: JSONObject.NULL).put("hasMore", more)
        .put("baseline", JSONArray()).put("events", JSONArray())
    private fun row(version: String, status: String = "ASSIGNED") = JSONObject().put("orderId", order)
        .put("rowVersion", version).put("status", status).put("updatedAt", "2026-09-18T00:00:00Z")
    private fun event(sequence: String, version: String, status: String) = row(version, status)
        .put("entityVersion", version).put("sequence", sequence).put("createdAt", "2026-09-18T00:00:00Z")
    private fun state(f: WorkFixture) = f.db.sync().state("1", "warehouse")

    @Test fun withdrawalDraftConsumesDeltaAndRejectsDelayedAssignmentProjection() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val initial = response("b1"); initial.getJSONArray("baseline").put(row("2"))
            SyncApply(f.db).apply(null, namespace, initial)
            val withdrawn = response(high = "3", cursor = "c3"); withdrawn.getJSONArray("events").put(event("3", "4", "DRAFT"))
            SyncApply(f.db).apply(state(f), namespace, withdrawn); f.reload()
            val delayed = response(high = "4", cursor = "c4"); delayed.getJSONArray("events").put(event("4", "3", "ASSIGNED"))
            SyncApply(f.db).apply(state(f), namespace, delayed)
            assertEquals("DRAFT", f.db.sync().order("1", "warehouse", "b1", order)!!.status)
        }
    }

    @Test fun interruptedBaselineResumesWithoutActivatingPartialRowsOrCursor() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val facts = f.db.scans().page(f.assignment, 0, 10)
            val first = response("b1", cursor = null).put("baselineComplete", false).put("baselineNextCursor", "page2")
            first.getJSONArray("baseline").put(row("2"))
            SyncApply(f.db).apply(null, namespace, first)
            assertNull(state(f)!!.cursor); assertNull(state(f)!!.activeBaselineId)
            f.reload(); val checkpoint = state(f)
            assertEquals("page2", checkpoint!!.downloadCursor)
            assertThrows(IllegalStateException::class.java) { SyncApply(f.db).apply(null, namespace, first) }
            SyncApply(f.db).apply(checkpoint, namespace, response("b1"))
            assertEquals("c2", state(f)!!.cursor); assertEquals("b1", state(f)!!.activeBaselineId)
            assertEquals(facts, f.db.scans().page(f.assignment, 0, 10))
        }
    }
    @Test fun malformedSecondEventRollsBackRowsAndCursorThenReplayAppliesOnce() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val baseline = response("b1"); baseline.getJSONArray("baseline").put(row("2"))
            SyncApply(f.db).apply(null, namespace, baseline); val before = state(f)
            val broken = response(high = "4", cursor = "c4")
            broken.getJSONArray("events").put(event("3", "3", "RECALL_PENDING")).put(event("5", "4", "CANCELLED"))
            assertThrows(IllegalStateException::class.java) { SyncApply(f.db).apply(before, namespace, broken) }
            assertEquals(before, state(f)); assertEquals("ASSIGNED", f.db.sync().order("1", "warehouse", "b1", order)!!.status)
            val correct = response(high = "3", cursor = "c3"); correct.getJSONArray("events").put(event("3", "3", "RECALL_PENDING"))
            SyncApply(f.db).apply(before, namespace, correct)
            assertThrows(IllegalStateException::class.java) { SyncApply(f.db).apply(before, namespace, correct) }
            assertEquals("RECALL_PENDING", f.db.sync().order("1", "warehouse", "b1", order)!!.status)
        }
    }
    @Test fun delayedOlderEntityVersionCannotReopenCancelledOrder() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val baseline = response("b1"); baseline.getJSONArray("baseline").put(row("4", "CANCELLED"))
            SyncApply(f.db).apply(null, namespace, baseline)
            val delta = response(high = "3", cursor = "c3"); delta.getJSONArray("events").put(event("3", "3", "ASSIGNED"))
            SyncApply(f.db).apply(state(f), namespace, delta)
            assertEquals("CANCELLED", f.db.sync().order("1", "warehouse", "b1", order)!!.status)
            assertEquals(3L, state(f)!!.sequence)
        }
    }
    @Test fun otherWarehouseAndChangingWatermarkCannotAdvanceDownload() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            assertThrows(IllegalStateException::class.java) {
                SyncApply(f.db).apply(null, namespace, response("b1").put("warehouseId", "another"))
            }
            val first = response("b1", cursor = null).put("baselineComplete", false).put("baselineNextCursor", "page2")
            SyncApply(f.db).apply(null, namespace, first); val before = state(f)
            assertThrows(IllegalStateException::class.java) { SyncApply(f.db).apply(before, namespace, response("b1", high = "3")) }
            assertEquals(before, state(f))
        }
    }
}

package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.data.LocalSyncState
import com.acrylic.outbound.pda.submission.SubmissionFreeze
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecoveryProtectionTest {
    @get:Rule val folder = TemporaryFolder()
    private fun envelope(epoch: String, mode: String = "NORMAL") = JSONObject().put("serverRecoveryEpoch", epoch).put("serviceMode", mode)
    @Test fun recoveryResponsePersistsAcrossSessionReplacementAndBlocksScanAndNewFreeze() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val before = f.db.scans().page(f.assignment, 0, 10)
            RecoveryProtection(f.db).observe(envelope("2"))
            f.session = JSONObject(f.session.toString()).put("serverRecoveryEpoch", "2")
            f.reload(); RecoveryProtection(f.db).observe(envelope("2"), "2")
            assertThrows(IllegalStateException::class.java) { f.receive(f.barcodes[1]) }
            assertThrows(IllegalStateException::class.java) { SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "保留原记录") }
            assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
            assertEquals(before, f.db.scans().page(f.assignment, 0, 10)); assertNotNull(f.db.scans().guard(f.barcodes[0]))
        }
    }
    @Test fun alreadyFrozenEvidenceRemainsReadableAndSameEpochNormalDoesNotClearRecovery() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val frozen = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "原冻结")
            RecoveryProtection(f.db).observe(envelope("1", "RECOVERING"), "1")
            RecoveryProtection(f.db).observe(envelope("1"), "1")
            assertEquals(frozen, SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "不能重写"))
            assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
        }
    }
    @Test fun errorEnvelopeTriggersProtectionBeforeApiFailureAndUnknown404RemainsDistinct() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f -> MockWebServer().use { server ->
            val body = envelope("2").put("code", "SERVER_RECOVERY_EPOCH_MISMATCH").put("message", "对账")
            server.enqueue(MockResponse().setResponseCode(409).setBody(body.toString()))
            val network = PdaNetwork(server.url("/").toString()) { RecoveryProtection(f.db).observe(it, "1") }
            val failure = assertThrows(ApiFailure::class.java) { network.request("pda/sync/orders") }
            assertEquals(409, failure.httpStatus); assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
            server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not found</html>"))
            assertEquals(404, assertThrows(ApiFailure::class.java) { network.request("pda/sync/orders") }.httpStatus)
        } }
    }
    @Test fun syncedRecallBlocksFinalSaveWithoutConsumingExistingConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val capture = f.receive(source = "MANUAL"); f.processor.drain(f.assignment); f.processor.saving.confirm(capture.id)
            val before = f.db.captures().get(capture.id)
            val namespace = LocalSyncState("1", "warehouse")
            val response = JSONObject().put("streamType", "ORDER").put("recoveryEpoch", "1").put("warehouseId", "warehouse")
                .put("baselineId", "b1").put("baselineNextCursor", JSONObject.NULL).put("baselineComplete", true)
                .put("highWatermark", "2").put("nextCursor", "c2").put("hasMore", true).put("events", org.json.JSONArray())
            response.put("baseline", org.json.JSONArray().put(JSONObject().put("orderId", f.input.assignment.getString("orderId"))
                .put("status", "RECALL_PENDING").put("rowVersion", "3").put("updatedAt", "2026-09-18T00:00:00Z")))
            SyncApply(f.db).apply(null, namespace, response)
            assertThrows(IllegalStateException::class.java) { f.processor.saving.save(capture.id) }
            assertThrows(IllegalStateException::class.java) { f.receive(f.barcodes[1]) }
            assertEquals(before, f.db.captures().get(capture.id)); assertNull(f.db.scans().get(capture.id))
        }
    }
}

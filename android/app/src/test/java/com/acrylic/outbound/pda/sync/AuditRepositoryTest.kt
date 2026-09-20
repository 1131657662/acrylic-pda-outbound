package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.security.JwtVerifier
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
class AuditRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun result(raw: String, epoch: String): JSONObject {
        val body = JSONObject(raw); val events = body.getJSONArray("events")
        return JSONObject().put("recoveryEpoch", epoch).put("originDeviceId", body.getString("originDeviceId"))
            .put("acknowledgedThrough", events.getJSONObject(events.length() - 1).getString("localSeq"))
            .put("batchSha256", JwtVerifier.sha(raw.toByteArray(Charsets.UTF_8)))
    }
    private fun checkpoint(f: WorkFixture, epoch: String = "1") = f.db.audit().checkpoint(epoch, f.session.getString("deviceId"))
    @Test fun lostResponseAndRestartReplayExactBatchDespiteNewLocalEvents() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); var original = ""
            val interrupted = AuditRepository(f.db, { f.session }) { raw, _ -> original = raw; throw IOException("回包丢失") }
            assertThrows(IOException::class.java) { interrupted.upload() }; assertEquals(original, checkpoint(f)!!.pendingJson)
            f.reload(); f.scan(f.barcodes[1]); val all = f.db.work().deviceEvents(f.session.getString("deviceId")); val requests = mutableListOf<String>()
            val repo = AuditRepository(f.db, { f.session }) { raw, epoch -> requests.add(raw); result(raw, epoch) }
            assertEquals("操作记录已补传", repo.upload()); assertEquals(original, requests.first()); assertEquals(2, requests.size)
            assertNull(checkpoint(f)!!.pendingJson); assertEquals(all.last().localSeq, checkpoint(f)!!.acknowledgedThrough)
            assertEquals(all, f.db.work().deviceEvents(f.session.getString("deviceId")))
        }
    }
    @Test fun wrongBatchEpochDeviceOrGapNeverAdvancesCheckpoint() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            for ((field, value) in listOf("batchSha256" to "wrong", "recoveryEpoch" to "2", "originDeviceId" to "other", "acknowledgedThrough" to "0")) {
                val repo = AuditRepository(f.db, { f.session }) { raw, epoch -> result(raw, epoch).put(field, value) }
                assertThrows(IllegalStateException::class.java) { repo.upload() }
                assertEquals(0L, checkpoint(f)!!.acknowledgedThrough); assertNotNull(checkpoint(f)!!.pendingJson)
            }
        }
    }
    @Test fun epochChangeResendsOriginalEventsWithoutClearingRecoveryBarrier() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            var old = ""; AuditRepository(f.db, { f.session }) { raw, epoch -> old = raw; result(raw, epoch) }.upload()
            f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "2", mode = "RECONCILING"))
            f.session.put("serverRecoveryEpoch", "2"); var restored = ""
            AuditRepository(f.db, { f.session }) { raw, epoch -> restored = raw; result(raw, epoch) }.upload()
            assertEquals(old, restored); assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
            assertEquals(checkpoint(f)!!.acknowledgedThrough, checkpoint(f, "2")!!.acknowledgedThrough)
        }
    }
    @Test fun pagesStayBoundedAndNoEventsAreRemovedAfterAcknowledgement() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.db.runInTransaction { repeat(405) { f.store.event(f.assignment, null, "WORK_TEST") } }
            val sizes = mutableListOf<Int>()
            AuditRepository(f.db, { f.session }) { raw, epoch -> sizes.add(JSONObject(raw).getJSONArray("events").length()); result(raw, epoch) }.upload()
            assertEquals(listOf(200, 200, 6), sizes); assertEquals(406, f.db.work().deviceEvents(f.session.getString("deviceId")).size)
        }
    }
    @Test fun permissionFailureRetainsPendingRequestAnd404IsOnlyLegacyFallback() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val denied = AuditRepository(f.db, { f.session }) { _, _ -> throw ApiFailure("PERMISSION_DENIED", "权限失效", httpStatus = 403) }
            assertThrows(ApiFailure::class.java) { denied.upload() }; val pending = checkpoint(f)
            val legacy = AuditRepository(f.db, { f.session }) { _, _ -> throw ApiFailure("NOT_FOUND", "旧服务", httpStatus = 404) }
            assertTrue(legacy.upload().contains("尚未提供")); assertEquals(pending, checkpoint(f))
        }
    }
    @Test fun acknowledgementBeyondBatchRequiresCompleteLocalCoverage() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.db.runInTransaction { repeat(205) { f.store.event(f.assignment, null, "WORK_TEST") } }
            val invalid = AuditRepository(f.db, { f.session }) { raw, epoch -> result(raw, epoch).put("acknowledgedThrough", "207") }
            assertThrows(IllegalStateException::class.java) { invalid.upload() }; assertEquals(0L, checkpoint(f)!!.acknowledgedThrough)
            val complete = AuditRepository(f.db, { f.session }) { raw, epoch -> result(raw, epoch).put("acknowledgedThrough", "206") }
            complete.upload(); assertEquals(206L, checkpoint(f)!!.acknowledgedThrough)
        }
    }
}

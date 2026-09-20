package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.recall.RecoveryAuditBody
import com.acrylic.outbound.pda.retention.*
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.sync.AuditRepository
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
class TemporaryAuditTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun lostResponseResumesIdenticalTemporaryBatchAfterDatabaseReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); var original = ""
            val failing = AuditRepository(f.db, { JSONObject(f.fixture.auth.toString()) }, { raw: String, _: String ->
                original = raw; throw IOException("lost")
            })
            assertThrows(IOException::class.java) { failing.upload() }; f.reopen()
            val repository = AuditRepository(f.db, { JSONObject(f.fixture.auth.toString()) }, { raw: String, epoch: String ->
                assertEquals(original, raw); acknowledgement(raw, epoch)
            })
            repository.upload()
            val rows = JSONObject(original).getJSONArray("events")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                assertEquals(f.id, row.getJSONObject("source").getString("clientOrderId"))
                assertEquals(f.catalog.request.deviceId, row.getJSONObject("source").getString("creatorDeviceId"))
                assertEquals(f.catalog.preparation.authorizationId, row.getString("authorizationId")); assertFalse(row.has("assignmentId"))
            }
            assertEquals(rows.length().toLong(), f.db.audit().checkpoint("1", f.catalog.request.deviceId)!!.acknowledgedThrough)
            assertNotNull(f.db.temporaryScans().guard(f.barcodes[0]))
        }
    }
    @Test fun missingTemporarySequenceStopsUploadBeforeNetwork() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan()
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_event WHERE localSeq=2")
            var called = false
            val repository = AuditRepository(f.db, { JSONObject(f.fixture.auth.toString()) }, { _: String, _: String -> called = true; JSONObject() })
            assertThrows(IllegalStateException::class.java) { repository.upload() }; assertFalse(called)
            assertNull(f.db.audit().checkpoint("1", f.catalog.request.deviceId))
        }
    }
    @Test fun duplicateSequenceAcrossSourcesCannotBeAcknowledged() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { temp ->
                val device = temp.catalog.request.deviceId
                formal.db.openHelper.writableDatabase.execSQL("UPDATE work_event SET originDeviceId=?", arrayOf(device))
                formal.db.openHelper.writableDatabase.execSQL("UPDATE temporary_event SET localSeq=1")
                assertEquals(-1L, formal.db.audit().coverage(device, 0, 1))
                val repo = AuditRepository(formal.db, { JSONObject(temp.fixture.auth.toString()) }, { _: String, _: String -> error("Must not send duplicates") })
                assertThrows(IllegalStateException::class.java) { repo.upload() }
            }
        }
    }
    @Test fun temporaryAuditContinuesAcrossBatchBoundary() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val draft = checkNotNull(f.db.temporaryDrafts().get(f.id))
            f.db.runInTransaction { repeat(205) { f.store.event(draft, null, "TEMP_TEST") } }
            val sizes = mutableListOf<Int>(); var through = 0L
            AuditRepository(f.db, { JSONObject(f.fixture.auth.toString()) }, { raw: String, epoch: String ->
                val events = JSONObject(raw).getJSONArray("events"); sizes += events.length()
                for (i in 0 until events.length()) assertEquals((++through).toString(), events.getJSONObject(i).getString("localSeq"))
                acknowledgement(raw, epoch)
            }).upload()
            assertEquals(listOf(200, 6), sizes)
            assertEquals(206L, f.db.audit().checkpoint("1", f.catalog.request.deviceId)!!.acknowledgedThrough)
        }
    }
    @Test fun olderServerRejectsTemporaryBatchWithoutDiscardingRequest() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val repository = AuditRepository(f.db, { JSONObject(f.fixture.auth.toString()) }, { _: String, _: String ->
                throw ApiFailure("VALIDATION_FAILED", "旧后台不支持TEMP", httpStatus = 400)
            })
            assertThrows(ApiFailure::class.java) { repository.upload() }
            val checkpoint = f.db.audit().checkpoint("1", f.catalog.request.deviceId)!!
            assertEquals(0L, checkpoint.acknowledgedThrough); assertNotNull(checkpoint.pendingJson)
        }
    }
    @Test fun mixedTrailArchivesAndRecallKeepEveryOriginalSequence() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { temp ->
                // Fixture identities differ; align only event origins for a local mixed-trail query test.
                val device = temp.catalog.request.deviceId
                formal.db.openHelper.writableDatabase.execSQL("UPDATE work_event SET originDeviceId=?", arrayOf(device))
                formal.db.openHelper.writableDatabase.execSQL("UPDATE work_device SET nextEventSeq=2 WHERE deviceId=?", arrayOf(device))
                formal.db.openHelper.writableDatabase.execSQL("UPDATE temporary_event SET localSeq=2")
                formal.db.openHelper.writableDatabase.execSQL("UPDATE work_device SET nextEventSeq=3 WHERE deviceId=?", arrayOf(device))
                temp.start(); temp.scan()
                val signer = AuditProofFactory(); val auth = JSONObject(temp.fixture.auth.toString()).put("signingKey", signer.key)
                temp.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "NORMAL"))
                AuditRepository(temp.db, { auth }, { raw: String, epoch: String -> acknowledgement(raw, epoch) }).upload()
                val archive = AuditArchiveRepository(temp.db, { auth }) { _, request, epoch ->
                    val rows = DeviceAuditTrail(temp.db).page(device, request.getString("afterSeq").toLong())
                    signer.proof(AuditArchiveVerifier.payload(device, request.getString("afterSeq").toLong(),
                        request.getString("throughSeq").toLong(), JSONArray(rows)), auth.getString("warehouseId"), epoch)
                }
                archive.archive()
                val proof = temp.db.auditArchives().rows("1", device).single()
                assertNotNull(proof.proofJson); assertEquals(1, temp.db.work().deviceEvents(device).size)
                temp.db.runInTransaction { temp.store.event(checkNotNull(temp.db.temporaryDrafts().get(temp.id)), null, "RECALL_STOPPED", "test") }
                val body = RecoveryAuditBody(formal.store).attach(JSONObject(), device, "1", auth.getString("warehouseId"))
                assertEquals(1, body.getJSONArray("auditArchives").length())
                assertEquals(proof.throughSeq.toString(), body.getJSONArray("auditCoverage").getJSONObject(0).getString("afterSeq"))
                assertEquals("TEMP", body.getJSONArray("auditEvents").getJSONObject(0).getJSONObject("source").getString("kind"))
            }
        }
    }
    private fun acknowledgement(raw: String, epoch: String): JSONObject {
        val body = JSONObject(raw); val rows = body.getJSONArray("events")
        return JSONObject().put("recoveryEpoch", epoch).put("originDeviceId", body.getString("originDeviceId"))
            .put("acknowledgedThrough", rows.getJSONObject(rows.length() - 1).getString("localSeq"))
            .put("batchSha256", AuditArchiveVerifier.digest(body))
    }
}

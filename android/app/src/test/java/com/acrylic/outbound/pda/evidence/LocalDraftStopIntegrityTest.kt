package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftStopIntegrityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun anotherDeviceFormalInputCannotPolluteTheStoppedDevicePrefix() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            val capture = formal.scan()
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { f ->
                f.start(); f.receive()
                val before = formal.db.scans().guard(capture.context.barcode)
                val stop = LocalDraftStopRepository(f.store).stop(f.id)
                assertEquals("1", JSONObject(stop.metadataJson).getString("captureHighWatermark"))
                val captures = f.db.localDraftStops().page(f.id, 0).filter { it.kind == "CAPTURE" }
                assertEquals(1, captures.size)
                assertFalse(captures.any { it.recordId == capture.id })
                assertEquals(before, formal.db.scans().guard(capture.context.barcode))
                assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
            }
        }
    }

    @Test fun corruptOriginalBytesFailWithoutReplacingEvidence() = altered(
        "UPDATE local_draft_stop_item SET rawBytes=X'7b7d' WHERE ordinal=1")

    @Test fun missingOriginalPageFailsWithoutRebuilding() = altered(
        "DELETE FROM local_draft_stop_item WHERE ordinal=1")

    @Test fun modifiedLivePendingInputFailsAgainstSavedOriginal() = altered(
        "UPDATE temporary_capture SET rawInput='changed' WHERE state='RECEIVED'")

    @Test fun originalInputCannotBeRemovedWhileItsAuditReferencesIt() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.receive(); val stop = LocalDraftStopRepository(f.store).stop(f.id)
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_capture WHERE state='RECEIVED'")
            }
            assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
        }
    }

    @Test fun earlierSourceMayProgressWithoutRewritingTheCapturedDevicePrefix() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val earlier = f.scan()
            val plan = f.plan().copy(revision = 0)
            val later = java.util.UUID.randomUUID().toString()
            f.drafts.create(later); val saved = f.drafts.save(later, plan); f.drafts.start(later, saved.revision)
            val stop = LocalDraftStopRepository(f.store).stop(later)
            assertEquals("1", JSONObject(stop.metadataJson).getString("captureHighWatermark"))
            val previous = f.db.localDraftStops().page(later, 0).single { it.kind == "SCAN" }
            f.store.load(f.id); f.processor.corrections.voidLast(f.id, earlier.id, "原来源实物核对")
            assertEquals("VOID", f.db.temporaryScans().scan(earlier.id)?.status)
            assertEquals(stop, LocalDraftStopRepository(f.store).read(later))
            assertArrayEquals(previous.rawBytes, f.db.localDraftStops().page(later, 0).single { it.kind == "SCAN" }.rawBytes)
        }
    }

    private fun altered(sql: String) = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.receive(); val original = LocalDraftStopRepository(f.store).stop(f.id)
            f.db.openHelper.writableDatabase.execSQL(sql)
            f.reopen()
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).read(f.id) }
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).stop(f.id) }
            assertEquals(original, f.db.localDraftStops().get(f.id))
            assertEquals("EVIDENCE_STOPPED", f.db.temporaryDrafts().get(f.id)?.state)
        }
    }

    @Test fun missingBarrierCannotBeRecreatedWithANewIdentity() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val original = LocalDraftStopRepository(f.store).stop(f.id)
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_draft_stop_signature")
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_draft_stop_item")
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_draft_stop")
            f.reopen()
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).stop(f.id) }
            assertEquals(listOf(original.evidenceId), f.db.temporaryScans().events(f.id)
                .filter { it.type == "LOCAL_EVIDENCE_STOP" }.map { it.detail })
        }
    }

    @Test fun changedStateCannotResumeWhileOriginalBarrierExists() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); LocalDraftStopRepository(f.store).stop(f.id)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_draft SET state='SCANNING'")
            assertThrows(IllegalStateException::class.java) { f.receive() }
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).read(f.id) }
        }
    }

    @Test fun differentDeviceCannotReadOrRewriteOriginalEvidence() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val original = LocalDraftStopRepository(f.store).stop(f.id)
            f.fixture.auth!!.put("deviceId", java.util.UUID.randomUUID().toString())
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).read(f.id) }
            assertEquals(original, f.db.localDraftStops().get(f.id))
        }
    }
}

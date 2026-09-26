package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.DeviceAuditTrail
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.retention.AuditProofFactory
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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
class LocalDraftStopStateTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun businessRejectionIsPreservedWithoutAcknowledgingFailure() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val input = f.receive(); f.processor.reception.validate(input.id)
            f.cloud(status = "RESERVED"); assertEquals("BUSINESS_REJECTED", f.processor.saving.save(input.id))
            val original = f.db.temporaryScans().capture(input.id)!!
            assertNull(original.failureAckAt)
            LocalDraftStopRepository(f.store).stop(f.id); f.reopen()
            LocalDraftStopRepository(f.store).read(f.id)
            assertEquals(original, f.db.temporaryScans().capture(input.id))
            assertNull(f.db.temporaryScans().guard(input.barcode))
        }
    }

    @Test fun voidAndDiscardedInputsRemainInTheZeroActivePackageSnapshot() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val saved = f.scan(); f.processor.corrections.voidLast(f.id, saved.id, "已卸下")
            val discarded = f.receive(source = "MANUAL"); f.processor.corrections.discard(discarded.id, "未装车")
            val stop = LocalDraftStopRepository(f.store).stop(f.id); f.reopen()
            assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
            val rows = f.db.localDraftStops().page(f.id, 0)
            assertEquals(setOf("COMMITTED", "DISCARDED"), rows.filter { it.kind == "CAPTURE" }.map { it.recordStatus }.toSet())
            assertEquals("VOID", rows.single { it.kind == "SCAN" }.recordStatus)
            assertEquals(0L, f.db.temporaryScans().total(f.id).count)
            assertNull(f.db.temporarySubmissions().forOrder(f.id))
        }
    }

    @Test fun archiveOriginalsSurviveRestartIndependentlyOfTheMutableArchiveTable() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val proof = archive(f)
            val stop = LocalDraftStopRepository(f.store).stop(f.id)
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_audit_archive")
            f.reopen(); assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
            val row = f.db.localDraftStops().page(f.id, 0).single { it.kind == "ARCHIVE" }
            assertEquals(SubmissionJson.canonical(proof), JSONObject(row.rawBytes.toString(Charsets.UTF_8)).getString("originalProof"))
        }
    }

    @Test fun invalidArchiveRollsBackEvenAfterSnapshotRowsWereWritten() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); archive(f)
            f.db.openHelper.writableDatabase.execSQL("UPDATE local_audit_archive SET proofJson='{}'")
            val before = f.db.work().device(f.catalog.request.deviceId)
            assertThrows(org.json.JSONException::class.java) { LocalDraftStopRepository(f.store).stop(f.id) }
            assertNull(f.db.localDraftStops().get(f.id)); assertEquals(0L, f.db.localDraftStops().count(f.id))
            assertEquals(0, f.db.temporaryScans().localStopCount(f.id))
            assertEquals(before, f.db.work().device(f.catalog.request.deviceId))
            assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
        }
    }

    private fun archive(f: TemporaryWorkFixture): JSONObject {
        val source = f.catalog.request
        val events = DeviceAuditTrail(f.db).page(source.deviceId, 0)
        val payload = AuditArchiveVerifier.payload(source.deviceId, 0, events.size.toLong(), JSONArray(events))
        val factory = AuditProofFactory()
        val proof = factory.proof(payload, source.warehouseId, source.recoveryEpoch)
        f.db.auditArchives().insert(factory.row(proof).copy(proofJson = SubmissionJson.canonical(proof),
            signingKeyJson = SubmissionJson.canonical(factory.key)))
        return proof
    }
}

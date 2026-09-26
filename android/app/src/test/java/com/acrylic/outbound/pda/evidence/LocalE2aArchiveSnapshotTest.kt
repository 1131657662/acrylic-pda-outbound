package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.DeviceAuditTrail
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.retention.AuditProofFactory
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryFreeze
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
class LocalE2aArchiveSnapshotTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun signedArchiveSurvivesRestartWithoutSourceRow() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            val proof = insertArchive(fixture)
            TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            val stop = LocalE2aStopRepository(fixture.store).record(fixture.id)
            assertEquals(1, stop.archiveSnapshotCount)
            val original = fixture.db.localE2aObjects().archives(fixture.id, "").single()
            assertEquals(LocalE2aArchiveSnapshot.recordId(stop.freezeDeviceId,
                stop.sourceRecoveryEpoch, 0), original.recordId)
            assertArrayEquals(SubmissionJson.canonical(proof).toByteArray(), original.rawBytes)
            fixture.db.openHelper.writableDatabase.execSQL("DELETE FROM local_audit_archive")
            fixture.reopen()
            assertEquals(stop, LocalE2aStopRepository(fixture.store).record(fixture.id))
            val restored = fixture.db.localE2aObjects().archives(fixture.id, "").single()
            assertEquals(original.objectId, restored.objectId)
            assertArrayEquals(original.rawBytes, restored.rawBytes)
        }
    }

    @Test fun missingArchiveSnapshotFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan(); insertArchive(fixture)
            TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            fixture.db.openHelper.writableDatabase.execSQL(
                "DELETE FROM local_e2a_object WHERE clientOrderId=? AND kind='ARCHIVE'", arrayOf(fixture.id))
            assertThrows(IllegalStateException::class.java) {
                LocalE2aStopRepository(fixture.store).record(fixture.id)
            }
        }
    }

    @Test fun alteredSavedArchiveKeyFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan(); insertArchive(fixture)
            TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            fixture.db.openHelper.writableDatabase.execSQL(
                "UPDATE local_e2a_object SET signingKeyJson='{}' WHERE clientOrderId=? AND kind='ARCHIVE'",
                arrayOf(fixture.id))
            assertThrows(JSONException::class.java) {
                LocalE2aStopRepository(fixture.store).record(fixture.id)
            }
        }
    }

    @Test fun invalidSignedArchiveRollsBackFreeze() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan(); insertArchive(fixture)
            fixture.db.openHelper.writableDatabase.execSQL(
                "UPDATE local_audit_archive SET proofJson='{}'")
            assertThrows(JSONException::class.java) {
                TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            }
            assertNull(fixture.db.temporarySubmissions().forOrder(fixture.id))
            assertNull(fixture.db.localE2aStops().get(fixture.id))
            assertEquals(0, fixture.db.temporaryScans().stopCount(fixture.id))
        }
    }

    private fun insertArchive(fixture: TemporaryWorkFixture): org.json.JSONObject {
        val device = fixture.catalog.request.deviceId
        val warehouse = fixture.catalog.request.warehouseId
        val epoch = fixture.catalog.request.recoveryEpoch
        val events = DeviceAuditTrail(fixture.db).page(device, 0)
        val payload = AuditArchiveVerifier.payload(device, 0, events.size.toLong(), JSONArray(events))
        val factory = AuditProofFactory()
        val proof = factory.proof(payload, warehouse, epoch)
        fixture.db.auditArchives().insert(factory.row(proof).copy(
            proofJson = SubmissionJson.canonical(proof), signingKeyJson = SubmissionJson.canonical(factory.key)))
        return proof
    }
}

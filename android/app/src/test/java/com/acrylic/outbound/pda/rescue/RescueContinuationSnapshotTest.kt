package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.temporary.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueContinuationSnapshotTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun consumedContinuationReadsOriginalRetainedPrefixAfterLogoutAndExpiry() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val draft = TemporaryContinuationActivation(f.work.store).activate(f.work.id, f.raw())
            val access = RescueReadGrant(RescueReadIdentity(draft.creatorDeviceId, draft.operatorId, draft.warehouseId)) {}
            f.work.fixture.auth = null; f.work.fixture.time = f.work.fixture.time.plusSeconds(172801)
            f.work.fixture.reopen()
            val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", draft.clientOrderId), access)
            assertEquals("1", RescueFacts.summary(snapshot).getString("active"))
            assertEquals("300500", RescueFacts.summary(snapshot).getString("weightGrams"))
            assertEquals(f.work.id, snapshot.captures.first().getString("clientOrderId"))
            assertFalse(snapshot.originals.containsKey("submission.json"))
            assertTrue(snapshot.originals.getValue("source-evidence.ndjson").toString(Charsets.UTF_8).contains("PREDECESSOR_SUBMISSION"))
            assertFalse(snapshot.originals.getValue("source-evidence.ndjson").toString(Charsets.UTF_8).contains("offlineToken"))
            assertEquals("CONTINUATION_BLOCKED", f.db.temporaryDrafts().get(draft.clientOrderId)?.state)
        }
    }
    @Test fun missingOrAlteredPredecessorCannotBePresentedAsEmptyNewOrder() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val draft = TemporaryContinuationActivation(f.work.store).activate(f.work.id, f.raw())
            val access = RescueReadGrant(RescueReadIdentity(draft.creatorDeviceId, draft.operatorId, draft.warehouseId)) {}
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_submission SET payload=payload || ' ' WHERE id=?", arrayOf(f.frozen.id))
            assertThrows(IllegalStateException::class.java) {
                RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", draft.clientOrderId), access)
            }
            assertNotNull(f.db.temporarySubmissions().get(f.frozen.id))
        }
    }
    @Test fun oversizedPredecessorIsRejectedBeforeRecursiveParsingAndPreserved() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val draft = TemporaryContinuationActivation(f.work.store).activate(f.work.id, f.raw())
            val access = RescueReadGrant(RescueReadIdentity(draft.creatorDeviceId, draft.operatorId, draft.warehouseId)) {}
            val oversized = f.frozen.payload.padEnd(RescueEnvelope.MAX_BYTES + 1, ' ')
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_submission SET payload=? WHERE id=?", arrayOf(oversized, f.frozen.id))
            val error = assertThrows(IllegalStateException::class.java) {
                RescueRoomSnapshot(f.db).read(RescueTarget("TEMP", draft.clientOrderId), access)
            }
            assertTrue(error.message, error.message.orEmpty().contains("累计原件"))
            f.db.openHelper.readableDatabase.query("SELECT length(payload) FROM temporary_submission WHERE id=?", arrayOf(f.frozen.id)).use {
                assertTrue(it.moveToFirst()); assertEquals(oversized.length, it.getInt(0))
            }
            assertEquals("CONTINUATION_BLOCKED", f.db.temporaryDrafts().get(draft.clientOrderId)?.state)
        }
    }
}

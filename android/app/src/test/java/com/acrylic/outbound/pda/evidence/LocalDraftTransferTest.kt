package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftTransferTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun stoppedSourceCreatesAtomicRequestsAndNeverRegeneratesKeysOnReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); f.receive(source = "MANUAL"); val stop = LocalDraftStopRepository(f.store).stop(f.id)
            val plan = LocalDraftTransferRepository(f.store).prepare(f.id)
            val requests = f.db.localDraftTransfers().page(f.id, -1)
            assertEquals(stop.recordCount + 2, plan.requestCount)
            assertEquals("OPEN", requests.first().operation); assertEquals("VERIFY", requests.last().operation)
            assertEquals(requests.size, requests.map { it.originalKey }.toSet().size)
            f.fixture.stopSigner.unavailable = true; f.reopen()
            assertEquals(plan, LocalDraftTransferRepository(f.store).prepare(f.id))
            assertEquals(requests, f.db.localDraftTransfers().page(f.id, -1)); assertEquals(1, f.fixture.stopSigner.calls)
            assertNull(f.db.temporarySubmissions().forOrder(f.id)); assertEquals("EVIDENCE_STOPPED", f.db.temporaryDrafts().get(f.id)!!.state)
        }
    }
    @Test fun failureDuringRequestInsertionRollsBackWholeTransportPlan() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val stop = LocalDraftStopRepository(f.store).stop(f.id)
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_test_request BEFORE INSERT ON local_draft_request
                WHEN NEW.position=1 BEGIN SELECT RAISE(ABORT,'injected'); END""")
            assertThrows(Exception::class.java) { LocalDraftTransferRepository(f.store).prepare(f.id) }
            assertNull(f.db.localDraftTransfers().get(f.id)); assertEquals(0, f.db.localDraftTransfers().count(f.id))
            assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id)); assertEquals(1, f.fixture.stopSigner.calls)
        }
    }
    @Test fun missingRequestOrMissingHeaderCannotBeRepairedWithNewKeys() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); LocalDraftTransferRepository(f.store).prepare(f.id)
            val db = f.db.openHelper.writableDatabase
            db.execSQL("DELETE FROM local_draft_request WHERE clientOrderId=? AND position=1", arrayOf(f.id))
            assertThrows(IllegalStateException::class.java) { LocalDraftTransferRepository(f.store).prepare(f.id) }
            db.execSQL("DELETE FROM local_draft_transfer WHERE clientOrderId=?", arrayOf(f.id))
            assertThrows(IllegalStateException::class.java) { LocalDraftTransferRepository(f.store).prepare(f.id) }
        }
    }
    @Test fun changedKeyOrBodyIsRejectedDespiteIndividualBodyHash() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id); LocalDraftTransferRepository(f.store).prepare(f.id)
            f.db.openHelper.writableDatabase.execSQL("UPDATE local_draft_request SET originalKey=? WHERE clientOrderId=? AND position=0",
                arrayOf(java.util.UUID.randomUUID().toString(), f.id))
            assertThrows(IllegalStateException::class.java) { LocalDraftTransferRepository(f.store).read(f.id) }
        }
    }
    @Test fun unstoppedDraftCannotGenerateTransferPlan() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            assertThrows(IllegalStateException::class.java) { LocalDraftTransferRepository(f.store).prepare(f.id) }
            assertNull(f.db.localDraftStops().get(f.id)); assertEquals(0L, f.db.localDraftTransfers().count(f.id))
        }
    }
}

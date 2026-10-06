package com.acrylic.outbound.pda.evidence
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.sync.SyncRun
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftQueueMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun nonemptyRoomTwentySixRetainsOriginalKeysRequestsReceiptsWithoutEnablingQueue() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.receive(); val stop = LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); val plan = wire.repository().prepare(f.id)
            wire.repository().resume(f.id, SyncRun(batches = 1))
            val requests = wire.all(); val receipt = f.db.localDraftTransfers().receipt(requests.first().originalKey)!!
            val signature = f.db.localDraftStopSignatures().get(f.id); val events = f.db.temporaryScans().events(f.id)
            downgrade(f); f.fixture.stopSigner.unavailable = true; f.reopen()
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version); assertNull(f.db.localDraftQueue().get(f.id))
            assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id)); assertEquals(signature, f.db.localDraftStopSignatures().get(f.id))
            assertEquals(plan, LocalDraftTransferRepository(f.store).read(f.id)); assertEquals(requests, wire.all())
            val restored = f.db.localDraftTransfers().receipt(receipt.originalKey)!!
            assertEquals(receipt.state, restored.state); assertEquals(receipt.signingKeyJson, restored.signingKeyJson)
            assertArrayEquals(receipt.responseBytes, restored.responseBytes); assertEquals(events, f.db.temporaryScans().events(f.id))
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            assertEquals(plan, wire.repository().prepare(f.id)); assertEquals("ORIGINALS_STORED", wire.repository().resume(f.id).state)
            assertEquals(1, wire.posts.count { it.operation == "OPEN" }); assertEquals(1, f.fixture.stopSigner.calls)
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    private fun downgrade(f: TemporaryWorkFixture) {
        f.db.close()
        val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "26.json").readText()).getJSONObject("database")
        f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            com.acrylic.outbound.pda.temporary.TemporaryContinuationMigrationFixture.restorePreContinuation(db)
            db.execSQL("DROP TABLE local_draft_queue")
            db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
            db.version = 26
        }
    }
}

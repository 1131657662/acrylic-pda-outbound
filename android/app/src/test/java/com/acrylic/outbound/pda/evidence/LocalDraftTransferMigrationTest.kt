package com.acrylic.outbound.pda.evidence
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
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
class LocalDraftTransferMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun nonemptyRoomTwentyFiveKeepsSignatureAndDoesNotInventTransportKeys() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.receive(); val stopped = LocalDraftStopRepository(f.store).stop(f.id)
            val signature = f.db.localDraftStopSignatures().get(f.id); val events = f.db.temporaryScans().events(f.id)
            f.db.close()
            val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "25.json").readText()).getJSONObject("database")
            f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
                db.execSQL("DROP TABLE local_draft_queue"); db.execSQL("DROP TABLE local_draft_receipt"); db.execSQL("DROP TABLE local_draft_request"); db.execSQL("DROP TABLE local_draft_transfer")
                db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash"))); db.version = 25
            }
            f.fixture.stopSigner.unavailable = true; f.reopen(); assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(stopped, LocalDraftStopRepository(f.store).read(f.id)); assertEquals(signature, f.db.localDraftStopSignatures().get(f.id))
            assertEquals(events, f.db.temporaryScans().events(f.id)); assertEquals(0L, f.db.localDraftTransfers().count(f.id))
            assertNull(f.db.localDraftTransfers().get(f.id)); assertThrows(IllegalStateException::class.java) { LocalDraftTransferRepository(f.store).read(f.id) }
            LocalDraftTransferRepository(f.store).prepare(f.id); assertEquals(1, f.fixture.stopSigner.calls)
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}

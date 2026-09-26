package com.acrylic.outbound.pda.evidence

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
class LocalDraftStopMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun unsignedRoomTwentyFourKeepsOriginalStopWithoutRetrospectiveSignature() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val input = f.receive()
            val stop = LocalDraftStopRepository(f.store).stop(f.id)
            val draft = f.db.temporaryDrafts().get(f.id)
            val events = f.db.temporaryScans().events(f.id)
            f.db.close()
            val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "24.json").readText()).getJSONObject("database")
            f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
                db.execSQL("DROP TABLE local_draft_stop_signature")
                db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
                db.version = 24
            }
            f.reopen()
            assertEquals(27, f.db.openHelper.readableDatabase.version)
            assertEquals(stop, f.db.localDraftStops().get(f.id)); assertEquals(stop.recordCount, f.db.localDraftStops().count(f.id))
            assertEquals(draft, f.db.temporaryDrafts().get(f.id)); assertEquals(events, f.db.temporaryScans().events(f.id))
            assertEquals(input, f.db.temporaryScans().capture(input.id)); assertNull(f.db.localDraftStopSignatures().get(f.id))
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).read(f.id) }
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).stop(f.id) }
            assertEquals(1, f.fixture.stopSigner.calls)
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun nonemptyRoomTwentyThreePreservesPendingInputWithoutInventingStop() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val input = f.receive()
            val draft = f.db.temporaryDrafts().get(f.id)
            val events = f.db.temporaryScans().events(f.id)
            val device = f.db.work().device(f.catalog.request.deviceId)
            f.db.close()
            val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "23.json").readText()).getJSONObject("database")
            f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
                db.execSQL("DROP TABLE local_draft_stop_signature")
                db.execSQL("DROP TABLE local_draft_stop_item"); db.execSQL("DROP TABLE local_draft_stop")
                db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
                db.version = 23
            }
            f.reopen()
            assertEquals(27, f.db.openHelper.readableDatabase.version)
            assertEquals(draft, f.db.temporaryDrafts().get(f.id)); assertEquals(input, f.db.temporaryScans().capture(input.id))
            assertEquals(events, f.db.temporaryScans().events(f.id)); assertEquals(device, f.db.work().device(f.catalog.request.deviceId))
            assertNull(f.db.localDraftStops().get(f.id)); assertEquals(0L, f.db.localDraftStops().count(f.id))
            assertNull(f.db.temporarySubmissions().forOrder(f.id))
            val stop = LocalDraftStopRepository(f.store).stop(f.id); f.reopen()
            assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}

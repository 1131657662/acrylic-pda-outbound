package com.acrylic.outbound.pda.evidence
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION

import com.acrylic.outbound.pda.temporary.TemporaryFreeze
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
class LocalE2aTransferMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nonemptyRoomNineteenPreservesStopObjectsAndCannotInventTransferKeys() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan()
            val frozen = TemporaryFreeze(f.store).freeze(f.id, "短装待核对")
            val stop = LocalE2aStopRepository(f.store).record(f.id)
            val original = checkNotNull(f.db.localE2aObjects().frozen(f.id, frozen.id))
            downgradeFixture(f)
            f.reopen()
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(frozen, f.db.temporarySubmissions().forOrder(f.id))
            assertEquals(stop, LocalE2aStopRepository(f.store).record(f.id))
            val restored = checkNotNull(f.db.localE2aObjects().frozen(f.id, frozen.id))
            assertEquals(original.objectId, restored.objectId)
            assertArrayEquals(original.rawBytes, restored.rawBytes)
            assertNull(f.db.localE2aTransfers().get(f.id))
            assertEquals(0L, f.db.localE2aTransfers().requestCount(f.id))
            assertThrows(IllegalStateException::class.java) { LocalE2aTransferRepository(f.store).record(f.id) }
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    private fun downgradeFixture(f: TemporaryWorkFixture) {
        f.db.close()
        val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "19.json").readText())
            .getJSONObject("database")
        f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            db.execSQL("DROP TABLE temporary_archive_evidence"); db.execSQL("DROP TABLE temporary_archive_response"); db.execSQL("DROP TABLE temporary_archive_request"); db.execSQL("DROP TABLE temporary_success_evidence"); db.execSQL("DROP TABLE temporary_success_response")
            db.execSQL("DROP TABLE local_e2a_receipt")
            db.execSQL("DROP TABLE local_e2a_queue")
            db.execSQL("DROP TABLE local_e2a_request")
            db.execSQL("DROP TABLE local_e2a_transfer")
            db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
            db.version = 19
        }
    }
}

package com.acrylic.outbound.pda.temporary
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION

import com.acrylic.outbound.pda.evidence.*
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
class TemporarySuccessMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun populatedRoomTwentyOneUpgradePreservesFrozenRequestsWithoutInventingSuccess() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); val frozen = TemporaryFreeze(f.store).freeze(f.id, "原件耐久迁移")
            val transfer = LocalE2aTransferRepository(f.store).record(f.id)
            val requests = E2aRequestSequence(f.db, transfer).all().toList()
            val guard = f.db.temporaryScans().guard(f.barcodes[0])
            val stop = f.db.localE2aStops().get(f.id)
            f.db.localE2aReceipts().insert(LocalE2aReceipt(requests.first().originalKey, "RAW", "原回执".toByteArray()))
            downgrade(f); f.reopen()
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(frozen, f.db.temporarySubmissions().get(frozen.id))
            assertEquals(guard, f.db.temporaryScans().guard(f.barcodes[0])); assertEquals(stop, f.db.localE2aStops().get(f.id))
            assertEquals(requests, E2aRequestSequence(f.db, transfer).all().toList())
            assertArrayEquals("原回执".toByteArray(), f.db.localE2aReceipts().get(requests.first().originalKey)!!.responseBytes)
            assertNull(f.db.temporarySuccess().evidence(frozen.id))
            assertEquals("RAW", f.db.temporarySuccess().run {
                insert(TemporarySuccessResponse(frozen.id, "a".repeat(64), "unverified".toByteArray()))
                response(frozen.id, "a".repeat(64))!!.state
            })
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    private fun downgrade(f: TemporaryWorkFixture) {
        f.db.close()
        val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "21.json").readText()).getJSONObject("database")
        f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            com.acrylic.outbound.pda.temporary.TemporaryContinuationMigrationFixture.restorePreContinuation(db)
            db.execSQL("DROP TABLE temporary_archive_evidence"); db.execSQL("DROP TABLE temporary_archive_response"); db.execSQL("DROP TABLE temporary_archive_request"); db.execSQL("DROP TABLE temporary_success_evidence"); db.execSQL("DROP TABLE temporary_success_response")
            db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
            db.version = 21
        }
    }
}

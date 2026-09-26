package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.*
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
class LocalE2aReceiptMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun populatedRoomTwentyPreservesExactRequestsAndStartsWithoutInventedReceipts() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); TemporaryFreeze(f.store).freeze(f.id, "短装待核对")
            val plan = LocalE2aTransferRepository(f.store).record(f.id)
            val requests = E2aRequestSequence(f.db, plan).all().toList()
            f.db.close()
            val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "20.json").readText())
                .getJSONObject("database")
            f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
                db.execSQL("DROP TABLE temporary_archive_evidence"); db.execSQL("DROP TABLE temporary_archive_response"); db.execSQL("DROP TABLE temporary_archive_request"); db.execSQL("DROP TABLE temporary_success_evidence"); db.execSQL("DROP TABLE temporary_success_response")
                db.execSQL("DROP TABLE local_e2a_receipt"); db.execSQL("DROP TABLE local_e2a_queue")
                db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
                db.version = 20
            }
            f.reopen()
            assertEquals(27, f.db.openHelper.readableDatabase.version)
            assertEquals(plan, LocalE2aTransferRepository(f.store).record(f.id))
            assertEquals(requests, E2aRequestSequence(f.db, plan).all().toList())
            assertNull(f.db.localE2aReceipts().queue(f.id))
            requests.forEach { assertNull(f.db.localE2aReceipts().get(it.originalKey)) }
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}

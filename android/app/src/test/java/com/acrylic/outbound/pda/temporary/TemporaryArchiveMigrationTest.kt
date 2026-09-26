package com.acrylic.outbound.pda.temporary

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
class TemporaryArchiveMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun nonemptyRoomTwentyTwoPreservesSuccessOriginalsWithoutInventingArchives() = runBlocking<Unit>(Dispatchers.IO) {
        TemporarySuccessFixture(folder.root).use { f ->
            val hash = f.repository.retain(f.local.id, f.bytes); f.repository.verify(f.local.id, hash, f.key)
            val accepted = f.db.temporarySuccess().evidence(f.local.id)
            val frozen = f.db.temporarySubmissions().forOrder(f.work.id)
            val guard = f.db.temporaryScans().guard(f.work.barcodes[0])
            f.db.close()
            val schema = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "22.json").readText()).getJSONObject("database")
            f.work.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
                db.execSQL("DROP TABLE temporary_archive_evidence"); db.execSQL("DROP TABLE temporary_archive_response"); db.execSQL("DROP TABLE temporary_archive_request")
                db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash"))); db.version = 22
            }
            f.reopen(); assertEquals(27, f.db.openHelper.readableDatabase.version)
            assertEquals(accepted, f.db.temporarySuccess().evidence(f.local.id))
            assertArrayEquals(f.bytes, f.db.temporarySuccess().response(f.local.id, hash)!!.responseBytes)
            assertNotNull(f.repository.read(f.local.id, f.key))
            assertEquals(frozen, f.db.temporarySubmissions().forOrder(f.work.id)); assertEquals(guard, f.db.temporaryScans().guard(f.work.barcodes[0]))
            assertNull(f.db.temporaryArchives().evidence(f.local.id, "1")); assertNull(f.db.temporaryArchives().request(f.local.id, "1"))
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}

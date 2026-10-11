package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import com.acrylic.outbound.pda.data.PdaDatabase
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRecoveredConfirmationMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun nonemptyOriginalDownloadAndRecoveryFenceSurviveConfirmationUpgrade() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            val original = f.source.repository.read(f.id); val control = f.db.sync().recovery()
            val payload = f.db.temporaryRecoveredHandoverChunks().read(f.id, "PAYLOAD", original.payloadLength)
            f.db.close(); val previous = TemporaryRecoveredConfirmationMigration.startVersion
            val schema = JSONObject(File(System.getProperty("pda.schemas"), "$previous.json").readText()).getJSONObject("database")
            f.source.local.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { old ->
                old.execSQL("DROP TABLE temporary_recovered_confirmation_receipt"); old.execSQL("DROP TABLE temporary_recovered_confirmation")
                old.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash"))); old.version = previous
            }
            f.source.local.db = PdaDatabase.open(f.source.local.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(original, f.source.repository.read(f.id)); assertEquals(control, f.db.sync().recovery())
            assertArrayEquals(payload, f.db.temporaryRecoveredHandoverChunks().read(f.id, "PAYLOAD", original.payloadLength))
            assertNotNull(f.repository.queue(f.id, true)); assertEquals("CONFIRMED", f.repository.resume(f.id).state)
            assertThrows(Exception::class.java) { f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_recovered_confirmation WHERE caseId=?", arrayOf(f.id)) }
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            f.db.openHelper.readableDatabase.query("PRAGMA synchronous").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        }
    }
}

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
class TemporaryRecoveredHandoverMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nonemptyOriginalPreparationAndReconciliationFenceSurviveUpgrade() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val original = f.db.temporary().byAuthorization(f.base.prepared.authorizationId)
            val recovery = f.db.sync().recovery()
            f.db.close()
            val previous = TemporaryRecoveredHandoverMigration.startVersion
            val schema = JSONObject(File(System.getProperty("pda.schemas"), "$previous.json").readText()).getJSONObject("database")
            f.local.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { old ->
                old.execSQL("DROP TABLE temporary_recovered_confirmation_receipt")
                old.execSQL("DROP TABLE temporary_recovered_confirmation")
                old.execSQL("DROP TABLE temporary_recovered_handover_chunk")
                old.execSQL("DROP TABLE temporary_recovered_handover_stage")
                old.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
                old.version = previous
            }
            f.local.db = PdaDatabase.open(f.local.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(original, f.db.temporary().byAuthorization(f.base.prepared.authorizationId))
            assertEquals(recovery, f.db.sync().recovery())
            assertNotNull(f.repository.stage(f.caseId))
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            f.db.openHelper.readableDatabase.query("PRAGMA synchronous").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        }
    }
    @Test fun boundedChunksSurviveFileReopenAndRestrictParentDeletion() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredHandoverFixture(folder.root).use { f ->
            val row = f.repository.stage(f.caseId)
            val bytes = ByteArray(3 * 1024 * 1024 + 17) { (it % 251).toByte() }
            f.db.runInTransaction {
                f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_recovered_handover_chunk WHERE caseId=? AND kind='PAYLOAD'", arrayOf(f.caseId))
                f.db.temporaryRecoveredHandoverChunks().save(f.caseId, "PAYLOAD", bytes)
            }
            f.local.reopen()
            assertArrayEquals(bytes, f.db.temporaryRecoveredHandoverChunks().read(f.caseId, "PAYLOAD", bytes.size))
            assertThrows(Exception::class.java) { f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_recovered_handover_stage WHERE caseId=?", arrayOf(f.caseId)) }
            assertEquals(row, f.db.temporaryRecoveredHandoverStages().get(f.caseId))
        }
    }
}

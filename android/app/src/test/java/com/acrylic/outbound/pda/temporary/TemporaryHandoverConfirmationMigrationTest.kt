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
class TemporaryHandoverConfirmationMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun migrationPreservesNonemptyStageAndOwnQualificationWithoutInventingConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            val stage = f.repository.stage(f.caseId); val own = f.db.temporary().byAuthorization(stage.targetAuthorizationId)
            f.db.close()
            val oldSchema = JSONObject(File(System.getProperty("pda.schemas"), "33.json").readText()).getJSONObject("database")
            f.local.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { old ->
                old.execSQL("DROP TABLE temporary_handover_confirmation_receipt")
                old.execSQL("DROP TABLE temporary_handover_confirmation")
                old.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(oldSchema.getString("identityHash")))
                old.version = 33
            }
            f.local.db = PdaDatabase.open(f.local.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertArrayEquals(stage.payloadBytes, f.repository.read(f.caseId).payloadBytes)
            assertEquals(stage.manifestJson, f.repository.read(f.caseId).manifestJson)
            assertEquals(own, f.db.temporary().byAuthorization(stage.targetAuthorizationId))
            assertNull(f.db.temporaryHandoverConfirmations().get(f.caseId)); assertTrue(f.db.temporaryHandoverConfirmations().pending().isEmpty())
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun largeReceiptStorageReadsChunksAndProtectsOriginalReferences() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverConfirmationFixture(folder.root).use { f ->
            f.repository.queue(f.id, true); f.repository.resume(f.id)
            val bytes = ByteArray(3 * 1024 * 1024 + 7) { (it % 251).toByte() }
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_handover_confirmation_receipt SET receiptBytes=? WHERE caseId=?", arrayOf(bytes, f.id))
            f.stage.local.reopen()
            assertArrayEquals(bytes, f.db.temporaryHandoverConfirmations().receipt(f.id)!!.receiptBytes)
            assertThrows(Exception::class.java) { f.repository.read(f.id) }
            assertThrows(Exception::class.java) { f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_handover_confirmation WHERE caseId=?", arrayOf(f.id)) }
            assertThrows(Exception::class.java) { f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_handover_stage WHERE caseId=?", arrayOf(f.id)) }
            assertNotNull(f.db.temporaryHandoverConfirmations().get(f.id))
        }
    }
}

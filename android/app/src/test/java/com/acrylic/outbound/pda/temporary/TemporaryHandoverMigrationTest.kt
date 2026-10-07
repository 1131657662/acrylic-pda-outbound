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
class TemporaryHandoverMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nonemptyFrozenWorkAndAuthorizationSurviveMigration() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan()
            val frozen = TemporaryFreeze(f.store).freeze(f.id, "非空升级")
            val request = f.catalog.request; val prepared = f.catalog.preparation
            val before = f.db.temporaryScans().captures(f.id)
            f.db.close()
            val schema = JSONObject(File(System.getProperty("pda.schemas"), "32.json").readText()).getJSONObject("database")
            f.fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { old ->
                old.execSQL("DROP TABLE temporary_handover_stage")
                old.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
                old.version = 32
            }
            f.fixture.db = PdaDatabase.open(f.fixture.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(frozen, f.db.temporarySubmissions().get(frozen.id))
            assertEquals(before, f.db.temporaryScans().captures(f.id))
            assertEquals(request, f.db.temporary().request(request.key))
            assertEquals(prepared, f.db.temporary().byAuthorization(prepared.authorizationId))
            assertNull(f.db.temporaryHandoverStages().get("missing"))
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun largeBlobChunksSurviveReopenAndForeignKeyProtectsOwnQualification() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverFixture(folder.root).use { f ->
            val row = f.repository.stage(f.caseId)
            val bytes = ByteArray(3 * 1024 * 1024 + 17) { (it % 251).toByte() }
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_handover_stage SET payloadBytes=? WHERE caseId=?", arrayOf(bytes, f.caseId))
            f.local.reopen()
            assertArrayEquals(bytes, f.db.temporaryHandoverStages().get(f.caseId)!!.payloadBytes)
            assertThrows(Exception::class.java) { f.db.temporaryHandoverStages().insert(row) }
            f.db.temporaryHandoverStages().insert(row.copy(caseId = "another-case-same-round"))
            assertNotNull(f.db.temporaryHandoverStages().get("another-case-same-round"))
            assertThrows(Exception::class.java) { f.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_preparation WHERE authorizationId=?", arrayOf(row.targetAuthorizationId)) }
            assertNotNull(f.db.temporary().byAuthorization(row.targetAuthorizationId))
        }
    }
}

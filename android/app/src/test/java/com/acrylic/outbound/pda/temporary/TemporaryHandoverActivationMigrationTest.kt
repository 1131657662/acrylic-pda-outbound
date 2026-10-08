package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
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
class TemporaryHandoverActivationMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nonemptyConfirmationMigratesWithoutInventingActivationAndCanThenInstall() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val stage = f.stage.repository.read(f.id)
            val confirmation = f.db.temporaryHandoverConfirmations().receipt(f.id)!!
            f.db.close()
            val old = JSONObject(File(System.getProperty("pda.schemas"), "34.json").readText()).getJSONObject("database")
            f.stage.local.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { sqlite ->
                sqlite.execSQL("DROP TABLE temporary_handover_guard"); sqlite.execSQL("DROP TABLE temporary_handover_activation")
                sqlite.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(old.getString("identityHash")))
                sqlite.version = 34
            }
            f.stage.local.db = PdaDatabase.open(f.stage.local.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertNull(f.db.temporaryHandoverActivations().get(f.id))
            assertArrayEquals(stage.payloadBytes, f.stage.repository.read(f.id).payloadBytes)
            assertArrayEquals(confirmation.receiptBytes, f.db.temporaryHandoverConfirmations().receipt(f.id)!!.receiptBytes)
            assertEquals("HANDOVER_BLOCKED", f.repository.install(f.id, f.original).state)
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}

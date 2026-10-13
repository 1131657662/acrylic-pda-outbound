package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentScanMigrationTest {
    @Test fun originalNonemptyTaskRequestPlanAndCatalogSurviveScanUpgrade() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val task = f.task.repository.read(f.command.id); val request = f.task.repository.latestRequest()
            val original = f.task.sku.repository.original(task.task.importId)
            f.task.sku.db.close()
            val previous = IndependentScanMigration.startVersion
            val schema = JSONObject(File(System.getProperty("pda.schemas"), "$previous.json").readText()).getJSONObject("database")
            f.task.sku.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
                listOf("guard", "void", "scan", "capture_state", "capture", "device_state").forEach { db.execSQL("DROP TABLE independent_$it") }
                db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
                db.version = previous
            }
            f.task.sku.db = PdaDatabase.open(f.task.sku.context); f.reopen()
            assertEquals(PDA_DATABASE_VERSION, f.task.sku.db.openHelper.readableDatabase.version)
            assertEquals(task, f.task.repository.read(f.command.id)); assertEquals(request, f.task.repository.latestRequest())
            assertArrayEquals(original, f.task.sku.repository.original(task.task.importId))
            f.start(); f.save(); assertEquals(1L, f.store.dao.total(f.command.id).count)
            f.task.sku.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}

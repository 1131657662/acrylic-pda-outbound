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
class IndependentTaskMigrationTest {
    @Test fun nonemptyOriginalCatalogSurvivesAdditiveTaskMigration() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val old = f.sku.repository.active()!!; val original = f.sku.repository.original(old.imported.id)
            f.sku.db.close(); downgrade(f)
            f.sku.db = PdaDatabase.open(f.sku.context)
            assertEquals(PDA_DATABASE_VERSION, f.sku.db.openHelper.readableDatabase.version)
            assertEquals(old, f.sku.repository.active()); assertArrayEquals(original, f.sku.repository.original(old.imported.id))
            val created = f.repository.create(f.command(), f.grant())
            f.sku.reopen(); assertEquals(created, f.repository.read(created.task.id))
            assertArrayEquals(original, f.sku.repository.original(old.imported.id))
            f.sku.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    private fun downgrade(f: IndependentTaskFixture) {
        val version = IndependentTaskMigration.startVersion
        val schema = JSONObject(File(System.getProperty("pda.schemas"), "$version.json").readText()).getJSONObject("database")
        f.sku.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            listOf("event", "state", "line", "task", "request").forEach {
                db.execSQL("DROP TABLE ${if (it == "task") "independent_task" else "independent_task_$it"}")
            }
            db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
            db.version = version
        }
    }
}

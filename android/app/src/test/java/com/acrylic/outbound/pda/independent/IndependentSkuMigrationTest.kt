package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.data.*
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
class IndependentSkuMigrationTest {
    @Test fun nonemptyLegacyOriginalAndRecoveryFenceSurviveCatalogUpgrade() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val control = LocalRecoveryControl(observedServerEpoch = "original-epoch", mode = "RECONCILING")
            f.db.sync().saveRecovery(control)
            val original = "original frozen body and signed receipt must remain byte exact"
            f.db.openHelper.writableDatabase.execSQL("""INSERT INTO prepared_order(assignmentId,orderId,operatorId,deviceId,
                bundleId,manifestSha256,assignmentJson,manifestJson,planJson,signingKeyJson,signedManifest,activatedAt,rowCount,ackAt,authorizationHistoryJson)
                VALUES('original-a','original-o','original-u','original-d','original-b','sha','{}','{}','{}','{}',?,'time',1,NULL,'[]')""", arrayOf(original))
            val prepared = f.db.preparations().order("original-a")
            f.db.close()
            downgradeToPrevious(f)
            f.db = PdaDatabase.open(f.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version)
            assertEquals(prepared, f.db.preparations().order("original-a"))
            assertEquals(control, f.db.sync().recovery())
            assertNull(f.repository.active())
            f.activate()
            assertEquals(prepared, f.db.preparations().order("original-a"))
            assertEquals(control, f.db.sync().recovery())
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            f.db.openHelper.readableDatabase.query("PRAGMA synchronous").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        }
    }
    private fun downgradeToPrevious(f: IndependentSkuFixture) {
        val previous = IndependentSkuMigration.startVersion
        val schema = JSONObject(File(System.getProperty("pda.schemas"), "$previous.json").readText()).getJSONObject("database")
        f.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            for (table in listOf("active", "decision", "import", "chunk", "original")) db.execSQL("DROP TABLE independent_sku_$table")
            db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(schema.getString("identityHash")))
            db.version = previous
        }
    }
}

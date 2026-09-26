package com.acrylic.outbound.pda.work

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkMigrationTest {
    @Test fun versionOneUpgradePreservesPreparedDataAndClaimIntent() = runBlocking<Unit>(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>(); context.deleteDatabase("outbound-pda.db")
        val legacy = context.openOrCreateDatabase("outbound-pda.db", 0, null)
        val schema = JSONObject(File(System.getProperty("pda.schemas"), "1.json").readText()).getJSONObject("database")
        createLegacy(legacy, schema)
        legacy.execSQL("INSERT INTO prepared_order VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("a", "o", "u", "d", "b", "sha", "{}", "{}", "{}", "{}", "signature", "time", 1, null))
        legacy.execSQL("INSERT INTO prepared_bale VALUES(?,?,?,?,?,?,?,?,?,?)", arrayOf<Any>("a", "003A0000001", "bale", "003", "model", "name", "15.501", 15501, 4, 1))
        legacy.execSQL("INSERT INTO claim_intent VALUES(?,?,?,?,?,?)", arrayOf("pending-order", "u", "d", "original-key", "original-body", "1"))
        legacy.version = 1; legacy.close()
        val db = PdaDatabase.open(context)
        try {
            assertEquals("signature", db.preparations().order("a")!!.signedManifest)
            assertEquals(15501, db.preparations().bale("a", "003A0000001")!!.weightGrams)
            assertEquals("original-key", db.preparations().intent("pending-order", "u", "d")!!.key)
            assertNull(db.work().order("a"))
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            db.openHelper.writableDatabase.query("PRAGMA synchronous").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        } finally { db.close(); context.deleteDatabase("outbound-pda.db") }
    }
    private fun createLegacy(db: SQLiteDatabase, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices")
            if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.insertOrThrow("room_master_table", null, ContentValues().apply { put("id", 42); put("identity_hash", schema.getString("identityHash")) })
    }
}

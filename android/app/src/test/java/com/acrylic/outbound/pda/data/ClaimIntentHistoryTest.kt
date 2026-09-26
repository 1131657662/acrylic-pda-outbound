package com.acrylic.outbound.pda.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ClaimIntentHistoryTest {
    @Test fun room17UpgradePreservesRejectedAndUnknownIntents() = runBlocking<Unit>(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("outbound-pda.db")
        val legacy = context.openOrCreateDatabase("outbound-pda.db", 0, null)
        val schema = JSONObject(File(System.getProperty("pda.schemas"), "17.json").readText()).getJSONObject("database")
        createLegacy(legacy, schema)
        legacy.execSQL("INSERT INTO claim_intent VALUES(?,?,?,?,?,?,?,?)", arrayOf<Any?>(
            "order", "user", "device", "original-key", "original-body", "epoch", "REJECTED", "原版本冲突"))
        legacy.execSQL("INSERT INTO claim_intent VALUES(?,?,?,?,?,?,?,?)", arrayOf<Any?>(
            "other-order", "user", "device", "unknown-key", "unknown-body", "epoch", "UNKNOWN", null))
        legacy.version = 17
        legacy.close()

        val database = PdaDatabase.open(context)
        try {
            val original = database.preparations().intent("order", "user", "device")!!
            assertEquals("original-key", original.key)
            assertEquals("原版本冲突", original.lastError)
            assertEquals("unknown-key", database.preparations().intent("other-order", "user", "device")!!.key)
            assertEquals("unknown-body", database.preparations().intent("other-order", "user", "device")!!.body)
            assertTrue(database.preparations().history("order", "user", "device").isEmpty())
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { database.close(); context.deleteDatabase("outbound-pda.db") }
    }

    @Test fun replacementArchivesRejectionWithoutOverwritingAnotherOperator() = runBlocking<Unit>(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("outbound-pda.db")
        val database = PdaDatabase.open(context)
        try {
            val old = ClaimIntent("order", "user", "device", "old-key", "old-body", "epoch", "REJECTED", "版本冲突")
            val colleague = ClaimIntent("order", "other", "device", "other-key", "other-body", "epoch")
            database.preparations().insertIntent(old)
            database.preparations().insertIntent(colleague)
            val next = old.copy(key = "new-key", body = "new-body", status = "UNKNOWN", lastError = null)
            database.preparations().replaceRejected(old, next, 123L)

            assertEquals(next, database.preparations().intent("order", "user", "device"))
            assertEquals(colleague, database.preparations().intent("order", "other", "device"))
            val history = database.preparations().history("order", "user", "device").single()
            assertEquals("old-key", history.key)
            assertEquals("old-body", history.body)
            assertEquals("版本冲突", history.lastError)
            assertEquals(123L, history.archivedAt)
            assertEquals("old-key", database.preparations().historyPage("user", "device", 20, 0).single().key)
            assertTrue(database.preparations().historyPage("other", "device", 20, 0).isEmpty())
            assertThrows(IllegalStateException::class.java) { database.preparations().replaceRejected(next, old, 124L) }
            assertEquals(next, database.preparations().intent("order", "user", "device"))
        } finally { database.close(); context.deleteDatabase("outbound-pda.db") }
    }

    private fun createLegacy(database: SQLiteDatabase, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (position in 0 until indices.length()) database.execSQL(indices.getJSONObject(position)
                .getString("createSql").replace("\${TABLE_NAME}", table))
        }
        database.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        database.insertOrThrow("room_master_table", null, ContentValues().apply {
            put("id", 42)
            put("identity_hash", schema.getString("identityHash"))
        })
    }
}

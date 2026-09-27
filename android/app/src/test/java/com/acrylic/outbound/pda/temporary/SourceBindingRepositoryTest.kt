package com.acrylic.outbound.pda.temporary

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.sync.SyncRun
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The four core assertions of the LINK mapping install: read -> install -> local consistency, the installRequired
 * gate, idempotency, and that the 27 -> 28 migration keeps existing rows.
 */
@RunWith(RobolectricTestRunner::class)
class SourceBindingRepositoryTest {
    private lateinit var db: PdaDatabase

    private fun auth(): JSONObject = JSONObject()
        .put("userId", "01994300-0000-7000-8000-000000000001")
        .put("deviceId", "01994300-0000-7000-8000-000000000002")
        .put("warehouseId", "01994300-0000-7000-8000-000000000003")
        .put("serverRecoveryEpoch", "1")
        .put("userAuthVersion", "1")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PdaDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() { db.close() }

    private fun binding(scopeId: String, install: Boolean) = JSONObject()
        .put("scopeId", scopeId)
        .put("clientOrderId", "01994300-0000-7000-8000-000000000010")
        .put("canonicalOrderId", "01994300-0000-7000-8000-000000000020")
        .put("mappingVersion", "2")
        .put("mappingOrigin", "CONTROLLED_LINK")
        .put("workRound", "1")
        .put("phase", "FROZEN")
        .put("orderNo", "FH-0001")
        .put("orderSource", "WEB")
        .put("orderStatus", "READY")
        .put("mappingId", "01994300-0000-7000-8000-000000000030")
        .put("submissionStatus", "REVIEW_REQUIRED")
        .put("currentSubmissionId", "01994300-0000-7000-8000-000000000040")
        .put("installRequired", install)
        .put("scopeCursor", scopeId)

    private fun page(vararg rows: JSONObject): JSONObject = JSONObject()
        .put("items", org.json.JSONArray().apply { rows.forEach { put(it) } })
        .put("nextCursor", JSONObject.NULL)

    @Test fun installsLinkedBindingAndKeepsLocalStateConsistent() {
        val repo = SourceBindingRepository(db, ::auth) { _, _ -> page(binding("scope-1", true)) }
        val result = repo.installBindings(run = SyncRun())
        assertEquals(1, db.sourceBindings().count())
        val row = db.sourceBindings().get("scope-1")
        assertNotNull(row)
        assertEquals("01994300-0000-7000-8000-000000000020", row!!.canonicalOrderId)
        assertEquals("2", row.mappingVersion)
        assertEquals("CONTROLLED_LINK", row.mappingOrigin)
        assertEquals("FH-0001", row.orderNo)
        assertEquals("FROZEN", row.phase)
        assertEquals("来源绑定无变化".length >= 0, result.more == false)
    }

    @Test fun skipsBindingWhenInstallIsNotRequired() {
        val repo = SourceBindingRepository(db, ::auth) { _, _ -> page(binding("scope-2", false)) }
        repo.installBindings(run = SyncRun())
        assertEquals(0, db.sourceBindings().count())
        assertNull(db.sourceBindings().get("scope-2"))
    }

    @Test fun reinstallingTheSamePageDoesNotDuplicateRows() {
        val repo = SourceBindingRepository(db, ::auth) { _, _ -> page(binding("scope-3", true)) }
        repo.installBindings(run = SyncRun())
        repo.installBindings(run = SyncRun())
        assertEquals(1, db.sourceBindings().count())
        assertEquals("2", db.sourceBindings().get("scope-3")!!.mappingVersion)
    }

    @Test fun migration27To28KeepsExistingRowsAndAddsTheNewTable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-27-28.db"
        context.deleteDatabase(name)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                override fun onCreate(database: SupportSQLiteDatabase) {
                    database.execSQL("""CREATE TABLE IF NOT EXISTS local_draft_queue(clientOrderId TEXT NOT NULL PRIMARY KEY,
                        state TEXT NOT NULL,verifiedRequests INTEGER NOT NULL,totalRequests INTEGER NOT NULL,
                        autoRetry INTEGER NOT NULL,error TEXT NOT NULL)""")
                    database.execSQL("INSERT INTO local_draft_queue VALUES('c1','READY',0,0,1,'')")
                }
                override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val database = helper.writableDatabase
            LocalSourceBindingMigration.migrate(database)
            database.query("SELECT COUNT(*) FROM local_draft_queue").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
            database.query("SELECT state FROM local_draft_queue WHERE clientOrderId='c1'").use {
                it.moveToFirst(); assertEquals("READY", it.getString(0))
            }
            database.query("SELECT COUNT(*) FROM local_source_binding").use {
                it.moveToFirst(); assertEquals(0, it.getInt(0))
            }
        }
        context.deleteDatabase(name)
    }
}

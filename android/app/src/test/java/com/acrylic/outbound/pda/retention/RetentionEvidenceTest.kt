package com.acrylic.outbound.pda.retention

import android.content.ContentValues
import android.database.DatabaseUtils
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.submission.SubmissionFreeze
import com.acrylic.outbound.pda.work.WorkFixture
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
class RetentionEvidenceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun protectedOrderAndBarcodeReferencesKeepAllFactsCountersAndPendingAudit() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { work ->
            val invalid = work.scan("ZZZZZZZZZZZ"); work.processor.corrections.acknowledge(invalid.id)
            work.scan(); val frozen = SubmissionFreeze(work.store) { work.now }.freeze(work.assignment, "短装待审")
            val device = work.session.getString("deviceId")
            work.db.audit().save(AuditCheckpoint("1", device, pendingJson = "original audit request"))
            work.db.recalls().insert(LocalRecall("recall", work.assignment, "original recall"))
            val f = RetentionFixture(work.db)
            f.order(work.db.work().order(work.assignment)!!.orderId)
            f.barcode(work.barcodes[0]); f.barcode(work.barcodes[1]); f.barcode("ZZZZZZZZZZZ")
            f.order("disposable"); f.barcode("00000000000"); f.retire()
            for (state in listOf("FROZEN", "RESULT_UNKNOWN", "REVIEW_REQUIRED", "ACTION_REQUIRED", "SYNCED")) {
                work.db.submissions().progress(frozen.id, state, "original receipt", "original error", state != "SYNCED")
                val before = rows(work).filterKeys { !it.startsWith("local_cloud_") }
                val result = f.cleaner.clean()
                assertEquals(if (state == "FROZEN") RetentionResult(1, 1) else RetentionResult(), result)
                assertEquals(before, rows(work).filterKeys { !it.startsWith("local_cloud_") })
            }
            assertNotNull(work.db.scans().guard(work.barcodes[0]))
            assertEquals(frozen.payload, work.db.submissions().get(frozen.id)!!.payload)
            assertNotNull(work.db.barcodes().get("1", "warehouse", "old", "ZZZZZZZZZZZ"))
            work.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun versionElevenMigrationPreservesAllExistingTablesAndDoesNotBackdateRetention() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { work ->
            work.scan(); SubmissionFreeze(work.store) { work.now }.freeze(work.assignment, "升级前冻结")
            work.db.audit().save(AuditCheckpoint("1", work.session.getString("deviceId"), 1, "pending audit"))
            val original = RetentionFixture(work.db)
            original.state("ORDER"); original.state("BARCODE"); original.order(); original.barcode()
            val before = rows(work)
            rebuildEleven(work, before)
            work.db = PdaDatabase.open(work.context)
            assertEquals(16, work.db.openHelper.readableDatabase.version)
            assertEquals(before, rows(work))
            work.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_retained_baseline").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            val upgraded = RetentionFixture(work.db)
            upgraded.state("ORDER", "new"); upgraded.state("BARCODE", "new"); upgraded.cleaner.clean()
            upgraded.now = upgraded.now.plus(java.time.Duration.ofDays(91))
            assertEquals(RetentionResult(), upgraded.cleaner.clean())
            assertNotNull(work.db.sync().order("1", "warehouse", "old", "order"))
            work.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    private fun schema() = JSONObject(File(System.getProperty("pda.schemas"), "11.json").readText()).getJSONObject("database")
    private fun rows(work: WorkFixture): Map<String, List<ContentValues>> {
        val entities = schema().getJSONArray("entities")
        return (0 until entities.length()).associate { index ->
            val entity = entities.getJSONObject(index); val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).joinToString(",") { fields.getJSONObject(it).getString("columnName") }
            table to work.db.openHelper.readableDatabase.query("SELECT $columns FROM $table ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add(ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) }) }
            }
        }
    }

    private fun rebuildEleven(work: WorkFixture, records: Map<String, List<ContentValues>>) {
        work.db.close(); work.context.deleteDatabase("outbound-pda.db")
        work.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            val old = schema(); val entities = old.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length())
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                records.getValue(table).forEach { db.insertOrThrow(table, null, it) }
            }
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(old.getString("identityHash")))
            db.version = 11
        }
    }
}

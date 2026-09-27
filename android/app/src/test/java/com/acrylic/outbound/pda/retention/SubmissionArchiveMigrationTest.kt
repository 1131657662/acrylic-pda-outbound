package com.acrylic.outbound.pda.retention
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION

import android.content.ContentValues
import android.database.DatabaseUtils
import com.acrylic.outbound.pda.data.*
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
class SubmissionArchiveMigrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun versionTwelveMigrationPreservesFactsAndNeverInventsArchiveConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            f.scan(); val frozen = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "迁移保留")
            f.db.audit().save(AuditCheckpoint("1", f.session.getString("deviceId"), 1, "original pending audit"))
            val retention = RetentionFixture(f.db); retention.state("ORDER"); retention.order(); retention.cleaner.clean()
            val before = rows(f); rebuildTwelve(f, before); f.db = PdaDatabase.open(f.context)
            assertEquals(PDA_DATABASE_VERSION, f.db.openHelper.readableDatabase.version); assertEquals(before, rows(f))
            assertNull(f.db.archives().get(frozen.id, "1")); assertEquals(frozen, f.db.submissions().get(frozen.id))
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    @Test fun cacheCleanupRetainsVerifiedArchiveAndOriginalSubmission() = runBlocking<Unit>(Dispatchers.IO) {
        ArchiveFixture().use { f ->
            f.repository.refresh(); val before = checkNotNull(f.saved())
            val retention = RetentionFixture(f.db); retention.order(); retention.barcode(); retention.retire()
            assertEquals(RetentionResult(1, 1), retention.cleaner.clean())
            assertEquals(before, f.saved()); assertEquals(f.local, f.db.submissions().get(f.local.id))
        }
    }
    private fun schema() = JSONObject(File(System.getProperty("pda.schemas"), "12.json").readText()).getJSONObject("database")
    private fun rows(f: WorkFixture): Map<String, List<ContentValues>> {
        val entities = schema().getJSONArray("entities")
        return (0 until entities.length()).associate { index ->
            val entity = entities.getJSONObject(index); val table = entity.getString("tableName"); val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).joinToString(",") { fields.getJSONObject(it).getString("columnName") }
            table to f.db.openHelper.readableDatabase.query("SELECT $columns FROM $table ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add(ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) }) }
            }
        }
    }
    private fun rebuildTwelve(f: WorkFixture, records: Map<String, List<ContentValues>>) {
        f.db.close(); f.context.deleteDatabase("outbound-pda.db")
        f.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            val old = schema(); val entities = old.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                records.getValue(table).forEach { db.insertOrThrow(table, null, it) }
            }
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(old.getString("identityHash"))); db.version = 12
        }
    }
}

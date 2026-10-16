package com.acrylic.outbound.pda

import android.database.Cursor
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.retention.RetentionCleaner
import com.acrylic.outbound.pda.retention.RetentionResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/** A separately named synthetic database; original app data/identity is never used as a fixture. */
class IndependentCacheRetentionDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val run get() = checkNotNull(args.getString("evidenceRun")).also { check(it.matches(Regex("[a-z0-9-]{1,40}"))) }
    private val evidence get() = File(context.filesDir, "cache-retention-evidence/$run").apply { mkdirs() }

    @Test fun nativeConsumerKeepsIndependentReferencesWhileDeletingOnlyDisposableProjection() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("independentCacheRetention") == "true")
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        val source = File(context.filesDir, "cache-retention-input/$run.db")
        val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("inputSha256"), hash)
        val name = "synthetic-cache-retention-$run.db"
        val target = context.getDatabasePath(name)
        check(target.canonicalPath != context.getDatabasePath("outbound-pda.db").canonicalPath)
        check(!target.exists()); source.copyTo(target)
        val db = Room.databaseBuilder(context, PdaDatabase::class.java, name).build()
        try {
            val before = snapshot(db); save("before.json", before)
            assertEquals(RetentionResult(barcodes = 1), RetentionCleaner(db, { "1" }) {
                Instant.parse("2026-04-01T00:00:00.001Z")
            }.clean())
            val after = snapshot(db); save("after.json", after)
            assertEquals(before.filterKeys { it != "local_cloud_barcode" }, after.filterKeys { it != "local_cloud_barcode" })
            assertEquals(2, before.getValue("local_cloud_barcode").size)
            assertEquals(1, after.getValue("local_cloud_barcode").size)
            assertNotNull(db.barcodes().get("1", "warehouse", "old", "003A0000001"))
            assertNull(db.barcodes().get("1", "warehouse", "old", "009A0000009"))
        } finally { db.close(); target.copyTo(File(evidence, "after.db")) }
        File(evidence, "completed.txt").writeText("Synthetic database only; original captures/guards preserved; one disposable projection removed.\n")
    }

    private fun snapshot(db: PdaDatabase): Map<String, List<String>> {
        val sql = db.openHelper.readableDatabase
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return tables.associateWith { table ->
            sql.query("SELECT * FROM \"${table.replace("\"", "\"\"")}\"").use { cursor ->
                buildList { while (cursor.moveToNext()) add(row(cursor)) }.sorted()
            }
        }
    }

    private fun row(cursor: Cursor): String = JSONObject().apply {
        for (index in 0 until cursor.columnCount) put(cursor.getColumnName(index), when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
            Cursor.FIELD_TYPE_BLOB -> java.util.Base64.getEncoder().encodeToString(cursor.getBlob(index))
            else -> cursor.getString(index)
        })
    }.toString()

    private fun save(name: String, facts: Map<String, List<String>>) {
        val json = JSONObject()
        facts.forEach { (table, rows) -> json.put(table, JSONArray(rows)) }
        File(evidence, name).writeText(json.toString(2))
    }
}

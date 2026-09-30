package com.acrylic.outbound.pda

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * R06 A1.1 criterion 鈶?on a real device: the **27 鈫?28** step (`LocalSourceBindingMigration`) must preserve every
 * pre-existing row and must not invent a source binding.
 *
 * <h2>Scope and honest limits (read before citing this as evidence)</h2>
 *
 * * **What this device test proves**: after a genuine process death and a real app-triggered migration, the
 *   database reports version 28, the table this step adds exists and is **empty**, and **every other table's
 *   per-table fingerprint (row count + sha256 over the sorted canonical rows) is byte-identical** to the
 *   pre-migration state. `PRAGMA foreign_key_check` stays clean.
 * * **What this device test does NOT prove**: it does **not** create stop/queue/receipt rows through the product,
 *   so it says nothing about "the stopping action is produced by the product on the device". That half is covered
 *   by the batch's real-Room unit tests (`LocalDraftStopWriterTest`, `LocalDraftSourceBindingMigrationTest`,
 *   `LocalDraftStopSignatureTest`, `LocalDraftStopIntegrityTest`). Two layers together, not one instead of the other.
 * * **Non-emptiness is gated, not assumed**: this test fabricates no rows (G4-2 by decision). It therefore prints
 *   `R06A11_G4_NONEMPTY local_draft_stop=<n> verdict=<NONEMPTY|EMPTY>` and records every table's count in the
 *   summary. If `local_draft_stop` is 0, this run is a **valid migration test but NOT a non-empty migration
 *   deliverable**, and the batch must add the G4-3 stop-side dataset before claiming criterion 鈶?non-empty.
 * * **No product verification is performed**: the signature rows here are pre-existing rows only; validity of a
 *   stop signature on device is deliberately out of scope (it would require the signed temporary-authorisation
 *   fixture chain).
 *
 * Two phases, driven by `.local/r06-a11-driver.ps1`: `phase1` downgrades and fingerprints, the driver issues a real
 * `am force-stop`, and `phase2` runs in a fresh process and asserts the post-migration state.
 */
@RunWith(AndroidJUnit4::class)
class LocalDraftStopUpgradeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as PdaApplication

    private val databaseName = "outbound-pda.db"
    private val addedTable = "local_source_binding"
    private val fingerprintFile = "r06a11-g4-fingerprint-before.json"
    private val seededFingerprintFile = "r06a11-g4-seeded-fingerprint-before.json"

    @Before fun requireAuthorisedDevice() {
        assertTrue("device checks must run on an emulator", android.os.Build.MODEL.startsWith("sdk_gphone"))
        assertEquals("device checks must use the staging build", "com.acrylic.outbound.pda.staging",
            app.packageName)
        assertEquals("this class is only authorised on PdaUpgradeVerify", "PdaUpgradeVerify",
            InstrumentationRegistry.getArguments().getString("r06a11Avd"))
    }

    /** Phase 1: create the database, force it back to 27 with the exported 27 identity hash, fingerprint it. */
    @Test fun phase1() {
        // Let the product create/migrate the database first: a database must exist before it can be downgraded.
        val created = app.database.openHelper.writableDatabase
        assertEquals("the product must bring a fresh database to the current version", 28, created.version)
        app.database.close()

        val identityHash = exportedIdentityHash()
        val path = context.getDatabasePath(databaseName).absolutePath
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("DROP TABLE IF EXISTS $addedTable")
            raw.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(identityHash))
            raw.version = 27
            assertEquals("the downgrade must take effect", 27, raw.version)
            assertEquals("the downgrade must install the exported 27 identity hash", 1,
                raw.rawQuery("SELECT COUNT(*) FROM room_master_table WHERE id=42 AND identity_hash=?",
                    arrayOf(identityHash)).use { it.moveToFirst(); it.getInt(0) })
            assertTrue("the added table must be gone before the migration",
                tableNames(raw).none { it == addedTable })

            val fingerprint = fingerprint(raw)
            val summary = JSONObject().put("phase", 1).put("version", raw.version)
                .put("identityHash", identityHash).put("tables", fingerprint)
            persist(summary)
            report(summary)
        }
    }

    /** Phase 2: in a fresh process the app opens the database, so Room runs the real 27 -> 28 migration. */
    @Test fun phase2() {
        val migrated = app.database.openHelper.writableDatabase
        assertEquals("the app launch must migrate the downgraded database", 28, migrated.version)

        val before = JSONObject(readPersisted().readText()).getJSONObject("tables")
        val path = context.getDatabasePath(databaseName).absolutePath
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertTrue("the step must create $addedTable", tableNames(raw).any { it == addedTable })
            assertEquals("the step must create $addedTable empty", 0L, count(raw, addedTable))

            val after = fingerprint(raw)
            for (name in before.keys()) {
                assertTrue("table $name must survive the migration", after.has(name))
                assertEquals("table $name must be byte-identical across the migration",
                    before.getJSONObject(name).getLong("count"), after.getJSONObject(name).getLong("count"))
                assertEquals("table $name must be byte-identical across the migration",
                    before.getJSONObject(name).getString("sha256"), after.getJSONObject(name).getString("sha256"))
            }
            raw.rawQuery("PRAGMA foreign_key_check", null).use {
                assertTrue("the migration must not break referential integrity", !it.moveToNext())
            }
            report(JSONObject().put("phase", 2).put("version", raw.version).put("tables", after))
        }
    }

    /**
     * G4-3 phase 1 - the same migration step as [phase1], but the database is first populated with the synthetic
     * stop-side dataset from [R06A11SyntheticStopDataset] so the "non-empty migration" bar is actually met for the
     * stop/transfer/queue family.
     *
     * The dataset is raw-SQL synthetic: the stopping action was NOT performed by the product here, the signature row
     * is a structural placeholder (never a valid proof), and no event is claimed to be product-produced. Those halves
     * are covered by the batch's real-Room unit tests. This phase proves only that every one of those rows is
     * byte-identical before and after the migration and the real process restart.
     */
    @Test fun phase1Seeded() {
        val created = app.database.openHelper.writableDatabase
        assertEquals("the product must bring a fresh database to the current version", 28, created.version)
        app.database.close()

        val identityHash = exportedIdentityHash()
        val path = context.getDatabasePath(databaseName).absolutePath
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            val seeded = R06A11SyntheticStopDataset.seed(raw)
            assertEquals("the synthetic stop row must exist before the migration", 1L,
                seeded.getLong("local_draft_stop"))
            assertEquals("the synthetic transfer row must exist before the migration", 1L,
                seeded.getLong("local_draft_transfer"))
            assertEquals("the synthetic queue row must exist before the migration", 1L,
                seeded.getLong("local_draft_queue"))
            assertEquals("it must start PAUSED so a migration cannot resurrect it", 0L,
                raw.rawQuery("SELECT autoRetry FROM local_draft_queue WHERE clientOrderId=?",
                    arrayOf(R06A11SyntheticStopDataset.ORDER_ID)).use { it.moveToFirst(); it.getLong(0) })

            raw.execSQL("DROP TABLE IF EXISTS $addedTable")
            raw.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42", arrayOf(identityHash))
            raw.version = 27
            assertEquals("the downgrade must take effect", 27, raw.version)
            assertTrue("the added table must be gone before the migration",
                tableNames(raw).none { it == addedTable })

            val summary = JSONObject().put("phase", 1).put("seeded", true).put("version", raw.version)
                .put("identityHash", identityHash).put("tables", fingerprint(raw)).put("seededCounts", seeded)
            persistAs(summary, seededFingerprintFile)
            report(summary)
        }
    }

    /**
     * G4-3 phase 2 - runs in a fresh process, so Room performs the real 27 -> 28 migration on the seeded database.
     * Asserts the seeded family survives byte-identically, the added table is created empty, referential integrity
     * holds, and the non-empty bar is actually met (local_draft_stop >= 1).
     */
    @Test fun phase2Seeded() {
        val migrated = app.database.openHelper.writableDatabase
        assertEquals("the app launch must migrate the downgraded database", 28, migrated.version)

        val before = JSONObject(readPersistedAs(seededFingerprintFile).readText()).getJSONObject("tables")
        val path = context.getDatabasePath(databaseName).absolutePath
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertTrue("the step must create $addedTable", tableNames(raw).any { it == addedTable })
            assertEquals("the step must create $addedTable empty", 0L, count(raw, addedTable))

            val after = fingerprint(raw)
            for (name in before.keys()) {
                assertTrue("table $name must survive the migration", after.has(name))
                assertEquals("table $name must keep its row count across the migration",
                    before.getJSONObject(name).getLong("count"), after.getJSONObject(name).getLong("count"))
                assertEquals("table $name must be byte-identical across the migration",
                    before.getJSONObject(name).getString("sha256"), after.getJSONObject(name).getString("sha256"))
            }
            for (name in listOf("local_draft_stop", "local_draft_stop_item", "local_draft_stop_signature",
                "local_draft_transfer", "local_draft_queue")) {
                assertTrue("$name must be non-empty: an empty-database migration is not a non-empty migration",
                    count(raw, name) >= 1L)
            }
            raw.rawQuery("PRAGMA foreign_key_check", null).use {
                assertTrue("the migration must not break referential integrity", !it.moveToNext())
            }
            report(JSONObject().put("phase", 2).put("seeded", true).put("version", raw.version).put("tables", after))
        }
    }

    private fun persistAs(summary: JSONObject, name: String) {
        File(context.filesDir, name).writeText(summary.toString())
        context.getExternalFilesDir(null)?.let { File(it, name).writeText(summary.toString()) }
    }

    private fun readPersistedAs(name: String): File {
        val file = File(context.filesDir, name)
        assertNotNull("phase 1 must have persisted its fingerprint; run phase1Seeded first", file.takeIf { it.isFile })
        return file
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    /** The identity hash Room wrote for schema 27, shipped into the test APK as an asset. */
    private fun exportedIdentityHash(): String {
        instrumentation.context.assets.open("room-27.json").bufferedReader().use { reader ->
            val database = JSONObject(reader.readText()).getJSONObject("database")
            assertEquals("the asset must be the version 27 schema", 27, database.getInt("version"))
            return database.getString("identityHash")
        }
    }

    private fun tableNames(db: SQLiteDatabase): List<String> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
            "AND name<>'room_master_table' ORDER BY name", null).use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names.add(cursor.getString(0))
            names
        }

    private fun count(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { it.moveToFirst(); it.getLong(0) }

    /**
     * Per-table fingerprint: the row count plus a sha256 over the sorted canonical row strings. The added table is
     * excluded because it legitimately differs (absent before, empty after); every other table must be identical.
     */
    private fun fingerprint(db: SQLiteDatabase): JSONObject {
        val result = JSONObject()
        for (name in tableNames(db)) {
            if (name == addedTable) continue
            assertTrue("unexpected table name: $name", name.matches(Regex("[A-Za-z0-9_]+")))
            val rows = mutableListOf<String>()
            db.rawQuery("SELECT * FROM `$name`", null).use { cursor ->
                while (cursor.moveToNext()) rows.add(canonicalRow(cursor))
            }
            result.put(name, JSONObject().put("count", rows.size).put("sha256", sha256(rows.sorted())))
        }
        return result
    }

    private fun canonicalRow(cursor: Cursor): String {
        val text = StringBuilder()
        for (index in 0 until cursor.columnCount) {
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> text.append("\u0000N")
                Cursor.FIELD_TYPE_INTEGER -> text.append("\u0000I").append(cursor.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> text.append("\u0000F").append(cursor.getDouble(index))
                Cursor.FIELD_TYPE_BLOB -> text.append("\u0000B").append(hex(cursor.getBlob(index)))
                else -> text.append("\u0000S").append(cursor.getString(index))
            }
            text.append('\u0001')
        }
        return text.toString()
    }

    private fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) out.append("%02x".format(byte))
        return out.toString()
    }

    private fun sha256(lines: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return hex(digest.digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8)))
    }

    private fun persist(summary: JSONObject) {
        File(context.filesDir, fingerprintFile).writeText(summary.toString())
        // Also outside the app sandbox so the driver can adb pull it alongside the captured instrumentation output.
        context.getExternalFilesDir(null)?.let { File(it, fingerprintFile).writeText(summary.toString()) }
    }

    private fun readPersisted(): File {
        val file = File(context.filesDir, fingerprintFile)
        assertNotNull("phase 1 must have persisted its fingerprint; run phase1 first", file.takeIf { it.isFile })
        return file
    }

    /** Prints per-table counts (the non-emptiness gate) into the captured `am instrument` output. */
    private fun report(summary: JSONObject) {
        val tables = summary.getJSONObject("tables")
        println("R06A11_G4 phase=${summary.getInt("phase")} version=${summary.getInt("version")}")
        for (name in tables.keys().asSequence().sorted()) {
            println("R06A11_G4_TABLE $name count=${tables.getJSONObject(name).getLong("count")}")
        }
        val stop = if (tables.has("local_draft_stop")) tables.getJSONObject("local_draft_stop").getLong("count") else 0L
        println("R06A11_G4_NONEMPTY local_draft_stop=$stop verdict=${if (stop > 0) "NONEMPTY" else "EMPTY"}")
        println("R06A11_G4 tables=${tables.keys().asSequence().count()} summary=${summary.toString().length} chars")
    }
}

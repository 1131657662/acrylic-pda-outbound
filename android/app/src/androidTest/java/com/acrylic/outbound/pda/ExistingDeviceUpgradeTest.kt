package com.acrylic.outbound.pda

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ConnectionSettings
import com.acrylic.outbound.pda.security.SessionVault
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import java.util.UUID

/** Run the capture on the existing v3 APK, replace only the APK, then run verification. */
@RunWith(AndroidJUnit4::class)
class ExistingDeviceUpgradeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val baseline get() = File(context.filesDir, "existing-upgrade-before.json")

    @Test fun captureExistingVersionThree() {
        assertEquals(3L, context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
        check(!baseline.exists()) { "An upgrade baseline already exists; preserve it" }
        readDatabase().use { database ->
            assertEquals(3, database.version)
            val tables = JSONObject()
            tableNames(database).forEach { tables.put(it, tableSnapshot(database, it)) }
            val evidence = JSONObject().put("versionCode", 3).put("schema", database.version)
                .put("tables", tables).put("privateFiles", privateFiles()).put("keySha256", verifyKey())
            baseline.writeText(evidence.toString())
        }
    }

    @Test fun verifyVersionEightPreservesExistingDevice() {
        assertEquals(8L, context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
        val before = JSONObject(baseline.readText())
        assertEquals(before.getString("keySha256"), verifyKey())
        verifyPrivateState(before)
        val upgraded = PdaDatabase.open(context)
        try { assertEquals(7, upgraded.openHelper.writableDatabase.version) } finally { upgraded.close() }
        val counts = JSONObject()
        readDatabase().use { database ->
            assertEquals(7, database.version)
            val tables = before.getJSONObject("tables")
            tables.keys().forEach { name ->
                val expected = tables.getJSONObject(name)
                val actual = tableSnapshot(database, name, expected.getJSONArray("columns"))
                assertEquals("Existing rows changed in $name", expected.toString(), actual.toString())
                counts.put(name, actual.getLong("count"))
            }
        }
        verifyPrivateState(before)
        val result = JSONObject().put("fromVersion", 3).put("toVersion", 8).put("fromSchema", 3).put("toSchema", 7)
            .put("keySha256", verifyKey()).put("existingRowsPreserved", true).put("privateFilesPreserved", true)
            .put("originalKeySignatureVerified", true).put("originalSessionDecrypted", true).put("tableCounts", counts)
        File(context.filesDir, "existing-upgrade-result.json").writeText(result.toString())
    }

    private fun readDatabase(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        context.getDatabasePath("outbound-pda.db").path, null, SQLiteDatabase.OPEN_READONLY)

    private fun tableNames(database: SQLiteDatabase): List<String> = database.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY name", null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun tableSnapshot(database: SQLiteDatabase, table: String, columns: JSONArray? = null): JSONObject {
        val projection = columns?.let { (0 until it.length()).joinToString(",") { i -> identifier(it.getString(i)) } } ?: "*"
        return database.rawQuery("SELECT $projection FROM ${identifier(table)}", null).use { cursor ->
            val rows = buildList { while (cursor.moveToNext()) add(row(cursor)) }
            JSONObject().put("columns", JSONArray(cursor.columnNames.toList())).put("count", rows.size)
                .put("sha256", sha(rows.sorted().joinToString("\n").toByteArray()))
        }
    }

    private fun identifier(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_]+")))
        return "`$value`"
    }

    private fun row(cursor: Cursor): String {
        val values = JSONArray()
        for (i in 0 until cursor.columnCount) values.put(when (cursor.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(cursor.getBlob(i))
            else -> cursor.getString(i)
        })
        return values.toString()
    }

    private fun privateFiles(): JSONObject {
        val root = File(context.applicationInfo.dataDir)
        val files = listOf("shared_prefs", "no_backup").flatMap { directory ->
            File(root, directory).walkTopDown().filter { it.isFile && !it.name.startsWith("androidx.work.workdb") }.toList()
        }.sortedBy { it.relativeTo(root).path }
        val result = JSONObject()
        files.forEach { result.put(it.relativeTo(root).path, sha(it.readBytes())) }
        return result
    }

    private fun verifyPrivateState(before: JSONObject) {
        val expected = JSONObject(before.getJSONObject("privateFiles").toString())
        // Scheduler WAL/SHM is process state, not an immutable identity or business record.
        expected.keys().asSequence().filter { it.startsWith("no_backup/androidx.work.workdb") }.toList().forEach(expected::remove)
        assertEquals(expected.toString(), privateFiles().toString())
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keys.containsAlias("outbound-session-v1"))
        val session = checkNotNull(SessionVault(context).read())
        assertEquals(ConnectionSettings(context).device, session.getString("deviceId"))
    }

    private fun verifyKey(): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "outbound-device-v1"
        assertTrue("Existing device key must not be regenerated", store.containsAlias(alias))
        val publicKey = store.getCertificate(alias).publicKey
        val challenge = UUID.randomUUID().toString().toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(store.getKey(alias, null) as PrivateKey); update(challenge); sign()
        }
        assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(publicKey); update(challenge); verify(signature) })
        return sha(publicKey.encoded)
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

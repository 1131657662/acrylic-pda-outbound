package com.acrylic.outbound.pda.ui

import android.widget.EditText
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.rescue.RescueBundle
import com.acrylic.outbound.pda.rescue.RescueEnvelope
import com.acrylic.outbound.pda.security.DeviceKeys
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Product pages with synthetic authority/JCA/file URI; no real device or external SAF claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentPagesExportTest {
    @Test fun originalCardTaskScanLogoutAndRescuePagesProduceSameNonemptyOriginalForPc() {
        IndependentPagesExportFixture().use { f ->
            val original = f.work.original
            val captureCard = f.work.task.card.card.readBytes(); val ownCard = f.ownCard.readBytes()
            f.scan(); f.logout()
            val before = databaseDigest(f)
            f.openExport(); f.chooseOwnCard()
            assertTrue(f.model.state.value.message, f.model.state.value.unlocked)
            assertNull(f.base.vault.read()); f.readSnapshot()
            val material = f.generate(); val bytes = material.readBytes()
            f.copy(null)
            assertTrue(f.model.state.value.canCopy); assertTrue(f.model.state.value.message.contains("取消"))
            val copy = File(f.activity.cacheDir, "synthetic-pages-copy-" + java.util.UUID.randomUUID() + ".pdarescue")
            f.copy(copy)
            assertTrue(f.model.state.value.copyState.contains("目标写入与回读一致"))
            assertTrue(f.model.state.value.copyState.contains("电脑副本尚未核验"))
            assertArrayEquals(f.packages.single().readBytes(), copy.readBytes())
            val files = verify(f, copy, material)
            persist(f, files.getValue("order.json"), copy, material)
            assertEquals(before, databaseDigest(f)); assertEquals(original, f.work.engine.state.value.task?.task)
            assertArrayEquals(captureCard, f.work.task.card.card.readBytes()); assertArrayEquals(ownCard, f.ownCard.readBytes())
            assertArrayEquals(bytes, material.readBytes()); assertEquals(2, f.base.server.requestCount)
            assertEquals("", f.activity.findViewById<EditText>(R.id.credential).text.toString())
        }
    }

    private fun verify(f: IndependentPagesExportFixture, copy: File, material: File): Map<String, ByteArray> {
        val secret = JSONObject(material.readText()).getString("packagePassword").toCharArray()
        val plain = try { RescueEnvelope.decrypt(copy.readBytes(), secret) } finally { secret.fill('\u0000') }
        val files = ZipInputStream(plain.inputStream()).use { zip ->
            buildMap { while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) } }
        }
        val order = JSONObject(files.getValue("order.json").toString(Charsets.UTF_8))
        assertEquals("INDEPENDENT", order.getString("source")); assertEquals(f.work.original.id, order.getString("localOrderId"))
        assertTrue(order.getBoolean("pendingReconciliation")); assertFalse(order.getBoolean("serverAuthorizationVerified"))
        val scan = JSONObject(files.getValue("scans.ndjson").toString(Charsets.UTF_8).trim())
        assertEquals("003A0000001", scan.getString("barcode")); assertEquals(300500L, scan.getLong("weightGrams"))
        assertEquals(1L, scan.getLong("orderScanSeq")); assertEquals("ACTIVE", scan.getString("status"))
        assertFalse(files.containsKey("submission.json")); assertFalse(files.containsKey("receipt.json"))
        val contents = files.values.joinToString { it.toString(Charsets.ISO_8859_1) }
        val secrets = listOf(f.work.original.signedCredential, JSONObject(f.ownCard.readText()).getString("credential"),
            JSONObject(f.work.task.card.card.readText()).getString("credential"), JSONObject(material.readText()).getString("packagePassword"),
            "synthetic-bearer-not-for-persistence", "synthetic-refresh-not-for-persistence")
        secrets.forEach { assertFalse(contents.contains(it)) }
        return files
    }

    private fun persist(f: IndependentPagesExportFixture, order: ByteArray, copy: File, material: File) {
        val root = File(checkNotNull(System.getenv("PDA_INDEPENDENT_PAGES_VECTOR_DIR")))
        assertTrue(root.mkdirs() || root.isDirectory); assertTrue(root.listFiles().orEmpty().isEmpty())
        val original = f.work.original
        val trust = JSONObject().put(original.deviceId, JSONObject().put("publicKeyDerBase64", DeviceKeys().publicKey(false))
            .put("operatorIds", JSONArray().put(original.operatorId)).put("warehouseIds", JSONArray().put(original.warehouseId)))
        mapOf("independent.pdarescue" to copy.readBytes(), "package-secret.json" to material.readBytes(),
            "external-trust.json" to trust.toString().toByteArray(Charsets.UTF_8), "order-original.json" to order).forEach { (name, bytes) ->
            Files.write(root.toPath().resolve(name), bytes, CREATE_NEW)
        }
    }

    private fun databaseDigest(f: IndependentPagesExportFixture): String = runBlocking(Dispatchers.IO) {
        val db = f.base.app.database.openHelper.readableDatabase
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val content = JSONArray()
        tables.forEach { table ->
            content.put(JSONObject().put("table", table).put("rows", JSONArray(tableRows(db, table))))
        }
        RescueBundle.sha(content.toString().toByteArray(Charsets.UTF_8)).also {
            println("independent-pages Room tables=${tables.size} typed-content-sha256=$it")
        }
    }

    private fun tableRows(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<String> =
        db.query("SELECT * FROM `$table`").use { cursor ->
            buildList { while (cursor.moveToNext()) add(canonicalRow(cursor)) }.sorted()
        }

    private fun canonicalRow(cursor: android.database.Cursor): String = JSONArray().apply {
        repeat(cursor.columnCount) { column ->
            val type = cursor.getType(column)
            val value = when (type) {
                android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(column)
                android.database.Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(cursor.getBlob(column))
                else -> error("unsupported SQLite value type")
            }
            put(JSONArray().put(type).put(value))
        }
    }.toString()
}

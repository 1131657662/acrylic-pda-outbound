package com.acrylic.outbound.pda.ui

import android.widget.EditText
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.rescue.RescueEnvelope
import com.acrylic.outbound.pda.security.DeviceKeys
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
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

/** Same signed TEMP input through product pages; synthetic authority/JCA/file URI only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryPagesExportTest {
    @Test fun nonemptyTemporaryOriginalExportsThroughOwnCardPagesAfterOfflineLogout() {
        TemporaryPagesExportFixture().use { f ->
            f.base.populate(); val ownCard = f.ownCard.readBytes()
            val expected = expected(f)
            f.base.stopServer(); f.logout()
            val before = TemporaryPagesRoomDigest.read(f)
            f.openExport(); f.chooseOwnCard()
            assertTrue(f.model.state.value.message, f.model.state.value.unlocked)
            assertNull(f.base.vault.read()); f.readSnapshot()
            val material = f.generate(); val originalMaterial = material.readBytes()
            f.copy(null)
            assertTrue(f.model.state.value.canCopy); assertTrue(f.model.state.value.message.contains("取消"))
            val copy = File(f.activity.cacheDir, "synthetic-temporary-copy-" + java.util.UUID.randomUUID() + ".pdarescue")
            f.copy(copy)
            assertTrue(f.model.state.value.copyState.contains("目标写入与回读一致"))
            assertTrue(f.model.state.value.copyState.contains("电脑副本尚未核验"))
            assertArrayEquals(f.packages.single().readBytes(), copy.readBytes())
            val files = unpack(copy, material); verify(f, files, expected)
            assertPackageHasNoSecret(f, files, material)
            persist(f, copy, material, expected)
            assertEquals(before, TemporaryPagesRoomDigest.read(f))
            assertArrayEquals(ownCard, f.ownCard.readBytes()); assertArrayEquals(originalMaterial, material.readBytes())
            assertNull(f.base.vault.read()); assertEquals(1, f.base.server.requestCount)
            assertEquals("", f.activity.findViewById<EditText>(R.id.credential).text.toString())
        }
    }

    private fun assertPackageHasNoSecret(f: TemporaryPagesExportFixture, files: Map<String, ByteArray>, material: File) {
        val content = files.values.joinToString { it.toString(Charsets.ISO_8859_1) }
        val secret = JSONObject(material.readText()).getString("packagePassword")
        listOf(secret, "synthetic-temporary-pages-bearer", "synthetic-temporary-pages-refresh",
            JSONObject(f.ownCard.readText()).getString("credential"), f.base.work.fixture.key.toString())
            .forEach { assertFalse(content.contains(it)) }
    }

    private fun expected(f: TemporaryPagesExportFixture): JSONObject = runBlocking(Dispatchers.IO) {
        val work = f.base.work; val id = work.id
        val captures = work.db.temporaryScans().captures(id)
        val scans = work.db.temporaryScans().page(id, 0)
        JSONObject().put("clientOrderId", id).put("orderId", id)
            .put("captures", JSONArray(captures.map { JSONObject().put("id", it.id)
                .put("orderScanSeq", it.orderScanSeq.toString()).put("state", it.state).put("rawInput", it.rawInput) }))
            .put("scans", JSONArray(scans.map { JSONObject().put("captureId", it.captureId)
                .put("orderScanSeq", it.orderScanSeq.toString()).put("barcode", it.barcode)
                .put("status", it.status).put("weightGrams", it.weightGrams.toString()) }))
            .put("eventIds", JSONArray(work.db.temporaryScans().events(id).map { it.id }))
            .put("activeCount", work.db.temporaryScans().total(id).count.toString())
            .put("weightGrams", work.db.temporaryScans().total(id).grams.toString())
    }

    private fun unpack(copy: File, material: File): Map<String, ByteArray> {
        val secret = JSONObject(material.readText()).getString("packagePassword").toCharArray()
        val plain = try { RescueEnvelope.decrypt(copy.readBytes(), secret) } finally { secret.fill('\u0000') }
        return ZipInputStream(plain.inputStream()).use { zip ->
            buildMap { while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) } }
        }
    }

    private fun rows(files: Map<String, ByteArray>, name: String) = files.getValue(name).toString(Charsets.UTF_8)
        .lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()

    private fun verify(f: TemporaryPagesExportFixture, files: Map<String, ByteArray>, expected: JSONObject) {
        val order = JSONObject(files.getValue("order.json").toString(Charsets.UTF_8))
        assertEquals("TEMP", order.getString("source")); assertEquals(f.base.work.id, order.getString("localOrderId"))
        assertEquals(expected.getString("orderId"), order.getString("orderId"))
        compare(rows(files, "captures.ndjson"), expected.getJSONArray("captures"))
        compare(rows(files, "scans.ndjson"), expected.getJSONArray("scans"))
        assertEquals(expected.getJSONArray("eventIds").toString(), JSONArray(rows(files, "events.ndjson").map { it.getString("id") }).toString())
        val summary = JSONObject(files.getValue("manifest.json").toString(Charsets.UTF_8)).getJSONObject("summary")
        assertEquals("1", summary.getString("active")); assertEquals("1", summary.getString("void"))
        assertEquals("1", summary.getString("failed")); assertEquals(expected.getString("weightGrams"), summary.getString("weightGrams"))
        assertFalse(files.containsKey("submission.json")); assertFalse(files.containsKey("receipt.json"))
        val content = files.values.joinToString { it.toString(Charsets.ISO_8859_1) }
        listOf("synthetic-temporary-pages-bearer", "synthetic-temporary-pages-refresh",
            JSONObject(f.ownCard.readText()).getString("credential"))
            .forEach { assertFalse(content.contains(it)) }
    }

    private fun compare(actual: List<JSONObject>, expected: JSONArray) {
        assertEquals(expected.length(), actual.size)
        actual.forEachIndexed { index, row ->
            val original = expected.getJSONObject(index)
            original.keys().forEach { field -> assertEquals(field, original.getString(field), row.getString(field)) }
        }
    }

    private fun persist(f: TemporaryPagesExportFixture, copy: File, material: File, expected: JSONObject) {
        val root = File(checkNotNull(System.getenv("PDA_TEMP_PAGES_VECTOR_DIR")))
        assertTrue(root.mkdirs() || root.isDirectory); assertTrue(root.listFiles().orEmpty().isEmpty())
        val actor = checkNotNull(f.base.work.fixture.auth)
        val trust = JSONObject().put(actor.getString("deviceId"), JSONObject().put("publicKeyDerBase64", DeviceKeys().publicKey(false))
            .put("operatorIds", JSONArray().put(actor.getString("userId"))).put("warehouseIds", JSONArray().put(actor.getString("warehouseId"))))
        mapOf("temporary.pdarescue" to copy.readBytes(), "package-secret.json" to material.readBytes(),
            "external-trust.json" to trust.toString().toByteArray(Charsets.UTF_8),
            "expected-original.json" to expected.toString().toByteArray(Charsets.UTF_8)).forEach { (name, bytes) ->
            Files.write(root.toPath().resolve(name), bytes, CREATE_NEW)
        }
    }
}

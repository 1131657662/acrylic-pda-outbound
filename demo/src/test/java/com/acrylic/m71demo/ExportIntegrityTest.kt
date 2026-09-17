package com.acrylic.m71demo

import com.acrylic.m71demo.device.Telemetry
import com.acrylic.m71demo.export.OrderExporter
import com.acrylic.m71demo.fixtures.HistoryFixture
import com.acrylic.m71demo.domain.DemoSigner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ExportIntegrityTest {
    @Test fun frozenEvidenceIsImmutableAndExportPreservesCaptureOrder() = TestRig().use { rig ->
        val expected = listOf("003A0000001", "571A0000001", "003A0000002")
        expected.forEach { rig.save(it); rig.clock.wall -= 5_000 }
        val exporter = object : OrderExporter(rig.store, Telemetry(rig.context)) {
            override fun syncDirectory(directory: File) { check(directory.isDirectory) }
        }
        val frozen = File(exporter.freeze("order"))
        val before = File(frozen, "manifest.json").readText()
        rig.correction.note("order", "After freeze note")
        assertNotEquals(frozen.path, exporter.export("order"))
        assertEquals(frozen.path, exporter.freeze("order"))
        assertEquals(before, File(frozen, "manifest.json").readText())
        val rows = File(frozen, "scans.ndjson").readLines().map { org.json.JSONObject(it) }
        assertEquals(expected, rows.map { it.getString("barcode") })
        assertTrue(rows.zipWithNext().all { (a, b) -> a.getLong("receivedAt") > b.getLong("receivedAt") })
        val target = File(requireNotNull(System.getProperty("demo.evidenceDir"))).apply { mkdirs() }
        frozen.copyRecursively(target, overwrite = true)
        File(target, "expected.ndjson").writeText(expected.mapIndexed { i, code ->
            org.json.JSONObject().put("orderScanSeq", i + 1).put("barcode", code)
                .put("weightGrams", "300500").put("deliveryCode", code.take(3)).toString()
        }.joinToString("\n"))
    }

    @Test fun directorySyncFailureCannotRegisterFrozenSubmission() = TestRig().use { rig ->
        rig.save("003A0000001")
        val exporter = object : OrderExporter(rig.store, Telemetry(rig.context)) {
            override fun syncDirectory(directory: File) {
                if (File(directory, "manifest.json").exists()) throw java.io.IOException("directory sync failed")
            }
        }
        assertThrows(java.io.IOException::class.java) { exporter.freeze("order") }
        assertNull(rig.db.orders().submission("order"))
        assertEquals("SCANNING", rig.db.orders().get("order")!!.state)
        assertEquals(1, rig.db.orders().get("order")!!.actualCount)
        assertNotNull(rig.db.scans().guard("003A0000001"))
    }

    @Test fun corruptedDetailCannotBeReusedAsValidFrozenEvidence() = TestRig().use { rig ->
        rig.save("003A0000001")
        val exporter = object : OrderExporter(rig.store, Telemetry(rig.context)) {
            override fun syncDirectory(directory: File) { check(directory.isDirectory) }
        }
        val frozen = File(exporter.freeze("order"))
        val registered = rig.db.orders().submission("order")
        File(frozen, "scans.ndjson").appendText("corrupted")
        assertThrows(IllegalStateException::class.java) { exporter.freeze("order") }
        assertEquals(registered, rig.db.orders().submission("order"))
        assertTrue(File(frozen, "scans.ndjson").readText().endsWith("corrupted"))
        assertEquals(1, rig.db.orders().get("order")!!.actualCount)
    }

    @Test fun historyFixtureHasThreeAuditsPerSavedCaptureAndValidForeignKeys() = TestRig().use { rig ->
        val fixture = HistoryFixture(rig.store, object : DemoSigner { override fun sign(claim: String) = "TEST_ONLY" })
        fixture.seed(400) { }
        val db = rig.db.openHelper.writableDatabase
        fun count(table: String) = db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(400, count("local_scan"))
        assertEquals(400, count("local_input_capture"))
        assertEquals(1200, count("local_operation_event"))
        assertEquals(400, count("local_barcode_guard"))
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}

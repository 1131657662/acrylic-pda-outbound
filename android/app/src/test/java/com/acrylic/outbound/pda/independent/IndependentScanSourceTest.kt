package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.work.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentScanSourceTest {
    @get:Rule val root = TemporaryFolder()
    @Test fun formalAndIndependentEffectiveGuardsRejectEachOtherInOriginalTransactions() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanSourceFixture(root.root).use { f ->
            val saved = f.independent.save()
            val received = f.formalInput(); f.processor.drain(f.assignment)
            assertEquals("INVALID", f.db.captures().get(received.id)?.state)
            assertNull(f.db.scans().get(received.id))
            f.processor.corrections.acknowledge(received.id)
            f.independent.corrections.voidSelected(f.independent.command.id, saved.capture.id, "已核实未装车")
            val formal = f.formalInput(); f.processor.drain(f.assignment)
            val result = f.db.captures().get(formal.id)!!
            assertEquals("COMMITTED", result.state)
            val blocked = f.independent.reception.receive(f.independent.input()); f.independent.reception.validate(blocked.capture.id)
            assertEquals("INVALID", f.independent.store.dao.failure(f.independent.command.id)?.processing?.state)
            assertEquals(formal.id, f.db.scans().guard(f.barcode)?.captureId)
        }
    }
    @Test fun formalAndIndependentPendingInputsBlockOtherSourceDurably() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanSourceFixture(root.root).use { f ->
            val independent = f.independent.reception.receive(f.independent.input(source = "MANUAL"))
            assertThrows(IllegalStateException::class.java) { f.formalInput() }
            f.independent.corrections.discard(independent.capture.id, "未装车，放弃手输")
            val formal = f.formalInput(source = "MANUAL")
            assertThrows(IllegalStateException::class.java) { f.independent.reception.receive(f.independent.input()) }
            f.processor.corrections.discard(formal.id, "未装车，放弃手输")
            f.independent.save(); assertEquals(1L, f.independent.store.dao.total(f.independent.command.id).count)
        }
    }
    @Test fun temporaryAndIndependentGuardsAndPendingInputsRespectBothDirections() = runBlocking<Unit>(Dispatchers.IO) {
        val contract = org.json.JSONObject(java.io.File(checkNotNull(System.getProperty("pda.temporaryFixture"))).readText())
        val bytes = com.acrylic.outbound.pda.preparation.ContractFixture.unpack(root.root, contract).getValue("inventory")
        val barcode = org.json.JSONArray(String(bytes, Charsets.UTF_8)).getJSONObject(0).getString("barcode")
        IndependentScanFixture(barcode).use { f ->
            TemporaryWorkFixture(root.root, f.task.sku.db).use { t ->
                t.start(); f.start()
                val saved = f.save(); val old = t.scan(saved.capture.barcode)
                assertEquals("INVALID", t.db.temporaryScans().capture(old.id)?.state)
                t.processor.corrections.acknowledge(old.id)
                f.corrections.voidSelected(f.command.id, saved.capture.id, "已核实卸下")
                val formal = t.scan(saved.capture.barcode)
                assertEquals("COMMITTED", t.db.temporaryScans().capture(formal.id)?.state)
                val blocked = f.reception.receive(f.input()); f.reception.validate(blocked.capture.id)
                assertEquals("INVALID", f.store.dao.failure(f.command.id)?.processing?.state)
                f.corrections.acknowledge(blocked.capture.id)
                t.processor.corrections.voidSelected(t.id, formal.id, "已核实卸下")
                val pending = f.reception.receive(f.input(source = "MANUAL"))
                assertThrows(IllegalStateException::class.java) { t.receive(saved.capture.barcode) }
                f.corrections.discard(pending.capture.id, "未装车，放弃手输")
                val other = t.receive(saved.capture.barcode, "MANUAL")
                assertThrows(IllegalStateException::class.java) { f.reception.receive(f.input()) }
                t.processor.corrections.discard(other.id, "未装车，放弃手输")
                f.save(); assertEquals(1L, f.store.dao.total(f.command.id).count)
            }
        }
    }
    @Test fun databaseConstraintsProtectDirectGuardWritersAndImmutableOriginals() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); val saved = f.save(); val db = f.task.sku.db.openHelper.writableDatabase
            val failure = assertThrows(Exception::class.java) {
                db.execSQL("INSERT INTO work_guard(barcode,captureId,assignmentId,dispatchCycle) VALUES(?,?,?,1)",
                    arrayOf(saved.capture.barcode, "unknown-c", "unknown-a"))
            }
            assertTrue(failure.message.orEmpty().contains("independent source"))
            listOf("independent_capture", "independent_scan").forEach { table ->
                assertThrows(Exception::class.java) { db.execSQL("DELETE FROM $table") }
            }
            f.corrections.voidSelected(f.command.id, saved.capture.id, "已核实卸下")
            assertThrows(Exception::class.java) { db.execSQL("DELETE FROM independent_void") }
            assertEquals(1, f.store.dao.captures(f.command.id).size)
        }
    }
}

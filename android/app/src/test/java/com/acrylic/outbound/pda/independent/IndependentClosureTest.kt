package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.rescue.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentClosureTest {
    @Test fun shortfallRequiresReasonAndClosureKeepsOriginalEffectiveGuards() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); val captured = f.scan.save()
            val closing = IndependentTaskClosing(f.scan.store) { f.signer }
            val before = f.digest()
            assertThrows(IllegalStateException::class.java) { closing.close(f.target.localOrderId, "", true) }
            assertEquals(before, f.digest())
            val body = closing.close(f.target.localOrderId, "实采短装，留待主管核对", true)
            assertEquals("1", body.getString("active")); assertTrue(body.getLong("shortfall") > 0)
            assertEquals("SEALED", f.read().order.getString("state"))
            assertNotNull(f.scan.store.dao.guard(captured.capture.barcode))
            assertEquals(body.toString(), IndependentClosure.verify(f.read())!!.toString())
        }
    }
    @Test fun closureRetryAcrossDatabaseReopenReadsSameOriginalSignature() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); f.scan.save()
            val closing = IndependentTaskClosing(f.scan.store) { f.signer }
            val body = closing.close(f.target.localOrderId, "短装保全", true)
            val original = f.db.independentTasks().events(f.target.localOrderId).last()
            f.scan.reopen()
            assertEquals(body.toString(), IndependentTaskClosing(f.scan.store) { error("must not resign") }.close(f.target.localOrderId, "", true).toString())
            assertEquals(original, f.db.independentTasks().events(f.target.localOrderId).last())
            assertEquals(body.toString(), IndependentClosure.verify(f.read())!!.toString())
        }
    }
    @Test fun unresolvedInputCannotBecomeACompleteBoundary() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); f.scan.reception.receive(f.scan.input(source = "MANUAL"))
            val before = f.digest()
            assertThrows(IllegalStateException::class.java) { IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true) }
            assertEquals(before, f.digest())
        }
    }
    @Test fun failedInputRequiresOriginalAcknowledgementBeforeClosure() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); val input = f.scan.reception.receive(f.scan.input("bad")); f.scan.reception.validate(input.capture.id)
            val closing = IndependentTaskClosing(f.scan.store) { f.signer }
            assertThrows(IllegalStateException::class.java) { closing.close(f.target.localOrderId, "短装", true) }
            f.scan.corrections.acknowledge(input.capture.id)
            assertNotNull(closing.close(f.target.localOrderId, "零实采短装", true))
        }
    }
    @Test fun expiredAuthorizationCannotCreateNewClosure() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); f.scan.task.identity.advance(48 * 3600 * 1000L)
            assertThrows(IllegalStateException::class.java) { IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true) }
            assertEquals("SCANNING", f.db.independentTasks().state(f.target.localOrderId)!!.state)
        }
    }
    @Test fun tailStateFailureRollsBackTheNewSignatureAndAudit() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); f.scan.save(); val before = f.digest(); val sqlite = f.db.openHelper.writableDatabase
            sqlite.execSQL("""CREATE TRIGGER closure_tail BEFORE UPDATE ON independent_task_state
                WHEN NEW.state='SEALED' BEGIN SELECT RAISE(ABORT,'closure tail'); END""")
            val closing = IndependentTaskClosing(f.scan.store) { f.signer }
            assertThrows(Exception::class.java) { closing.close(f.target.localOrderId, "短装", true) }
            assertEquals(before, f.digest()); sqlite.execSQL("DROP TRIGGER closure_tail")
            assertNotNull(closing.close(f.target.localOrderId, "短装", true))
            assertEquals(1, f.db.independentTasks().events(f.target.localOrderId).count { it.type == IndependentClosure.EVENT })
        }
    }
    @Test fun sealedFactsAndBarcodeGuardsRefuseSubsequentWrites() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); val captured = f.scan.save(); IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true)
            val before = f.digest(); val id = f.target.localOrderId
            assertThrows(IllegalStateException::class.java) { f.scan.reception.receive(f.scan.input()) }
            assertThrows(IllegalStateException::class.java) { f.scan.corrections.voidSelected(id, captured.capture.id, "不能封存后撤销") }
            assertThrows(Exception::class.java) { f.scan.store.dao.release(captured.capture.id) }
            assertThrows(Exception::class.java) { f.scan.store.dao.updateState(captured.processing.copy(state = "DISCARDED")) }
            assertThrows(Exception::class.java) { f.db.independentTasks().updateState(f.db.independentTasks().state(id)!!.copy(state = "SCANNING")) }
            assertThrows(Exception::class.java) { f.db.independentTasks().insertEvent(f.db.independentTasks().events(id).last().copy(id = java.util.UUID.randomUUID().toString(), sequence = 999)) }
            assertEquals(before, f.digest())
        }
    }
    @Test fun signedClosedOriginalIsAvailableToIndependentConsumers() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); val first = f.scan.save(); f.scan.corrections.voidSelected(f.target.localOrderId, first.capture.id, "已卸下")
            f.scan.save(); IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装，原实物待核", true)
            val snapshot = f.read(); val body = checkNotNull(IndependentClosure.verify(snapshot))
            assertEquals("1", body.getString("void")); assertEquals("2", body.getString("inputHighWater"))
            val directory = System.getenv("INDEPENDENT_CLOSURE_VECTOR_DIR")?.let { File(it).apply { mkdirs() } } ?: return@use
            directory.resolve("closed.zip").writeBytes(RescueBundle.build(snapshot, f.signer))
            directory.resolve("trust.json").writeText(JSONObject().put(f.scan.task.actor.deviceId, JSONObject()
                .put("publicKeyDerBase64", Base64.getEncoder().encodeToString(f.signer.publicKey))
                .put("operatorIds", listOf(f.scan.task.actor.operatorId)).put("warehouseIds", listOf(f.scan.task.actor.warehouseId))).toString())
        }
    }
    @Test fun sealedStateCannotBeReplacedOrReceiveAnotherPlan() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "零实采短装", true)
            val before = f.digest(); val sqlite = f.db.openHelper.writableDatabase
            assertThrows(Exception::class.java) { sqlite.execSQL("""INSERT OR REPLACE INTO independent_task_state
                SELECT taskId,'SCANNING',nextScanSeq,nextEventSeq,actualCount,weightGrams FROM independent_task_state""") }
            assertThrows(Exception::class.java) { f.db.independentTasks().insertLines(listOf(f.scan.created.lines.first().copy(deliveryCode = "004"))) }
            assertEquals(before, f.digest())
        }
    }
    @Test fun foreignActorCannotCloseOriginalCollection() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); val before = f.digest(); f.scan.task.actor = f.scan.task.actor.copy(operatorId = java.util.UUID.randomUUID().toString())
            assertThrows(IllegalStateException::class.java) { IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true) }
            assertEquals(before, f.digest())
        }
    }
    @Test fun rebootCannotGenerateANewTrustedEnding() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); val before = f.digest(); f.scan.task.identity.sample = f.scan.task.identity.sample.copy(boot = 999)
            assertThrows(IllegalStateException::class.java) { IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true) }
            assertEquals(before, f.digest())
        }
    }
    @Test fun sealedTaskAndOriginalRequestCannotBeReplaced() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true)
            val before = f.digest(); val sqlite = f.db.openHelper.writableDatabase
            for (table in listOf("independent_task", "independent_task_request")) {
                assertThrows(Exception::class.java) { sqlite.execSQL("INSERT OR REPLACE INTO $table SELECT * FROM $table") }
            }
            assertEquals(before, f.digest())
        }
    }
    @Test fun foreignProcessingRowCannotReplaceASealedInputState() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.scan.start(); val saved = f.scan.save()
            val other = f.scan.task.repository.create(f.scan.task.command(), f.scan.task.grant())
            val foreign = saved.capture.copy(id = java.util.UUID.randomUUID().toString(), taskId = other.task.id, deviceSequence = 999)
            f.scan.store.dao.insertCapture(foreign)
            f.scan.store.dao.insertState(IndependentCaptureState(foreign.id, state = "DISCARDED"))
            IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true)
            val before = f.digest(); val sqlite = f.db.openHelper.writableDatabase
            assertThrows(Exception::class.java) { sqlite.execSQL("UPDATE OR REPLACE independent_capture_state SET captureId=? WHERE captureId=?",
                arrayOf(saved.capture.id, foreign.id)) }
            assertEquals(before, f.digest()); assertEquals("COMMITTED", f.scan.store.dao.capture(saved.capture.id)!!.processing.state)
        }
    }
}

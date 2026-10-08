package com.acrylic.outbound.pda.temporary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHandoverHttpTest {
    private lateinit var context: Context
    internal lateinit var db: PdaDatabase
    internal lateinit var output: File
    internal lateinit var connection: TemporaryHandoverHttpConnection
    internal lateinit var caseId: String

    @Test fun liveRecipientPreparationAndDurableOriginalStage() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("TEMP_HANDOVER_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires isolated actual source and target server", !path.isNullOrBlank())
        val phase = checkNotNull(System.getenv("TEMP_HANDOVER_ANDROID_PHASE"))
        check(phase in setOf("prepare", "stage", "confirm", "activate", "freeze", "upload", "work", "success", "evidence", "stop", "continuation", "continuationSuccess"))
        initialize(File(checkNotNull(path)).canonicalFile, phase)
        try { runPhase(phase) }
        finally { preserve("$phase-final") }
    }

    private fun runPhase(phase: String) {
        val work = TemporaryHandoverHttpWork(this)
        when (phase) {
            "prepare" -> prepare(); "stage" -> stage(); "confirm" -> confirm(); "activate" -> activate()
            "freeze" -> work.freeze(); "upload" -> work.upload()
            "evidence" -> TemporaryHandoverHttpStop(this).evidence(); "stop" -> TemporaryHandoverHttpStop(this).stop()
            "continuation" -> TemporaryHandoverHttpContinuation(this).activateAndSubmit()
            "continuationSuccess" -> TemporaryHandoverHttpContinuation(this).success()
            "work" -> { work.freeze(); work.upload() }; else -> work.success()
        }
    }

    private fun initialize(file: File, phase: String) {
        check(file.path.replace('\\', '/').matches(Regex(".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,5}/private/temporary-handover-input\\.json")))
        val input = JSONObject(file.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_TEMP_HANDOVER_HTTP")
        caseId = input.getString("caseId")
        val evidence = File(checkNotNull(checkNotNull(file.parentFile).parentFile), "temporary-handover-android")
        output = File(evidence, phase).also { check(it.mkdir()) }
        context = ApplicationProvider.getApplicationContext()
        if (phase != "prepare") {
            val target = context.getDatabasePath("outbound-pda.db"); target.parentFile!!.mkdirs()
            val previous = previousPhase(phase, evidence)
            check(!target.exists()); File(evidence, "$previous-final.db").copyTo(target)
        }
        db = PdaDatabase.open(context)
        connection = TemporaryHandoverHttpConnection(input, output)
    }

    private fun previousPhase(phase: String, evidence: File) = when (phase) {
        "stage" -> "prepare"; "confirm" -> "stage"; "activate" -> "confirm"
        "work", "freeze" -> "activate"; "upload" -> "freeze"
        "evidence" -> "upload"; "stop" -> "evidence"
        "continuation" -> "stop"; "continuationSuccess" -> "continuation"
        else -> if (File(evidence, "upload-final.db").exists()) "upload" else "work"
    }

    private fun prepare() {
        val repository = TemporaryRepository(db, connection, File(output, "download"))
        assertThrows(IOException::class.java) { repository.prepare {} }
        val request = checkNotNull(repository.local())
        assertNull(db.temporary().prepared(request.key)!!.ackJson)
        reopen("prepare-lost-ack")
        val prepared = TemporaryRepository(db, connection, File(output, "download-retry")).prepare {}
        assertNotNull(prepared.ackJson)
        val result = JSONObject().put("authorizationId", prepared.authorizationId).put("manifestSha256", prepared.manifestSha256)
        File(output.parentFile, "prepare-result.json").writeText(result.toString())
    }

    private fun stage() {
        assertThrows(IOException::class.java) { repository().stage(caseId) }
        assertNull(db.temporaryHandoverStages().get(caseId))
        reopen("stage-lost-response")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER temp_stage_last_write AFTER INSERT ON temporary_handover_stage BEGIN SELECT RAISE(ABORT,'D06_HTTP_STAGE_LAST_WRITE'); END")
        assertThrows(Exception::class.java) { repository().stage(caseId) }
        assertNull(db.temporaryHandoverStages().get(caseId))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER temp_stage_last_write")
        reopen("stage-last-write-rollback")
        val row = repository().stage(caseId)
        reopen("stage-saved")
        assertArrayEquals(row.payloadBytes, repository().read(caseId).payloadBytes)
        assertArrayEquals(row.payloadBytes, repository().stage(caseId).payloadBytes)
        val identity = connection.session.toString()
        connection.session.put("userId", java.util.UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { repository().read(caseId) }
        assertNotNull(db.temporaryHandoverStages().header(caseId))
        connection.session = JSONObject(identity)
        File(output.parentFile, "android-original.json").writeBytes(repository().read(caseId).payloadBytes)
        File(output.parentFile, "stage-result.json").writeText(JSONObject().put("caseId", caseId).put("state", row.state)
            .put("payloadSha256", row.payloadSha256).put("inputHighWatermark", row.inputHighWatermark.toString()).toString())
    }

    private fun repository() = TemporaryHandoverRepository(db, connection)
    private fun confirmation() = TemporaryHandoverConfirmationRepository(db, connection, true)

    private fun activation(): TemporaryHandoverActivationRepository {
        val store = store()
        return TemporaryHandoverActivationRepository(store, true)
    }

    private fun activate() {
        val captureCounter = if (connection.exerciseWork) 1L else 3L
        val eventCounter = if (connection.exerciseWork) 1L else 5L
        db.work().insertDevice(com.acrylic.outbound.pda.work.WorkDevice(connection.session.getString("deviceId"), captureCounter, eventCounter))
        assertThrows(IOException::class.java) { activation().restore(caseId) }
        assertNull(db.temporaryHandoverActivations().get(caseId)); reopen("activate-lost-response")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER temp_activate_last_write AFTER INSERT ON temporary_event WHEN NEW.type='TEMP_HANDOVER_INSTALLED' BEGIN SELECT RAISE(ABORT,'D06_HTTP_ACTIVATE_LAST_WRITE'); END")
        assertThrows(Exception::class.java) { activation().restore(caseId) }
        assertNull(db.temporaryHandoverActivations().get(caseId))
        assertTrue(db.temporaryHandoverGuards().forCase(caseId).isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER temp_activate_last_write")
        reopen("activate-last-write-rollback")
        val draft = activation().restore(caseId); val receipt = db.temporaryHandoverActivations().get(caseId)!!
        assertEquals("HANDOVER_BLOCKED", draft.state); assertFalse(draft.canScan(true))
        assertNull(db.temporaryContinuations().forTarget(draft.clientOrderId))
        assertEquals(captureCounter, db.work().device(draft.creatorDeviceId)!!.nextCaptureSeq)
        assertEquals(eventCounter + 1, db.work().device(draft.creatorDeviceId)!!.nextEventSeq)
        reopen("activate-saved"); assertEquals(draft, activation().restore(caseId))
        assertEquals(eventCounter + 1, db.work().device(draft.creatorDeviceId)!!.nextEventSeq)
        File(output.parentFile, "android-activation.json").writeBytes(receipt.receiptBytes)
        File(output.parentFile, "activate-result.json").writeText(JSONObject().put("caseId", caseId).put("state", draft.state)
            .put("clientOrderId", draft.clientOrderId).put("authorizationId", draft.authorizationId)
            .put("workRound", draft.workRound.toString()).put("nextScanSeq", draft.nextScanSeq.toString())
            .put("retainedCount", db.temporaryHandoverGuards().forCase(caseId).size).put("receiptSha256", receipt.receiptSha256).toString())
    }

    internal fun store() = TemporaryWorkStore(db, connection, false, true) { com.acrylic.outbound.pda.security.TimeSample(1000, 500, 1) }

    private fun confirm() {
        val intent = confirmation().queue(caseId, true)
        assertThrows(IOException::class.java) { confirmation().resume(caseId) }
        assertNull(db.temporaryHandoverConfirmations().receipt(caseId))
        reopen("confirm-lost-response")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER temp_confirm_last_write AFTER UPDATE OF state ON temporary_handover_confirmation WHEN NEW.state='CONFIRMED' BEGIN SELECT RAISE(ABORT,'D06_HTTP_CONFIRM_LAST_WRITE'); END")
        assertThrows(Exception::class.java) { confirmation().resume(caseId) }
        assertNull(db.temporaryHandoverConfirmations().receipt(caseId))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER temp_confirm_last_write")
        reopen("confirm-last-write-rollback")
        assertEquals(intent.commandId, confirmation().queue(caseId, true).commandId)
        assertEquals("CONFIRMED", confirmation().resume(caseId).state)
        reopen("confirm-saved")
        assertEquals("CONFIRMED", confirmation().read(caseId)!!.state)
        val saved = checkNotNull(db.temporaryHandoverConfirmations().receipt(caseId))
        val identity = connection.session.toString()
        connection.session.put("userId", java.util.UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { confirmation().read(caseId) }
        assertArrayEquals(saved.receiptBytes, db.temporaryHandoverConfirmations().receipt(caseId)!!.receiptBytes)
        connection.session = JSONObject(identity)
        File(output.parentFile, "android-confirmation.json").writeBytes(saved.receiptBytes)
        File(output.parentFile, "android-confirmation-command.json").writeText(intent.boundCommandJson)
        File(output.parentFile, "confirm-result.json").writeText(JSONObject().put("caseId", caseId).put("state", "CONFIRMED")
            .put("receiptSha256", saved.receiptSha256).put("commandId", intent.commandId).toString())
    }
    internal fun reopen(label: String) { preserve(label); db = PdaDatabase.open(context) }
    private fun preserve(label: String) {
        db.close()
        for (suffix in listOf("", "-wal", "-shm")) {
            val file = File(context.getDatabasePath("outbound-pda.db").path + suffix)
            if (file.exists()) file.copyTo(File(output.parentFile, "$label.db$suffix"))
        }
    }
}

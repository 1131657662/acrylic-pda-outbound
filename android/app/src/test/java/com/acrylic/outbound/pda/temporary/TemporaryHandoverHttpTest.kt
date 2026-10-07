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
    private lateinit var db: PdaDatabase
    private lateinit var output: File
    private lateinit var connection: TemporaryHandoverHttpConnection
    private lateinit var caseId: String

    @Test fun liveRecipientPreparationAndDurableOriginalStage() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("TEMP_HANDOVER_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires isolated actual source and target server", !path.isNullOrBlank())
        val phase = checkNotNull(System.getenv("TEMP_HANDOVER_ANDROID_PHASE"))
        check(phase in setOf("prepare", "stage", "confirm"))
        initialize(File(checkNotNull(path)).canonicalFile, phase)
        try { when (phase) { "prepare" -> prepare(); "stage" -> stage(); else -> confirm() } }
        finally { preserve("$phase-final") }
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
            check(!target.exists()); File(evidence, if (phase == "stage") "prepare-final.db" else "stage-final.db").copyTo(target)
        }
        db = PdaDatabase.open(context)
        connection = TemporaryHandoverHttpConnection(input, output)
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
    private fun reopen(label: String) { preserve(label); db = PdaDatabase.open(context) }
    private fun preserve(label: String) {
        db.close()
        for (suffix in listOf("", "-wal", "-shm")) {
            val file = File(context.getDatabasePath("outbound-pda.db").path + suffix)
            if (file.exists()) file.copyTo(File(output.parentFile, "$label.db$suffix"))
        }
    }
}

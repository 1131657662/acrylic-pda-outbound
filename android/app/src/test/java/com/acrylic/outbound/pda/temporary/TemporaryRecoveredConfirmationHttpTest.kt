package com.acrylic.outbound.pda.temporary

import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.LocalRecoveryControl
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.JwtVerifier
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRecoveredConfirmationHttpTest {
    @Test fun actualProductConfirmationPersistsAcrossFreshAndroidJvm() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("RECOVERED_HANDOVER_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires fresh isolated original-source recipient and real product POST", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex("D:/acrylic-pda-outbound/\\.local/e2a-cross-20260925-[1-9][0-9]{0,5}/private/android-http-input\\.json")))
        val input = JSONObject(file.readText()); val phase = checkNotNull(System.getenv("RECOVERED_HANDOVER_ANDROID_PHASE"))
        check(phase in setOf("confirm", "restart")); val original = File(input.getString("originalDirectory")).canonicalFile
        check(original == file.parentFile!!.parentFile)
        val evidence = File(original, "recovered-confirmation-android").also { it.mkdirs() }
        val output = File(evidence, phase).also { check(it.mkdir()) }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = context.getDatabasePath("outbound-pda.db"); check(!target.exists()); target.parentFile!!.mkdirs()
        if (phase == "restart") File(evidence, "confirm-final.db").copyTo(target)
        val db = PdaDatabase.open(context)
        try { exercise(db, input, phase, original, output) }
        finally { db.close(); target.copyTo(File(evidence, "$phase-final.db")); File(output, "database-sha256.txt").writeText(JwtVerifier.sha(target.readBytes())) }
    }
    private fun exercise(db: PdaDatabase, input: JSONObject, phase: String, original: File, output: File) {
        val session = input.getJSONObject("session"); val id = input.getString("caseId")
        val connection = TemporaryRecoveredConfirmationHttpConnection(input, output)
        val repository = TemporaryRecoveredConfirmationRepository(db, connection, true)
        if (phase == "confirm") {
            db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = session.getString("serverRecoveryEpoch"), mode = "RECONCILING"))
            TemporaryRecoveredHandoverRepository(db, connection, true).stage(id)
            confirm(db, repository, connection, id)
        }
        val row = checkNotNull(repository.read(id)); assertEquals("CONFIRMED", row.state)
        val receipt = checkNotNull(db.temporaryRecoveredConfirmations().receipt(id))
        val response = connection.original(TemporaryConnection.Original("pda/handovers/$id/recovered-confirmation", session,
            body = row.commandJson.toByteArray(Charsets.UTF_8), maximumBytes = 8 * 1024 * 1024))
        assertArrayEquals(receipt.receiptBytes, response); assertEquals(row.commandId, repository.queue(id, true).commandId)
        assertEquals("CONFIRMED", repository.resume(id).state)
        assertNull(db.temporaryHandoverConfirmations().get(id)); assertNull(db.temporaryHandoverActivations().get(id))
        assertEquals("RECONCILING", db.sync().recovery()!!.mode)
        File(output, "result.json").writeText(JSONObject().put("phase", phase).put("caseId", id).put("sessionId", session.getString("sessionId"))
            .put("actualProductPost", true).put("nativeRoom", true).put("commandSha256", row.commandSha256)
            .put("receiptSha256", receipt.receiptSha256).put("originalHttpReceiptBytes", true).put("mayResumeScanning", false).put("localDeletionAllowed", false).toString())
        export(db, row, original, receipt.receiptBytes)
    }
    private fun confirm(db: PdaDatabase, repository: TemporaryRecoveredConfirmationRepository,
        connection: TemporaryRecoveredConfirmationHttpConnection, id: String) {
        assertThrows(IllegalStateException::class.java) { repository.queue(id, false) }
        val row = repository.queue(id, true); connection.loseConfirmation = true
        assertThrows(java.io.IOException::class.java) { repository.resume(id) }
        assertNull(db.temporaryRecoveredConfirmations().receipt(id))
        assertEquals(row.commandJson, db.temporaryRecoveredConfirmations().get(id)!!.commandJson)
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_received_room_tail BEFORE UPDATE ON temporary_recovered_confirmation
            WHEN NEW.state='CONFIRMED' BEGIN SELECT RAISE(ABORT,'last Room write'); END""")
        try { assertThrows(Exception::class.java) { repository.resume(id) }; assertNull(db.temporaryRecoveredConfirmations().receipt(id)) }
        finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_received_room_tail") }
        assertEquals(row.commandId, repository.queue(id, true).commandId); assertEquals("CONFIRMED", repository.resume(id).state)
    }
    private fun export(db: PdaDatabase, row: TemporaryRecoveredConfirmation, original: File, receipt: ByteArray) {
        val stage = checkNotNull(db.temporaryRecoveredHandoverStages().get(row.caseId))
        val files = mapOf("confirmation-command.json" to row.commandJson.toByteArray(Charsets.UTF_8), "confirmation-receipt.json" to receipt,
            "confirmation-manifest.json" to db.temporaryRecoveredHandoverChunks().read(row.caseId, "MANIFEST", stage.manifestLength),
            "confirmation-payload.json" to db.temporaryRecoveredHandoverChunks().read(row.caseId, "PAYLOAD", stage.payloadLength))
        for ((name, bytes) in files) {
            val file = File(original, name)
            if (file.exists()) assertArrayEquals(file.readBytes(), bytes) else file.writeBytes(bytes)
        }
    }
}

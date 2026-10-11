package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRecoveredConfirmationRejectionTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun validlySignedChangedEvidenceCannotCompleteConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("purpose", "TEMP_HANDOVER_CONFIRMATION") }, { it.put("extra", true) },
            { it.getJSONObject("capabilities").put("mayResumeScanning", true) },
            { it.getJSONObject("actor").put("sessionId", UUID.randomUUID().toString()) },
            { it.getJSONObject("originalCommand").put("sourceRecoveryEpoch", "2") },
            { it.getJSONObject("qualification").put("qualificationId", UUID.randomUUID().toString()) },
            { it.put("recipientSessionSha256", "a".repeat(64)) })
        for ((index, change) in changes.withIndex()) {
            TemporaryRecoveredConfirmationFixture(File(folder.root, "bad-$index").also { it.mkdirs() }).use { f ->
                val original = f.repository.queue(f.id, true); f.transform = change
                assertThrows(Exception::class.java) { f.repository.resume(f.id) }
                assertEquals(original.commandJson, f.db.temporaryRecoveredConfirmations().get(f.id)!!.commandJson)
                assertNull(f.db.temporaryRecoveredConfirmations().receipt(f.id)); assertNull(f.db.temporaryHandoverActivations().get(f.id))
            }
        }
    }
    @Test fun ordinaryAudienceAndChangedIdentityKeepIntentBlocked() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            val original = f.repository.queue(f.id, true); f.audience = "acrylic-pda-temporary-handover-confirmation"
            assertThrows(Exception::class.java) { f.repository.resume(f.id) }; assertNull(f.db.temporaryRecoveredConfirmations().receipt(f.id))
            f.source.session.put("sessionId", UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { f.repository.queue(f.id, true) }
            assertEquals(original.commandJson, f.db.temporaryRecoveredConfirmations().get(f.id)!!.commandJson)
            assertEquals(1, f.commands.size)
        }
    }
    @Test fun completedReceiptCorruptionIsRejectedOnReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            f.repository.queue(f.id, true); f.repository.resume(f.id)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_recovered_confirmation_receipt SET receiptBytes=X'00' WHERE caseId=?", arrayOf(f.id))
            f.source.local.reopen(); assertThrows(Exception::class.java) { f.repository.read(f.id) }
            assertEquals("CONFIRMED", f.db.temporaryRecoveredConfirmations().get(f.id)!!.state)
        }
    }
}

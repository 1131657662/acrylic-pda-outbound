package com.acrylic.outbound.pda.continuation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverRealHttpTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var db: PdaDatabase
    private lateinit var connection: HandoverRealHttpConnection
    private lateinit var inputFile: File
    private lateinit var caseId: String
    private lateinit var target: String
    private lateinit var stage: LocalHandoverStage

    @Test fun liveSignedHandoverSurvivesDownloadFailureAndLostConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("HANDOVER_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires the one-shot isolated live server", !path.isNullOrBlank())
        initialize(File(checkNotNull(path)).canonicalFile)
        try {
            stage = connection.stage(caseId)
            target = JSONObject(stage.manifestJson).getJSONObject("activationReceipt")
                .getJSONObject("businessDecision").getString("assignmentId")
            downloadFailurePreservesStagedEvidence()
            lostConfirmationPreservesImportedEvidence()
            reopenReplaysOriginalAcknowledgement()
            saveResult()
        } finally { preserveDatabase() }
    }

    private fun initialize(file: File) {
        check(file.path.replace('\\', '/').matches(Regex(
            ".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,2}/private/handover-input\\.json")))
        val input = JSONObject(file.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_HANDOVER_REAL_HTTP")
        inputFile = file
        caseId = input.getString("caseId")
        context = ApplicationProvider.getApplicationContext()
        db = PdaDatabase.open(context)
        connection = HandoverRealHttpConnection(input) { db }
    }

    private fun downloadFailurePreservesStagedEvidence() {
        assertEquals("STAGED", stage.state)
        assertNull(db.work().order(target))
        interrupted { HandoverPreparationDownload(db, connection).import(caseId, folder.root) {} }
        assertNull(db.preparations().order(target))
        assertNull(db.work().order(target))
        assertEquals("STAGED", db.handoverStages().get(caseId)?.state)
        assertArrayEquals(stage.recoveryBytes, db.handoverStages().get(caseId)?.recoveryBytes)
        assertTrue(connection.ackBodies.isEmpty())
        reopen()
    }

    private fun lostConfirmationPreservesImportedEvidence() {
        interrupted { HandoverPreparationDownload(db, connection).import(caseId, folder.root) {} }
        assertEquals("IMPORTED_FROZEN", db.handoverStages().get(caseId)?.state)
        assertEquals("HANDOVER_FROZEN", db.work().order(target)?.state)
        assertNull(db.preparations().order(target)?.ackAt)
        assertEquals(1, connection.ackBodies.size)
    }

    private fun reopenReplaysOriginalAcknowledgement() {
        val downloads = connection.downloads
        reopen()
        val confirmed = HandoverDownloadAcknowledgement(db, connection).acknowledge(caseId)
        assertNotNull(confirmed.ackAt)
        assertEquals(downloads, connection.downloads)
        assertEquals(2, connection.ackBodies.size)
        assertEquals(connection.ackBodies.first(), connection.ackBodies.last())
        assertEquals(connection.ackReceipts.first(), connection.ackReceipts.last())
        assertEquals("HANDOVER_FROZEN", db.work().order(target)?.state)
        assertArrayEquals(stage.recoveryBytes, db.handoverStages().get(caseId)?.recoveryBytes)
        assertEquals("IMPORTED_FROZEN", connection.stage(caseId).state)
        HandoverDownloadAcknowledgement(db, connection).acknowledge(caseId)
        assertEquals(2, connection.ackBodies.size)
    }

    private fun interrupted(action: () -> Unit) {
        val error = runCatching(action).exceptionOrNull()
        assertTrue("Expected injected network interruption, got $error", error is IOException)
        assertTrue(checkNotNull(error).message.orEmpty().startsWith("Injected "))
    }

    private fun reopen() { db.close(); db = PdaDatabase.open(context) }

    private fun saveResult() {
        val prepared = checkNotNull(db.preparations().order(target))
        val result = JSONObject().put("caseId", caseId).put("assignmentId", target)
            .put("sourceSha256", stage.recoverySha256).put("ackAt", prepared.ackAt)
            .put("state", db.work().order(target)?.state).put("captures", db.captures().page(target, 0, 100).size)
            .put("ackRequests", connection.ackBodies.size)
        val attempt = checkNotNull(checkNotNull(inputFile.parentFile).parentFile)
        val output = File(attempt, "android-result.json")
        check(output.createNewFile()); output.writeText(result.toString())
        File(attempt, "android-ack-command.json").writeText(connection.ackBodies.first())
    }

    private fun preserveDatabase() {
        db.close()
        val original = context.getDatabasePath("outbound-pda.db")
        for (suffix in listOf("", "-wal", "-shm")) {
            val source = File(original.path + suffix)
            if (source.isFile) source.copyTo(File(inputFile.parentFile, "android-room.db$suffix"))
        }
    }
}

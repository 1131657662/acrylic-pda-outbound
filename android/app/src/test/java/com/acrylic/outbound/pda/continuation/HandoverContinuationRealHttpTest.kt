package com.acrylic.outbound.pda.continuation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.work.*
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
class HandoverContinuationRealHttpTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var db: PdaDatabase
    private lateinit var connection: HandoverRealHttpConnection
    private lateinit var attempt: File
    private lateinit var caseId: String
    private lateinit var target: String

    @Test fun liveSignedContinuationReplaysAfterReopenAndAcceptsAndroidSubmission() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("HANDOVER_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires one-shot isolated live server", !path.isNullOrBlank())
        initialize(File(checkNotNull(path)).canonicalFile)
        try {
            val stage = connection.stage(caseId)
            target = HandoverPreparationDownload(db, connection).import(caseId, folder.root) {}.assignmentId
            connection.loseContinuation = true
            assertThrows(IOException::class.java) { activate() }
            assertEquals("HANDOVER_FROZEN", db.work().state(target))
            assertNull(db.continuations().current(target))
            db.close(); db = PdaDatabase.open(context)
            assertEquals(target, activate())
            assertEquals(connection.continuationBodies.first(), connection.continuationBodies.last())
            assertEquals(connection.continuationReceipts.first(), connection.continuationReceipts.last())
            assertArrayEquals(stage.recoveryBytes, db.handoverStages().get(caseId)!!.recoveryBytes)
            val frozen = freezeWithNewDeviceInput()
            val response = connection.call(HandoverDownloadConnection.Call(
                "pda/orders/${db.preparations().order(target)!!.orderId}/submissions", connection.current(), JSONObject(frozen.payload)))
            val status = ReceiptVerifier.verify(frozen, response, connection.current().getJSONObject("signingKey"))
            assertEquals("SYNCED", status)
            saveResult(frozen, status)
        } finally { preserveDatabase() }
    }

    private fun initialize(file: File) {
        check(file.path.replace('\\', '/').matches(Regex(".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,3}/private/handover-input\\.json")))
        val input = JSONObject(file.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_HANDOVER_REAL_HTTP")
        attempt = file.parentFile!!.parentFile!!
        caseId = input.getString("caseId")
        context = ApplicationProvider.getApplicationContext()
        db = PdaDatabase.open(context)
        connection = HandoverRealHttpConnection(input) { db }
        connection.failDownload = false; connection.loseAck = false
    }

    private fun activate() = HandoverContinuationRepository(db, connection).activate(caseId)

    private fun freezeWithNewDeviceInput(): LocalSubmission {
        val access = object : WorkAccess {
            override fun identity(order: PreparedOrder) {
                check(order.operatorId == connection.current().getString("userId") && order.deviceId == connection.current().getString("deviceId"))
            }
            override fun authorize(order: PreparedOrder): java.time.Instant {
                identity(order)
                return connection.now().also { JwtVerifier.authorization(JSONObject(order.assignmentJson), connection.current(),
                    connection.current().getJSONObject("signingKey"), it) }
            }
            override fun sample() = TimeSample(2000, connection.now().toEpochMilli(), 1)
        }
        val store = WorkStore(db, access)
        val processor = WorkProcessor(store) { _, _ -> }
        val capture = processor.reception.receive(WorkInput(target, "bad", "BROADCAST", access.sample()))
        processor.drain(target); processor.corrections.acknowledge(capture.id)
        assertEquals(1L, capture.context.deviceCaptureSeq)
        return SubmissionFreeze(store, connection::now).freeze(target, "接收机核对短装")
    }

    private fun saveResult(frozen: LocalSubmission, status: String) {
        val current = checkNotNull(db.continuations().current(target))
        val result = JSONObject().put("caseId", caseId).put("assignmentId", target).put("state", db.work().state(target))
            .put("continuationId", current.id).put("continuationRequests", connection.continuationBodies.size)
            .put("submissionId", frozen.id).put("submissionStatus", status).put("submissionSha256", frozen.sha256)
        File(attempt, "android-result.json").apply { check(createNewFile()); writeText(result.toString()) }
        File(attempt, "android-continuation-command.json").writeText(connection.continuationBodies.first())
        File(attempt, "private/android-continuation-receipt.json").writeText(connection.continuationReceipts.last())
        File(attempt, "android-frozen-submission.json").writeText(frozen.payload)
    }

    private fun preserveDatabase() {
        db.close()
        val original = context.getDatabasePath("outbound-pda.db")
        for (suffix in listOf("", "-wal", "-shm")) {
            val source = File(original.path + suffix)
            if (source.isFile) source.copyTo(File(attempt, "private/android-room.db$suffix"))
        }
    }
}

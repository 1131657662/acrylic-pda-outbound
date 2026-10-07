package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.sync.AuditRepository
import com.acrylic.outbound.pda.temporary.*
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
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class E2aRealHttpRecoveryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun actualRoomFreezeAndFiveLostReceiptsRecoverOverRealHttp() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("E2A_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires the protected one-shot nonempty MySQL runner", !path.isNullOrBlank())
        val inputFile = File(checkNotNull(path)).canonicalFile
        check(inputFile.path.replace('\\', '/').matches(Regex(
            ".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,3}/private/android-http-input\\.json")))
        val input = JSONObject(inputFile.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_ANDROID_ROOM_REAL_HTTP")
        TemporaryWorkFixture(folder.root, contract = input).use { work ->
            val connection = E2aRealHttpConnection(work, input)
            val store = { TemporaryWorkStore(work.db, connection, work.store.sample) }
            work.start("1"); work.scan()
            if (input.optBoolean("otherSourceBeforeFreeze")) E2aRealHttpOtherSource.capture(work)
            if (input.optBoolean("partialUploadBeforeAbort")) E2aRealHttpPartialUpload.grow(work)
            val frozen = TemporaryFreeze(store()).freeze(work.id, "合成短装待核对")
            if (input.optBoolean("partialUploadBeforeAbort")) E2aRealHttpPartialUpload.start(frozen, connection)
            val submissions = TemporarySubmissionRepository(work.db, connection)
            if (input.optBoolean("abortBeforePayload")) {
                assertEquals("ABORTED", submissions.abort(frozen.id, "原机冻结后取消，保留实物证据").state)
            } else assertEquals("REVIEW_REQUIRED", submissions.upload(frozen.id).state)
            recover(work, connection, store)
            saveResult(checkNotNull(checkNotNull(inputFile.parentFile).parentFile), work, connection)
        }
    }

    private fun recover(work: TemporaryWorkFixture, connection: E2aRealHttpConnection, store: () -> TemporaryWorkStore) {
        var repo = repository(work, connection, store)
        repo.queue(work.id)
        val plan = checkNotNull(work.db.localE2aTransfers().get(work.id))
        val requests = E2aRequestSequence(work.db, plan).all().toList()
        var lost = 0
        for (attempt in 1..(requests.size / 20 + 12)) {
            try { repo.resume(work.id) }
            catch (error: IOException) {
                assertTrue(error.message.orEmpty().startsWith("Injected lost"))
                lost++; work.reopen(); connection.unavailable = true
                repo = repository(work, connection, store)
            }
            if (work.db.localE2aReceipts().queue(work.id)?.state == "PARTIAL") break
        }
        assertEquals(5, lost)
        assertEquals("PARTIAL", work.db.localE2aReceipts().queue(work.id)?.state)
        assertEquals(requests, E2aRequestSequence(work.db, plan).all().toList())
        assertEquals(requests.size + 1, connection.posts.size)
        assertEquals(1, connection.signatures)
        assertFalse(checkNotNull(work.db.localE2aReceipts().queue(work.id)).autoRetry)
        requests.forEach { assertEquals("VERIFIED", work.db.localE2aReceipts().get(it.originalKey)?.state) }
    }

    private fun repository(work: TemporaryWorkFixture, connection: E2aRealHttpConnection,
        store: () -> TemporaryWorkStore) = LocalE2aRecoveryRepository(store) { control ->
        AuditRepository(work.db, { checkNotNull(connection.current()) }, { body, epoch ->
            connection.call(TemporaryConnection.Call("pda/audit/events", checkNotNull(connection.current()),
                JSONObject(body), epoch = epoch))
        }).syncAudit(control)
    }

    private fun saveResult(attempt: File, work: TemporaryWorkFixture, connection: E2aRealHttpConnection) {
        val stop = checkNotNull(work.db.localE2aStops().get(work.id))
        val result = JSONObject().put("submissionId", stop.submissionId).put("state", "PARTIAL")
        val submission = checkNotNull(work.db.temporarySubmissions().get(stop.submissionId))
        result.put("submissionState", submission.state).put("submissionReceipt", submission.receipt)
        result.put("payloadSha256", submission.sha256)
        val receipts = JSONObject()
        connection.receiptBytes.forEach { (originalKey, bytes) ->
            val key = JSONObject(bytes.toString(Charsets.UTF_8)).getString("originalKey")
            assertArrayEquals(bytes, work.db.localE2aReceipts().get(key)?.responseBytes)
            receipts.put(originalKey, Base64.getEncoder().encodeToString(bytes))
        }
        result.put("receipts", receipts)
        val output = File(attempt, "android-result.json")
        check(output.createNewFile()); output.writeText(result.toString())
    }
}

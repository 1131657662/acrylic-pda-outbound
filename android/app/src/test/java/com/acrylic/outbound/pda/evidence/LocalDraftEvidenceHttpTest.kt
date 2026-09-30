package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.*
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
import java.util.UUID

/** Actual Room27 product writer and durable queue; identity and device signing remain synthetic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftEvidenceHttpTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun originalSignedRoomStopStoresThroughRealHttpAndReplaysOriginalReceipts() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("DRAFT_EVIDENCE_HTTP_PRIVATE")
        assumeTrue("Requires protected nonempty MySQL runner", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex(
            ".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,2}/private/android-http-input\\.json")))
        val input = JSONObject(file.readText()); check(input.getString("fixturePurpose") == "SYNTHETIC_ANDROID_ROOM_REAL_HTTP")
        TemporaryWorkFixture(folder.root, contract = input).use { work ->
            work.start(); work.scan()
            val connection = E2aRealHttpConnection(work, input)
            LocalDraftHttpArchive.create(work, connection)
            val stopped = preserve(work, connection)
            recover(work, connection, stopped, input, checkNotNull(checkNotNull(file.parentFile).parentFile))
        }
    }
    private suspend fun preserve(work: TemporaryWorkFixture, connection: E2aRealHttpConnection): LocalDraftStop {
        val engine = TemporaryEngine(TemporaryWorkStore(work.db, connection, work.store.sample)) {}
        try {
            engine.resume(work.id); withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 } }
            assertTrue(engine.receive(work.barcodes[1], "MANUAL"))
            val pending = engine.preserve(work.id)
            assertFalse(engine.receive(work.barcodes[0], "BROADCAST"))
            val stop = withTimeout(10000) { pending.await() }
            withTimeout(10000) { engine.state.first { it.queued == 0 } }
            assertEquals(2, work.db.temporaryScans().captures(work.id).size)
            assertEquals("LOCAL_EVIDENCE_STOP", work.db.temporaryScans().events(work.id).last().type)
            return stop
        } finally { engine.close() }
    }
    private fun recover(work: TemporaryWorkFixture, connection: E2aRealHttpConnection, stop: LocalDraftStop, input: JSONObject, attempt: File) {
        fun repository() = LocalDraftRecoveryRepository { TemporaryWorkStore(work.db, connection, work.store.sample) }
        val plan = repository().prepare(work.id); val requests = work.db.localDraftTransfers().page(work.id, -1)
        assertEquals(plan.requestCount, requests.size.toLong()); assertEquals(1, connection.signatures)
        connection.unavailable = true
        for (operation in listOf("OPEN", "ITEM", "VERIFY")) {
            val error = assertThrows(java.io.IOException::class.java) { repository().resume(work.id) }
            assertTrue(error.message.orEmpty().contains(operation)); work.reopen()
            assertEquals(plan, repository().prepare(work.id)); assertEquals(requests, work.db.localDraftTransfers().page(work.id, -1))
            if (operation == "OPEN") {
                identityPause(work, connection)
                negatives(work, stop, input, requests.first().originalKey)
            }
        }
        assertEquals("ORIGINALS_STORED", repository().resume(work.id).state); work.reopen()
        assertEquals("ORIGINALS_STORED", repository().resume(work.id).state)
        assertEquals(plan.requestCount, connection.posts.size.toLong()); assertEquals(1, connection.signatures)
        requests.forEach { request ->
            val row = checkNotNull(work.db.localDraftTransfers().receipt(request.originalKey))
            assertEquals("VERIFIED", row.state); assertArrayEquals(connection.receiptBytes[request.originalKey], row.responseBytes)
        }
        val verify = requests.last(); val response = checkNotNull(work.db.localDraftTransfers().receipt(verify.originalKey)!!.responseBytes)
        val export = JSONObject().put("evidenceId", stop.evidenceId).put("clientOrderId", work.id).put("openKey", requests.first().originalKey)
            .put("open", JSONObject(requests.first().requestJson)).put("verifyKey", verify.originalKey).put("verifyResponse", JSONObject(response.toString(Charsets.UTF_8)))
            .put("roomVersion", work.db.openHelper.readableDatabase.version).put("originalRequestCount", plan.requestCount)
            .put("lostResponsesRecovered", 3).put("productWriterStop", true).put("identityPauseVerified", true)
            .put("archiveProof", JSONObject(checkNotNull(work.db.auditArchives().rows("1", input.getJSONObject("session").getString("deviceId")).single().proofJson)))
        File(attempt, "draft-android-result.json").writeText(SubmissionJson.canonical(export))
        assertEquals("EVIDENCE_STOPPED", work.db.temporaryDrafts().get(work.id)!!.state); assertNull(work.db.temporarySubmissions().forOrder(work.id))
    }
    private fun identityPause(work: TemporaryWorkFixture, connection: E2aRealHttpConnection) {
        val original = JSONObject(checkNotNull(work.fixture.auth).toString()); val posts = connection.posts.size
        work.fixture.auth = JSONObject(original.toString()).put("userId", UUID.randomUUID().toString())
        LocalDraftQueueGuard(work.db).reconcile(work.fixture.auth)
        work.fixture.auth = original; work.reopen(); LocalDraftQueueGuard(work.db).reconcile(original)
        val repository = LocalDraftRecoveryRepository { TemporaryWorkStore(work.db, connection, work.store.sample) }
        assertFalse(work.db.localDraftQueue().get(work.id)!!.autoRetry)
        assertThrows(Exception::class.java) { repository.resume(work.id) }
        assertEquals(posts, connection.posts.size); repository.prepare(work.id)
    }
    private fun negatives(work: TemporaryWorkFixture, stop: LocalDraftStop, input: JSONObject, openKey: String) {
        val network = PdaNetwork(input.getString("baseUrl")); val headers = headers(input); val base = "pda/local-draft-evidence/${stop.evidenceId}"
        val body = bytes(JSONObject().put("evidenceId", stop.evidenceId).put("rootSha256", stop.rootSha256))
        reject { network.original("$base/verify?originalKey=${UUID.randomUUID()}", body, headers) }
        val row = work.db.localDraftStops().page(work.id, 0).first(); val key = UUID.randomUUID().toString()
        val path = "$base/item?originalKey=$key&ordinal=${row.ordinal}&kind=${row.kind}&recordId=${row.recordId}" +
            "&deviceSeq=${row.deviceSeq}&recordStatus=${row.recordStatus}&byteCount=${row.rawBytes.size}&bytesSha256=${row.bytesSha256}"
        reject { network.original(path.replace(key, openKey), null, headers, row.rawBytes) }
        reject { network.original(path, null, headers, row.rawBytes.copyOf().apply { this[0] = 0 }) }
    }
    private fun headers(input: JSONObject) = mapOf("Authorization" to "Bearer ${input.getString("accessToken")}", "X-Server-Recovery-Epoch" to "1")
    private fun bytes(value: JSONObject) = SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8)
    private fun reject(action: () -> Unit) { val error = assertThrows(ApiFailure::class.java, action); assertTrue(error.httpStatus in listOf(400, 409)) }
}

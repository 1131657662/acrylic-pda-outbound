package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.evidence.E2aRealHttpConnection
import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.network.ApiFailure
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryArchiveHttpTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun actualAndroidFreezeSuccessAndArchiveKeepAllOriginalEvidence() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("TEMP_ARCHIVE_HTTP_PRIVATE")
        assumeTrue("Requires protected nonempty MySQL runner", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex(
            "D:/acrylic-pda-worktrees/r06-e2a-migration/\\.local/e2a-cross-20260925-[1-9][0-9]{0,2}/private/android-http-input\\.json")))
        val input = JSONObject(file.readText()); check(input.getString("fixturePurpose") == "SYNTHETIC_ANDROID_ROOM_REAL_HTTP")
        TemporaryWorkFixture(folder.root, contract = input).use { work ->
            val connection = E2aRealHttpConnection(work, input)
            work.start("1"); work.barcodes.forEach { work.scan(it) }
            val store = TemporaryWorkStore(work.db, connection, work.store.sample)
            val frozen = TemporaryFreeze(store).freeze(work.id, "")
            assertEquals("REVIEW_REQUIRED", TemporarySubmissionRepository(work.db, connection).upload(frozen.id).state)
            val headers = mapOf("Authorization" to "Bearer ${input.getString("accessToken")}", "X-Server-Recovery-Epoch" to "1")
            val adapter = PdaNetwork("http://127.0.0.1:19435/internal/")
            adapter.original("confirm", JSONObject().put("submissionId", frozen.id).toString().toByteArray(), headers)
            success(work, frozen, input, headers)
            archive(work, frozen.id, adapter, headers, checkNotNull(checkNotNull(file.parentFile).parentFile))
            assertEquals(frozen.payload, work.db.temporarySubmissions().get(frozen.id)!!.payload)
            assertEquals("FROZEN", work.db.temporaryDrafts().get(work.id)!!.state)
            work.barcodes.forEach { assertNotNull(work.db.temporaryScans().guard(it)) }
            assertEquals(1, connection.signatures)
        }
    }

    private fun success(work: TemporaryWorkFixture, frozen: TemporarySubmission, input: JSONObject, headers: Map<String, String>) {
        val network = PdaNetwork(input.getString("baseUrl")); val route = "pda/submissions/${frozen.id}"
        network.original(route, null, headers) // The first complete response is deliberately lost before persistence.
        work.db.temporarySubmissions().abortIntent(frozen.id, "成功后迟到原取消请求")
        work.reopen()
        val repository = TemporarySubmissionRepository(work.db, E2aRealHttpConnection(work, input))
        assertEquals("SYNCED", repository.upload(frozen.id).state)
        val hash = work.db.temporarySuccess().evidence(frozen.id)!!.responseSha256
        assertEquals("成功后迟到原取消请求", work.db.temporarySubmissions().get(frozen.id)!!.abortReason)
        work.reopen()
        assertEquals("SYNCED", TemporarySubmissionRepository(work.db, E2aRealHttpConnection(work, input)).checked(frozen.id).state)
        val body = JSONObject(frozen.payload); val abort = JSONObject().put("reason", "成功后迟到原取消请求")
        listOf("source", "assignmentId", "continuationId", "authorizationId", "freezeDeviceId", "workRound", "sourceRecoveryEpoch")
            .forEach { abort.put(it, body.get(it)) }
        val late = network.original("$route/abort", abort.toString().toByteArray(), headers)
        val lateHash = TemporarySuccessRepository(work.db).retain(frozen.id, late)
        assertEquals("SUCCEEDED", TemporarySuccessRepository(work.db).verify(frozen.id, lateHash, work.fixture.key).getString("status"))
        assertEquals(hash, work.db.temporarySuccess().evidence(frozen.id)!!.responseSha256)
        val request = TemporaryArchiveRepository(work.db).prepare(frozen.id, "1", work.fixture.key)
        val closed = assertThrows(ApiFailure::class.java) { network.original("$route/archive-confirmation", request.requestJson.toByteArray(), headers) }
        assertEquals("TEMPORARY_CONFIRMATION_REQUIRED", closed.code)
    }

    private fun archive(work: TemporaryWorkFixture, id: String, adapter: PdaNetwork, headers: Map<String, String>, attempt: File) {
        val key = work.fixture.key; val request = TemporaryArchiveRepository(work.db).prepare(id, "1", key)
        adapter.original("archive/$id", request.requestJson.toByteArray(), headers) // Server committed; simulate lost acknowledgement.
        work.reopen(); assertEquals(request, TemporaryArchiveRepository(work.db).prepare(id, "1", key))
        val raw = adapter.original("archive/$id", request.requestJson.toByteArray(), headers)
        val hash = TemporaryArchiveRepository(work.db).retain(request, raw); work.reopen()
        assertNull(TemporaryArchiveRepository(work.db).read(id, "1", key, key))
        TemporaryArchiveStorageChecks.rollback(work, request, hash)
        val proof = TemporaryArchiveRepository(work.db).verify(id, "1", hash, key, key)
        work.reopen(); assertEquals(proof.toString(), TemporaryArchiveRepository(work.db).read(id, "1", key, key).toString())
        val negatives = JSONObject(File(attempt, "archive-negative-vectors.json").readText())
        TemporaryArchiveStorageChecks.negatives(work, request, hash, negatives)
        val fixture = JSONObject().put("submissionId", id).put("recoveryEpoch", "1").put("requestJson", request.requestJson)
            .put("contextJson", request.contextJson).put("responseUtf8", raw.toString(Charsets.UTF_8)).put("key", key).put("negatives", negatives)
        File(checkNotNull(System.getProperty("pda.temporaryArchiveFixture"))).writeText(fixture.toString())
        File(attempt, "android-archive-completed.json").writeText(JSONObject().put("submissionId", id)
            .put("responseSha256", hash).put("realAndroidFreeze", true).put("productState", work.db.temporarySubmissions().get(id)!!.state)
            .put("localDeletionAllowed", false).toString())
    }
}

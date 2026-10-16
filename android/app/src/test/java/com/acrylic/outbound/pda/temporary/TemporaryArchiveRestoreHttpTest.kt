package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.evidence.E2aRealHttpConnection
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.submission.SubmissionJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryArchiveRestoreHttpTest {
    @Test fun originalAndroidBatchSurvivesFullServerRestore() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("E2A_ANDROID_HTTP_PRIVATE")
        assumeTrue("Requires isolated real HTTP/MySQL restore driver", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex(
            ".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,3}/private/android-http-input\\.json")))
        val input = JSONObject(file.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_ANDROID_ROOM_REAL_HTTP")
        val attempt = checkNotNull(checkNotNull(file.parentFile).parentFile)
        when (System.getenv("E2A_ARCHIVE_RESTORE_PHASE")) {
            "source" -> source(attempt, input)
            "restored" -> restored(attempt, input)
            "lost-success" -> TemporaryLostSuccessRestore.run(attempt, input)
            else -> error("Explicit one-shot source/restored phase required")
        }
    }

    private fun headers(input: JSONObject) = mapOf("Authorization" to "Bearer ${input.getString("accessToken")}",
        "X-Server-Recovery-Epoch" to "1")

    private fun source(attempt: File, input: JSONObject) {
        val directory = File(attempt, "android-source-room"); check(!directory.exists()); check(directory.mkdirs())
        val db = ArchiveRestoreRoom.open(directory)
        try {
            val downloads = File(attempt, "android-downloads"); check(downloads.mkdirs())
            val work = TemporaryWorkFixture(downloads, borrowed = db, contract = input)
            sourceBatch(attempt, input, work)
            File(attempt, "android-source-facts.json").writeText(ArchiveRestoreRoom.snapshot(db).toString())
            File(attempt, "android-source-completed.json").writeText(JSONObject().put("actualAndroidFreeze", true).put("state", "SYNCED").toString())
        } finally { db.close() }
    }

    private fun sourceBatch(attempt: File, input: JSONObject, work: TemporaryWorkFixture) {
        val db = work.db
        work.start("1"); work.barcodes.forEach { work.scan(it) }
        val connection = E2aRealHttpConnection(work, input)
        val frozen = TemporaryFreeze(TemporaryWorkStore(db, connection, work.store.sample)).freeze(work.id, "")
        assertEquals("REVIEW_REQUIRED", TemporarySubmissionRepository(db, connection).upload(frozen.id).state)
        assertNull(db.temporarySuccess().evidence(frozen.id))
        ArchiveRestoreRoom.pendingSnapshot(db, attempt)
        val adapter = PdaNetwork("http://127.0.0.1:19435/internal/")
        adapter.original("confirm", JSONObject().put("submissionId", frozen.id).toString().toByteArray(), headers(input))
        assertEquals("SYNCED", TemporarySubmissionRepository(db, connection).upload(frozen.id).state)
        val raw = PdaNetwork(input.getString("baseUrl")).original("pda/submissions/${frozen.id}", null, headers(input))
        File(attempt, "source-success-response.bin").writeBytes(raw)
        val key = work.fixture.key; val request = TemporaryArchiveRepository(db).prepare(frozen.id, "1", key)
        val bytes = adapter.original("archive/${frozen.id}", request.requestJson.toByteArray(), headers(input))
        val hash = TemporaryArchiveRepository(db).retain(request, bytes)
        TemporaryArchiveRepository(db).verify(frozen.id, "1", hash, key, key)
        TemporaryArchiveStorageChecks.negatives(work, request, hash, JSONObject(File(attempt, "archive-negative-vectors.json").readText()))
        val record = JSONObject().put("submissionId", frozen.id).put("draftId", work.id).put("payload", frozen.payload)
            .put("key", key).put("requestJson", request.requestJson).put("archiveUtf8", bytes.toString(Charsets.UTF_8))
        File(attempt, "android-original.json").writeText(record.toString())
    }

    private fun restored(attempt: File, input: JSONObject) {
        val source = File(attempt, "android-source-room"); val target = File(attempt, "android-restored-room")
        ArchiveRestoreRoom.copyOriginal(source, target)
        val original = JSONObject(File(attempt, "android-original.json").readText())
        val db = ArchiveRestoreRoom.open(target)
        try {
            val before = ArchiveRestoreRoom.snapshot(db)
            assertEquals(SubmissionJson.canonical(JSONObject(File(attempt, "android-source-facts.json").readText())),
                SubmissionJson.canonical(before))
            val id = original.getString("submissionId")
            val local = checkNotNull(db.temporarySubmissions().get(id)); assertEquals(original.getString("payload"), local.payload)
            success(attempt, input, original, db, local)
            archive(attempt, input, original, db)
            assertEquals(local, db.temporarySubmissions().get(id))
            val after = ArchiveRestoreRoom.snapshot(db)
            assertEquals(SubmissionJson.canonical(before), SubmissionJson.canonical(after))
            File(attempt, "android-restored-facts.json").writeText(after.toString())
            File(attempt, "android-restored-completed.json").writeText(JSONObject().put("sameOriginalBatch", true)
                .put("allTablesPreserved", true).put("localDeletionAllowed", false).toString())
        } finally { db.close() }
    }

    private fun success(attempt: File, input: JSONObject, original: JSONObject,
                        db: com.acrylic.outbound.pda.data.PdaDatabase, local: TemporarySubmission) {
        val id = original.getString("submissionId"); val key = original.getJSONObject("key")
        val network = PdaNetwork(input.getString("baseUrl")); val route = "pda/submissions/$id"
        network.original(route, null, headers(input)) // Deliberately discard the first restored-server response.
        val raw = network.original(route, null, headers(input))
        val receipt = TemporarySuccessVerifier.verify(local, raw, key)
        assertEquals(SubmissionJson.canonical(TemporarySuccessRepository(db).read(id, key)!!), SubmissionJson.canonical(receipt))
        File(attempt, "restored-success-response.bin").writeBytes(raw)
    }

    private fun archive(attempt: File, input: JSONObject, original: JSONObject, db: com.acrylic.outbound.pda.data.PdaDatabase) {
        val id = original.getString("submissionId"); val key = original.getJSONObject("key")
        val request = TemporaryArchiveRepository(db).prepare(id, "1", key)
        assertEquals(original.getString("requestJson"), request.requestJson)
        val closed = assertThrows(ApiFailure::class.java) { PdaNetwork(input.getString("baseUrl"))
            .original("pda/submissions/$id/archive-confirmation", request.requestJson.toByteArray(), headers(input)) }
        assertEquals("TEMPORARY_CONFIRMATION_REQUIRED", closed.code)
        val raw = PdaNetwork("http://127.0.0.1:19435/internal/").original("archive/$id", request.requestJson.toByteArray(), headers(input))
        assertArrayEquals(original.getString("archiveUtf8").toByteArray(Charsets.UTF_8), raw)
        val hash = TemporaryArchiveRepository(db).retain(request, raw)
        val proof = TemporaryArchiveRepository(db).verify(id, "1", hash, key, key)
        assertFalse(proof.getBoolean("localDeletionAllowed"))
        File(attempt, "restored-archive-response.bin").writeBytes(raw)
    }
}

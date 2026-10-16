package com.acrylic.outbound.pda.retention

import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.temporary.ArchiveRestoreRoom
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FormalRepositoryRestoreHttpTest {
    @Test fun originalUnknownBatchRecoversThroughProductSessionAndRepository() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("E2A_FORMAL_REPOSITORY_PRIVATE")
        assumeTrue("Requires isolated product repository real HTTP/MySQL driver", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex(
            ".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,3}/private/formal-repository-input\\.json")))
        val input = JSONObject(file.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_FORMAL_ANDROID_REAL_HTTP")
        val attempt = checkNotNull(checkNotNull(file.parentFile).parentFile)
        when (System.getenv("E2A_FORMAL_RESTORE_PHASE")) {
            "source" -> source(attempt, input)
            "restored" -> recover(attempt, input, true)
            else -> error("Explicit source/restored phase required")
        }
    }

    private fun source(attempt: File, input: JSONObject) {
        val directory = File(attempt, "formal-source-room"); check(directory.mkdirs())
        val db = ArchiveRestoreRoom.open(directory)
        try {
            FormalRepositorySession(ApplicationProvider.getApplicationContext(), input, db).use { fixture ->
                val local = FormalRepositoryPreparation.freeze(attempt, input, db, fixture.session)
                val original = JSONObject().put("id", local.id).put("assignmentId", local.assignmentId)
                    .put("payload", local.payload).put("key", input.getJSONObject("signingKey"))
                File(attempt, "formal-original.json").writeText(original.toString())
                assertThrows(IOException::class.java) { SubmissionRepository(db, fixture.session).upload(local.id) }
                val unknown = checkNotNull(db.submissions().get(local.id))
                assertEquals("RESULT_UNKNOWN", unknown.state); assertEquals(local.payload, unknown.payload)
                assertNull(unknown.receipt); assertFalse(unknown.autoRetry); assertNull(db.archives().get(local.id, "1"))
                File(attempt, "formal-unknown-facts.json").writeText(ArchiveRestoreRoom.snapshot(db).toString())
            }
        } finally { db.close() }
        ArchiveRestoreRoom.copyOriginal(directory, File(attempt, "formal-unknown-room"))
        recover(attempt, input, false)
    }

    private fun recover(attempt: File, input: JSONObject, restored: Boolean) {
        val phase = if (restored) "restored" else "source"
        val directory = File(attempt, "formal-$phase-room")
        if (restored) ArchiveRestoreRoom.copyOriginal(File(attempt, "formal-unknown-room"), directory)
        val db = ArchiveRestoreRoom.open(directory)
        try {
            val before = JSONObject(File(attempt, "formal-unknown-facts.json").readText())
            assertEquals(SubmissionJson.canonical(before), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
            FormalRepositorySession(ApplicationProvider.getApplicationContext(), input, db).use { fixture ->
                success(attempt, input, db, fixture.session, phase)
            }
            val after = ArchiveRestoreRoom.snapshot(db)
            val changed = before.keys().asSequence().filter {
                SubmissionJson.canonical(before.get(it)) != SubmissionJson.canonical(after.get(it)) }.toSet()
            assertEquals(setOf("local_submission", "local_submission_archive"), changed)
            File(attempt, "formal-$phase-facts.json").writeText(after.toString())
        } finally { db.close() }
        reopened(attempt, input, directory, phase)
        File(attempt, "formal-$phase-completed.json").writeText("{\"productSessionAndRepository\":true,\"guards\":2}")
    }

    private fun success(attempt: File, input: JSONObject, db: PdaDatabase,
                        session: com.acrylic.outbound.pda.network.PdaSession, phase: String) {
        val original = JSONObject(File(attempt, "formal-original.json").readText())
        val id = original.getString("id")
        val local = SubmissionRepository(db, session).upload(id)
        assertEquals("SYNCED", local.state); assertEquals(original.getString("payload"), local.payload)
        assertEquals(2, db.work().guards(local.assignmentId).size); assertFalse(local.autoRetry)
        assertEquals("SYNCED", ReceiptVerifier.verify(local, JSONObject(local.receipt!!), input.getJSONObject("signingKey")))
        File(attempt, "formal-$phase-repository-receipt.json").writeText(local.receipt)
        val archives = SubmissionArchiveRepository(db, session::requireSession, { java.time.Instant.now() }) { key, body, epoch ->
            session.call("pda/submissions/$key/archive-confirmation", body, epoch)
        }
        assertTrue(archives.refresh().contains("已核对"))
        val archive = checkNotNull(db.archives().get(id, "1"))
        assertFalse(JSONObject(archive.confirmationJson).getBoolean("localDeletionAllowed"))
        File(attempt, "formal-$phase-repository-archive.json").writeText(archive.confirmationJson)
    }

    private fun reopened(attempt: File, input: JSONObject, directory: File, phase: String) {
        val db = ArchiveRestoreRoom.open(directory)
        try {
            val before = ArchiveRestoreRoom.snapshot(db)
            FormalRepositorySession(ApplicationProvider.getApplicationContext(), input, db).use { fixture ->
                val id = JSONObject(File(attempt, "formal-original.json").readText()).getString("id")
                assertEquals("SYNCED", SubmissionRepository(db, fixture.session).upload(id).state)
            }
            assertEquals(SubmissionJson.canonical(before), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
            File(attempt, "formal-$phase-reopened-facts.json").writeText(before.toString())
        } finally { db.close() }
    }
}

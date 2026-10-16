package com.acrylic.outbound.pda.temporary

import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRepositoryRestoreHttpTest {
    @Test fun originalSuccessRecoversThroughProductSessionAfterLostResponseAndServerRestore() = runBlocking<Unit>(Dispatchers.IO) {
        val path = System.getenv("E2A_TEMP_REPOSITORY_PRIVATE")
        assumeTrue("Requires isolated TEMP product session real HTTP/MySQL driver", !path.isNullOrBlank())
        val file = File(checkNotNull(path)).canonicalFile
        check(file.path.replace('\\', '/').matches(Regex(
            ".*/\\.local/e2a-cross-20260925-[1-9][0-9]{0,3}/private/temp-repository-input\\.json")))
        val input = JSONObject(file.readText())
        check(input.getString("fixturePurpose") == "SYNTHETIC_ANDROID_ROOM_REAL_HTTP")
        val attempt = checkNotNull(checkNotNull(file.parentFile).parentFile)
        when (System.getenv("E2A_TEMP_REPOSITORY_PHASE")) {
            "source" -> source(attempt, input)
            "restored" -> recover(attempt, input, true)
            else -> error("Explicit source/restored phase required")
        }
    }

    private fun source(attempt: File, input: JSONObject) {
        val directory = File(attempt, "temp-product-source-room")
        ArchiveRestoreRoom.copyOriginal(File(attempt, "android-pending-room"), directory)
        val original = JSONObject(File(attempt, "android-original.json").readText())
        val id = original.getString("submissionId")
        val db = ArchiveRestoreRoom.open(directory)
        try {
            val before = JSONObject(File(attempt, "android-pending-facts.json").readText())
            assertEquals(SubmissionJson.canonical(before), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
            TemporaryRepositorySession(ApplicationProvider.getApplicationContext(), input, db).use { fixture ->
                assertEquals("REVIEW_REQUIRED", db.temporarySubmissions().get(id)!!.state)
                assertThrows(IOException::class.java) { TemporarySubmissionRepository(db, fixture.connection).upload(id) }
                val unknown = checkNotNull(db.temporarySubmissions().get(id))
                assertEquals("RESULT_UNKNOWN", unknown.state); assertEquals(original.getString("payload"), unknown.payload)
                assertNull(db.temporarySuccess().evidence(id))
                assertEquals(SubmissionJson.canonical(before.get("temporary_success_response")),
                    SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db).get("temporary_success_response")))
                File(attempt, "temp-product-unknown-facts.json").writeText(ArchiveRestoreRoom.snapshot(db).toString())
            }
        } finally { db.close() }
        ArchiveRestoreRoom.copyOriginal(directory, File(attempt, "temp-product-unknown-room"))
        recover(attempt, input, false)
    }

    private fun recover(attempt: File, input: JSONObject, restored: Boolean) {
        val phase = if (restored) "restored" else "source"
        val directory = File(attempt, "temp-product-$phase-room")
        if (restored) ArchiveRestoreRoom.copyOriginal(File(attempt, "temp-product-unknown-room"), directory)
        val db = ArchiveRestoreRoom.open(directory)
        try {
            val before = JSONObject(File(attempt, "temp-product-unknown-facts.json").readText())
            assertEquals(SubmissionJson.canonical(before), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
            TemporaryRepositorySession(ApplicationProvider.getApplicationContext(), input, db).use { fixture ->
                success(attempt, db, fixture.connection)
            }
            val after = ArchiveRestoreRoom.snapshot(db)
            val changed = before.keys().asSequence().filter {
                SubmissionJson.canonical(before.get(it)) != SubmissionJson.canonical(after.get(it)) }.toSet()
            assertEquals(setOf("temporary_submission", "temporary_success_response", "temporary_success_evidence"), changed)
            File(attempt, "temp-product-$phase-facts.json").writeText(after.toString())
        } finally { db.close() }
        reopened(directory, input)
        File(attempt, "temp-product-$phase-completed.json").writeText(
            JSONObject().put("productSessionAndRepository", true).put("localDeletionAllowed", false).toString())
    }

    private fun success(attempt: File, db: PdaDatabase, connection: TemporarySession) {
        val original = JSONObject(File(attempt, "android-original.json").readText())
        val id = original.getString("submissionId"); val repository = TemporarySubmissionRepository(db, connection)
        assertEquals("SYNCED", repository.upload(id).state); assertEquals("SYNCED", repository.checked(id).state)
        assertEquals(original.getString("payload"), db.temporarySubmissions().get(id)!!.payload)
        val expected = JSONObject(File(attempt, "source-success-response.bin").readText()).getJSONObject("data")
        val receipt = TemporarySuccessRepository(db).read(id, original.getJSONObject("key"))!!
        assertEquals(SubmissionJson.canonical(expected), SubmissionJson.canonical(receipt))
    }

    private fun reopened(directory: File, input: JSONObject) {
        val db = ArchiveRestoreRoom.open(directory)
        try {
            val before = ArchiveRestoreRoom.snapshot(db)
            val id = db.openHelper.readableDatabase.query("SELECT id FROM temporary_submission").use {
                assertEquals(1, it.count); check(it.moveToFirst()); it.getString(0)
            }
            TemporaryRepositorySession(ApplicationProvider.getApplicationContext(), input, db).use { fixture ->
                assertEquals("SYNCED", TemporarySubmissionRepository(db, fixture.connection).upload(id).state)
            }
            assertEquals(SubmissionJson.canonical(before), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
        } finally { db.close() }
    }
}

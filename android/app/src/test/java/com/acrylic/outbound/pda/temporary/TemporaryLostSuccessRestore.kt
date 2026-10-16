package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/** Consume the original pre-success Room snapshot after server SQL/files restoration. */
internal object TemporaryLostSuccessRestore {
    fun run(attempt: File, input: JSONObject) {
        val target = File(attempt, "android-lost-success-room")
        ArchiveRestoreRoom.copyOriginal(File(attempt, "android-pending-room"), target)
        val original = JSONObject(File(attempt, "android-original.json").readText())
        val id = original.getString("submissionId")
        val connection = ArchiveRecoveryQueryConnection(input, id, attempt)
        val before = JSONObject(File(attempt, "android-pending-facts.json").readText())
        var db = ArchiveRestoreRoom.open(target)
        try {
            assertEquals(SubmissionJson.canonical(before), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
            val local = checkNotNull(db.temporarySubmissions().get(id))
            assertEquals("REVIEW_REQUIRED", local.state); assertEquals(original.getString("payload"), local.payload)
            assertNull(db.temporarySuccess().evidence(id))
            assertThrows(IOException::class.java) { TemporarySubmissionRepository(db, connection).upload(id) }
            assertEquals("RESULT_UNKNOWN", db.temporarySubmissions().get(id)!!.state)
            assertNull(db.temporarySuccess().evidence(id)); assertEquals(1, connection.gets)
            File(attempt, "android-lost-success-unknown-facts.json").writeText(ArchiveRestoreRoom.snapshot(db).toString())
        } finally { db.close() }
        db = ArchiveRestoreRoom.open(target)
        try { recovered(attempt, original, before, db, connection) } finally { db.close() }
    }

    private fun recovered(attempt: File, original: JSONObject, before: JSONObject,
                          db: PdaDatabase, connection: ArchiveRecoveryQueryConnection) {
        val id = original.getString("submissionId"); val repository = TemporarySubmissionRepository(db, connection)
        assertEquals("SYNCED", repository.upload(id).state)
        assertEquals("SYNCED", repository.checked(id).state)
        assertEquals(original.getString("payload"), db.temporarySubmissions().get(id)!!.payload)
        val expected = JSONObject(File(attempt, "source-success-response.bin").readText()).getJSONObject("data")
        val receipt = TemporarySuccessRepository(db).read(id, original.getJSONObject("key"))!!
        assertEquals(SubmissionJson.canonical(expected), SubmissionJson.canonical(receipt))
        assertEquals(2, connection.gets)
        val after = ArchiveRestoreRoom.snapshot(db)
        assertEquals("SYNCED", repository.upload(id).state); assertEquals(2, connection.gets)
        assertEquals(SubmissionJson.canonical(after), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
        val changed = before.keys().asSequence().filter { table ->
            SubmissionJson.canonical(before.get(table)) != SubmissionJson.canonical(after.get(table)) }.toSet()
        assertEquals(setOf("temporary_submission", "temporary_success_response", "temporary_success_evidence"), changed)
        File(attempt, "android-lost-success-facts.json").writeText(after.toString())
        File(attempt, "android-lost-success-completed.json").writeText(JSONObject().put("originalId", id)
            .put("gets", connection.gets).put("noNewSignatureOrPost", true).put("localDeletionAllowed", false).toString())
    }
}

package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.retention.*
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/** Actual backend responses are discarded after commit; no archive proof is mocked. */
object ArchiveEndToEndScenario {
    fun verify(app: PdaApplication, submission: String) {
        val local = checkNotNull(app.database.submissions().get(submission))
        val captures = app.database.captures().page(local.assignmentId, 0, Int.MAX_VALUE)
        val scans = app.database.scans().page(local.assignmentId, 0, Int.MAX_VALUE)
        val events = app.database.work().events(local.assignmentId)
        assertTrue(events.isNotEmpty())
        val result = submissionArchive(app, submission)
        result.put("audit", auditArchive(app))
        assertEquals(local, app.database.submissions().get(submission))
        assertEquals(captures, app.database.captures().page(local.assignmentId, 0, Int.MAX_VALUE))
        assertEquals(scans, app.database.scans().page(local.assignmentId, 0, Int.MAX_VALUE))
        assertEquals(events, app.database.work().events(local.assignmentId))
        result.put("submissionId", submission).put("assignmentId", local.assignmentId)
            .put("businessFactsPreserved", true).put("captures", captures.size).put("scans", scans.size)
            .put("events", events.size).put("roomVersion", app.database.openHelper.readableDatabase.version)
        File(app.filesDir, "archive-e2e-evidence.json").writeText(result.toString())
    }

    private fun submissionArchive(app: PdaApplication, id: String): JSONObject {
        val auth = app.session.requireSession(); val epoch = auth.getString("serverRecoveryEpoch")
        assertNull(app.database.archives().get(id, epoch))
        var first = ""
        val interrupted = SubmissionArchiveRepository(app.database, app.session::requireSession, app.session::now) { key, body, version ->
            first = SubmissionJson.canonical(app.session.call("pda/submissions/$key/archive-confirmation", body, version))
            throw IOException("Test: discard successful archive response before local persistence")
        }
        assertTrue(interrupted.refresh().contains("尚未核验"))
        assertTrue(first.isNotBlank()); assertNull(app.database.archives().get(id, epoch))
        assertTrue(app.archives.refresh().contains("归档已核对"))
        val row = checkNotNull(app.database.archives().get(id, epoch))
        assertEquals(first, row.confirmationJson)
        val local = checkNotNull(app.database.submissions().get(id))
        val key = JSONObject(row.signingKeyJson)
        SubmissionArchiveVerifier.verify(local, auth, SubmissionArchiveVerifier.request(local, key), JSONObject(first), key)
        app.archives.refresh(); assertEquals(row, app.database.archives().get(id, epoch))
        return JSONObject().put("submissionLostResponseRetriedSameProof", true)
            .put("submissionSignedProofVerified", true).put("localDeletionAllowed", JSONObject(first).getBoolean("localDeletionAllowed"))
    }

    private fun auditArchive(app: PdaApplication): JSONObject {
        val auth = app.session.requireSession(); val epoch = auth.getString("serverRecoveryEpoch"); val device = auth.getString("deviceId")
        assertTrue(app.auditSync.upload().contains("已补传"))
        val through = checkNotNull(app.database.audit().checkpoint(epoch, device)).acknowledgedThrough
        assertTrue(through > 0); assertTrue(app.database.auditArchives().rows(epoch, device).isEmpty())
        var first = ""
        val interrupted = AuditArchiveRepository(app.database, app.session::requireSession) { restoring, body, version ->
            assertFalse(restoring)
            first = SubmissionJson.canonical(app.session.call("pda/audit/archives", body, version))
            throw IOException("Test: discard successful audit response before local persistence")
        }
        assertThrows(IOException::class.java) { interrupted.archive() }
        val pending = app.database.auditArchives().rows(epoch, device).single()
        assertNull(pending.proofJson); assertTrue(first.isNotBlank())
        assertTrue(app.auditArchives.archive().contains("归档已核验"))
        val row = app.database.auditArchives().rows(epoch, device).single()
        assertEquals(pending.requestJson, row.requestJson); assertEquals(first, row.proofJson)
        assertEquals(through, AuditArchiveVerifier.prefix(listOf(row), auth.getString("warehouseId")).single().throughSeq)
        app.auditArchives.archive(); assertEquals(listOf(row), app.database.auditArchives().rows(epoch, device))
        return JSONObject().put("afterSeq", row.afterSeq).put("throughSeq", row.throughSeq)
            .put("lostResponseRetriedSameRequestAndProof", true).put("signedPrefixVerified", true)
    }

    fun verifyReopened(app: PdaApplication, db: PdaDatabase, submission: String) {
        val auth = app.session.requireSession(); val epoch = auth.getString("serverRecoveryEpoch")
        val local = checkNotNull(db.submissions().get(submission))
        val archive = checkNotNull(db.archives().get(submission, epoch))
        val key = JSONObject(archive.signingKeyJson)
        SubmissionArchiveVerifier.verify(local, auth, SubmissionArchiveVerifier.request(local, key), JSONObject(archive.confirmationJson), key)
        val prefix = AuditArchiveVerifier.prefix(db.auditArchives().rows(epoch, auth.getString("deviceId")), auth.getString("warehouseId"))
        assertTrue(prefix.isNotEmpty())
        assertEquals(db.work().deviceEvents(auth.getString("deviceId")).last().localSeq, prefix.last().throughSeq)
        val file = File(app.filesDir, "archive-e2e-evidence.json")
        file.writeText(JSONObject(file.readText()).put("roomReopenedAndProofsReverified", true).toString())
    }
}

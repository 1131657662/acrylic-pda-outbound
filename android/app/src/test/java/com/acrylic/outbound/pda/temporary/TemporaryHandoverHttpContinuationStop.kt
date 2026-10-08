package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.evidence.LocalE2aRecoveryRepository
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.sync.AuditRepository
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/** Stops the actual R06 target with its own E2A roots before the next TEMP handover. */
internal class TemporaryHandoverHttpContinuationStop(private val fixture: TemporaryHandoverHttpTest) {
    private val db get() = fixture.db
    private val connection get() = fixture.connection
    private val client get() = db.temporaryContinuations().forSource(
        db.temporaryHandoverActivations().get(fixture.caseId)!!.newClientOrderId)!!.newClientOrderId
    private val reason = "隔离R06原机交接，保留原事实和实物占用"
    private fun store() = TemporaryWorkStore(db, connection, true, true) { TimeSample(1000, 500, 1) }

    fun evidence() {
        val recovery = LocalE2aRecoveryRepository(::store) { control ->
            AuditRepository(db, { connection.current() }, { body, epoch ->
                connection.call(TemporaryConnection.Call("pda/audit/events", connection.current(), JSONObject(body), epoch = epoch))
            }).syncAudit(control)
        }
        recovery.queue(client)
        repeat(20) { if (db.localE2aReceipts().queue(client)?.state != "PARTIAL") recovery.resume(client) }
        assertEquals("PARTIAL", db.localE2aReceipts().queue(client)!!.state)
        val frozen = db.temporarySubmissions().forOrder(client)!!
        val request = TemporaryContinuationStopRequest(db).body(frozen, reason, connection.session.getJSONObject("signingKey"))
        File(fixture.output.parentFile, "android-continuation-stop-request.json").writeText(SubmissionJson.canonical(request))
        result("continuationEvidence", frozen)
    }

    fun stop() {
        val frozen = db.temporarySubmissions().forOrder(client)!!
        val losing = object : TemporaryConnection by connection {
            var lost = false
            override fun original(request: TemporaryConnection.Original): ByteArray {
                val response = connection.original(request)
                if (request.path.endsWith("/stop") && !lost) {
                    lost = true; throw IOException("Injected lost actual R06 STOP response")
                }
                return response
            }
        }
        assertThrows(IOException::class.java) { TemporarySubmissionRepository(db, losing, true).stopContinuation(frozen.id, reason) }
        assertEquals(reason, db.temporarySubmissions().get(frozen.id)!!.abortReason)
        fixture.reopen("continuation-stop-lost-response")
        assertThrows(Exception::class.java) { TemporarySubmissionRepository(db, connection, true).stopContinuation(frozen.id, "changed") }
        roomRollback(frozen)
        val saved = TemporarySubmissionRepository(db, connection, true).upload(frozen.id)
        assertEquals("ABORTED", saved.state); assertFalse(saved.autoRetry)
        assertEquals(frozen.payload, saved.payload); assertNull(db.temporarySuccess().evidence(frozen.id))
        result("continuationStop", saved)
    }

    private fun roomRollback(frozen: TemporarySubmission) {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER continuation_stop_tail AFTER UPDATE OF state ON temporary_submission WHEN NEW.state='ABORTED' BEGIN SELECT RAISE(ABORT,'D06_REVERSE_STOP_ROOM_TAIL'); END")
        try {
            assertThrows(Exception::class.java) { TemporarySubmissionRepository(db, connection, true).upload(frozen.id) }
            assertNotEquals("ABORTED", db.temporarySubmissions().get(frozen.id)!!.state)
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER continuation_stop_tail") }
        fixture.reopen("continuation-stop-room-rollback")
    }

    private fun result(phase: String, frozen: TemporarySubmission) {
        File(fixture.output.parentFile, "$phase-result.json").writeText(JSONObject().put("submissionId", frozen.id)
            .put("state", frozen.state).put("payloadSha256", frozen.sha256).toString())
    }
}

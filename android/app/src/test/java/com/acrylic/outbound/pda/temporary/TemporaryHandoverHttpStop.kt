package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.evidence.LocalE2aRecoveryRepository
import com.acrylic.outbound.pda.sync.AuditRepository
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/** Uses the receiving device's actual frozen STOP/audit roots; transport losses never impersonate its predecessor. */
internal class TemporaryHandoverHttpStop(private val fixture: TemporaryHandoverHttpTest) {
    private val db get() = fixture.db
    private val connection get() = fixture.connection
    private val client get() = db.temporaryHandoverActivations().get(fixture.caseId)!!.newClientOrderId
    private val reason = "隔离交接新轮技术停止，保留实物占用"
    fun evidence(resultPhase: String = "evidence") {
        fun recovery() = LocalE2aRecoveryRepository(fixture::store) { control ->
            AuditRepository(db, { connection.current() }, { body, epoch ->
                connection.call(TemporaryConnection.Call("pda/audit/events", connection.current(), JSONObject(body), epoch = epoch))
            }).syncAudit(control)
        }
        recovery().queue(client)
        repeat(20) { if (db.localE2aReceipts().queue(client)?.state != "PARTIAL") recovery().resume(client) }
        assertEquals("PARTIAL", db.localE2aReceipts().queue(client)!!.state)
        val frozen = db.temporarySubmissions().forOrder(client)!!
        val request = TemporaryHandoverStopRequest(db).body(frozen, reason, connection.session.getJSONObject("signingKey"))
        File(fixture.output.parentFile, "android-handover-stop-request.json").writeText(
            com.acrylic.outbound.pda.submission.SubmissionJson.canonical(request))
        result(resultPhase, frozen)
    }
    fun stop(resultPhase: String = "stop") {
        connection.loseSubmission = false
        val frozen = db.temporarySubmissions().forOrder(client)!!
        assertThrows(IOException::class.java) { TemporaryHandoverStopRepository(db, connection, true).stop(frozen.id, reason) }
        assertEquals(reason, db.temporarySubmissions().get(frozen.id)!!.abortReason)
        fixture.reopen("stop-lost-response")
        assertThrows(Exception::class.java) { TemporaryHandoverStopRepository(db, connection, true).stop(frozen.id, "changed reason") }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER handover_stop_tail AFTER UPDATE OF state ON temporary_submission WHEN NEW.state='ABORTED' BEGIN SELECT RAISE(ABORT,'D06_HTTP_STOP_LAST_WRITE'); END")
        assertThrows(Exception::class.java) { TemporarySubmissionRepository(db, connection).upload(frozen.id) }
        assertNotEquals("ABORTED", db.temporarySubmissions().get(frozen.id)!!.state)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER handover_stop_tail"); fixture.reopen("stop-last-write-rollback")
        val saved = TemporarySubmissionRepository(db, connection).upload(frozen.id)
        assertEquals("ABORTED", saved.state); assertFalse(saved.autoRetry)
        assertEquals(frozen.payload, saved.payload); assertNull(db.temporarySuccess().evidence(frozen.id))
        result(resultPhase, saved)
    }
    private fun result(phase: String, frozen: TemporarySubmission) {
        File(fixture.output.parentFile, "$phase-result.json").writeText(JSONObject().put("submissionId", frozen.id)
            .put("state", frozen.state).put("payloadSha256", frozen.sha256).toString())
    }
}

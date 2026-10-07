package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.evidence.LocalE2aRecoveryRepository
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.sync.AuditRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/** Same Room input, production E2A/stop consumers and real HTTP. Only identity and device keys are synthetic. */
internal class TemporaryHigherRoundHttpClient(private val f: TemporaryFixture,
    private val connection: TemporaryContinuationHttpConnection, private val evidence: File) {
    private fun store() = TemporaryWorkStore(f.db, connection, true) {
        TimeSample(1000, connection.now().toEpochMilli(), 1)
    }

    suspend fun next(frozen: TemporarySubmission): TemporarySubmission {
        recoverEvidence(frozen.clientOrderId)
        f.db.close(); f.context.getDatabasePath("outbound-pda.db").copyTo(File(evidence, "android-before-technical-stop.db")); f.reopen()
        val reason = "隔离高轮技术停止，保留全部实物占用"
        assertThrows(IOException::class.java) { TemporarySubmissionRepository(f.db, connection, true).stopContinuation(frozen.id, reason) }
        f.reopen()
        val saved = f.db.temporarySubmissions().get(frozen.id)!!
        assertEquals(reason, saved.abortReason); assertTrue(saved.autoRetry)
        assertThrows(IllegalStateException::class.java) {
            TemporarySubmissionRepository(f.db, connection, true).stopContinuation(frozen.id, "changed reason")
        }
        assertEquals("ABORTED", TemporarySubmissionRepository(f.db, connection, true).upload(frozen.id).state)
        assertEquals(1, connection.stopRequests.size)
        File(evidence, "android-stopped.json").writeText(connection.stopRequests.single())
        val activation = File(evidence, "android-next-activation.json")
        withTimeout(60_000) { while (!activation.exists()) delay(250) }
        val next = TemporaryContinuationActivation(store()).activate(frozen.clientOrderId, activation.readBytes())
        val ticket = f.db.temporaryContinuations().forTarget(next.clientOrderId)!!.ticketId
        val coordinator = TemporaryContinuationCoordinator(store())
        coordinator.queue(ticket); assertEquals("READY", coordinator.resume(ticket).state)
        f.reopen()
        val result = TemporaryFreeze(store()).freeze(next.clientOrderId, "")
        assertEquals("3", JSONObject(result.payload).getString("workRound"))
        assertEquals("4", JSONObject(result.payload).getString("expectedCount"))
        assertEquals("ABORTED", f.db.temporarySubmissions().get(frozen.id)!!.state)
        return result
    }

    private fun recoverEvidence(id: String) {
        fun repository() = LocalE2aRecoveryRepository(::store) { control ->
            AuditRepository(f.db, { checkNotNull(connection.current()) }, { body, epoch ->
                connection.call(TemporaryConnection.Call("pda/audit/events", checkNotNull(connection.current()),
                    JSONObject(body), epoch = epoch))
            }).syncAudit(control)
        }
        repository().queue(id)
        var lost = 0
        for (attempt in 1..20) {
            try { repository().resume(id) }
            catch (failure: IOException) { lost++; f.reopen() }
            if (f.db.localE2aReceipts().queue(id)?.state == "PARTIAL") break
        }
        assertEquals(1, lost)
        assertEquals("PARTIAL", f.db.localE2aReceipts().queue(id)?.state)
    }
}

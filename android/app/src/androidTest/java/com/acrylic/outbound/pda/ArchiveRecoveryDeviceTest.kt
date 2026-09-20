package com.acrylic.outbound.pda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.retention.*
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Explicit phases on preserved data. No device registration, clearing or business writes. */
@RunWith(AndroidJUnit4::class)
class ArchiveRecoveryDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val facts get() = ArchiveRecoveryFacts(app)

    private fun connected(action: () -> Unit) {
        facts.requireIsolated()
        val saved = app.session.settings.server
        SubmissionProxy("http://10.0.2.2:18088/api/v1/", java.net.URI(saved).port).use {
            assertEquals(saved, it.base)
            action()
        }
    }

    @Test fun restartedProcessRetainsIdentityAndOriginalProofs() = connected {
        assertFalse(facts.baseline.exists())
        assertEquals(facts.device, app.session.call("pda/devices/me").getString("id"))
        assertEquals("1", app.session.requireSession().getString("serverRecoveryEpoch"))
        assertEquals("NORMAL", app.database.sync().recovery()!!.mode)
        ArchiveEndToEndScenario.verifyReopened(app, app.database, facts.submission)
        facts.baseline.writeText(facts.snapshot().toString())
        facts.evidence("restart", JSONObject().put("originalIdentityAndProofsVerified", true))
    }

    @Test fun recoveryPendingResponseBlocksProofAndBusiness() = connected {
        assertThrows(IllegalStateException::class.java) { app.session.call("pda/devices/me") }
        assertEquals("1", app.session.requireSession().getString("serverRecoveryEpoch"))
        assertTrue(app.database.auditArchives().rows("2", facts.device).isEmpty())
        assertNull(app.database.audit().checkpoint("2", facts.device))
        blocked(); facts.assertPreserved()
        facts.evidence("server-pending", JSONObject().put("clientBlocksPendingMode", true).put("oldSessionRetained", true))
    }

    @Test fun missingDurableFileKeepsOriginalPendingRequest() = connected {
        app.session.call("pda/devices/me")
        assertEquals("2", app.session.requireSession().getString("serverRecoveryEpoch"))
        val error = assertThrows(ApiFailure::class.java) { app.auditArchives.restore() }
        assertEquals(503, error.httpStatus)
        val before = pendingRequest()
        assertThrows(ApiFailure::class.java) { app.auditArchives.restore() }
        assertEquals(before, pendingRequest()); pendingAndBlocked()
        facts.evidence("missing-file", JSONObject().put("status", error.httpStatus).put("code", error.code))
    }

    @Test fun committedRestoreLostResponseLeavesRecoverablePendingRow() = connected {
        val before = pendingRequest()
        val interrupted = AuditArchiveRepository(app.database, app.session::requireSession) { restoring, body, epoch ->
            assertTrue(restoring); assertEquals("2", epoch)
            val response = app.session.call("pda/audit/archives/restore", body, epoch)
            facts.evidence("lost-response", JSONObject().put("request", before)
                .put("proof", SubmissionJson.canonical(response)))
            throw IOException("Test: discard committed restore response before Room persistence")
        }
        assertThrows(IOException::class.java) { interrupted.restore() }
        assertEquals(before, pendingRequest()); pendingAndBlocked()
    }

    @Test fun nextProcessResumesOriginalRequestAndKeepsReconciliationBarrier() = connected {
        val lost = JSONObject(File(facts.directory, "lost-response.json").readText())
        assertNotEquals(lost.getInt("pid"), android.os.Process.myPid())
        assertEquals(lost.getString("request"), pendingRequest())
        assertTrue(app.auditArchives.restore())
        val row = app.database.auditArchives().rows("2", facts.device).single()
        assertEquals(lost.getString("request"), row.requestJson); assertEquals(lost.getString("proof"), row.proofJson)
        assertEquals(15L, AuditArchiveVerifier.prefix(listOf(row), app.session.requireSession().getString("warehouseId")).single().throughSeq)
        assertEquals(15L, app.database.audit().checkpoint("2", facts.device)!!.acknowledgedThrough)
        assertTrue(app.auditArchives.restore())
        assertEquals(row, app.database.auditArchives().rows("2", facts.device).single())
        assertTrue(app.archives.refresh().contains("尚未核验"))
        assertNull(app.database.archives().get(facts.submission, "2"))
        blocked(); facts.assertPreserved()
        facts.evidence("resumed", JSONObject().put("originalRequestAndProofRetained", true).put("throughSeq", 15)
            .put("businessAndOldArchivesPreserved", true).put("mode", "RECONCILING"))
    }

    @Test fun completedRecoveryProofSurvivesAnotherProcessRestart() = connected {
        val prior = JSONObject(File(facts.directory, "resumed.json").readText())
        assertNotEquals(prior.getInt("pid"), android.os.Process.myPid())
        val before = app.database.auditArchives().rows("2", facts.device).single()
        assertTrue(app.auditArchives.restore()); assertEquals(before, app.database.auditArchives().rows("2", facts.device).single())
        assertEquals(15L, app.database.audit().checkpoint("2", facts.device)!!.acknowledgedThrough)
        assertEquals(15L, AuditArchiveVerifier.prefix(listOf(before), app.session.requireSession().getString("warehouseId")).single().throughSeq)
        app.session.call("pda/devices/me"); blocked(); facts.assertPreserved()
        facts.evidence("final-restart", JSONObject().put("savedNewEpochProofVerified", true).put("mode", "RECONCILING"))
    }

    private fun pendingRequest() = app.database.auditArchives().rows("2", facts.device).single().requestJson

    private fun pendingAndBlocked() {
        val row = app.database.auditArchives().rows("2", facts.device).single()
        assertTrue(row.restoring); assertNull(row.proofJson); assertNull(row.signingKeyJson)
        assertEquals(0L, app.database.audit().checkpoint("2", facts.device)?.acknowledgedThrough ?: 0)
        blocked(); facts.assertPreserved()
    }

    private fun blocked() {
        assertEquals("2", app.database.sync().recovery()!!.observedServerEpoch)
        assertEquals("RECONCILING", app.database.sync().recovery()!!.mode)
        assertThrows(IllegalStateException::class.java) { RecoveryProtection(app.database).requireNormal() }
    }
}

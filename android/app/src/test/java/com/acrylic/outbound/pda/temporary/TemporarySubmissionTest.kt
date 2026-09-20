package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.data.LocalRecoveryControl
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporarySubmissionTest {
    @get:Rule val folder = TemporaryFolder()
    private fun frozen(f: TemporaryWorkFixture): TemporarySubmission { f.start(); f.scan(); return TemporaryFreeze(f.store).freeze(f.id, "短装待核对") }
    private fun review(local: TemporarySubmission): JSONObject {
        val b = JSONObject(local.payload)
        return JSONObject().put("submissionId", local.id).put("orderId", UUID.randomUUID().toString()).put("source", b.getJSONObject("source"))
            .put("freezeDeviceId", b.getString("freezeDeviceId")).put("workRound", "1").put("issuedRecoveryEpoch", b.getString("sourceRecoveryEpoch"))
            .put("status", "REVIEW_REQUIRED").put("payloadSha256", local.sha256).put("mayResumeScanning", false).put("stockReleased", false)
    }
    private fun connection(f: TemporaryWorkFixture, call: (TemporaryConnection.Call) -> JSONObject) = object : TemporaryConnection by f.fixture {
        override fun call(request: TemporaryConnection.Call) = call(request)
    }
    @Test fun lostResponseQueriesOriginalIdAfterRoomReopenWithoutSendingNewBody() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val local = frozen(f); val result = review(local); var posted = 0; var accepted = false
            val connection = connection(f) { request ->
                if (request.path == "pda/temporary-orders/submissions") {
                    posted++; assertEquals(local.sha256, com.acrylic.outbound.pda.security.JwtVerifier.sha(com.acrylic.outbound.pda.submission.SubmissionJson.canonical(checkNotNull(request.body)).toByteArray()))
                    accepted = true; throw java.io.IOException("injected lost response")
                }
                if (accepted) result else throw ApiFailure("SUBMISSION_NOT_FOUND", "missing")
            }
            f.db.temporarySubmissions().queue(local.id)
            assertTrue(runCatching { TemporarySubmissionRepository(f.db, connection).upload(local.id) }.isFailure)
            assertEquals("RESULT_UNKNOWN", f.db.temporarySubmissions().get(local.id)?.state)
            assertNotNull(com.acrylic.outbound.pda.update.UpdateSafety(f.db) { com.acrylic.outbound.pda.work.WorkUi() }.installBlockReason())
            f.reopen(); assertEquals("REVIEW_REQUIRED", TemporarySubmissionRepository(f.db, connection).upload(local.id).state)
            assertEquals(1, posted); assertNotNull(f.db.temporaryScans().guard(f.barcodes[0]))
        }
    }
    @Test fun delayedResponseCannotSaveAcrossNewLoginOrRecoveryBarrier() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val local = frozen(f); val result = review(local)
            val connection = connection(f) {
                f.fixture.auth!!.put("localSessionId", UUID.randomUUID().toString())
                f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "2", mode = "RECONCILING")); result
            }
            assertTrue(runCatching { TemporarySubmissionRepository(f.db, connection).upload(local.id) }.isFailure)
            assertNull(f.db.temporarySubmissions().get(local.id)?.receipt)
            assertNotNull(f.db.temporaryScans().guard(f.barcodes[0]))
        }
    }
    @Test fun cancelIntentSurvivesLostResponseAndNeverUploadsBodyOnRetry() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val local = frozen(f); val calls = mutableListOf<String>(); var reason = ""
            val connection = connection(f) { request ->
                calls.add(request.path)
                if (request.path.endsWith("/abort")) { reason = request.body!!.getString("reason"); throw java.io.IOException("lost abort") }
                throw ApiFailure("SUBMISSION_NOT_FOUND", "missing")
            }
            assertTrue(runCatching { TemporarySubmissionRepository(f.db, connection).abort(local.id, "原取消原因") }.isFailure)
            f.reopen(); assertTrue(runCatching { TemporarySubmissionRepository(f.db, connection).upload(local.id) }.isFailure)
            assertEquals("原取消原因", reason); assertEquals(2, calls.count { it.endsWith("/abort") })
            assertNotNull(com.acrylic.outbound.pda.update.UpdateSafety(f.db) { com.acrylic.outbound.pda.work.WorkUi() }.installBlockReason())
            assertFalse(calls.contains("pda/temporary-orders/submissions")); assertNotNull(f.db.temporaryScans().guard(f.barcodes[0]))
        }
    }
    @Test fun forgedReviewHashAndSuccessNeverBecomeClearance() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val local = frozen(f); val key = f.fixture.key
            for (response in listOf(review(local).put("payloadSha256", "0".repeat(64)), review(local).put("status", "SUCCEEDED"),
                review(local).put("stockReleased", true), review(local).put("issuedRecoveryEpoch", "2"))) {
                assertTrue(runCatching { TemporarySubmissionReceipt.verify(local, response, key) }.isFailure)
            }
        }
    }
}

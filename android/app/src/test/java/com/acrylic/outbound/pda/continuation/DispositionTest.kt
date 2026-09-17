package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DispositionTest {
    @get:Rule val folder = TemporaryFolder()
    private val fixture get() = JSONObject(File(checkNotNull(System.getProperty("pda.dispositionFixture"))).readText())
    private fun local(f: JSONObject): LocalRecall {
        val body = f.getJSONObject("recovery"); val payload = SubmissionJson.canonical(body)
        return LocalRecall(body.getString("changeRequestId"), body.getString("assignmentId"), "{}", body.getString("recoveryPackageId"),
            payload, JwtVerifier.sha(payload.toByteArray()), "RECALLED")
    }
    @Test fun verifiesActualServerReductionAndSuccessReceipt() {
        val f = fixture; val response = f.getJSONObject("activation")
        ContinuationVerifier.verify(local(f), response, f.getJSONObject("signingKey"))
        assertEquals(0, DispositionRecords.effective(f.getJSONObject("recovery"), response.getJSONArray("dispositions")).size)
        val body = f.getJSONObject("submission"); val payload = SubmissionJson.canonical(body)
        val submission = LocalSubmission(body.getString("submissionId"), "root", payload, JwtVerifier.sha(payload.toByteArray()))
        assertEquals("SYNCED", ReceiptVerifier.verify(submission, f.getJSONObject("receipt"), f.getJSONObject("signingKey")))
    }
    @Test fun forgedDispositionSignatureIsRejected() {
        val f = fixture; f.getJSONObject("activation").getJSONArray("dispositions").getJSONObject(0).put("action", "QUARANTINE")
        assertThrows(IllegalStateException::class.java) { ContinuationVerifier.verify(local(f), f.getJSONObject("activation"), f.getJSONObject("signingKey")) }
    }
    @Test fun historicalDispositionCannotDisappearOrChangeAndNewRowsMustMatch() {
        val f = fixture; val body = f.getJSONObject("recovery"); val proofs = f.getJSONObject("activation").getJSONArray("dispositions")
        DispositionRecords.verifyExtension(body, proofs)
        val wrong = JSONArray(proofs.toString()); wrong.getJSONObject(0).put("expectedDispatchCycle", "2")
        assertThrows(IllegalStateException::class.java) { DispositionRecords.verifyExtension(body, wrong) }
        body.put("dispositions", proofs)
        assertThrows(IllegalStateException::class.java) { DispositionRecords.verifyExtension(body, JSONArray()) }
        assertThrows(IllegalStateException::class.java) { DispositionRecords.verifyExtension(body, wrong) }
    }
    @Test fun localApplyExcludesTotalsPreservesFactsAndRejectsDuplicateScan() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val saved = f.scan(); val scan = f.db.scans().get(saved.id)!!; val body = JSONObject().put("scans", JSONArray().put(SubmissionRecords.scan(scan)))
            val response = response(body)
            f.db.runInTransaction { DispositionInstall(f.store).apply(f.assignment, body, response) }
            assertEquals(scan, f.db.scans().get(saved.id)); assertEquals(0L, f.db.scans().total(f.assignment).count)
            assertNotNull(f.db.scans().guard(scan.barcode)); assertNull(f.db.scans().lastActive(f.assignment))
            assertThrows(IllegalStateException::class.java) { SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "全部卸下") }
            val duplicate = f.db.captures().get(f.scan(scan.barcode).id)!!
            assertEquals("INVALID", duplicate.state); assertTrue(duplicate.error.contains("不能重复装车"))
            f.reload(); assertEquals(1, f.db.dispositions().all(f.assignment).size); assertEquals(0L, f.db.work().order(f.assignment)!!.actualCount)
        }
    }
    @Test fun failedLocalActivationRollsBackDispositionAndTotals() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val saved = f.scan(); val scan = f.db.scans().get(saved.id)!!; val body = JSONObject().put("scans", JSONArray().put(SubmissionRecords.scan(scan)))
            assertThrows(IllegalStateException::class.java) { f.db.runInTransaction {
                DispositionInstall(f.store).apply(f.assignment, body, response(body)); error("activation write failure")
            } }
            assertTrue(f.db.dispositions().all(f.assignment).isEmpty()); assertEquals(1L, f.db.scans().total(f.assignment).count)
            assertEquals(scan, f.db.scans().get(saved.id)); f.store.verifyTotals(f.assignment)
        }
    }
    private fun response(body: JSONObject): JSONObject {
        val s = body.getJSONArray("scans").getJSONObject(0)
        return JSONObject().put("dispositions", JSONArray().put(JSONObject().put("captureId", s.getString("captureId"))
            .put("baleId", s.getString("baleId")).put("barcode", s.getString("barcode")).put("expectedDispatchCycle", s.getString("expectedDispatchCycle"))
            .put("action", "RELEASE").put("approvedEvidenceId", UUID.randomUUID().toString())))
    }
}

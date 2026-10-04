package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverPackageVerifierTest {
    @get:Rule val folder = TemporaryFolder()
    private val f = HandoverPackageFixture()

    @Test fun signedPackageStagesOriginalBytes() {
        val (manifest, bytes) = f.packageBytes()
        val result = HandoverPackageVerifier.verify(manifest, bytes, f.session)
        assertEquals(f.caseId, result.caseId)
        assertEquals(f.assignment, result.originalAssignmentId)
        assertArrayEquals(bytes, result.recoveryBytes)
    }

    @Test fun activatedPackageStagesOriginalBytes() {
        val (manifest, bytes) = f.packageBytes(activated = true)
        val result = HandoverPackageVerifier.verify(manifest, bytes, f.session)
        assertEquals(f.caseId, result.caseId)
        assertArrayEquals(bytes, result.recoveryBytes)
    }

    @Test fun preparedStageCanAdvanceToSignedActivation() = runBlocking<Unit>(Dispatchers.IO) {
        val (preparedManifest, bytes) = f.packageBytes()
        val (activatedManifest, _) = f.packageBytes(activated = true)
        val prepared = HandoverPackageVerifier.verify(preparedManifest, bytes, f.session)
        val activated = HandoverPackageVerifier.verify(activatedManifest, bytes, f.session)
        WorkFixture(folder.root).use { work ->
            saveVerifiedHandoverStage(work.db, prepared, f.session)
            saveVerifiedHandoverStage(work.db, activated, f.session)
            saveVerifiedHandoverStage(work.db, activated, f.session)
            val saved = work.db.handoverStages().get(f.caseId)!!
            assertEquals("ACTIVATED", JSONObject(saved.manifestJson).getString("status"))
            assertArrayEquals(bytes, saved.recoveryBytes)
        }
    }

    @Test fun activationCannotReplacePreparedOriginalBytes() = runBlocking<Unit>(Dispatchers.IO) {
        val (preparedManifest, bytes) = f.packageBytes()
        val (activatedManifest, _) = f.packageBytes(activated = true)
        val prepared = HandoverPackageVerifier.verify(preparedManifest, bytes, f.session)
        val activated = HandoverPackageVerifier.verify(activatedManifest, bytes, f.session)
        WorkFixture(folder.root).use { work ->
            saveVerifiedHandoverStage(work.db, prepared, f.session)
            assertThrows(IllegalStateException::class.java) {
                saveVerifiedHandoverStage(work.db, activated.copy(recoveryBytes = bytes + byteArrayOf(1)), f.session)
            }
            assertEquals(prepared.manifestJson, work.db.handoverStages().get(f.caseId)!!.manifestJson)
        }
    }

    @Test fun activatedPackageWithoutReceiptIsRejected() {
        val (manifest, bytes) = f.packageBytes(activated = true)
        manifest.remove("activationReceipt")
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, f.session) }
    }

    @Test fun activatedPackageWithOtherTargetIsRejected() {
        val (manifest, bytes) = f.packageBytes(activated = true)
        val context = manifest.getJSONObject("stopReceipt").getJSONObject("caseContext")
        val decision = JSONObject().put("caseId", f.caseId).put("status", "ACTIVATED")
            .put("targetDeviceId", UUID.randomUUID().toString()).put("targetUserId", f.targetUser)
            .put("workRound", "2").put("startOrderScanSeq", "8")
        manifest.put("activationReceipt", f.activation(context, decision))
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, f.session) }
    }

    @Test fun preparedPackageWithActivationReceiptIsRejected() {
        val (manifest, bytes) = f.packageBytes(activated = true)
        manifest.put("status", "TARGET_PREPARED")
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, f.session) }
    }

    @Test fun changedManifestTargetIsRejected() {
        val (manifest, bytes) = f.packageBytes()
        manifest.getJSONObject("preparedTicket").put("targetDeviceId", UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, f.session) }
    }

    @Test fun changedSignedSourceIsRejected() {
        val (manifest, bytes) = f.packageBytes()
        manifest.getJSONObject("stopReceipt").getJSONObject("source").put("assignmentId", UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, f.session) }
    }

    @Test fun wrongWarehouseIsRejected() {
        val (manifest, bytes) = f.packageBytes()
        val wrong = JSONObject(f.session.toString()).put("warehouseId", UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, wrong) }
    }

    @Test fun changedOriginalBytesAreRejected() {
        val (manifest, bytes) = f.packageBytes()
        val altered = JSONObject(bytes.toString(Charsets.UTF_8)).put("orderId", UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest,
            SubmissionJson.canonical(altered).toByteArray(), f.session) }
    }

    @Test fun unfamiliarSigningKeyFailsClosed() {
        val (manifest, bytes) = f.packageBytes()
        val wrong = JSONObject(f.session.toString()).put("signingKey",
            JSONObject(f.key.toString()).put("keyId", "rotated"))
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, wrong) }
    }
}

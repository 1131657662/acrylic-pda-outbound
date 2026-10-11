package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.continuation.HandoverImportSigning
import com.acrylic.outbound.pda.sync.SyncWorkGuard
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class FaultWithdrawalReceiptTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }
    private fun request(f: WorkFixture, signer: HandoverImportSigning): JSONObject {
        val a = f.input.assignment
        val receipt = JSONObject().put("changeRequestId", "test-withdrawal").put("assignmentId", f.assignment)
            .put("orderId", a.getString("orderId")).put("revision", a.getInt("revision"))
            .put("issuedRecoveryEpoch", a.getString("serverRecoveryEpoch")).put("status", "WITHDRAWN")
            .put("orderStatus", "DRAFT").put("mayResumeScanning", false).put("originalDeviceStopReceived", false)
        val jwt = signer.jwt(JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-fault-withdrawal")
            .put("receipt", JSONObject(receipt.toString())))
        return JSONObject().put("changeRequestId", "test-withdrawal").put("status", "WITHDRAWN")
            .put("resolutionProof", receipt.put("signedReceipt", jwt))
    }
    @Test fun verifiedWithdrawalPersistsFenceAcrossReopenWithoutDeletingOriginalScans() = scenario { f ->
        f.scan(); val scans = f.db.scans().page(f.assignment, 0, 10); val signer = HandoverImportSigning()
        val order = f.db.preparations().order(f.assignment)!!.copy(signingKeyJson = signer.key.toString())
        val command = request(f, signer); val proof = FaultWithdrawalReceipt.verify(command, order, f.assignment)
        assertFalse(proof.getBoolean("originalDeviceStopReceived"))
        f.db.recalls().insert(LocalRecall("test-withdrawal", f.assignment, command.toString(), state = "WITHDRAWN", proof = proof.toString()))
        f.reload(); assertThrows(IllegalStateException::class.java) { SyncWorkGuard(f.db).requireAllowed(order) }
        assertEquals(scans, f.db.scans().page(f.assignment, 0, 10)); assertNotNull(f.db.scans().guard(f.barcodes[0]))
    }
    @Test fun tamperedReceiptOrDifferentOriginalAssignmentCannotBecomeRevocationProof() = scenario { f ->
        val signer = HandoverImportSigning(); val order = f.db.preparations().order(f.assignment)!!.copy(signingKeyJson = signer.key.toString())
        val tampered = request(f, signer); tampered.getJSONObject("resolutionProof").put("mayResumeScanning", true)
        assertThrows(IllegalStateException::class.java) { FaultWithdrawalReceipt.verify(tampered, order, f.assignment) }
        assertThrows(IllegalStateException::class.java) { FaultWithdrawalReceipt.verify(request(f, signer), order, "different-assignment") }
        assertNull(f.db.recalls().header(f.assignment)); assertEquals("SCANNING", f.db.work().order(f.assignment)!!.state)
    }
}

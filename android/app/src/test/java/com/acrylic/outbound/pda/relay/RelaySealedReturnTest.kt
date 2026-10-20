package com.acrylic.outbound.pda.relay

import com.acrylic.outbound.pda.submission.ReceiptVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporarySuccessJson
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.UUID

/** Host-only replay of sealed transport evidence, never a new HTTP or device receipt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class RelaySealedReturnTest {
    private lateinit var vector: RelaySealedVector

    @Before fun loadExplicitExternalEvidence() {
        val path = System.getenv("PDA_RELAY_SEALED_VECTOR")
        assumeTrue("Sealed external evidence must be explicitly supplied", !path.isNullOrBlank())
        vector = RelaySealedVector(File(checkNotNull(path)))
    }

    @After fun preserveOriginalBytes() {
        if (::vector.isInitialized) vector.unchanged()
    }

    private fun verify(bytes: ByteArray = vector.returned): JSONObject = RelayReceiptVerifier.verify(
        bytes, vector.identity, vector.payload, vector.device, vector.key)

    @Test fun sealedPythonReturnPassesBothAndroidSignatureConsumers() {
        val receipt = verify()
        assertEquals("SYNCED", ReceiptVerifier.verify(vector.local(), receipt, vector.key))
        assertEquals("SUCCEEDED", receipt.getString("status"))
        assertEquals(JSONObject(vector.payload).getString("expectedCount"), receipt.getString("actualCount"))
        assertEquals(JSONObject(vector.payload).getString("expectedWeightGrams"), receipt.getString("actualWeightGrams"))
        assertEquals(false, TemporarySuccessJson.parse(vector.returned).get("localDeletionAllowed"))
    }

    @Test fun downloadedBytesMatchTheSameSealedProcessResponse() {
        assertEquals(SubmissionJson.canonical(vector.processed),
            SubmissionJson.canonical(TemporarySuccessJson.parse(vector.returned)))
    }

    @Test fun differentProvisionedRootRejectsSealedReturn() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val key = vector.key.put("publicKey", Base64.getEncoder().encodeToString(other.public.encoded))
        assertThrows(Exception::class.java) { RelayReceiptVerifier.verify(
            vector.returned, vector.identity, vector.payload, vector.device, key) }
    }

    @Test fun originalIdentityMismatchRejectsSealedReturn() {
        for (field in listOf("operatorId", "deviceId", "warehouseId", "localOrderId")) {
            val identity = vector.identity.put(field, UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { RelayReceiptVerifier.verify(
                vector.returned, identity, vector.payload, vector.device, vector.key) }
        }
    }

    @Test fun originalPayloadByteChangeRejectsSealedReturn() {
        assertThrows(Exception::class.java) { RelayReceiptVerifier.verify(
            vector.returned, vector.identity, vector.payload + " ", vector.device, vector.key) }
    }

    @Test fun changedBusinessTotalsRejectSealedReturn() {
        val file = TemporarySuccessJson.parse(vector.returned)
        file.getJSONObject("businessReceipt").put("actualWeightGrams", "0")
        assertThrows(Exception::class.java) { verify(file.toString().toByteArray()) }
    }

    @Test fun changedDeletionBoundaryRejectsSealedReturn() {
        val file = TemporarySuccessJson.parse(vector.returned).put("localDeletionAllowed", true)
        assertThrows(Exception::class.java) { verify(file.toString().toByteArray()) }
    }

    @Test fun duplicateEnvelopeFieldRejectsSealedReturn() {
        val original = vector.returned.toString(Charsets.UTF_8)
        val duplicate = "{\"localDeletionAllowed\":false," + original.substringAfter('{')
        assertThrows(Exception::class.java) { verify(duplicate.toByteArray()) }
    }

    @Test fun sealedSuccessCannotRegressOrReplaceAnExistingTerminal() {
        val receipt = verify()
        RelayReceiptVerifier.requireForward("SYNCED", receipt.toString(), "SYNCED", receipt)
        assertThrows(Exception::class.java) { RelayReceiptVerifier.requireForward(
            "SYNCED", receipt.toString(), "REVIEW_REQUIRED", JSONObject().put("status", "REVIEW_REQUIRED")) }
        val changed = JSONObject(receipt.toString()).put("actualCount", "0")
        assertThrows(Exception::class.java) { RelayReceiptVerifier.requireForward("SYNCED", receipt.toString(), "SYNCED", changed) }
    }
}

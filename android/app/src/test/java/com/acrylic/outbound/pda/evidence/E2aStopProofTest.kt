package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class E2aStopProofTest {
    @Test fun stopCoreMatchesServerVectorAndVerifiesServerSignature() {
        val vector = vector()
        val facts = facts(JSONObject(vector.getString("stopCoreUtf8")))
        val canonical = SubmissionJson.canonical(E2aStopProof.core(facts))
        assertEquals(vector.getString("stopCoreUtf8"), canonical)
        val verifier = Signature.getInstance("SHA256withECDSA")
        val publicKey = java.security.KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(vector.getString("ecPublicKeySpkiBase64"))))
        verifier.initVerify(publicKey)
        verifier.update("ACRYLIC-R06/E2A/STOP/1\n$canonical".toByteArray())
        check(verifier.verify(Base64.getDecoder().decode(vector.getString("stopSignatureDerBase64"))))
        assertEquals(E2aWire.sha(publicKey.encoded), vector.getString("ecKeyId"))
    }

    @Test fun signerMustUseTheExactOriginalPublicKey() {
        val generated = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val signer: (String) -> String = { input ->
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(generated.private)
            signature.update(input.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(signature.sign())
        }
        val facts = facts(JSONObject(vector().getString("stopCoreUtf8")))
        val publicDer = Base64.getEncoder().encodeToString(generated.public.encoded)
        val envelope = E2aStopProof.sign(facts, publicDer, signer)
        assertEquals(E2aWire.sha(generated.public.encoded), envelope.getString("keyId"))
        assertThrows(IllegalStateException::class.java) {
            E2aStopProof.sign(facts.copy(captureHighWatermark = -1), publicDer, signer)
        }
        val other = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        assertThrows(IllegalStateException::class.java) {
            E2aStopProof.sign(facts, Base64.getEncoder().encodeToString(other.public.encoded), signer)
        }
        val wrongCurve = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp384r1"))
        }.generateKeyPair()
        assertThrows(IllegalStateException::class.java) {
            E2aStopProof.sign(facts, Base64.getEncoder().encodeToString(wrongCurve.public.encoded), signer)
        }
    }

    private fun vector() = JSONObject(File(checkNotNull(System.getProperty("pda.e2aVector"))).readText())

    private fun facts(core: JSONObject) = E2aStopProof.Facts(core.getString("warehouseId"), core.getString("sourceId"),
        core.getString("submissionId"), core.getLong("workRound"), core.getString("originalOperatorId"),
        core.getString("freezeDeviceId"), core.getLong("sourceRecoveryEpoch"), core.getString("stopEventId"),
        core.getLong("captureHighWatermark"), core.getLong("auditThroughSeq"), core.getLong("stopTransactionVersion"),
        core.getString("manifestId"), core.getLong("manifestVersion"), core.getString("manifestBytesHash"),
        core.getString("setHash"))
}

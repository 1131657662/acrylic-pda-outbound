package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** Generated test identity only; never represents a field device or a server receipt. */
internal class HandoverImportSigning {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val key = JSONObject().put("algorithm", "RS256").put("keyId", "import-test-key")
        .put("publicKey", Base64.getEncoder().encodeToString(pair.public.encoded))

    fun receipt(body: JSONObject, stop: Boolean = false): JSONObject {
        body.put("capabilities", JSONObject().put("writeConfirmationAvailable", false).put("releaseAllowed", false)
            .put("mayResumeScanning", false).put("stockReleased", false).put("localDeletionAllowed", false))
        val claims = JSONObject().put("iss", if (stop) "acrylic-outbound" else "acrylic-outbound-server")
            .put("aud", if (stop) "acrylic-pda-handover-stop" else "acrylic-outbound-handover-receipt")
            .put("receipt", body)
        return body.put("signedReceipt", jwt(claims))
    }

    fun jwt(claims: JSONObject): String {
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT").put("kid", "import-test-key")
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val input = encoder.encodeToString(SubmissionJson.canonical(header).toByteArray()) + "." +
            encoder.encodeToString(SubmissionJson.canonical(claims).toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(pair.private); update(input.toByteArray(Charsets.US_ASCII))
        }.sign()
        return input + "." + encoder.encodeToString(signature)
    }
}
